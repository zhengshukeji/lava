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
import org.jspecify.annotations.Nullable;

import java.text.Normalizer;

/**
 * 敏感词的归一化规则：词的存储形态和文本的匹配形态都由它决定，不可变且线程安全。
 *
 * <pre>{@code
 * SensitiveWordNormalizer normalizer = SensitiveWordNormalizer.builder()
 *         // 可选：繁体转简体，映射数据由调用方提供
 *         .charMapping(traditionalToSimplified)
 *         .build();
 *
 * // 录入敏感词库前归一化：「敏 感_詞」→「敏感词」，「Hello-World」→「hello world」
 * String stored = normalizer.normalize(input);
 * }</pre>
 *
 * <p>逐字归一化依次为：Unicode 兼容归一化 NFKC（全角、数学字母、带圈字母、康熙部首、单位符号等
 * 折算为常规字符）、转小写、按 {@link CharMapping} 替换（如繁体转简体）、丢弃非字母数字。</p>
 *
 * <p>ASCII 字母数字默认只按整词匹配：每段连续的 ASCII 字母数字两侧有整词边界，词 {@code va} 不会命中
 * {@code java}，{@code abc} 不会命中「tab cat」去空格后的 {@code tabcat}，适合代码较多的文本；代价是
 * 「a b c」这类逐字母插空的写法拦不住。关闭后 ASCII 与中文一样按子串匹配。</p>
 *
 * <p>同一套敏感词库的录入、搜索和匹配必须使用同一个归一化器（同样的配置），否则存储形态与匹配形态
 * 对不上，词会静默失效。调整配置等同于改变存储形态，已存的词需要重新归一化。</p>
 */
public final class SensitiveWordNormalizer {

    /**
     * 匹配形态中的整词边界；空格在逐字归一化时总会被丢弃，不会与正文字符混淆。
     */
    static final char BOUNDARY = ' ';

    /**
     * 折叠未被接收方提前结束时的返回值。
     */
    static final int NOT_STOPPED = -1;

    /**
     * 逐字映射；没有配置时为空。
     */
    private final @Nullable CharMapping charMapping;

    /**
     * ASCII 字母数字是否只按整词匹配。
     */
    private final boolean asciiWholeWord;

    /**
     * 匹配形态的字符接收方。
     */
    @FunctionalInterface
    interface CharSink {

        /**
         * 接收匹配形态的下一个字符。
         *
         * @param key 匹配形态字符
         * @return 需要提前结束折叠时返回 {@code true}
         */
        boolean accept(char key);
    }

    /**
     * 按构建器的配置创建归一化器。
     *
     * @param builder 构建器
     */
    private SensitiveWordNormalizer(Builder builder) {
        this.charMapping = builder.charMapping;
        this.asciiWholeWord = builder.asciiWholeWord;
    }

    /**
     * 创建构建器：默认不做逐字映射，ASCII 只按整词匹配。
     *
     * @return 新的构建器
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * 按存储形态归一化一段文本，供录入词、搜索词和命中词展示使用。
     *
     * <p>与匹配同一套逐字规则；整词匹配时两个 ASCII 词之间的空白和符号折成一个空格保留，其余空白和
     * 符号去掉。这样「hello world」「Hello-World」都存为 {@code hello world}，匹配时与原写法的整词边界
     * 对得上；若把空格也去掉，存下的 {@code helloworld} 只能命中连写的那一种。</p>
     *
     * @param text 原始文本
     * @return 归一化后的文本；不含任何字母和数字时为空串
     */
    public String normalize(String text) {
        ValidationUtils.requireNonNull(text, "text must not be null");
        StringBuilder folded = new StringBuilder(text.length() + 2);
        fold(text, 0, key -> {
            folded.append(key);
            return false;
        });
        // 匹配形态里的边界只保留夹在两个 ASCII 字母数字之间的，首尾和中英文交界处的去掉。
        StringBuilder stored = new StringBuilder(folded.length());
        for (int k = 0; k < folded.length(); k++) {
            char key = folded.charAt(k);
            if (key != BOUNDARY) {
                stored.append(key);
                continue;
            }
            boolean afterAsciiWord = !stored.isEmpty() && stored.charAt(stored.length() - 1) < 0x80;
            boolean beforeAsciiWord = k + 1 < folded.length()
                    && folded.charAt(k + 1) != BOUNDARY
                    && folded.charAt(k + 1) < 0x80;
            if (afterAsciiWord && beforeAsciiWord) {
                stored.append(BOUNDARY);
            }
        }
        return stored.toString();
    }

