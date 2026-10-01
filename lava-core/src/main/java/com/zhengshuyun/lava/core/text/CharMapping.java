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

import com.zhengshuyun.lava.core.lang.ValidationUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.IntConsumer;

/**
 * 逐字映射表：把一个字符换成另一个字符，典型用途是繁体转简体，不可变且线程安全。
 *
 * <pre>{@code
 * // 映射数据由调用方提供，推荐 OpenCC 的 TSCharacters.txt（繁体到简体的单字表）
 * CharMapping traditionalToSimplified;
 * try (InputStream input = getClass().getResourceAsStream("/opencc/TSCharacters.txt")) {
 *     traditionalToSimplified = CharMapping.fromOpenCc(input);
 * }
 * }</pre>
 *
 * <p>映射按 codePoint 一对一替换，不改变文本的字符个数。构建时把链式条目折到最终的字
 * （「薴」→「苧」→「苎」时直接记为「薴」→「苎」），保证映射结果再映射不变；出现循环时拒绝构建。</p>
 *
 * <p>查表是匹配的热路径：键在 BMP 内的条目存入按下标取值的数组（占 256KB），不逐字装箱；
 * BMP 外的扩展区条目很少，存入 Map。</p>
 */
public final class CharMapping {

    /**
     * 尚未选定候选时的占位值；不是合法 codePoint。
     */
    private static final int NO_CANDIDATE = -1;

    /**
     * 键在 BMP 内的映射：下标为原字，值为目标字，0 表示不转换。
     */
    private final int[] bmp = new int[Character.MIN_SUPPLEMENTARY_CODE_POINT];

    /**
     * 键在 BMP 外的映射。
     */
    private final Map<Integer, Integer> supplementary;

    /**
     * 实际生效的条目数（不含映射到自身的条目）。
     */
    private final int size;

    /**
     * 用已经折完链的映射创建映射表。
     *
     * @param resolved 原字到最终目标字的映射，不含映射到自身的条目
     */
    private CharMapping(Map<Integer, Integer> resolved) {
        Map<Integer, Integer> others = new HashMap<>();
        resolved.forEach((from, to) -> {
            if (from < bmp.length) {
                bmp[from] = to;
            } else {
                others.put(from, to);
            }
        });
        this.supplementary = Map.copyOf(others);
        this.size = resolved.size();
    }

    /**
     * 用 codePoint 映射创建映射表；映射到自身的条目忽略，链式条目折到最终的字。
     *
     * @param mapping 原字到目标字的映射，键和值都是合法的 codePoint
     * @return 映射表
     * @throws IllegalArgumentException 存在非法 codePoint 或循环映射
     */
    public static CharMapping of(Map<Integer, Integer> mapping) {
        ValidationUtils.requireNonNull(mapping, "mapping must not be null");
        Map<Integer, Integer> table = new HashMap<>(mapping.size());
        mapping.forEach((from, to) -> {
            ValidationUtils.requireNonNull(from, "mapping key must not be null");
            ValidationUtils.requireNonNull(to, "mapping value must not be null");
            ValidationUtils.requireTrue(Character.isValidCodePoint(from), "invalid code point: " + from);
            ValidationUtils.requireTrue(Character.isValidCodePoint(to), "invalid code point: " + to);
            if (!from.equals(to)) {
                table.put(from, to);
            }
        });

        // 链式映射折到底：映射结果再映射必须不变，否则存储形态和匹配形态会对不上
        Map<Integer, Integer> resolved = new HashMap<>(table.size());
        for (Map.Entry<Integer, Integer> entry : table.entrySet()) {
            int target = entry.getValue();
            Set<Integer> visited = new HashSet<>();
            while (table.containsKey(target)) {
                ValidationUtils.requireTrue(
                        visited.add(target),
                        "cyclic mapping at: " + Character.toString(entry.getKey())
                );
                target = table.get(target);
            }
            if (target != entry.getKey()) {
                resolved.put(entry.getKey(), target);
            }
        }
        return new CharMapping(resolved);
    }

    /**
     * 解析 OpenCC 字典格式的单字表，如 {@code TSCharacters.txt}（繁体到简体）。
     *
     * <p>每行为「原字 Tab 候选」，多个候选以空格分隔；{@code #} 开头的注释和空行跳过。候选取第一个，
     * 与 OpenCC 默认转换一致；例外是第一个候选在 BMP 外、后面又有 BMP 内的其他字时，取第一个 BMP
     * 内的字，例如「勣」的候选是「𪟝 绩」，取常用的「绩」。首选候选就是原字的条目（如 OpenCC 标记
     * {@code @tofu-risk} 的生僻字）不做转换。</p>
     *
     * <p>输入流按 UTF-8 读取直到结束，调用方负责关闭。</p>
     *
     * @param input OpenCC 字典文本
     * @return 映射表
     * @throws IllegalArgumentException 存在格式错误的行、非单字的键或候选，或循环映射
     * @throws UncheckedIOException     读取失败
     */
    public static CharMapping fromOpenCc(InputStream input) {
        ValidationUtils.requireNonNull(input, "input must not be null");
        String text;
        try {
            text = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        Map<Integer, Integer> mapping = new HashMap<>();
        for (String line : text.split("\\R")) {
            if (line.isBlank() || line.startsWith("#")) {
                continue;
            }
            String[] columns = line.split("\t", 2);
            ValidationUtils.requireTrue(columns.length == 2, "malformed OpenCC line: " + line);
            int from = singleCodePoint(columns[0], line);
            int to = NO_CANDIDATE;
            for (String candidate : columns[1].strip().split(" +")) {
                int codePoint = singleCodePoint(candidate, line);
                if (to == NO_CANDIDATE) {
                    to = codePoint;
                } else if (!Character.isBmpCodePoint(to)
                        && Character.isBmpCodePoint(codePoint)
                        && codePoint != from) {
                    // 首选是扩展区生僻字而后面有常用字时，用常用字：正文里出现的几乎总是常用写法
                    to = codePoint;
                    break;
                }
            }
            mapping.put(from, to);
        }
        return of(mapping);
    }

    /**
     * 取单个字的 codePoint。
     *
     * @param value 字典中的一个字
     * @param line  所在行，用于错误信息
     * @return codePoint
     * @throws IllegalArgumentException 不是恰好一个字
     */
    private static int singleCodePoint(String value, String line) {
        ValidationUtils.requireTrue(
                !value.isEmpty() && value.codePointCount(0, value.length()) == 1,
                "OpenCC entry must be a single character: " + line
        );
        return value.codePointAt(0);
    }

    /**
     * 映射单个字符。
     *
     * @param codePoint 原字符
     * @return 映射后的字符；表中没有时原样返回
     */
    public int map(int codePoint) {
        if (codePoint >= 0 && codePoint < bmp.length) {
            int mapped = bmp[codePoint];
            return mapped == 0 ? codePoint : mapped;
        }
        return supplementary.getOrDefault(codePoint, codePoint);
    }

    /**
     * 返回实际生效的条目数。
     *
     * @return 不含映射到自身条目的条目数
     */
    public int size() {
        return size;
    }

    /**
     * 依次访问每个生效条目的原字，供归一化器校验映射与其他归一化步骤组合后仍然幂等。
     *
     * @param action 接收原字
     */
    void forEachKey(IntConsumer action) {
        for (int from = 0; from < bmp.length; from++) {
            if (bmp[from] != 0) {
                action.accept(from);
            }
        }
        supplementary.keySet().forEach(action::accept);
    }
}
