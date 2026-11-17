package com.beeper.android.messageformat.markdown

import org.intellij.markdown.IElementType
import org.intellij.markdown.MarkdownElementType
import org.intellij.markdown.MarkdownElementTypes

internal object MatrixMarkdownTokenTypes {
    val PIPE: IElementType = MarkdownElementType("|", isToken = true)
}

internal object MatrixMarkdownElementTypes {
    val SPOILER: IElementType = MarkdownElementType("SPOILER")
}

/** ATX heading types by level, `h1` first. */
internal val ATX_HEADINGS: List<IElementType> = listOf(
    MarkdownElementTypes.ATX_1,
    MarkdownElementTypes.ATX_2,
    MarkdownElementTypes.ATX_3,
    MarkdownElementTypes.ATX_4,
    MarkdownElementTypes.ATX_5,
    MarkdownElementTypes.ATX_6,
)
