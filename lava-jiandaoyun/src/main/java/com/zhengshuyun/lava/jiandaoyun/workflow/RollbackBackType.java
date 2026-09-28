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
 * 流程回退时的「回退人选择」：被回退人重新提交后的流转方式。
 *
 * <p>仅当节点配置了「回退人选择」时需要传入。</p>
 */
public enum RollbackBackType {
    /** 正常流转：按流程顺序重新经过后续节点。 */
    NORMAL(1),
    /** 直达目标节点：提交后直接回到执行回退的节点。 */
    DIRECT(2);

    /** 简道云协议中的取值。 */
    private final int code;

    /**
     * 绑定协议取值。
     *
     * @param code 协议取值
     */
    RollbackBackType(int code) {
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
