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

package com.zhengshuyun.lava.json;

import tools.jackson.databind.module.SimpleModule;

import java.util.OptionalLong;

/**
 * 注册后把每个 {@code long}/{@link Long} 值序列化为 JSON string 的模块。
 *
 * <p>规则不随数值大小变化，覆盖装箱与基本类型、基本数组、装箱数组、集合、嵌套字段和
 * {@link OptionalLong}；单个字段可用
 * {@code @JsonFormat(shape = JsonFormat.Shape.NUMBER)} 覆盖回 number。
 */
public final class LongAsStringModule extends SimpleModule {

    /**
     * 创建 long-as-string 模块。
     */
    public LongAsStringModule() {
        super(LongAsStringModule.class.getName());
        addSerializer(Long.class, new LongAsStringSerializer());
        addSerializer(Long.TYPE, new LongAsStringSerializer());
        addSerializer(long[].class, new LongArrayAsStringSerializer());
        addSerializer(OptionalLong.class, new OptionalLongAsStringSerializer());
    }
}
