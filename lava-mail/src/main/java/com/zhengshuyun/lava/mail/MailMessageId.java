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

/**
 * 由文件夹名、UIDVALIDITY 和 UID 组成的稳定 IMAP 消息标识。
 *
 * @param folder      文件夹全名
 * @param uidValidity 邮箱的 UIDVALIDITY
 * @param uid         消息 UID
 */
public record MailMessageId(String folder, long uidValidity, long uid) {
    static final long MAX_IMAP_UID = 0xffff_ffffL;

    /**
     * 校验并规范化消息标识。
     *
     * @param folder      文件夹全名
     * @param uidValidity 邮箱 UIDVALIDITY
     * @param uid         消息 UID
     */
    public MailMessageId {
        folder = MailValidationUtils.requireNonBlankWithoutControls(folder, "folder");
        if (!validUid(uidValidity) || !validUid(uid)) {
            throw new IllegalArgumentException(
                    "uidValidity and uid must be unsigned 32-bit positive values");
        }
    }

    static boolean validUid(long value) {
        return value >= 1 && value <= MAX_IMAP_UID;
    }
}
