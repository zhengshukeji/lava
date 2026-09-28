/*
 * Copyright 2026 整数科技 (zhengshuyun.com)
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.zhengshuyun.lava.pay.wechat;

import com.zhengshuyun.lava.http.HttpClient;
import com.zhengshuyun.lava.http.OkHttpInterop;
import com.zhengshuyun.lava.pay.wechat.bill.*;
import com.zhengshuyun.lava.pay.wechat.exception.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 验证微信支付账单申请、流式下载、解压、哈希校验和原子发布的安全边界。
 */
class BillClientTest {
    /**
     * 用于校验账单日期窗口和响应签名时间的固定 UTC 时钟。
     */
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-08-29T00:00:00Z"), ZoneOffset.UTC);
    /**
     * 客户端请求签名使用的测试商户 RSA 密钥对。
     */
    private static KeyPair merchantKeys;
    /**
     * 模拟服务端响应签名使用的微信支付 RSA 密钥对。
     */
    private static KeyPair wechatKeys;

    /**
     * JUnit 为每个测试提供的隔离目录，用于断言账单文件与临时文件状态。
     */
    @TempDir
    Path temporaryDirectory;
    /**
     * 每个测试独占的本地微信支付协议模拟服务端。
     */
    private WechatPayTestServer server;
    /**
     * 指向本地模拟服务端的待测微信支付根客户端。
     */
    private WechatPayClient client;

