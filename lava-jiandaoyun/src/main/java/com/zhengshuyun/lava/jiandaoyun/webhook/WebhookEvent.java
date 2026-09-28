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

package com.zhengshuyun.lava.jiandaoyun.webhook;

import com.zhengshuyun.lava.core.lang.ValidationUtils;
import com.zhengshuyun.lava.jiandaoyun.data.DataRecord;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 简道云推送事件。
 *
 * @param op 事件类型，如 data_create、data_update、data_remove、data_recover、form_update
 * @param opTime 事件触发时间（毫秒时间戳）；推送未携带时为 {@code null}
 * @param data 推送内容，结构随事件类型变化
 */
public record WebhookEvent(String op, @Nullable Long opTime, Map<String, @Nullable Object> data) {
    /** 新建数据事件。 */
    public static final String DATA_CREATE = "data_create";
    /** 修改数据事件。 */
    public static final String DATA_UPDATE = "data_update";
    /** 删除数据事件。 */
    public static final String DATA_REMOVE = "data_remove";
    /** 恢复数据事件。 */
    public static final String DATA_RECOVER = "data_recover";
    /** 表单结构变更事件。 */
    public static final String FORM_UPDATE = "form_update";

    /**
     * 校验事件类型并冻结推送内容。
     *
     * @param op 事件类型
     * @param opTime 事件触发时间
     * @param data 推送内容
     */
    public WebhookEvent {
        ValidationUtils.requireNotBlank(op, "op must not be blank");
        data = Collections.unmodifiableMap(new LinkedHashMap<>(
                ValidationUtils.requireNonNull(data, "data must not be null")));
    }

    /**
     * 返回事件触发时间。
     *
     * @return 触发时间；推送未携带时为 {@code null}
     */
    public @Nullable Instant opInstant() {
        return opTime == null ? null : Instant.ofEpochMilli(opTime);
    }

    /**
     * 判断是否为数据事件（新建、修改、删除、恢复）。
     *
     * @return 数据事件时为 {@code true}
     */
    public boolean isDataEvent() {
        return DATA_CREATE.equals(op) || DATA_UPDATE.equals(op) || DATA_REMOVE.equals(op) || DATA_RECOVER.equals(op);
    }

    /**
     * 把推送内容解释为表单数据，适用于数据事件。
     *
     * @return 数据记录
     */
    public DataRecord asDataRecord() {
        return DataRecord.of(data);
    }
}
