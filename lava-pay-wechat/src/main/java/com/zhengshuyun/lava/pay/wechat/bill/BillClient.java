/*
 * Copyright 2026 整数科技 (zhengshuyun.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.zhengshuyun.lava.pay.wechat.bill;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.zhengshuyun.lava.core.lang.ValidationUtils;
import com.zhengshuyun.lava.http.HttpStream;
import com.zhengshuyun.lava.pay.wechat.exception.*;
import com.zhengshuyun.lava.pay.wechat.internal.WechatPayTransport;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.zip.GZIPInputStream;

/**
 * 微信支付交易账单、资金账单申请与安全下载客户端。
 */
public final class BillClient {
    /** 申请交易账单下载信息的 APIv3 固定路径。 */
    private static final String TRADE_BILL_PATH = "/v3/bill/tradebill";

    /** 申请资金账单下载信息的 APIv3 固定路径。 */
    private static final String FUND_FLOW_BILL_PATH = "/v3/bill/fundflowbill";

    /** 根客户端共享的签名传输层与关闭状态。 */
    private final WechatPayTransport transport;

    /**
     * 由根客户端创建账单入口。
     *
     * @param transport 共享协议传输层
     */
    public BillClient(WechatPayTransport transport) {
        this.transport = ValidationUtils.requireNonNull(transport, "transport");
    }

    /**
     * 申请交易账单下载信息。
     *
     * @param request 交易账单参数
     * @return 已验签下载信息
     */
    public BillDownloadInfo applyTradeBill(TradeBillRequest request) {
        transport.ensureOpen();
        ValidationUtils.requireNonNull(request, "request must not be null");
        URI uri = transport.query(transport.endpoint(TRADE_BILL_PATH), "bill_date",
                request.billDate().format(DateTimeFormatter.ISO_LOCAL_DATE));
        if (request.billType() != null) {
            uri = transport.query(uri, "bill_type", request.billType().name());
        }
        if (request.tarType() != null) {
            uri = transport.query(uri, "tar_type", request.tarType().name());
        }
        BillInfoPayload payload = transport.get(uri, BillInfoPayload.class);
        return payload.toPublic(request.tarType());
    }

    /**
     * 申请资金账单下载信息。
     *
     * @param request 资金账单参数
     * @return 已验签下载信息
     */
    public BillDownloadInfo applyFundFlowBill(FundFlowBillRequest request) {
        transport.ensureOpen();
        ValidationUtils.requireNonNull(request, "request must not be null");
        URI uri = transport.query(transport.endpoint(FUND_FLOW_BILL_PATH), "bill_date",
                request.billDate().format(DateTimeFormatter.ISO_LOCAL_DATE));
        if (request.accountType() != null) {
            uri = transport.query(uri, "account_type", request.accountType().name());
        }
        if (request.tarType() != null) {
            uri = transport.query(uri, "tar_type", request.tarType().name());
        }
        BillInfoPayload payload = transport.get(uri, BillInfoPayload.class);
        return payload.toPublic(request.tarType());
    }

