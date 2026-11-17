package com.beeper.android.messageformat.markdown

import com.beeper.android.messageformat.markdown.fence.MatrixCodeFenceProvider
import org.intellij.markdown.flavours.commonmark.CommonMarkMarkerProcessor
import org.intellij.markdown.flavours.gfm.table.GitHubTableMarkerProvider
import org.intellij.markdown.parser.MarkerProcessor
import org.intellij.markdown.parser.MarkerProcessorFactory
import org.intellij.markdown.parser.ProductionHolder
import org.intellij.markdown.parser.constraints.CommonMarkdownConstraints
import org.intellij.markdown.parser.markerblocks.MarkerBlockProvider
import org.intellij.markdown.parser.markerblocks.providers.AtxHeaderProvider
import org.intellij.markdown.parser.markerblocks.providers.BlockQuoteProvider
import org.intellij.markdown.parser.markerblocks.providers.HorizontalRuleProvider
import org.intellij.markdown.parser.markerblocks.providers.ListMarkerProvider

internal class MatrixMarkdownMarkerProcessor(
    productionHolder: ProductionHolder,
    constraintsBase: CommonMarkdownConstraints,
    features: Set<MarkdownFeature>,
) : CommonMarkMarkerProcessor(productionHolder, constraintsBase) {

    private val markerBlockProviders = buildList<MarkerBlockProvider<MarkerProcessor.StateInfo>> {
        if (MarkdownFeature.CODE_BLOCK in features) add(MatrixCodeFenceProvider())
        if (MarkdownFeature.HORIZONTAL_RULE in features) add(HorizontalRuleProvider())
        if (MarkdownFeature.BLOCKQUOTE in features) add(BlockQuoteProvider())
        if (MarkdownFeature.UNORDERED_LIST in features || MarkdownFeature.ORDERED_LIST in features) {
            add(ListMarkerProvider())
        }
        if (MarkdownFeature.HEADING in features) add(AtxHeaderProvider())
        if (MarkdownFeature.TABLE in features) add(GitHubTableMarkerProvider())
    }

    override fun getMarkerBlockProviders(): List<MarkerBlockProvider<MarkerProcessor.StateInfo>> = markerBlockProviders

    class Factory(private val features: Set<MarkdownFeature>) : MarkerProcessorFactory {
        override fun createMarkerProcessor(productionHolder: ProductionHolder): MarkerProcessor<*> =
            MatrixMarkdownMarkerProcessor(productionHolder, MatrixMarkdownConstraints(features), features)
    }
}
