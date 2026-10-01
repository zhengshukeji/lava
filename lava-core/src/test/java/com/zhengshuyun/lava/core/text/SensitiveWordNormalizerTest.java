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

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SensitiveWordNormalizerTest {

    private final SensitiveWordNormalizer defaults = SensitiveWordNormalizer.builder().build();

    @Test
    void foldsWidthCaseAndSymbols() {
        assertEquals("abc123敏感词", defaults.normalize("ＡＢＣ１２３ 敏_感-词！"));
        assertEquals("", defaults.normalize(" ，。!? "));
    }

    @Test
    void keepsOneSpaceBetweenAsciiWords() {
        assertEquals("hello world", defaults.normalize("  Hello - World!"));
        assertEquals("abc 123敏感词", defaults.normalize("ＡＢＣ　１２３ 敏_感-词！"));
        // 中英文交界不需要空格，匹配时两侧本来就有整词边界
        assertEquals("下载app", defaults.normalize("下 载 APP"));
        assertEquals("hello world", defaults.normalize("hello world"));
    }

    @Test
    void dropsAllSpacesWhenAsciiMatchesAsSubstring() {
        SensitiveWordNormalizer substring = SensitiveWordNormalizer.builder()
                .asciiWholeWord(false)
                .build();

        assertEquals("helloworld", substring.normalize("Hello - World!"));
    }

    @Test
    void appliesCharMappingAfterLowercase() {
        SensitiveWordNormalizer normalizer = SensitiveWordNormalizer.builder()
                .charMapping(CharMapping.of(Map.of((int) '電', (int) '电', (int) '腦', (int) '脑')))
                .build();

        assertEquals("电脑app", normalizer.normalize("電 腦ＡＰＰ"));
    }

    @Test
    void rejectsMappingThatIsNotIdempotent() {
        // 映射到大写字母：归一化结果再转小写会变化，存储形态与匹配形态对不上
        CharMapping toUpper = CharMapping.of(Map.of((int) 'x', (int) 'Y'));
        // 映射到符号：再归一化时会被丢弃
        CharMapping toSymbol = CharMapping.of(Map.of((int) 'x', (int) '-'));

        assertThrows(IllegalArgumentException.class, () -> SensitiveWordNormalizer.builder().charMapping(toUpper).build());
        assertThrows(IllegalArgumentException.class, () -> SensitiveWordNormalizer.builder().charMapping(toSymbol).build());
    }
}