    /**
     * 将账单流式下载到目标路径并校验 SHA-1。目标已存在时拒绝覆盖，GZIP 响应会先解压。
     *
     * <p>目标文件系统必须支持硬链接，以便完整发布已校验内容并排他占用目标名称；
     * 不支持时按文件 IO 失败处理，不退化为覆盖移动或逐字节复制到目标。</p>
     *
     * @param info 申请账单返回的已验签下载信息
     * @param target 目标文件路径
     * @return 最终路径、大小和实际摘要
     * @throws WechatPayFileException 目标已存在、目标无效或文件系统不能完成安全发布时抛出
     */
    public BillDownloadResult download(BillDownloadInfo info, Path target) {
        transport.ensureOpen();
        ValidationUtils.requireNonNull(info, "info must not be null");
        ValidationUtils.requireNonNull(target, "target must not be null");
        if (!"SHA1".equalsIgnoreCase(info.hashType())) {
            throw new WechatPayProtocolException("微信支付返回了不支持的账单摘要类型");
        }

        Path absoluteTarget = target.toAbsolutePath().normalize();
        if (Files.exists(absoluteTarget, LinkOption.NOFOLLOW_LINKS)) {
            throw new WechatPayFileException(WechatPayFileFailure.TARGET_EXISTS, null);
        }
        Path parent = absoluteTarget.getParent();
        if (parent == null || !Files.isDirectory(parent)) {
            throw new WechatPayFileException(WechatPayFileFailure.INVALID_TARGET, null);
        }

        Path temporary = null;
        try {
            String fileName = absoluteTarget.getFileName().toString();
            temporary = Files.createTempFile(parent, "." + fileName + ".", ".part");
            MessageDigest digest = MessageDigest.getInstance("SHA-1");

            // 1. 账单响应按官方规则不含签名；GZIP 响应需先解压，再按账单原文计算 SHA-1。
            try (HttpStream stream = transport.openDownload(info.downloadUrl());
                 InputStream responseBody = stream.body();
                 InputStream input = info.tarType() == BillTarType.GZIP
                         ? new GZIPInputStream(responseBody) : responseBody;
                 OutputStream output = Files.newOutputStream(temporary,
                         StandardOpenOption.TRUNCATE_EXISTING)) {
                byte[] buffer = new byte[16 * 1024];
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    if (read == 0) {
                        continue;
                    }
                    digest.update(buffer, 0, read);
                    output.write(buffer, 0, read);
                }
            }

            // 2. 摘要不一致时绝不发布部分或被篡改的账单文件。
            String actualHash = HexFormat.of().formatHex(digest.digest());
            if (!actualHash.equalsIgnoreCase(info.hashValue())) {
                throw new WechatPaySecurityException(WechatPaySecurityFailure.HASH_MISMATCH);
            }

            // 3. 同目录硬链接一次性发布完整内容，并在目标名称已被占用时失败。
            // ATOMIC_MOVE 可能替换竞争写入方的文件，不能用于保证不覆盖；不支持硬链接时明确失败。
            long size = Files.size(temporary);
            Files.createLink(absoluteTarget, temporary);
            return new BillDownloadResult(
                    absoluteTarget,
                    size,
                    "SHA1",
                    actualHash
            );
        } catch (FileAlreadyExistsException exception) {
            throw new WechatPayFileException(WechatPayFileFailure.TARGET_EXISTS, exception);
        } catch (WechatPayException exception) {
            throw exception;
        } catch (IOException | NoSuchAlgorithmException | UnsupportedOperationException exception) {
            throw new WechatPayFileException(WechatPayFileFailure.IO, exception);
        } finally {
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (IOException ignored) {
                    // 只清理临时名称；失败不删除已发布的目标，也不覆盖主要操作结果。
                }
            }
        }
    }

    /**
     * 承载申请账单接口返回的下载元数据，转为公开模型前校验必填字段。
     *
     * @param hashType 文件摘要算法，当前微信支付固定返回 {@code SHA1}
     * @param hashValue 账单原文的 40 位十六进制 SHA-1 摘要
     * @param downloadUrl 带短时效下载令牌的账单地址，不得记录到日志
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record BillInfoPayload(
            @JsonProperty("hash_type") String hashType,
            @JsonProperty("hash_value") String hashValue,
            @JsonProperty("download_url") URI downloadUrl) {

        /**
         * 校验微信支付返回的账单下载元数据。
         *
         * @throws IllegalArgumentException 摘要类型、摘要值或下载地址缺失时抛出
         */
        private BillInfoPayload {
            ValidationUtils.requireNotBlank(hashType, "hashType must not be blank");
            ValidationUtils.requireNotBlank(hashValue, "hashValue must not be blank");
            ValidationUtils.requireNonNull(downloadUrl, "downloadUrl must not be null");
        }

        /**
         * 将内部响应映射为会在文本表示中隐藏下载令牌的公开模型。
         *
         * @param tarType 申请账单时指定的压缩类型；未指定时为 {@code null}
         * @return 校验通过的账单下载信息
         * @throws WechatPayProtocolException 响应字段不符合账单下载协议时抛出
         */
        private BillDownloadInfo toPublic(@Nullable BillTarType tarType) {
            try {
                return new BillDownloadInfo(
                        hashType,
                        hashValue,
                        downloadUrl,
                        tarType
                );
            } catch (IllegalArgumentException exception) {
                throw new WechatPayProtocolException("微信支付账单下载信息不符合接口约束", exception);
            }
        }
    }
}
