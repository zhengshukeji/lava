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
package com.zhengshuyun.lava.mail;

import com.zhengshuyun.lava.core.lang.ValidationUtils;

import java.util.Arrays;

/**
 * 发信时使用的内存附件，构造和读取内容时均执行防御性复制。
 *
 * @param fileName    附件文件名
 * @param contentType MIME 内容类型
 * @param content     附件字节内容
 */
@SuppressWarnings("ArrayRecordComponent") // 构造和读取时都执行防御性复制，不会暴露内部数组。
public record MailAttachment(String fileName, String contentType, byte[] content) {
    /**
     * 校验附件元数据并复制附件字节。
     *
     * @param fileName    附件文件名
     * @param contentType MIME 内容类型
     * @param content     附件字节内容
     */
    public MailAttachment {
        fileName = MailValidationUtils.requireNonBlank(fileName, "fileName");
        contentType = MailValidationUtils.requireNonBlank(contentType, "contentType");
        ValidationUtils.requireNonNull(content, "content");
        if (fileName.codePoints().anyMatch(Character::isISOControl)
                || contentType.codePoints().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("fileName and contentType must not contain control characters");
        }
        content = content.clone();
    }

    /**
     * 返回附件内容副本。
     *
     * @return 附件字节副本
     */
    @Override
    public byte[] content() {
        return content.clone();
    }

    /**
     * 返回附件字节数。
     *
     * @return 附件大小
     */
    public long size() {
        return content.length;
    }

    /**
     * record 对数组组件默认按引用比较，这里改为按附件字节内容比较，以保持值对象语义。
     */
    @Override
    public boolean equals(Object object) {
        return this == object
                || object instanceof MailAttachment other
                && fileName.equals(other.fileName)
                && contentType.equals(other.contentType)
                && Arrays.equals(content, other.content);
    }

    /**
     * 与 {@link #equals(Object)} 一致地按附件字节内容计算哈希值。
     */
    @Override
    public int hashCode() {
        int result = fileName.hashCode();
        result = 31 * result + contentType.hashCode();
        return 31 * result + Arrays.hashCode(content);
    }
}
