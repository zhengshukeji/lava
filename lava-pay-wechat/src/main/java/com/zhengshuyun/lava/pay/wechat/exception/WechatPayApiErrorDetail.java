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

package com.zhengshuyun.lava.pay.wechat.exception;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.jspecify.annotations.Nullable;

/**
 * 微信支付 API 返回的参数错误详情。
 *
 * @param field 参数位置，Body 参数通常使用 JSON Pointer
 * @param value 微信支付返回的错误值；可能包含业务数据，不会写入异常文本
 * @param issue 具体错误原因
 * @param location 参数来源位置，例如 {@code body}、{@code url} 或 {@code query}
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record WechatPayApiErrorDetail(
        @Nullable String field,
        @Nullable String value,
        @Nullable String issue,
        @Nullable String location
) {
}
