package com.beeper.android.messageformat.composer

import androidx.compose.ui.text.TextRange
import com.beeper.android.messageformat.markdown.MatrixInlineEntity
import kotlin.test.Test
import kotlin.test.assertEquals

class MatrixMarkdownTextFieldStateTest {

    private fun alice(start: Int, end: Int) = MatrixInlineEntity.UserMention(start, end, "@alice:example.org")

    private fun state(text: String = "hi @Alice there", vararg entities: MatrixInlineEntity = arrayOf(alice(3, 9))) =
        MatrixMarkdownTextFieldState(text, entities.toList())

    private fun MatrixMarkdownTextFieldState.assertState(text: String, vararg entities: MatrixInlineEntity) {
        assertEquals(text, this.text.toString())
        assertEquals(entities.toList(), this.entities)
    }

    @Test
    fun typingBeforeShiftsTheEntity() {
        val state = state()
        state.edit { replace(0, 0, "oh ") }
        state.assertState("oh hi @Alice there", alice(6, 12))
    }

    @Test
    fun typingAtTheBoundariesStaysOutside() {
        val state = state()
        state.edit { replace(3, 3, "X") }
        state.assertState("hi X@Alice there", alice(4, 10))
        state.edit { replace(10, 10, "Y") }
        state.assertState("hi X@AliceY there", alice(4, 10))
    }

    @Test
    fun typingAfterLeavesTheRangeAlone() {
        val state = state()
        state.edit { replace(15, 15, "!") }
        state.assertState("hi @Alice there!", alice(3, 9))
    }

    @Test
    fun deletingIntoThePillRemovesAllOfIt() {
        val backspace = state()
        backspace.edit { replace(8, 9, ""); selection = TextRange(8) }
        backspace.assertState("hi  there")
        assertEquals(TextRange(3), backspace.textFieldState.selection)

        val forwardDelete = state()
        forwardDelete.edit { replace(3, 4, ""); selection = TextRange(3) }
        forwardDelete.assertState("hi  there")
    }

    @Test
    fun replacingPartOfThePillDropsTheRestButKeepsTypedText() {
        val state = state()
        state.edit { replace(3, 6, "x"); selection = TextRange(4) }
        state.assertState("hi x there")
        assertEquals(TextRange(4), state.textFieldState.selection)
    }

    @Test
    fun replacingASelectionContainingThePillKeepsTheTypedText() {
        val whole = state()
        whole.edit { replace(3, 9, "x"); selection = TextRange(4) }
        whole.assertState("hi x there")
        assertEquals(TextRange(4), whole.textFieldState.selection)

        val beyond = state()
        beyond.edit { replace(1, 11, "x"); selection = TextRange(2) }
        beyond.assertState("hxhere")
        assertEquals(TextRange(2), beyond.textFieldState.selection)
    }

    @Test
    fun cursorStaysAfterTextTypedOverTheEndOfAPill() {
        val state = state()
        state.edit { replace(6, 9, "x"); selection = TextRange(7) }
        state.assertState("hi x there")
        assertEquals(TextRange(4), state.textFieldState.selection)
    }

    @Test
    fun deletingAcrossThePillNeedsNoExtraDeletion() {
        val state = state()
        state.edit { replace(1, 11, "") }
        state.assertState("hhere")
    }

    @Test
    fun typingInsideDissolvesThePillIntoText() {
        val state = state()
        state.edit { replace(6, 6, "x") }
        state.assertState("hi @Alxice there")
    }

    @Test
    fun pastingOverEverythingNeverCreatesMentions() {
        val state = state("@Alice", alice(0, 6))
        state.edit { replace(0, length, "ask @Alice about it") }
        state.assertState("ask @Alice about it")
    }

    @Test
    fun pastingOverAPillNeverDeletesPastedText() {
        val state = state("hi @Alice", alice(3, 9))
        state.edit { replace(0, length, "hi @Bob") }
        state.assertState("hi @Bob")
        val repeated = state("A@Alice", alice(1, 7))
        repeated.edit { replace(0, length, "@Alice") }
        repeated.assertState("@Alice", alice(0, 6))
    }

    @Test
    fun undoKeepsAPillThatStartsLikeTheUndoneText() {
        val state = state("@Alice", alice(0, 6))
        state.edit { replace(0, 0, "@") }
        state.textFieldState.undoState.undo()
        state.assertState("@Alice", alice(0, 6))
    }

    @Test
    fun undoRealignsEntities() {
        val state = state()
        state.edit { replace(0, 0, "oh ") }
        state.textFieldState.undoState.undo()
        state.assertState("hi @Alice there", alice(3, 9))
    }

