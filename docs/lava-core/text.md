# 敏感词匹配

`SensitiveWordMatcher` 在文本中查找词库里的词，基于 Aho-Corasick 自动机：不管词库有多少词，文本都只扫一遍。词和文本先按 `SensitiveWordNormalizer` 的同一套规则折叠，插空、全角、大小写、兼容字形以及（可选的）繁体写法都能命中，命中位置精确对应原文。

## 基本用法

```java
// 归一化规则：词库录入、搜索和匹配必须共用同一个
SensitiveWordNormalizer normalizer = SensitiveWordNormalizer.builder().build();

// 录入词库前先归一化，存储形态稳定，便于去重
String stored = normalizer.normalize("敏 感_词");   // "敏感词"

// 用词库构建匹配器；词库变更时整体重建一个新的并替换引用
SensitiveWordMatcher matcher = SensitiveWordMatcher.builder()
        .normalizer(normalizer)
        .words(words)
        .build();

// 多段文本逐段扫描，返回第一个命中；未命中时为 null
SensitiveWordHit hit = matcher.findFirst(List.of(title, content));
if (hit != null) {
    // 命中词的归一化形态，及其在 hit.text() 中的原文区间（左闭右开）
    String original = hit.text().substring(hit.startOffset(), hit.endOffset());
}

// 一段文本中的全部命中，如在原文上高亮
List<SensitiveWordHit> hits = matcher.findAll(content);
```

归一化器和匹配器都不可变、线程安全。匹配器构建后可被多个线程并发读取。

## 归一化规则

每个字依次经过：

1. Unicode 兼容归一化 NFKC：全角 `ＡＢＣ`、数学字母 `𝐚𝐛𝐜`、带圈字母 `ⓐⓑⓒ`、康熙部首「⼈」、单位符号「㎏」等折算为常规字符。
2. 转小写。
3. 逐字映射（可选），如繁体转简体。
4. 丢弃空白和符号。

| 写法 | 存储形态 | 说明 |
| --- | --- | --- |
| `敏 感_词` | `敏感词` | 中文之间的插空直接去掉 |
| `Hello-World` | `hello world` | 两个英文词之间保留一个空格 |
| `下 载 APP` | `下载app` | 中英文交界不需要空格 |

## 英文整词匹配

ASCII 字母数字默认只按整词匹配：`va` 不会命中 `java`，`abc` 不会命中 `tab cat`。文本里有代码时，子串匹配会大面积误伤，因此默认开启。代价是 `a b c` 这类逐字母插空的写法拦不住。

昵称、评论等不含代码的场景可以改为子串匹配：

```java
SensitiveWordNormalizer normalizer = SensitiveWordNormalizer.builder()
        .asciiWholeWord(false)
        .build();
```

关闭后英文词之间的空格也会去掉，`Hello-World` 存为 `helloworld`。

## 繁体转简体

`CharMapping` 是逐字映射表，配置到归一化器后，简繁写法折成同一个匹配形态，词库只需维护一种写法。映射数据由调用方提供，推荐 [OpenCC](https://github.com/BYVoid/OpenCC) 的 `data/dictionary/TSCharacters.txt`（繁体到简体的单字表，Apache-2.0），放入项目资源目录后加载：

```java
CharMapping traditionalToSimplified;
try (InputStream input = getClass().getResourceAsStream("/opencc/TSCharacters.txt")) {
    traditionalToSimplified = CharMapping.fromOpenCc(input);
}

SensitiveWordNormalizer normalizer = SensitiveWordNormalizer.builder()
        .charMapping(traditionalToSimplified)
        .build();

normalizer.normalize("電 腦");   // "电脑"
```

`fromOpenCc` 的取舍：

- 一个繁体字有多个简体候选时取第一个，与 OpenCC 默认转换一致；例外是第一个候选在扩展区（BMP 外）、后面又有常用字时取常用字，如「勣」取「绩」而不是「𪟝」。
- 首选候选就是原字的条目（OpenCC 标记 `@tofu-risk` 的生僻字）不做转换。
- 链式条目折到最终的字，如「薴」→「苧」→「苎」记为「薴」→「苎」；出现循环时抛出 `IllegalArgumentException`。

也可以用 `CharMapping.of(Map<Integer, Integer>)` 传入自己的 codePoint 映射，规则相同。映射表常用区部分按数组下标查表（占 256KB），不逐字装箱。

## 性能

一次扫描的耗时只与文本长度成正比，与词数无关；文本边扫描边折叠，不复制整段文本，命中第一个词即停止。ASCII 与常用汉字跳过 NFKC，不产生临时对象。

::: warning 边界
- 词库的录入、搜索和匹配必须使用同一个归一化器（同样的配置）。调整配置等同于改变存储形态，已存的词需要重新归一化，否则会静默失效。
- `build()` 会校验逐字映射与其他步骤组合后仍然幂等（映射结果再归一化不变），映射到大写字母或符号时抛出 `IllegalArgumentException`。
- `findAll` 每处命中后从词尾继续扫描，相互重叠的出现只取先命中的一处。
:::
