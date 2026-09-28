# 时间格式化与解析

## 严格日期格式

`DateTimeFormatterUtils` 提供不可变、线程安全并采用严格解析的常用格式：

| 常量 | 格式 |
| --- | --- |
| `DATE` | `uuuu-MM-dd` |
| `TIME` | `HH:mm:ss` |
| `DATE_TIME` | `uuuu-MM-dd HH:mm:ss` |
| `DATE_TIME_MILLIS` | `uuuu-MM-dd HH:mm:ss.SSS` |
| `COMPACT_DATE` | `uuuuMMdd` |
| `COMPACT_DATE_TIME` | `uuuuMMddHHmmss` |
| `SLASH_DATE` | `uuuu/MM/dd` |
| `SLASH_DATE_TIME` | `uuuu/MM/dd HH:mm:ss` |

```java
LocalDate date = LocalDate.parse("2026-08-30", DateTimeFormatterUtils.DATE);
String text = DateTimeFormatterUtils.DATE_TIME.format(dateTime);
```

严格解析会拒绝 `2026-02-30` 之类不存在的日期。

## 兼容解析

`TimeUtils.parse(...)` 用于接收多种常见日期时间文本：

```java
LocalDateTime first = TimeUtils.parse("2026-08-30 12:30:00");
LocalDateTime second = TimeUtils.parse("2026/08/30 12:30");
LocalDateTime third = TimeUtils.parse("20260830123000");
LocalDateTime fourth = TimeUtils.parse("2026年08月30日 12时30分");
```

输入为 `null`、空白或无法识别时返回 `null`。如果协议只允许一种格式，优先直接使用对应的严格 `DateTimeFormatter`，不要使用兼容解析掩盖错误输入。

## 时长展示

```java
DurationFormatter formatter = DurationFormatter.builder()
        .chinese()
        .range(ChronoUnit.DAYS, ChronoUnit.SECONDS)
        .showZeroValues(false)
        .separator(" ")
        .build();

String text = formatter.format(Duration.ofSeconds(90));
```

支持的单位范围是天到纳秒。月和年不是固定时长，不能由 `Duration` 精确表达，因此不支持。

构建器可配置：

- `largestUnit(...)` 和 `smallestUnit(...)`；
- `range(...)`；
- `chinese()`、`english()` 或 `locale(...)`；
- `showZeroValues(...)`；
- `separator(...)`。
