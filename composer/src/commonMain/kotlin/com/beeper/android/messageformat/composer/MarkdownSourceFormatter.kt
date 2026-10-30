package com.beeper.android.messageformat.composer

import androidx.compose.foundation.text.input.TextFieldBuffer
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.text.LinkAnnotation
import co.touchlab.kermit.Logger
import com.beeper.android.messageformat.MatrixBodyParseResult
import com.beeper.android.messageformat.MatrixBodyStyledFormatter
import com.beeper.android.messageformat.MatrixFormatInteractionState
import com.beeper.android.messageformat.markdown.MatrixMarkdownParser
import com.beeper.android.messageformat.markdown.MatrixInlineEntity

/**
 * Applies the markdown formatting of composer text to a [TextFieldBuffer] as span styles from [formatter].
 * The last parse is kept because the background drawing needs it too.
 */
internal class MarkdownSourceFormatter(
    private val parser: MatrixMarkdownParser,
    private val formatter: MatrixBodyStyledFormatter,
) {
    private val log = Logger.withTag("MarkdownSourceFormatter")
    private var lastText: String? = null
    private var lastEntities: List<MatrixInlineEntity> = emptyList()
    private var lastResult: MatrixBodyParseResult? = null

    fun annotate(text: String, entities: List<MatrixInlineEntity>): MatrixBodyParseResult {
        lastResult?.takeIf { text == lastText && entities == lastEntities }?.let { return it }
        // A parser failure must not break typing
        val result = runCatching { parser.annotateSource(text, entities) }.getOrElse {
            log.e("Formatting failed, showing plain text", it)
            MatrixBodyParseResult(text)
        }
        return result.also {
            lastText = text
            lastEntities = entities
            lastResult = it
        }
    }

    fun applyTo(buffer: TextFieldBuffer, entities: List<MatrixInlineEntity>) {
        val result = annotate(buffer.asCharSequence().toString(), entities)
        // Spoilers are never hidden while typing
        val interaction = MatrixFormatInteractionState(result.expandableItems, mutableStateOf(result.expandableItems))
        val styled = formatter.applyStyle(result, interaction)
        // Paragraph styles would split lines into separate paragraphs
        styled.spanStyles.forEach { buffer.addStyle(it.item, it.start, it.end) }
        // Links are not clickable while typing, only styled
        styled.getLinkAnnotations(0, styled.length).forEach { link ->
            link.item.linkStyles?.style?.let { buffer.addStyle(it, link.start, link.end) }
        }
    }
}

private val LinkAnnotation.linkStyles
    get() = when (this) {
        is LinkAnnotation.Url -> styles
        is LinkAnnotation.Clickable -> styles
        else -> null
    }
