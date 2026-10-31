package com.beeper.android.messageformat.desktop

val EXAMPLE_MARKDOWN = """
# Markdown input

Some **bold**, _italic_, ~~struck~~ and `inline code` text, plus a ||spoiler|| and a ||reason|hidden part||.
Line breaks inside a paragraph become hard breaks.

> A quote
> spanning two lines

- bullet one
- bullet two
  - nested

1. first
5. jumps to five

```kotlin
val answer = 42
```

[A link](https://example.org/) and <https://example.org/auto> and an inline image ![Example image](mxc://local.beeper.com/spiritcroc_TnTnE9q1fOXD0gtA9UlcBrStvCVLWwAbAtKcAIOKqWJRtQeZwsniGSRVA8xV51tB "Emoji: example")

| Column | Other |
|--------|-------|
| cell   | cell  |

---

Raw <b>html</b> stays literal & escaped.
""".trimIndent()
