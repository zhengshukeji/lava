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
import org.jspecify.annotations.Nullable;

import java.time.Instant;

/**
 * 单次邮箱分页查询的过滤条件和游标。
 *
 * @param folder          文件夹全名；为 {@code null} 时使用 IMAP 配置的默认文件夹
 * @param pageSize        每页最多返回的消息数，范围为 1 到 1000
 * @param cursor          分页游标；第一页为 {@code null}
 * @param unreadOnly      是否只返回未读邮件
 * @param receivedAfter   只返回收件时间严格晚于该时刻的邮件
 * @param receivedBefore  只返回收件时间严格早于该时刻的邮件
 * @param fromContains    发件地址或显示名包含的文本，按大小写不敏感匹配
 * @param subjectContains 主题包含的文本，按大小写不敏感匹配
 */
public record MailQuery(
        @Nullable String folder,
        int pageSize,
        @Nullable MailCursor cursor,
        boolean unreadOnly,
        @Nullable Instant receivedAfter,
        @Nullable Instant receivedBefore,
        @Nullable String fromContains,
        @Nullable String subjectContains) {

    /**
     * 校验并规范化查询条件。
     *
     * @param folder          可选文件夹全名
     * @param pageSize        每页最大结果数
     * @param cursor          可选分页游标
     * @param unreadOnly      是否只读未读邮件
     * @param receivedAfter   可选收件时间下界
     * @param receivedBefore  可选收件时间上界
     * @param fromContains    可选发件人过滤文本
     * @param subjectContains 可选主题过滤文本
     */
    public MailQuery {
        if (folder != null) {
            folder = MailValidationUtils.requireNonBlankWithoutControls(folder, "folder");
        }
        if (pageSize < 1 || pageSize > 1_000) {
            throw new IllegalArgumentException("pageSize must be between 1 and 1000");
        }
        if (receivedAfter != null && receivedBefore != null
                && !receivedAfter.isBefore(receivedBefore)) {
            throw new IllegalArgumentException("receivedAfter must be before receivedBefore");
        }
        if (fromContains != null) {
            fromContains = MailValidationUtils.requireNonBlank(fromContains, "fromContains");
        }
        if (subjectContains != null) {
            subjectContains = MailValidationUtils.requireNonBlank(subjectContains, "subjectContains");
        }
    }

    /**
     * 创建使用默认文件夹且不带过滤条件的第一页查询。
     *
     * @param pageSize 每页最多返回的消息数
     * @return 第一页查询
     */
    public static MailQuery firstPage(int pageSize) {
        return new MailQuery(null, pageSize, null, false, null, null, null, null);
    }

    /**
     * 保留当前过滤条件并创建下一页查询。
     *
     * @param nextCursor 上一页返回的非空游标
     * @return 下一页查询
     */
    public MailQuery nextPage(MailCursor nextCursor) {
        return new MailQuery(
                folder, pageSize, ValidationUtils.requireNonNull(nextCursor, "nextCursor"), unreadOnly,
                receivedAfter, receivedBefore, fromContains, subjectContains);
    }
}
