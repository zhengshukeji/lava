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
 * 按 UID 降序分页的游标，{@code beforeUid} 为排他上界。
 *
 * <p>游标只能用于创建它的同一文件夹和同一 UIDVALIDITY；邮箱重建后应丢弃旧游标并从第一页开始。</p>
 *
 * @param folder      文件夹全名
 * @param uidValidity 创建游标时邮箱的 UIDVALIDITY
 * @param beforeUid   下一页只读取小于该值的 UID
 */
public record MailCursor(String folder, long uidValidity, long beforeUid) {
    /**
     * 校验并规范化分页游标。
     *
     * @param folder      文件夹全名
     * @param uidValidity 邮箱 UIDVALIDITY
     * @param beforeUid   UID 排他上界
     */
    public MailCursor {
        folder = MailValidationUtils.requireNonBlankWithoutControls(folder, "folder");
        if (!MailMessageId.validUid(uidValidity) || !MailMessageId.validUid(beforeUid)) {
            throw new IllegalArgumentException(
                    "uidValidity and beforeUid must be unsigned 32-bit positive values");
        }
    }
}
