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

package com.zhengshuyun.lava.jiandaoyun.workflow;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 抄送列表的阅读状态过滤。
 */
public enum CcReadStatus {
    /** 已读。 */
    READ("read"),
    /** 未读。 */
    UNREAD("unread"),
    /** 全部。 */
    ALL("all");

    /** 简道云协议中的取值。 */
    private final String code;

    /**
     * 绑定协议取值。
     *
     * @param code 协议取值
     */
    CcReadStatus(String code) {
        this.code = code;
    }

    /**
     * 返回简道云协议中的取值。
     *
     * @return 协议取值
     */
    @JsonValue
    public String code() {
        return code;
    }
}
