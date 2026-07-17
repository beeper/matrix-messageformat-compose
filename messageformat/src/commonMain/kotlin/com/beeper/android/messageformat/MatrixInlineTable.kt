package com.beeper.android.messageformat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Maps [InlineTableInfo] to actual [InlineTextContent] rendering the tables as full-width grids.
 *
 * @param maxWidth the full width available to the rendering Text composable.
 * @param tableHeights measured table heights in px by table id, updated from layout so the
 *  [Placeholder] height can follow dynamic content (e.g. expanding details tags in cells).
 */
@Composable
internal fun Map<String, InlineTableInfo>.toTableInlineContent(
    maxWidth: Dp,
    style: TextStyle,
    textColor: Color,
    drawStyle: MatrixBodyDrawStyle,
    formatter: MatrixBodyStyledFormatter,
    inlineContent: @Composable (MatrixBodyParseResult) -> Map<String, InlineTextContent>,
    tableHeights: SnapshotStateMap<String, Int>,
    onLinkLongPress: ((LinkAnnotation) -> Unit)?,
): Map<String, InlineTextContent> {
    val density = LocalDensity.current
    return mapValues { (id, info) ->
        val indention = formatter.tableIndention(info.indentionDepth)
        val width = with(density) {
            // Same density for both conversions, so indention + placeholder width fill the
            // available width, including under non-default font scales. A small epsilon keeps
            // rounding or the leading zero-width space from wrapping the placeholder to its
            // own (unindented) line.
            (maxWidth.toSp().value - indention.value - PLACEHOLDER_WIDTH_EPSILON_SP)
                .coerceAtLeast(MIN_TABLE_WIDTH_SP).sp
        }
        val height = tableHeights[id]?.let { with(density) { it.toSp() } }
            ?: estimateTableHeight(info, style, drawStyle.tableStyle, density)
        InlineTextContent(
            Placeholder(
                width = width,
                height = height,
                placeholderVerticalAlign = PlaceholderVerticalAlign.Top,
            )
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    // Measure the true content height, despite the placeholder bounding the
                    // incoming constraints; overflows draw downwards until the placeholder
                    // height caught up
                    .wrapContentHeight(align = Alignment.Top, unbounded = true)
                    .onSizeChanged { size ->
                        if (tableHeights[id] != size.height) {
                            tableHeights[id] = size.height
                        }
                    }
            ) {
                MatrixInlineTable(
                    info = info,
                    style = style,
                    textColor = textColor,
                    drawStyle = drawStyle,
                    formatter = formatter,
                    inlineContent = inlineContent,
                    onLinkLongPress = onLinkLongPress,
                )
            }
        }
    }
}

private const val MIN_TABLE_WIDTH_SP = 48f
private const val PLACEHOLDER_WIDTH_EPSILON_SP = 1f

/**
 * Rough single-line-per-row height estimate, only used until the first real measurement.
 */
private fun estimateTableHeight(
    info: InlineTableInfo,
    style: TextStyle,
    tableStyle: MatrixTableStyle,
    density: Density,
): TextUnit {
    val lineHeight = style.lineHeight.takeIf { it.isSp }
        ?: ((style.fontSize.takeIf { it.isSp } ?: 14.sp) * 1.4f)
    val rowChrome = with(density) {
        (
            tableStyle.cellPadding.calculateTopPadding() +
                tableStyle.cellPadding.calculateBottomPadding() +
                tableStyle.gridLineWidth
        ).toSp()
    }
    val lines = (info.rows.size + (if (info.caption != null) 1 else 0)).coerceAtLeast(1)
    return (lines * (lineHeight.value + rowChrome.value)).sp
}

/**
 * Renders one parsed table: centered caption above an equal-column-width grid.
 */
