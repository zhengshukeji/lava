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
 * 流程加签方式。
 */
public enum AddSignType {
    /** 前加签：加签人先处理，再回到当前处理人。 */
    BEFORE(0),
    /** 后加签：当前处理人提交后由加签人处理。 */
    AFTER(1),
    /** 并行加签：加签人与当前处理人同时处理。 */
    PARALLEL(2);

    /** 简道云协议中的取值。 */
    private final int code;

    /**
     * 绑定协议取值。
     *
     * @param code 协议取值
     */
    AddSignType(int code) {
        this.code = code;
    }

    /**
     * 返回简道云协议中的取值。
     *
     * @return 协议取值
     */
    @JsonValue
    public int code() {
        return code;
    }
}
