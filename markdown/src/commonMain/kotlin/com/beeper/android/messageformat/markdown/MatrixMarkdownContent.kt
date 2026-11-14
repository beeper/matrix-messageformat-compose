package com.beeper.android.messageformat.markdown

/** Markdown [text] with the [entities] in it, which lie inside the text without overlapping, in text order. */
class MatrixMarkdownContent private constructor(
    val text: String,
    val entities: List<MatrixInlineEntity>,
) {
    override fun equals(other: Any?): Boolean =
        other is MatrixMarkdownContent && text == other.text && entities == other.entities

    override fun hashCode(): Int = 31 * text.hashCode() + entities.hashCode()

    override fun toString(): String = "MatrixMarkdownContent(text=$text, entities=$entities)"

    companion object {
        /** Drops the [entities] that do not fit [text]. */
        operator fun invoke(text: String, entities: List<MatrixInlineEntity> = emptyList()): MatrixMarkdownContent =
            MatrixMarkdownContent(text, entities.validIn(text))
    }
}