@Composable
internal fun MatrixInlineTable(
    info: InlineTableInfo,
    style: TextStyle,
    textColor: Color,
    drawStyle: MatrixBodyDrawStyle,
    formatter: MatrixBodyStyledFormatter,
    inlineContent: @Composable (MatrixBodyParseResult) -> Map<String, InlineTextContent>,
    onLinkLongPress: ((LinkAnnotation) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val columns = info.rows.maxOfOrNull { it.cells.size } ?: 0
    val tableStyle = drawStyle.tableStyle
    val gridColor = tableStyle.gridColor.takeOrElse { textColor.copy(alpha = 0.25f) }
    val headerBackground = tableStyle.headerBackgroundColor.takeOrElse {
        textColor.copy(alpha = 0.1f)
    }
    Column(modifier.fillMaxWidth()) {
        info.caption?.let { caption ->
            MatrixStyledFormattedText(
                parseResult = caption,
                modifier = Modifier.fillMaxWidth().padding(bottom = 2.dp),
                style = style,
                drawStyle = drawStyle,
                formatter = formatter,
                textAlign = TextAlign.Center,
                inlineContent = inlineContent,
                onLinkLongPress = onLinkLongPress,
            )
        }
        if (columns > 0) {
            TableGrid(
                rows = info.rows,
                columns = columns,
                gridColor = gridColor,
                headerBackground = headerBackground,
                gridLineWidth = tableStyle.gridLineWidth,
                cellPadding = tableStyle.cellPadding,
                modifier = Modifier.fillMaxWidth().border(tableStyle.gridLineWidth, gridColor),
            ) { cell ->
                MatrixStyledFormattedText(
                    parseResult = cell.content,
                    style = style,
                    drawStyle = drawStyle,
                    formatter = formatter,
                    inlineContent = inlineContent,
                    onLinkLongPress = onLinkLongPress,
                )
            }
        }
    }
}

/**
 * Equal-column-width grid built as a custom [Layout] instead of Rows with
 * [IntrinsicSize][androidx.compose.foundation.layout.IntrinsicSize] height: cells may contain
 * nested tables, whose [androidx.compose.foundation.layout.BoxWithConstraints] does not support
 * intrinsic measurements. Cell contents get measured once with fixed width and unbounded height,
 * then per-cell decorations (header background, interior grid lines) get sized to the resulting
 * row heights.
 */
@Composable
private fun TableGrid(
    rows: List<TableRowInfo>,
    columns: Int,
    gridColor: Color,
    headerBackground: Color,
    gridLineWidth: Dp,
    cellPadding: PaddingValues,
    modifier: Modifier = Modifier,
    cellContent: @Composable (TableCellInfo) -> Unit,
) {
    Layout(
        content = {
            rows.forEachIndexed { rowIndex, row ->
                repeat(columns) { columnIndex ->
                    // Decoration slot per cell, sized to the full cell after content measurement
                    val cell = row.cells.getOrNull(columnIndex)
                    Box(
                        Modifier
                            .tableCellGridLines(
                                color = gridColor,
                                width = gridLineWidth,
                                drawTop = rowIndex > 0,
                                drawStart = columnIndex > 0,
                            )
                            .then(
                                if (cell?.isHeader == true) {
                                    Modifier.background(headerBackground)
                                } else {
                                    Modifier
                                }
                            )
                    )
                }
            }
            rows.forEach { row ->
                repeat(columns) { columnIndex ->
                    // Content slot per cell; rows with fewer cells get padded with empty ones
                    val cell = row.cells.getOrNull(columnIndex)
                    Box(Modifier.padding(cellPadding)) {
                        if (cell != null) {
                            cellContent(cell)
                        }
                    }
                }
            }
        },
        modifier = modifier,
    ) { measurables, constraints ->
        val cellCount = rows.size * columns
        val decorationMeasurables = measurables.subList(0, cellCount)
        val contentMeasurables = measurables.subList(cellCount, 2 * cellCount)
        // Equal column widths, with the rounding remainder distributed to the first columns
        val baseWidth = constraints.maxWidth / columns
        val remainder = constraints.maxWidth % columns
        val columnWidths = IntArray(columns) { baseWidth + if (it < remainder) 1 else 0 }
        val contentPlaceables = contentMeasurables.mapIndexed { index, measurable ->
            measurable.measure(
                Constraints(
                    minWidth = columnWidths[index % columns],
                    maxWidth = columnWidths[index % columns],
                    minHeight = 0,
                    maxHeight = Constraints.Infinity,
                )
            )
        }
        val rowHeights = IntArray(rows.size) { rowIndex ->
            (0 until columns).maxOf { contentPlaceables[rowIndex * columns + it].height }
        }
        val decorationPlaceables = decorationMeasurables.mapIndexed { index, measurable ->
            measurable.measure(
                Constraints.fixed(columnWidths[index % columns], rowHeights[index / columns])
            )
        }
        layout(constraints.maxWidth, rowHeights.sum()) {
            var y = 0
            rows.indices.forEach { rowIndex ->
                var x = 0
                repeat(columns) { columnIndex ->
                    val index = rowIndex * columns + columnIndex
                    decorationPlaceables[index].placeRelative(x, y)
                    contentPlaceables[index].placeRelative(x, y)
                    x += columnWidths[columnIndex]
                }
                y += rowHeights[rowIndex]
            }
        }
    }
}

/**
 * Interior grid lines: each cell only draws its top and start edge, the outer border covers
 * the perimeter, so adjacent cells don't produce double-width lines.
 */
private fun Modifier.tableCellGridLines(
    color: Color,
    width: Dp,
    drawTop: Boolean,
    drawStart: Boolean,
) = drawBehind {
    val stroke = width.toPx()
    if (drawTop) {
        drawLine(color, Offset(0f, 0f), Offset(size.width, 0f), stroke)
    }
    if (drawStart) {
        // Cells get placed mirrored under RTL, so the start edge flips as well
        val x = if (layoutDirection == LayoutDirection.Rtl) size.width else 0f
        drawLine(color, Offset(x, 0f), Offset(x, size.height), stroke)
    }
}
