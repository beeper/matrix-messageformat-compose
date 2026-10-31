package com.beeper.android.messageformat.markdown

import com.beeper.android.messageformat.markdown.html.MatrixHtmlProviders
import com.beeper.android.messageformat.markdown.spoiler.PipeSplittingLexer
import com.beeper.android.messageformat.markdown.spoiler.SpoilerDelimiterParser
import org.intellij.markdown.IElementType
import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.flavours.MarkdownFlavourDescriptor
import org.intellij.markdown.flavours.gfm.StrikeThroughDelimiterParser
import org.intellij.markdown.flavours.gfm.lexer._GFMLexer
import org.intellij.markdown.html.GeneratingProvider
import org.intellij.markdown.html.URI
import org.intellij.markdown.lexer.GeneratedLexer
import org.intellij.markdown.lexer.MarkdownLexer
import org.intellij.markdown.parser.LinkMap
import org.intellij.markdown.parser.MarkerProcessorFactory
import org.intellij.markdown.parser.sequentialparsers.DelimiterParser
import org.intellij.markdown.parser.sequentialparsers.EmphasisLikeParser
import org.intellij.markdown.parser.sequentialparsers.SequentialParser
import org.intellij.markdown.parser.sequentialparsers.SequentialParserManager
import org.intellij.markdown.parser.sequentialparsers.impl.AutolinkParser
import org.intellij.markdown.parser.sequentialparsers.impl.BacktickParser
import org.intellij.markdown.parser.sequentialparsers.impl.EmphStrongDelimiterParser
import org.intellij.markdown.parser.sequentialparsers.impl.ImageParser
import org.intellij.markdown.parser.sequentialparsers.impl.InlineLinkParser

/** The CommonMark and GFM syntax of [features], rendered to the Matrix HTML subset. */
internal class MatrixMarkdownFlavour(val features: Set<MarkdownFeature>) : MarkdownFlavourDescriptor {

    override val markerProcessorFactory: MarkerProcessorFactory = MatrixMarkdownMarkerProcessor.Factory(features)

    override fun createInlinesLexer(): MarkdownLexer {
        val lexer: GeneratedLexer = _GFMLexer()
        return MarkdownLexer(if (MarkdownFeature.SPOILER in features) PipeSplittingLexer(lexer) else lexer)
    }

    override val sequentialParserManager: SequentialParserManager = object : SequentialParserManager() {
        override fun getParserSequence(): List<SequentialParser> = buildList {
            if (MarkdownFeature.AUTOLINK in features) {
                add(AutolinkParser(listOf(MarkdownTokenTypes.AUTOLINK, MarkdownTokenTypes.EMAIL_AUTOLINK)))
            }
            if (MarkdownFeature.INLINE_CODE in features) add(BacktickParser())
            if (MarkdownFeature.IMAGE in features) add(ImageParser())
            if (MarkdownFeature.INLINE_LINK in features) add(InlineLinkParser())
            val delimiterParsers = buildList<DelimiterParser> {
                if (MarkdownFeature.BOLD in features || MarkdownFeature.ITALIC in features) {
                    add(EmphStrongDelimiterParser())
                }
                if (MarkdownFeature.STRIKETHROUGH in features) add(StrikeThroughDelimiterParser())
                if (MarkdownFeature.SPOILER in features) add(SpoilerDelimiterParser())
            }
            if (delimiterParsers.isNotEmpty()) add(EmphasisLikeParser(*delimiterParsers.toTypedArray()))
        }
    }

    override fun createHtmlGeneratingProviders(linkMap: LinkMap, baseURI: URI?): Map<IElementType, GeneratingProvider> =
        MatrixHtmlProviders.create(features)
}