    /**
     * 把文本折叠成匹配形态并逐字符交给接收方：非 ASCII 字母数字原样相连；整词匹配时每段连续的
     * ASCII 字母数字两侧各有一个边界，相邻边界合并为一个。
     *
     * <p>每个原文字符先按 {@link #expand(int)} 做兼容归一化，可能展开成多个字符（如「㎏」展开为
     * 「kg」），展开出的字符共用同一个原文结束位置。</p>
     *
     * <p>接收方提前结束时返回此刻已消费到的原文位置：字符本身触发时位于该字符之后；边界触发时
     * 位于引出边界的字符之前，即上一个字母数字之后，不把词后的符号或下一个字算进去。</p>
     *
     * <p>从 {@code from} 开始折叠时，起点前的内容视为不存在：调用方只在上一处命中的词尾续扫，
     * 词尾之后要么是符号、要么换了文字类别，整词边界与从头扫描一致。</p>
     *
     * @param text 原始文本
     * @param from 折叠起点（UTF-16 单位）
     * @param sink 匹配形态字符的接收方
     * @return 接收方要求提前结束时的原文结束下标（UTF-16 单位）；扫描完毕未结束时为 {@link #NOT_STOPPED}
     */
    int fold(String text, int from, CharSink sink) {
        Folder folder = new Folder(sink);
        for (int i = from; i < text.length(); ) {
            int original = text.codePointAt(i);
            i += Character.charCount(original);
            String expanded = expand(original);
            if (expanded == null) {
                // 绝大多数字符（ASCII 与常用汉字）不需要兼容归一化，直接处理，不产生临时字符串
                int stopped = folder.feed(normalize(original), i);
                if (stopped != NOT_STOPPED) {
                    return stopped;
                }
                continue;
            }
            for (int k = 0; k < expanded.length(); ) {
                int codePoint = expanded.codePointAt(k);
                k += Character.charCount(codePoint);
                int stopped = folder.feed(normalize(codePoint), i);
                if (stopped != NOT_STOPPED) {
                    return stopped;
                }
            }
        }
        return folder.finish();
    }

    /**
     * 从词尾向前回溯，定位命中词第一个字符的原文位置。
     *
     * <p>原文中词首到词尾之间的字母数字全部属于命中词，空白和符号在折叠时丢弃，因此逐字向前
     * 数够词里的字母数字个数即到词首，插空再长也准确。一个原文字符可能展开成多个字母数字
     * （「㎏」→「kg」），数够时停在该字符起点。</p>
     *
     * @param text      原文
     * @param endOffset 命中词之后的原文下标
     * @param word      命中词的归一化形态，词内空格是整词边界
     * @return 命中词起点的原文下标（UTF-16 单位）
     */
    int startOffset(String text, int endOffset, String word) {
        long remaining = word.chars().filter(key -> key != BOUNDARY).count();
        int index = endOffset;
        while (index > 0 && remaining > 0) {
            int original = text.codePointBefore(index);
            index -= Character.charCount(original);
            String expanded = expand(original);
            if (expanded == null) {
                // 映射结果可能落在 BMP 外（BMP 内的原字映射到扩展区汉字），词里占两个单位
                int key = normalize(original);
                remaining -= key < 0 ? 0 : Character.charCount(key);
            } else {
                // 词长按 UTF-16 单位计，归一化结果在 BMP 外时占两个单位
                remaining -= expanded.codePoints()
                        .map(this::normalize)
                        .filter(key -> key >= 0)
                        .mapToLong(Character::charCount)
                        .sum();
            }
        }
        return index;
    }

    /**
     * 对需要的字符做 Unicode 兼容归一化（NFKC）：全角字母、数学字母（𝐯）、带圈字母（ⓥ）、
     * 康熙部首（⼈）、单位符号（㎏）等兼容写法折算为常规字符，堵住换字形绕过。
     *
     * <p>ASCII 和 CJK 统一汉字的 NFKC 结果就是自身，直接跳过，逐字匹配的热路径不产生临时字符串。</p>
     *
     * @param codePoint 原始字符
     * @return 兼容归一化结果；不需要处理的字符为空
     */
    private static @Nullable String expand(int codePoint) {
        if (codePoint < 0x80
                || (codePoint >= 0x4E00 && codePoint <= 0x9FFF)
                || (codePoint >= 0x3400 && codePoint <= 0x4DBF)) {
            return null;
        }
        return Normalizer.normalize(Character.toString(codePoint), Normalizer.Form.NFKC);
    }

    /**
     * 归一化单个字符：转小写，按逐字映射替换，非字母和数字丢弃。全角等兼容写法已由
     * {@link #expand(int)} 折算。
     *
     * @param codePoint 兼容归一化后的字符
     * @return 归一化后的字符；需要丢弃时为 -1
     */
    private int normalize(int codePoint) {
        if (!Character.isLetterOrDigit(codePoint)) {
            return -1;
        }
        int lower = Character.toLowerCase(codePoint);
        return charMapping == null ? lower : charMapping.map(lower);
    }

