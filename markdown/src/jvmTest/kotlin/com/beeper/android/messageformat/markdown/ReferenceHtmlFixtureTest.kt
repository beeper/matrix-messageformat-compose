package com.beeper.android.messageformat.markdown

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.fail

/**
 * Pins the HTML output to `reference-html-fixtures.json`. Deliberate output changes are made by
 * editing the expected HTML there, so every change is explicit.
 */
class ReferenceHtmlFixtureTest {

    @Serializable
    private data class Fixture(val markdown: String, val html: String)

    private val fixtures: List<Fixture> = Json.decodeFromString(
        checkNotNull(javaClass.classLoader.getResource("reference-html-fixtures.json")) { "fixtures missing" }
            .readText()
    )

    private val parser = MatrixMarkdownParser(MarkdownFeature.ALL)

    // Whitespace between blocks is not a meaningful difference
    private fun String.normalized(): String = replace(">\n", ">").replace("\n<", "<").trimEnd('\n')

    @Test
    fun htmlMatchesReferenceOutput() {
        val mismatches = fixtures.mapNotNull { fixture ->
            val actual = parser.toHtml(fixture.markdown)
            if (actual.normalized() == fixture.html.normalized()) return@mapNotNull null
            "markdown: ${fixture.markdown.show()}\n  expected: ${fixture.html.show()}\n  actual:   ${actual.show()}"
        }
        if (mismatches.isNotEmpty()) {
            fail("${mismatches.size} of ${fixtures.size} fixtures differ:\n${mismatches.joinToString("\n")}")
        }
    }

    @Test
    fun htmlSurvivesEditingUnchanged() {
        val mismatches = fixtures.flatMap { fixture ->
            // Both our own HTML and the reference HTML that other clients send
            listOf(parser.toHtml(fixture.markdown), fixture.html).mapNotNull { html ->
                val content = parser.fromHtml(html)
                val actual = parser.toHtml(content)
                // Links to their own URL become plain URLs, which clients link when showing them
                val expected = BARE_LINK.replace(html) { it.groupValues[2] }
                if (actual.normalized() == expected.normalized()) return@mapNotNull null
                "html: ${html.show()}\n  markdown: ${content.text.show()}\n  html after: ${actual.show()}"
            }
        }
        if (mismatches.isNotEmpty()) {
            fail("${mismatches.size} of ${2 * fixtures.size} HTML inputs differ:\n${mismatches.joinToString("\n")}")
        }
    }

    private companion object {
        val BARE_LINK = Regex("""<a href="([^"]*)">(\1)</a>""")
    }

    private fun String.show(): String = "\"" + replace("\\", "\\\\").replace("\n", "\\n").replace("\"", "\\\"") + "\""
}
