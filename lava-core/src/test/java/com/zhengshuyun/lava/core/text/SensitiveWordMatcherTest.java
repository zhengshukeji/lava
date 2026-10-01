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

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SensitiveWordMatcherTest {

    /**
     * 测试用的小型繁简映射。
     */
    private static final CharMapping TRADITIONAL = CharMapping.of(Map.of(
            (int) '電', (int) '电',
            (int) '腦', (int) '脑',
            (int) '學', (int) '学',
            (int) '習', (int) '习',
            (int) '資', (int) '资',
            (int) '詞', (int) '词',
            // BMP 内的原字映射到扩展区汉字
            (int) '勣', "𪟝".codePointAt(0)
    ));

    private static final SensitiveWordNormalizer WITH_TRADITIONAL = SensitiveWordNormalizer.builder()
            .charMapping(TRADITIONAL)
            .build();

    @Test
    void phraseMatchesSpaceAndHyphenButNotConcatenation() {
        SensitiveWordMatcher matcher = matcher("hello world");

        assertEquals("hello world", wordOf(matcher.findFirst(List.of("say hello world now"))));
        assertEquals("hello world", wordOf(matcher.findFirst(List.of("HELLO-WORLD"))));
        assertNull(matcher.findFirst(List.of("helloworld", "hello worlds")));
    }

    @Test
    void compatibilityFormsMatchAfterNfkc() {
        SensitiveWordMatcher matcher = matcher("abc", "人民", "kg");

        // 数学粗体字母、带圈字母、康熙部首、单位符号
        assertEquals("abc", wordOf(matcher.findFirst(List.of("use 𝐚𝐛𝐜 now"))));
        assertEquals("abc", wordOf(matcher.findFirst(List.of("ⓐⓑⓒ"))));
        assertEquals("人民", wordOf(matcher.findFirst(List.of("⼈民"))));
        assertEquals("kg", wordOf(matcher.findFirst(List.of("5 ㎏"))));
    }

    @Test
    void chineseMatchesAcrossInsertedSpaces() {
        SensitiveWordMatcher matcher = matcher("敏感词");

        assertEquals("敏感词", wordOf(matcher.findFirst(List.of("这里有 敏 感_词 吗"))));
        assertEquals("敏感词", wordOf(matcher.findFirst(List.of("前缀敏感词后缀"))));
    }

    @Test
    void asciiMatchesWholeWordsOnly() {
        SensitiveWordMatcher matcher = matcher("va", "abc", "value");

        assertNull(matcher.findFirst(List.of(
                "import java.util.List;",
                "run tab cat install",
                "const smallValue = 1",
                "AbcService.start()"
        )));
        assertEquals("abc", wordOf(matcher.findFirst(List.of("use the ABC, please"))));
        assertEquals("abc", wordOf(matcher.findFirst(List.of("ＡＢＣ"))));
        assertEquals("va", wordOf(matcher.findFirst(List.of("取va值"))));
        // 整词匹配的代价：逐字母插空的写法被拆成多个词
        assertNull(matcher.findFirst(List.of("a b c")));
    }

    @Test
    void asciiMatchesSubstringsWhenWholeWordIsOff() {
        SensitiveWordMatcher matcher = SensitiveWordMatcher.builder()
                .normalizer(SensitiveWordNormalizer.builder().asciiWholeWord(false).build())
                .words("va", "abc")
                .build();

        assertEquals("va", wordOf(matcher.findFirst(List.of("import java.util.List;"))));
        assertEquals("abc", wordOf(matcher.findFirst(List.of("a b c"))));
    }

    @Test
    void mixedWordsFollowRulesOnEachSide() {
        SensitiveWordMatcher matcher = matcher("下载app", "a面");

        assertEquals("下载app", wordOf(matcher.findFirst(List.of("怎么 下 载 APP"))));
        assertEquals("a面", wordOf(matcher.findFirst(List.of("翻到a面"))));
        assertNull(matcher.findFirst(List.of("java面板", "下载appstore")));
    }

    @Test
    void traditionalAndSimplifiedFoldTogether() {
        SensitiveWordMatcher simplifiedWord = SensitiveWordMatcher.builder()
                .normalizer(WITH_TRADITIONAL)
                .words("学习资料")
                .build();
        assertEquals("学习资料", wordOf(simplifiedWord.findFirst(List.of("这是一份學習資料的目录"))));
        assertEquals("学习资料", wordOf(simplifiedWord.findFirst(List.of("學 習_資 料"))));

        // 简繁两种写法折成同一个匹配形态，只收录一次，报告简体形态
        SensitiveWordMatcher bothForms = SensitiveWordMatcher.builder()
                .normalizer(WITH_TRADITIONAL)
                .words("電腦", "电脑")
                .build();
        assertEquals(1, bothForms.size());
        assertEquals("电脑", wordOf(bothForms.findFirst(List.of("電腦"))));
    }

    @Test
    void unnormalizedWordsStillMatchAndReportNormalizedForm() {
        SensitiveWordMatcher matcher = matcher("ＡＢＣ", "敏 感");

        assertEquals("abc", wordOf(matcher.findFirst(List.of("abc"))));
        assertEquals("敏感", wordOf(matcher.findFirst(List.of("敏感"))));
    }

    @Test
    void fallsBackAlongFailureLinks() {
        // 扫描「子甲乙丙戊」时先走 甲乙丙 分支，到 戊 失配，需回退到 乙丙 才能继续命中 乙丙戊。
        assertEquals("乙丙戊", wordOf(matcher("甲乙丙丁", "乙丙戊").findFirst(List.of("子甲乙丙戊"))));
        // 长词内部包含的短词同样命中
        assertEquals("三四", wordOf(matcher("一二三四五六", "三四").findFirst(List.of("一二三四七七"))));
    }

    @Test
    void textsAreNotJoined() {
        assertNull(matcher("敏感").findFirst(List.of("这是敏", "感的")));
    }

    @Test
    void emptyResultsAndDeduplication() {
        assertNull(matcher("敏感").findFirst(List.of("正常内容")));
        assertNull(matcher().findFirst(List.of("敏感")));
        assertEquals(List.of(), matcher().findAll("敏感"));
        assertEquals(2, matcher("a", "A", "", "!!", "b").size());
    }

    @Test
    void hitPointsAtOriginalSpan() {
        SensitiveWordMatcher matcher = SensitiveWordMatcher.builder()
                .normalizer(WITH_TRADITIONAL)
                .words("敏感词", "abc", "hello world", "ل", "勣業")
                .build();

        // 中文按字符触发，区间从第一个字到最后一个字，插空再长词首也准确
        assertSpan(matcher, "前缀 敏 感_词 后缀", "敏 感_词");
        assertSpan(matcher, "前缀敏" + " ".repeat(600) + "感词后缀", "敏" + " ".repeat(600) + "感词");
        // 英文靠词后的边界触发，区间不含前后的符号或中文
        assertSpan(matcher, "use ABC, please", "ABC");
        assertSpan(matcher, "开ABC关", "ABC");
        assertSpan(matcher, "trailing abc", "abc");
        assertSpan(matcher, "say Hello-World now", "Hello-World");
        // emoji 占两个 UTF-16 单位，位置按原文下标计算
        assertSpan(matcher, "😀😀敏感词😀", "敏感词");
        // 兼容字形折算前后长度不同（数学字母占两个单位），位置仍指向原文
        assertSpan(matcher, "use 𝐚𝐛𝐜, ok", "𝐚𝐛𝐜");
        // 一个原文字符展开出多个字母时，区间落在该字符上
        assertSpan(matcher, "前ﷺ后", "ﷺ");
        // 繁体逐字映射后，区间仍指向原文的繁体写法
        assertSpan(matcher, "前缀 敏 感_詞 后缀", "敏 感_詞");
        // 映射结果在扩展区（占两个单位），回溯词首时按映射结果的长度扣减
        assertSpan(matcher, "大功勣業成", "勣業");
    }

    @Test
    void hitCarriesItsText() {
        SensitiveWordHit hit = matcher("敏感").findFirst(List.of("第一段", "第二段有敏感内容"));

        assertNotNull(hit);
        assertEquals("第二段有敏感内容", hit.text());
    }

    @Test
    void findAllReturnsEveryOccurrence() {
        SensitiveWordMatcher chinese = SensitiveWordMatcher.builder()
                .normalizer(WITH_TRADITIONAL)
                .words("电脑")
                .build();
        assertEquals(List.of("0-2", "3-5", "7-10"), spans(chinese.findAll("电脑和電腦還有電 腦")));
        // 英文仍只按整词
        assertEquals(List.of("0-3", "9-12"), spans(matcher("abc").findAll("abc abcd ABC")));
    }

    /**
     * 用默认归一化规则构建匹配器。
     *
     * @param words 敏感词库
     * @return 匹配器
     */
    private static SensitiveWordMatcher matcher(String... words) {
        return SensitiveWordMatcher.builder().words(words).build();
    }

    /**
     * 取命中结果里的词，未命中时为空。
     *
     * @param hit 命中结果
     * @return 命中的词
     */
    private static @Nullable String wordOf(@Nullable SensitiveWordHit hit) {
        return hit == null ? null : hit.word();
    }

    /**
     * 把命中列表转成「起点-终点」文本，便于整体断言。
     *
     * @param hits 命中列表
     * @return 区间文本
     */
    private static List<String> spans(List<SensitiveWordHit> hits) {
        return hits.stream().map(hit -> hit.startOffset() + "-" + hit.endOffset()).toList();
    }

    /**
     * 断言单段文本命中后的原文区间。
     *
     * @param matcher      匹配器
     * @param text         原文，期望区间在其中只出现一次
     * @param expectedSpan 期望命中的原文片段
     */
    private static void assertSpan(SensitiveWordMatcher matcher, String text, String expectedSpan) {
        SensitiveWordHit hit = matcher.findFirst(List.of(text));
        assertNotNull(hit, text);
        int expectedStart = text.indexOf(expectedSpan);
        assertEquals(expectedStart, hit.startOffset(), text);
        assertEquals(expectedStart + expectedSpan.length(), hit.endOffset(), text);
    }
}
