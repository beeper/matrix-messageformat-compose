package com.beeper.android.messageformat.markdown

/**
 * Allow opting into specific markdown features
 */
enum class MarkdownFeature {
    /** `**bold**` and `__bold__` */
    BOLD,
    /** `*italic*` and `_italic_` */
    ITALIC,
    /** `~~strikethrough~~` */
    STRIKETHROUGH,
    /** `||spoiler||` and `||reason|spoiler||` */
    SPOILER,
    /** `` `code` `` */
    INLINE_CODE,
    /** Fenced code blocks */
    CODE_BLOCK,
    /** `[text](url)` */
    INLINE_LINK,
    /** `<https://example.org>` and `<mail@example.org>` */
    AUTOLINK,
    /** `![alt](url "title")`, with the `Emoji: ` title convention for custom emotes */
    IMAGE,
    /** `# Heading` */
    HEADING,
    /** `---` and `***` on their own line */
    HORIZONTAL_RULE,
    /** `> quote` */
    BLOCKQUOTE,
    /** `- item` and `* item` */
    UNORDERED_LIST,
    /** `1. item` */
    ORDERED_LIST,
    /** GitHub flavoured tables */
    TABLE;

    companion object {
        val ALL: Set<MarkdownFeature> = entries.toSet()
    }
}
