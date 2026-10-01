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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;

/**
 * 基于 Aho-Corasick 自动机的敏感词匹配器，不可变且线程安全。
 *
 * <pre>{@code
 * SensitiveWordMatcher matcher = SensitiveWordMatcher.builder()
 *         .normalizer(normalizer)
 *         .words(words)
 *         .build();
 *
 * SensitiveWordHit hit = matcher.findFirst(List.of(userInput));
 * if (hit != null) {
 *     // 命中词与原文位置：hit.word()、hit.startOffset()、hit.endOffset()
 * }
 * }</pre>
 *
 * <p>一次扫描的耗时只与文本长度成正比，与词数无关；文本边扫描边折叠，不复制整段文本。词和文本
 * 由同一个 {@link SensitiveWordNormalizer} 折叠成匹配形态，中文插空、全角、大小写等写法都能命中，
 * 命中位置精确回溯到原文。</p>
 *
 * <p>敏感词库变更时整体重建一个新匹配器并替换引用，读线程不会看到构建了一半的自动机。</p>
 *
 * <p>敏感词库由调用方维护。中文敏感词库可参考开源的 <a href="https://github.com/konsheng/Sensitive-lexicon">Sensitive-lexicon</a>（MIT），
 * 按业务裁剪后使用：一两个字的短词容易误伤正常内容。</p>
 */
public final class SensitiveWordMatcher {

    /**
     * 自动机节点。
     */
    private static final class Node {

        /**
         * 按下一个字符跳转的子节点。
         */
        private final Map<Character, Node> children = new HashMap<>();

        /**
         * 匹配失败时回退的节点：当前路径的最长真后缀所在节点。
         */
        private @Nullable Node fail;

        /**
         * 以当前位置结尾的词：自身恰为词尾时是自身的词，否则沿失败链继承；没有时为空。
         */
        private @Nullable String match;
    }

    /**
     * 词和文本共用的归一化规则。
     */
    private final SensitiveWordNormalizer normalizer;

    /**
     * 根节点。
     */
    private final Node root = new Node();

    /**
     * 实际收录的词数。
     */
    private final int size;

    /**
     * 按构建器的配置构建自动机；归一化后为空的词忽略，匹配形态相同的词只收录一次。
     *
     * <p>词在这里再按匹配形态折叠一次，即使传入未经归一化的写法（如「ＡＢＣ」「敏 感」）也与被检查
     * 文本走同一套规则，不会静默失效；命中时一律报告归一化形态。</p>
     *
     * @param builder 构建器
     */
    private SensitiveWordMatcher(Builder builder) {
        this.normalizer = builder.normalizer;

        // 1. 全部词的匹配形态插入字典树，词尾节点记下词的归一化形态
        int count = 0;
        for (String word : builder.words) {
            StringBuilder form = new StringBuilder(word.length() + 2);
            normalizer.fold(word, 0, key -> {
                form.append(key);
                return false;
            });
            if (form.toString().isBlank()) {
                continue;
            }
            Node node = root;
            for (int i = 0; i < form.length(); i++) {
                node = node.children.computeIfAbsent(form.charAt(i), key -> new Node());
            }
            if (node.match == null) {
                node.match = normalizer.normalize(word);
                count++;
            }
        }
        this.size = count;

        // 2. 按层广度优先补全失败链：子节点的失败节点，是父节点失败链上第一个拥有同一字符分支的节点。
        // 父节点总是先于子节点处理，因此回溯时用到的失败链都已就绪。
        Queue<Node> queue = new ArrayDeque<>();
        for (Node child : root.children.values()) {
            child.fail = root;
            queue.add(child);
        }
        while (!queue.isEmpty()) {
            Node node = queue.poll();
            for (Map.Entry<Character, Node> entry : node.children.entrySet()) {
                char key = entry.getKey();
                Node child = entry.getValue();
                Node fallback = node.fail;
                while (fallback != root && !fallback.children.containsKey(key)) {
                    fallback = fallback.fail;
                }
                Node target = fallback.children.get(key);
                child.fail = target != null ? target : root;
                // 3. 词尾继承：自身不是词尾时，失败节点上的词同样以当前位置结尾，命中判定只需看一个字段。
                if (child.match == null) {
                    child.match = child.fail.match;
                }
                queue.add(child);
            }
        }
    }

    /**
     * 创建构建器：默认使用 {@link SensitiveWordNormalizer#builder()} 的默认规则，敏感词库为空。
     *
     * @return 新的构建器
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * 返回收录的词数。
     *
     * @return 去重后的词数
     */
    public int size() {
        return size;
    }