    /**
     * 折叠过程的状态：记录上一个输出的字符类别，决定何时补整词边界。
     */
    private final class Folder {

        /**
         * 匹配形态字符的接收方。
         */
        private final CharSink sink;

        /**
         * 上一个输出的是 ASCII 字母数字（其后尚未输出边界）。
         */
        private boolean afterAscii;

        /**
         * 上一个输出的是边界。
         */
        private boolean afterBoundary;

        /**
         * 上一个字母数字之后的原文位置，边界触发命中时以它作为词尾。
         */
        private int lastEnd;

        /**
         * 绑定接收方。
         *
         * @param sink 匹配形态字符的接收方
         */
        private Folder(CharSink sink) {
            this.sink = sink;
        }

        /**
         * 输入一个归一化后的字符。
         *
         * @param key    归一化后的字符；空白和符号为 -1
         * @param rawEnd 该字符所属原文字符之后的原文下标
         * @return 接收方要求结束时的原文结束下标；否则为 {@link #NOT_STOPPED}
         */
        private int feed(int key, int rawEnd) {
            if (key < 0) {
                // 空白和符号只在 ASCII 词尾留下边界，中文之间的插空直接抹掉。
                if (afterAscii) {
                    if (sink.accept(BOUNDARY)) {
                        return lastEnd;
                    }
                    afterAscii = false;
                    afterBoundary = true;
                }
                return NOT_STOPPED;
            }
            // 关闭整词匹配时 ASCII 与中文同等对待，不产生任何边界
            boolean ascii = asciiWholeWord && key < 0x80;
            // ASCII 段开头、或从 ASCII 段切换到中文时补一个边界。
            boolean needBoundary = ascii ? !afterAscii && !afterBoundary : afterAscii;
            if (needBoundary && sink.accept(BOUNDARY)) {
                return lastEnd;
            }
            if (Character.isBmpCodePoint(key)) {
                if (sink.accept((char) key)) {
                    return rawEnd;
                }
            } else if (sink.accept(Character.highSurrogate(key)) || sink.accept(Character.lowSurrogate(key))) {
                return rawEnd;
            }
            lastEnd = rawEnd;
            afterAscii = ascii;
            afterBoundary = false;
            return NOT_STOPPED;
        }

        /**
         * 文本结束：ASCII 段收尾时补一个边界。
         *
         * @return 接收方要求结束时的原文结束下标；否则为 {@link #NOT_STOPPED}
         */
        private int finish() {
            return afterAscii && sink.accept(BOUNDARY) ? lastEnd : NOT_STOPPED;
        }
    }

    /**
     * {@link SensitiveWordNormalizer} 的构建器。
     */
    public static final class Builder {

        /**
         * 逐字映射。
         */
        private @Nullable CharMapping charMapping;

        /**
         * ASCII 字母数字是否只按整词匹配。
         */
        private boolean asciiWholeWord = true;

        /**
         * 只能通过 {@link SensitiveWordNormalizer#builder()} 创建。
         */
        private Builder() {
        }

        /**
         * 设置逐字映射，如繁体转简体；在转小写之后、丢弃符号之前应用。
         *
         * @param charMapping 逐字映射；传 {@code null} 表示不映射
         * @return 当前构建器
         */
        public Builder charMapping(@Nullable CharMapping charMapping) {
            this.charMapping = charMapping;
            return this;
        }

        /**
         * 设置 ASCII 字母数字是否只按整词匹配，默认开启。
         *
         * @param asciiWholeWord 开启时 {@code va} 不命中 {@code java}；关闭时按子串匹配
         * @return 当前构建器
         */
        public Builder asciiWholeWord(boolean asciiWholeWord) {
            this.asciiWholeWord = asciiWholeWord;
            return this;
        }

        /**
         * 构建归一化器，并校验逐字映射与其他归一化步骤组合后仍然幂等。
         *
         * @return 不可变的归一化器
         * @throws IllegalArgumentException 映射结果再归一化会变化，如映射到大写字母或符号
         */
        public SensitiveWordNormalizer build() {
            SensitiveWordNormalizer normalizer = new SensitiveWordNormalizer(this);
            CharMapping mapping = normalizer.charMapping;
            if (mapping != null) {
                // 存储形态会被再次折叠后用于匹配，归一化结果再归一化必须不变，否则词会静默失效
                mapping.forEachKey(from -> {
                    String once = normalizer.normalize(Character.toString(from));
                    ValidationUtils.requireTrue(
                            normalizer.normalize(once).equals(once),
                            "charMapping is not idempotent at: " + Character.toString(from)
                    );
                });
            }
            return normalizer;
        }
    }
}
