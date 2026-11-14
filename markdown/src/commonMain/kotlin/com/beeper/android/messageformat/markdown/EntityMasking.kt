package com.beeper.android.messageformat.markdown

/**
 * Temporarily hides text that must not be read as markdown, such as a mention's display name, and
 * puts the entity's HTML back in its place when rendering.
 */
internal class EntityMasking private constructor(
    val text: String,
    private val entities: List<MatrixInlineEntity>,
    private val placeholders: CharArray,
) {

    /** Replaces every placeholder run in [output] with [render] of the entity it stands for. */
    inline fun unmask(output: CharSequence, render: (MatrixInlineEntity) -> CharSequence): CharSequence {
        if (output.none { entityIndex(it) != null }) return output
        val result = StringBuilder(output.length)
        var index = 0
        while (index < output.length) {
            val entityIndex = entityIndex(output[index])
            if (entityIndex == null) {
                result.append(output[index])
                index++
                continue
            }
            while (index < output.length && output[index] == placeholders[entityIndex]) index++
            result.append(render(entities[entityIndex]))
        }
        return result
    }

    fun entityIndex(char: Char): Int? = placeholders.indexOf(char).takeIf { it >= 0 }

    companion object {
        private val PRIVATE_USE = '\uE000'..'\uF8FF'

        fun mask(text: String, entities: List<MatrixInlineEntity>): EntityMasking {
            val taken = text.filter { it in PRIVATE_USE }.toSet()
            val candidates = PRIVATE_USE.asSequence().filter { it !in taken }.iterator()
            val placeholders = CharArray(entities.size) { candidates.next() }
            val masked = StringBuilder(text)
            entities.forEachIndexed { index, entity ->
                for (offset in entity.start until entity.end) masked.setCharAt(offset, placeholders[index])
            }
            return EntityMasking(masked.toString(), entities, placeholders)
        }
    }
}
