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

package com.zhengshuyun.lava.core.text;

/**
 * 一次敏感词命中。
 *
 * <p>位置按 UTF-16 单位计、左闭右开，可直接用于 {@link String#substring(int, int)}；区间覆盖词内
 * 插入的空白和符号，如「敏 感_词」整段。</p>
 *
 * @param word        命中词的归一化形态
 * @param text        命中所在的那段原文
 * @param startOffset 命中词第一个字符的原文下标
 * @param endOffset   命中词之后的原文下标
 */
public record SensitiveWordHit(
        String word,
        String text,
        int startOffset,
        int endOffset
) {
}
