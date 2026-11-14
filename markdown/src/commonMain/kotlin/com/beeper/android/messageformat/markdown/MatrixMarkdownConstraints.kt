package com.beeper.android.messageformat.markdown

import org.intellij.markdown.parser.LookaheadText
import org.intellij.markdown.parser.constraints.CommonMarkdownConstraints

/** CommonMark block constraints without `+` list markers, gated on the [MarkdownFeature]s for lists and quotes. */
internal class MatrixMarkdownConstraints private constructor(
    private val features: Set<MarkdownFeature>,
    private val root: MatrixMarkdownConstraints?,
    indents: IntArray,
    types: CharArray,
    isExplicit: BooleanArray,
    charsEaten: Int,
) : CommonMarkdownConstraints(indents, types, isExplicit, charsEaten) {

    constructor(features: Set<MarkdownFeature>) : this(features, null, IntArray(0), CharArray(0), BooleanArray(0), 0)

    override val base: CommonMarkdownConstraints
        get() = root ?: this

    override fun createNewConstraints(
        indents: IntArray,
        types: CharArray,
        isExplicit: BooleanArray,
        charsEaten: Int,
    ): CommonMarkdownConstraints =
        MatrixMarkdownConstraints(features, root ?: this, indents, types, isExplicit, charsEaten)

    override fun fetchListMarker(pos: LookaheadText.Position): ListMarkerInfo? = when (pos.char) {
        '*', '-' -> if (MarkdownFeature.UNORDERED_LIST in features) ListMarkerInfo(1, pos.char, 1) else null
        in '0'..'9' -> if (MarkdownFeature.ORDERED_LIST in features) super.fetchListMarker(pos) else null
        else -> null
    }

    override fun addModifierIfNeeded(
        pos: LookaheadText.Position?,
        interruptsParagraph: Boolean,
    ): CommonMarkdownConstraints? {
        val result = super.addModifierIfNeeded(pos, interruptsParagraph) ?: return null
        if (MarkdownFeature.BLOCKQUOTE !in features && result.types.lastOrNull() == BQ_CHAR) return null
        return result
    }
}
