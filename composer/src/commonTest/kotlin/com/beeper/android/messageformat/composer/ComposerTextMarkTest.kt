package com.beeper.android.messageformat.composer

import androidx.compose.foundation.text.input.TextFieldBuffer
import androidx.compose.ui.text.TextRange
import com.beeper.android.messageformat.markdown.MatrixInlineEntity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ComposerTextMarkTest {

    private val mark = ComposerTextMark("note", 6, 9)

    private fun state(text: String = "I saw teh cat", vararg marks: ComposerTextMark<*> = arrayOf(mark)) =
        MatrixMarkdownTextFieldState(text).also { assertTrue(it.setMarks(text, marks.toList())) }

    /** Edits as the text field does for the user's input. */
    private fun MatrixMarkdownTextFieldState.input(block: TextFieldBuffer.() -> Unit) {
        textFieldState.edit {
            block()
            with(inputTransformation) { transformInput() }
        }
    }

    private fun MatrixMarkdownTextFieldState.assertMarks(text: String, vararg marks: ComposerTextMark<*>) {
        assertEquals(text, this.text.toString())
        assertEquals(marks.toList(), this.marks)
    }

    @Test
    fun marksForAnotherTextAreRejected() {
        val state = state()
        assertFalse(state.setMarks("I saw the cat", emptyList()))
        state.assertMarks("I saw teh cat", mark)
    }

    @Test
    fun marksOutsideTheTextAreDropped() {
        val state = MatrixMarkdownTextFieldState("teh")
        val fitting = ComposerTextMark("note", 0, 3)
        val outside = listOf(ComposerTextMark("note", 2, 4), ComposerTextMark("note", 1, 1), ComposerTextMark("note", -1, 2))
        state.setMarks("teh", outside + fitting)
        state.assertMarks("teh", fitting)
    }

    @Test
    fun editsBeforeAMarkShiftIt() {
        val state = state()
        state.input { replace(0, 1, "We") }
        state.assertMarks("We saw teh cat", mark.copy(start = 7, end = 10))
        state.edit { replace(0, 3, "") }
        state.assertMarks("saw teh cat", mark.copy(start = 4, end = 7))
    }

    @Test
    fun editsAfterAMarkLeaveItAlone() {
        val state = state()
        state.input { replace(10, 13, "dog!") }
        state.assertMarks("I saw teh dog!", mark)
    }

    @Test
    fun editingTheMarkedTextRemovesTheMark() {
        val typedInside = state()
        typedInside.input { replace(7, 7, "x") }
        typedInside.assertMarks("I saw txeh cat")

        val deletedInside = state()
        deletedInside.input { replace(8, 9, "") }
        deletedInside.assertMarks("I saw te cat")

        val replaced = state()
        replaced.input { replace(6, 9, "the") }
        replaced.assertMarks("I saw the cat")
    }

    @Test
    fun editsAtTheEdgesOfAMarkKeepItsText() {
        val appended = state()
        appended.input { replace(9, 9, "m") }
        appended.assertMarks("I saw tehm cat", mark)

        val prepended = state()
        prepended.input { replace(6, 6, "a") }
        prepended.assertMarks("I saw ateh cat", mark.copy(start = 7, end = 10))

        val joinedAfter = state()
        joinedAfter.input { replace(9, 10, "") }
        joinedAfter.assertMarks("I saw tehcat", mark)

        val joinedBefore = state()
        joinedBefore.input { replace(5, 6, "") }
        joinedBefore.assertMarks("I sawteh cat", mark.copy(start = 5, end = 8))
    }

    @Test
    fun aRewriteThatMayHaveEditedTheEdgeKeepsTheMark() {
        val before = state()
        before.input { replace(6, 9, "tteh") }
        before.assertMarks("I saw tteh cat", mark.copy(start = 7, end = 10))

        val after = state()
        after.input { replace(6, 9, "tehh") }
        after.assertMarks("I saw tehh cat", mark)
    }

    @Test
    fun aRewriteOfUnchangedTextKeepsTheMark() {
        val state = state()
        state.input { replace(0, length, "I saw teh cat!") }
        state.assertMarks("I saw teh cat!", mark)
    }

    @Test
    fun undoHidesMarksUntilTheirTextIsBack() {
        val state = state()
        state.input { replace(13, 13, "s") }
        state.assertMarks("I saw teh cats", mark)
        state.undoState.undo()
        state.assertMarks("I saw teh cat")
        state.undoState.redo()
        state.assertMarks("I saw teh cats", mark)
    }

    @Test
    fun insertingAnEntityShiftsLaterMarks() {
        val state = state()
        state.insertEntity(TextRange(0, 1), "@Alice", textAfter = " ") { start, end ->
            MatrixInlineEntity.UserMention(start, end, "@alice:example.org")
        }
        state.assertMarks("@Alice  saw teh cat", mark.copy(start = 12, end = 15))
    }

    @Test
    fun replacingTheContentDropsMarks() {
        val state = state()
        state.setContent("something else")
        state.assertMarks("something else")
    }
}