    @Test
    fun undoingAnInsertedEntityDropsItAndRedoBringsItBack() {
        val state = MatrixMarkdownTextFieldState("hey @al")
        state.insertEntity(TextRange(4, 7), "@Alice") { start, end -> alice(start, end) }
        state.textFieldState.undoState.undo()
        state.assertState("hey @al")
        state.textFieldState.undoState.redo()
        state.assertState("hey @Alice ", alice(4, 10))
    }

    @Test
    fun undoingOneOfTwoEqualPillsKeepsOnlyOne() {
        val state = state("@Alice", alice(0, 6))
        state.setContent("@Alice@Alice", listOf(alice(0, 6), alice(6, 12)))
        state.textFieldState.undoState.undo()
        state.assertState("@Alice", alice(0, 6))
    }

    @Test
    fun undoingADeletedPillBringsItBack() {
        val state = state()
        state.edit { replace(8, 9, ""); selection = TextRange(8) }
        state.assertState("hi  there")
        state.textFieldState.undoState.undo()
        state.assertState("hi @Alice there", alice(3, 9))
    }

    @Test
    fun editRightAfterUndoStartsFromTheRealignedEntities() {
        val state = state()
        state.edit { replace(0, 0, "oh ") }
        state.textFieldState.undoState.undo()
        state.edit { replace(0, 0, "X") }
        state.assertState("Xhi @Alice there", alice(4, 10))
    }

    @Test
    fun editBypassingTheStateKeepsEntitiesInUnchangedText() {
        val state = state("@Alice", alice(0, 6))
        state.textFieldState.edit { replace(0, 0, "@") }
        state.assertState("@@Alice", alice(1, 7))
        state.textFieldState.edit { replace(3, 3, "x") }
        state.assertState("@@Axlice")
    }

    @Test
    fun wholeTextReplacementKeepsEntitiesInUnchangedText() {
        val state = state()
        state.edit { replace(0, length, "hi @Alice there!") }
        state.assertState("hi @Alice there!", alice(3, 9))
        state.edit { replace(0, length, "oh hi @Alice there!") }
        state.assertState("oh hi @Alice there!", alice(6, 12))
        state.edit { replace(0, length, "gone") }
        state.assertState("gone")
    }

    @Test
    fun breakingOnePillLeavesOthersAligned() {
        val bob = MatrixInlineEntity.UserMention(14, 18, "@bob:example.org")
        val state = state("hi @Alice and @Bob", alice(3, 9), bob)
        state.edit { replace(4, 5, "") }
        state.assertState("hi  and @Bob", bob.moved(8, 12))
    }

    @Test
    fun insertEntityReplacesTheTriggerAndTracksTheEntity() {
        val state = MatrixMarkdownTextFieldState("hey @al")
        state.insertEntity(TextRange(4, 7), "@Alice") { start, end -> alice(start, end) }
        state.assertState("hey @Alice ", alice(4, 10))
        assertEquals(TextRange(11), state.textFieldState.selection)
    }

    @Test
    fun insertEntityOverACutPillLandsWhereThePillWas() {
        val state = state()
        val emote = { start: Int, end: Int -> MatrixInlineEntity.CustomEmote(start, end, "cat", "mxc://x/cat") }
        state.insertEntity(TextRange(5, 12), ":cat:", emote)
        state.assertState("hi :cat: ere", emote(3, 8))
        assertEquals(TextRange(9), state.textFieldState.selection)
    }

    @Test
    fun insertEntityInsideAPillGoesNextToIt() {
        val state = state()
        state.insertEntity(TextRange(5), "@Bob") { start, end -> MatrixInlineEntity.UserMention(start, end, "@bob:x") }
        state.assertState("hi @Bob @Alice there", MatrixInlineEntity.UserMention(3, 7, "@bob:x"), alice(8, 14))
    }

    @Test
    fun setContentReplacesTextEntitiesAndSelection() {
        val state = state()
        state.setContent("yo @Bob", listOf(MatrixInlineEntity.UserMention(3, 7, "@bob:example.org")), TextRange(1))
        state.assertState("yo @Bob", MatrixInlineEntity.UserMention(3, 7, "@bob:example.org"))
        assertEquals(TextRange(1), state.textFieldState.selection)
        state.setContent("")
        state.assertState("")
    }

    @Test
    fun cursorSnapsOutOfPills() {
        val state = state()
        state.edit { selection = TextRange(5) }
        assertEquals(TextRange(3), state.textFieldState.selection)
        state.edit { selection = TextRange(7) }
        assertEquals(TextRange(9), state.textFieldState.selection)
    }

    @Test
    fun steppingIntoAPillFromItsEdgeCrossesIt() {
        val state = state()
        state.edit { selection = TextRange(9) }
        state.edit { selection = TextRange(8) }
        assertEquals(TextRange(3), state.textFieldState.selection)
        state.edit { selection = TextRange(4) }
        assertEquals(TextRange(9), state.textFieldState.selection)
    }
}
