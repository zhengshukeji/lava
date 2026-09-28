/*
 * Copyright 2026 整数科技 (zhengshuyun.com)
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.zhengshuyun.lava.pay.wechat.bill;

import org.jspecify.annotations.Nullable;

import java.net.URI;

/**
 * 已验签的账单下载信息。
 *
 * @param hashType 文件摘要类型，当前固定为 {@code SHA1}
 * @param hashValue 账单原文的 40 位十六进制 SHA-1 摘要
 * @param downloadUrl 带敏感下载令牌且通常五分钟内有效的绝对地址
 * @param tarType 申请时使用的压缩类型；未指定时为 {@code null}
 */
public record BillDownloadInfo(
        String hashType,
        String hashValue,
        URI downloadUrl,
        @Nullable BillTarType tarType
) {
    /**
     * 返回不包含账单下载令牌的安全摘要。
     *
     * @return 已脱敏文本
     */
    @Override
    public String toString() {
        return "BillDownloadInfo[hashType=" + hashType
                + ", hashValue=[redacted], downloadUrl=[redacted], tarType="
                + tarType + ']';
    }
}
