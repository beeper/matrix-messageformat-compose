package com.beeper.android.messageformat.composer

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import co.touchlab.kermit.Logger
import com.beeper.android.messageformat.MatrixBodyParseResult
import com.beeper.android.messageformat.MatrixBodyStyledFormatter
import com.beeper.android.messageformat.MatrixFormatInteractionState
import com.beeper.android.messageformat.markdown.MatrixMarkdownContent
import com.beeper.android.messageformat.markdown.MatrixMarkdownParser

/**
 * The markdown formatting of composer text, as span styles from [formatter].
 * The last parse is kept because the background drawing needs it too.
 */
internal class MarkdownSourceFormatter(
    private val parser: MatrixMarkdownParser,
    private val formatter: MatrixBodyStyledFormatter,
) {
    private val log = Logger.withTag("MarkdownSourceFormatter")
    private var last: Pair<MatrixMarkdownContent, MatrixBodyParseResult>? = null

    fun annotate(content: MatrixMarkdownContent): MatrixBodyParseResult {
        last?.takeIf { it.first == content }?.let { return it.second }
        // A parser failure must not break typing
        val result = runCatching { parser.annotateSource(content) }.getOrElse {
            log.e("Formatting failed, showing plain text", it)
            MatrixBodyParseResult(content.text)
        }
        return result.also { last = content to it }
    }

    fun spanStyles(content: MatrixMarkdownContent): List<AnnotatedString.Range<SpanStyle>> {
        val result = annotate(content)
        val styled = formatter.applyStyle(result, revealedInteractionState(result))
        // Links are not clickable while typing, only styled
        val linkStyles = styled.getLinkAnnotations(0, styled.length).mapNotNull { link ->
            link.item.linkStyles?.style?.let { AnnotatedString.Range(it, link.start, link.end) }
        }
        // Paragraph styles would split lines into separate paragraphs
        return styled.spanStyles + linkStyles
    }
}

/** Spoilers are never hidden while typing. */
internal fun revealedInteractionState(result: MatrixBodyParseResult) =
    MatrixFormatInteractionState(result.expandableItems, mutableStateOf(result.expandableItems))

private val LinkAnnotation.linkStyles
    get() = when (this) {
        is LinkAnnotation.Url -> styles
        is LinkAnnotation.Clickable -> styles
        else -> null
    }
