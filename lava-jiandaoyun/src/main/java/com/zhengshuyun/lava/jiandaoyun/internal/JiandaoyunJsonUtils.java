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

package com.zhengshuyun.lava.jiandaoyun.internal;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.zhengshuyun.lava.json.JsonCodec;
import com.zhengshuyun.lava.json.JsonMapperFactory;

/**
 * 简道云协议 JSON 编解码工具。
 *
 * <p>简道云请求中的可选参数（如分页的 limit/skip）未配置时应省略对应字段，由服务端应用
 * 默认值。该工具集中配置忽略 {@code null} 值，避免请求模型逐个声明相同的 Jackson 注解，
 * 同时保证请求编码和响应解码使用一致的 JSON 规则。</p>
 */
public final class JiandaoyunJsonUtils {
    /** 线程安全的简道云共享 JSON 编解码器。 */
    private static final JsonCodec CODEC = new JsonCodec(JsonMapperFactory.builder()
            .customize(builder -> builder.changeDefaultPropertyInclusion(
                    inclusion -> inclusion.withValueInclusion(JsonInclude.Include.NON_NULL)))
            .build());

    /** 禁止实例化简道云 JSON 工具。 */
    private JiandaoyunJsonUtils() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * 返回忽略空值字段的简道云 JSON 编解码器。
     *
     * @return 共享编解码器
     */
    public static JsonCodec codec() {
        return CODEC;
    }
}
