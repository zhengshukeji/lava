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

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CharMappingTest {

    @Test
    void parsesOpenCcDictionary() {
        CharMapping mapping = openCc("""
                # Open Chinese Convert (OpenCC) Dictionary
                # Format: key\tvalue(s) (values separated by spaces)

                # @tofu-risk: not covered by baseline fonts
                㑮\t㑮 𫝈
                電\t电
                乾\t干 乾
                """);

        assertEquals('电', mapping.map('電'));
        // 多个候选取第一个
        assertEquals('干', mapping.map('乾'));
        // 首选候选是原字的条目不做转换
        assertEquals('㑮', mapping.map('㑮'));
        assertEquals('A', mapping.map('A'));
        assertEquals(2, mapping.size());
    }

    @Test
    void prefersBmpCandidateWhenFirstIsSupplementary() {
        CharMapping mapping = openCc("勣\t𪟝 绩\n㗲\t𠵾\n");

        // 首选是扩展区生僻字、后面有常用字时取常用字
        assertEquals('绩', mapping.map('勣'));
        // 只有扩展区候选时照常取它
        assertEquals("𠵾".codePointAt(0), mapping.map('㗲'));
    }

    @Test
    void resolvesChainedEntries() {
        CharMapping mapping = openCc("薴\t苧\n苧\t苎\n");

        assertEquals('苎', mapping.map('薴'));
        assertEquals('苎', mapping.map('苧'));
    }

    @Test
    void rejectsMalformedDictionary() {
        assertThrows(IllegalArgumentException.class, () -> openCc("電电\n"));
        assertThrows(IllegalArgumentException.class, () -> openCc("電腦\t电脑\n"));
        assertThrows(IllegalArgumentException.class, () -> openCc("電\t\n"));
    }

    @Test
    void rejectsCyclicMapping() {
        assertThrows(IllegalArgumentException.class, () -> openCc("甲\t乙\n乙\t甲\n"));
        assertThrows(IllegalArgumentException.class, () -> CharMapping.of(Map.of((int) 'a', (int) 'b', (int) 'b', (int) 'a')));
    }

    @Test
    void ofIgnoresIdentityEntries() {
        CharMapping mapping = CharMapping.of(Map.of((int) '電', (int) '电', (int) '电', (int) '电'));

        assertEquals(1, mapping.size());
        assertEquals('电', mapping.map('電'));
        assertThrows(IllegalArgumentException.class, () -> CharMapping.of(Map.of(-1, (int) 'a')));
    }

    /**
     * 解析内联的 OpenCC 字典文本。
     *
     * @param text 字典内容
     * @return 映射表
     */
    private static CharMapping openCc(String text) {
        return CharMapping.fromOpenCc(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)));
    }
}