    /**
     * 为全部账单测试生成一次性 RSA 2048 位商户密钥和微信支付密钥。
     *
     * @throws Exception 当当前 JCA 环境无法创建 RSA 密钥生成器时抛出
     */
    @BeforeAll
    static void generateKeys() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        merchantKeys = generator.generateKeyPair();
        wechatKeys = generator.generateKeyPair();
    }

    /**
     * 在每个测试前启动独立模拟服务端，并构建具有固定时钟的待测客户端。
     */
    @BeforeEach
    void setUp() {
        server = WechatPayTestServer.start(wechatKeys.getPrivate(), CLOCK);
        client = WechatPayClient.builder()
                .mchid("1900000109")
                .merchantPrivateKey(merchantKeys.getPrivate())
                .merchantSerialNo("ABCDEF")
                .apiV3Key("0123456789abcdef0123456789abcdef")
                .wechatPayPublicKeyId(WechatPayTestServer.PUBLIC_KEY_ID)
                .wechatPayPublicKey(wechatKeys.getPublic())
                .apiBaseUrl(server.baseUrl())
                .clock(CLOCK)
                .build();
    }

    /**
     * 在每个测试后关闭待测客户端和本地模拟服务端。
     */
    @AfterEach
    void tearDown() {
        client.close();
        server.close();
    }

    /**
     * 验证账单被流式下载、SHA-1 校验后发布，并在目标已存在时拒绝覆盖。
     *
     * @throws Exception 当测试摘要计算或临时文件读取失败时抛出
     */
    @Test
    void billIsStreamedVerifiedAndPublishedWithoutOverwrite() throws Exception {
        byte[] file = "交易时间,商户订单号\n2026-08-28,ORDER_001\n"
                .getBytes(StandardCharsets.UTF_8);
        String hash = sha1(file);
        String info = "{\"hash_type\":\"SHA1\",\"hash_value\":\"" + hash
                + "\",\"download_url\":\"" + server.baseUrl()
                + "download?token=secret\"}";
        server.enqueueSigned(200, info);
        server.enqueueUnsigned(200, file, "text/csv");

        BillDownloadInfo download = client.bills().applyTradeBill(
                TradeBillRequest.builder()
                        .billDate(LocalDate.of(2026, 8, 28))
                        .billType(TradeBillType.ALL)
                        .build());
        Path target = temporaryDirectory.resolve("trade.csv");
        BillDownloadResult result = client.bills().download(download, target);

        assertArrayEquals(file, Files.readAllBytes(target));
        assertEquals(file.length, result.size());
        assertEquals(hash, result.hashValue());
        try (var files = Files.list(temporaryDirectory)) {
            assertEquals(List.of(target), files.toList());
        }
        assertTrue(server.takeRequest().target().contains("bill_date=2026-08-28"));
        assertEquals("/download?token=secret", server.takeRequest().target());

        WechatPayFileException exists = assertThrows(WechatPayFileException.class,
                () -> client.bills().download(download, target));
        assertEquals(WechatPayFileFailure.TARGET_EXISTS, exists.failure());
    }

    /**
     * 目标在下载开始后出现时，发布必须失败并保留其他写入方的完整文件。
     *
     * @throws Exception 本地文件操作或摘要计算失败时抛出
     */
    @Test
    void targetCreatedDuringDownloadIsNeverOverwritten() throws Exception {
        byte[] file = "downloaded-bill".getBytes(StandardCharsets.UTF_8);
        Path target = temporaryDirectory.resolve("race.csv");
        BillDownloadInfo info = new BillDownloadInfo(
                "SHA1",
                sha1(file),
                server.baseUrl().resolve("download"),
                null
        );
        server.enqueueUnsigned(200, file, "text/csv");

        HttpClient.Builder httpBuilder = HttpClient.builder().followRedirects(false);
        OkHttpInterop.addInterceptor(httpBuilder, chain -> {
            // 拦截器在下载方法的初始存在性检查之后执行，确定性地重现竞争窗口。
            Files.writeString(target, "existing-business-data", StandardOpenOption.CREATE_NEW);
            return chain.proceed(chain.request());
        });
        try (HttpClient http = httpBuilder.build();
             WechatPayClient racingClient = WechatPayClient.builder()
                     .mchid("1900000109")
                     .merchantPrivateKey(merchantKeys.getPrivate())
                     .merchantSerialNo("ABCDEF")
                     .apiV3Key("0123456789abcdef0123456789abcdef")
                     .wechatPayPublicKeyId(WechatPayTestServer.PUBLIC_KEY_ID)
                     .wechatPayPublicKey(wechatKeys.getPublic())
                     .apiBaseUrl(server.baseUrl())
                     .httpClient(http)
                     .build()) {
            WechatPayFileException failure = assertThrows(WechatPayFileException.class,
                    () -> racingClient.bills().download(info, target));

            assertEquals(WechatPayFileFailure.TARGET_EXISTS, failure.failure());
            assertEquals("existing-business-data", Files.readString(target));
            try (var files = Files.list(temporaryDirectory)) {
                assertEquals(List.of(target), files.toList());
            }
        }
    }

    /**
     * 悬空符号链接也占用了目标名称，不能被当作不存在的文件覆盖。
     *
     * @throws Exception 创建符号链接或计算摘要失败时抛出
     */
    @Test
    @EnabledOnOs({OS.LINUX, OS.MAC})
    void danglingSymbolicLinkIsNotReplaced() throws Exception {
        byte[] file = "downloaded-bill".getBytes(StandardCharsets.UTF_8);
        Path target = temporaryDirectory.resolve("link.csv");
        Path missing = temporaryDirectory.resolve("missing.csv");
        Files.createSymbolicLink(target, missing);
        BillDownloadInfo info = new BillDownloadInfo(
                "SHA1",
                sha1(file),
                server.baseUrl().resolve("download"),
                null
        );
        server.enqueueUnsigned(200, file, "text/csv");

        WechatPayFileException failure = assertThrows(WechatPayFileException.class,
                () -> client.bills().download(info, target));

        assertEquals(WechatPayFileFailure.TARGET_EXISTS, failure.failure());
        assertTrue(Files.exists(target, LinkOption.NOFOLLOW_LINKS));
        assertEquals(missing, Files.readSymbolicLink(target));
        assertFalse(Files.exists(missing));
    }

    /**
     * 不支持硬链接的文件系统明确失败，不退化为可能覆盖目标的移动或暴露半文件的复制。
     *
     * @throws Exception ZIP 文件系统创建或摘要计算失败时抛出
     */
    @Test
    void unsupportedPublicationLeavesNoTargetOrTemporaryFile() throws Exception {
        byte[] file = "downloaded-bill".getBytes(StandardCharsets.UTF_8);
        BillDownloadInfo info = new BillDownloadInfo(
                "SHA1",
                sha1(file),
                server.baseUrl().resolve("download"),
                null
        );
        server.enqueueUnsigned(200, file, "text/csv");
        URI archive = URI.create("jar:" + temporaryDirectory.resolve("bills.zip").toUri());
        try (var fileSystem = FileSystems.newFileSystem(archive, Map.of("create", "true"))) {
            Path target = fileSystem.getPath("/trade.csv");

            WechatPayFileException failure = assertThrows(WechatPayFileException.class,
                    () -> client.bills().download(info, target));

            assertEquals(WechatPayFileFailure.IO, failure.failure());
            assertFalse(Files.exists(target));
            try (var files = Files.list(fileSystem.getPath("/"))) {
                assertEquals(0, files.count());
            }
        }
    }

    /**
     * 验证账单 SHA-1 不匹配时关闭失败，既不发布目标文件，也不遗留 {@code .part} 临时文件。
     *
     * @throws Exception 当枚举测试临时目录内容失败时抛出
     */
    @Test
    void hashMismatchLeavesNoPublishedOrPartialFile() throws Exception {
        byte[] file = "changed".getBytes(StandardCharsets.UTF_8);
        String info = "{\"hash_type\":\"SHA1\",\"hash_value\":\""
                + "0000000000000000000000000000000000000000"
                + "\",\"download_url\":\"" + server.baseUrl() + "download\"}";
        server.enqueueSigned(200, info);
        server.enqueueUnsigned(200, file, "text/csv");
        BillDownloadInfo download = client.bills().applyFundFlowBill(
                FundFlowBillRequest.builder()
                        .billDate(LocalDate.of(2026, 8, 28))
                        .build());
        Path target = temporaryDirectory.resolve("fund.csv");

        WechatPaySecurityException failure = assertThrows(WechatPaySecurityException.class,
                () -> client.bills().download(download, target));

        assertEquals(WechatPaySecurityFailure.HASH_MISMATCH, failure.failure());
        assertFalse(Files.exists(target));
        try (var files = Files.list(temporaryDirectory)) {
            assertTrue(files.noneMatch(path -> path.getFileName().toString().endsWith(".part")));
        }
    }

    /**
     * 验证 GZIP 账单先解压再对原文执行 SHA-1 校验，最终发布的文件也为解压后内容。
     *
     * @throws Exception 当测试压缩、摘要计算或临时文件读取失败时抛出
     */
    @Test
    void gzipBillIsDecompressedBeforeHashVerificationAndPublication() throws Exception {
        byte[] file = "交易时间,商户订单号\n2026-08-28,ORDER_001\n"
                .getBytes(StandardCharsets.UTF_8);
        String info = "{\"hash_type\":\"SHA1\",\"hash_value\":\"" + sha1(file)
                + "\",\"download_url\":\"" + server.baseUrl()
                + "download?token=secret\"}";
        server.enqueueSigned(200, info);
        server.enqueueUnsigned(200, gzip(file), "application/gzip");

        BillDownloadInfo download = client.bills().applyTradeBill(
                TradeBillRequest.builder()
                        .billDate(LocalDate.of(2026, 8, 28))
                        .tarType(BillTarType.GZIP)
                        .build());
        Path target = temporaryDirectory.resolve("trade.csv");
        BillDownloadResult result = client.bills().download(download, target);

        assertArrayEquals(file, Files.readAllBytes(target));
        assertEquals(file.length, result.size());
        assertTrue(server.takeRequest().target().contains("tar_type=GZIP"));
        assertEquals("/download?token=secret", server.takeRequest().target());
    }

    /**
     * 验证手工构造的跨源下载地址被拒绝，防止携带敏感令牌访问非微信支付主机，且调试文本不泄露令牌。
     */
    @Test
    void manuallyConstructedDownloadInfoCannotSendCredentialsToAnotherOrigin() {
        BillDownloadInfo forged = new BillDownloadInfo(
                "SHA1",
                "0000000000000000000000000000000000000000",
                URI.create("https://example.com/v3/billdownload/file?token=secret"),
                null
        );
        assertFalse(forged.toString().contains("secret"));
        Path target = temporaryDirectory.resolve("forged.csv");

        assertThrows(WechatPayProtocolException.class,
                () -> client.bills().download(forged, target));
        assertFalse(Files.exists(target));
    }

    /**
     * 计算测试账单原文的 SHA-1 小写十六进制摘要。
     *
     * @param value 待计算摘要的原始字节
     * @return 40 个小写十六进制字符组成的 SHA-1 摘要
     * @throws Exception 当当前 JCA 环境不支持 SHA-1 时抛出
     */
    private static String sha1(byte[] value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(value));
    }

    /**
     * 将测试账单原文压缩为 GZIP 字节，用于模拟压缩账单下载。
     *
     * @param value 待压缩的原始账单字节
     * @return 完整的 GZIP 数据
     * @throws Exception 当内存输出或 GZIP 写入失败时抛出
     */
    private static byte[] gzip(byte[] value) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(output)) {
            gzip.write(value);
        }
        return output.toByteArray();
    }
}
