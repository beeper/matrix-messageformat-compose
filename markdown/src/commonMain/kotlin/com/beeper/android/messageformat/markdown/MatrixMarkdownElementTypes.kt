package com.beeper.android.messageformat.markdown

import org.intellij.markdown.IElementType
import org.intellij.markdown.MarkdownElementType

internal object MatrixMarkdownTokenTypes {
    val PIPE: IElementType = MarkdownElementType("|", isToken = true)
}

internal object MatrixMarkdownElementTypes {
    val SPOILER: IElementType = MarkdownElementType("SPOILER")
}