    /**
     * 返回构建时使用的归一化规则；敏感词库的录入和搜索应使用同一个。
     *
     * @return 归一化器
     */
    public SensitiveWordNormalizer normalizer() {
        return normalizer;
    }

    /**
     * 在多段文本中查找第一个命中的词。
     *
     * <p>每段文本独立扫描，不会把上一段的结尾和下一段的开头拼成一个词。</p>
     *
     * @param texts 待检查的文本
     * @return 第一个命中；全部未命中时为 {@code null}
     */
    public @Nullable SensitiveWordHit findFirst(Iterable<String> texts) {
        ValidationUtils.requireNonNull(texts, "texts must not be null");
        if (root.children.isEmpty()) {
            return null;
        }
        for (String text : texts) {
            ValidationUtils.requireNonNull(text, "text must not be null");
            SensitiveWordHit hit = findFirst(text, 0);
            if (hit != null) {
                return hit;
            }
        }
        return null;
    }

    /**
     * 依次查找一段文本中的全部命中，如在原文上高亮。
     *
     * <p>每处命中后从词尾继续扫描，相互重叠的出现只取先命中的一处。</p>
     *
     * @param text 原文
     * @return 全部命中，按出现顺序排列；没有命中时为空列表
     */
    public List<SensitiveWordHit> findAll(String text) {
        ValidationUtils.requireNonNull(text, "text must not be null");
        List<SensitiveWordHit> hits = new ArrayList<>();
        if (root.children.isEmpty()) {
            return hits;
        }
        // 词尾一定在扫描起点之后，循环必然推进
        SensitiveWordHit hit = findFirst(text, 0);
        while (hit != null) {
            hits.add(hit);
            hit = findFirst(text, hit.endOffset());
        }
        return hits;
    }

    /**
     * 从指定位置起在一段文本中查找第一个命中的词。
     *
     * @param text 待检查的文本
     * @param from 扫描起点（UTF-16 单位）
     * @return 命中结果；未命中时为空
     */
    private @Nullable SensitiveWordHit findFirst(String text, int from) {
        // 折叠回调里需要推进自动机状态，用单元素数组承载可变的当前节点。
        Node[] current = {root};
        int endOffset = normalizer.fold(text, from, key -> {
            Node node = current[0];
            while (node != root && !node.children.containsKey(key)) {
                node = node.fail;
            }
            node = node.children.getOrDefault(key, root);
            current[0] = node;
            return node.match != null;
        });
        String word = current[0].match;
        if (endOffset == SensitiveWordNormalizer.NOT_STOPPED || word == null) {
            return null;
        }
        return new SensitiveWordHit(word, text, normalizer.startOffset(text, endOffset, word), endOffset);
    }

    /**
     * {@link SensitiveWordMatcher} 的构建器。
     */
    public static final class Builder {

        /**
         * 归一化规则。
         */
        private SensitiveWordNormalizer normalizer = SensitiveWordNormalizer.builder().build();

        /**
         * 敏感词库。
         */
        private Collection<String> words = List.of();

        /**
         * 只能通过 {@link SensitiveWordMatcher#builder()} 创建。
         */
        private Builder() {
        }

        /**
         * 设置归一化规则；必须与敏感词库录入时使用的一致。
         *
         * @param normalizer 归一化器
         * @return 当前构建器
         */
        public Builder normalizer(SensitiveWordNormalizer normalizer) {
            this.normalizer = ValidationUtils.requireNonNull(normalizer, "normalizer must not be null");
            return this;
        }

        /**
         * 设置敏感词库；词可以是原始写法，构建时统一归一化。
         *
         * @param words 敏感词库
         * @return 当前构建器
         */
        public Builder words(Collection<String> words) {
            ValidationUtils.requireNonNull(words, "words must not be null");
            for (String word : words) {
                ValidationUtils.requireNonNull(word, "word must not be null");
            }
            this.words = List.copyOf(words);
            return this;
        }

        /**
         * 设置敏感词库。
         *
         * @param words 敏感词库
         * @return 当前构建器
         */
        public Builder words(String... words) {
            ValidationUtils.requireNonNull(words, "words must not be null");
            return words(List.of(words));
        }

        /**
         * 构建匹配器。
         *
         * @return 不可变的匹配器
         */
        public SensitiveWordMatcher build() {
            return new SensitiveWordMatcher(this);
        }
    }
}
