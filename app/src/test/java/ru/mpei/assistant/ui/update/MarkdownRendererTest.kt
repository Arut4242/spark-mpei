package ru.mpei.assistant.ui.update

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownRendererTest {

    @Test
    fun `test parseMarkdown headings`() {
        val md = """
            # Заголовок 1
            ## Заголовок 2
            ### Заголовок 3
        """.trimIndent()

        val blocks = parseMarkdown(md)
        assertEquals(3, blocks.size)

        val h1 = blocks[0] as MarkdownBlock.Heading
        assertEquals(1, h1.level)
        assertEquals("Заголовок 1", h1.text)

        val h2 = blocks[1] as MarkdownBlock.Heading
        assertEquals(2, h2.level)
        assertEquals("Заголовок 2", h2.text)

        val h3 = blocks[2] as MarkdownBlock.Heading
        assertEquals(3, h3.level)
        assertEquals("Заголовок 3", h3.text)
    }

    @Test
    fun `test parseMarkdown bullet and numbered lists`() {
        val md = """
            * Пункт 1
            - Пункт 2
              * Вложенный пункт
            1. Первый
            2. Второй
        """.trimIndent()

        val blocks = parseMarkdown(md)
        assertEquals(5, blocks.size)

        val item1 = blocks[0] as MarkdownBlock.ListItem
        assertEquals("Пункт 1", item1.text)
        assertEquals(0, item1.indentLevel)

        val item2 = blocks[1] as MarkdownBlock.ListItem
        assertEquals("Пункт 2", item2.text)
        assertEquals(0, item2.indentLevel)

        val item3 = blocks[2] as MarkdownBlock.ListItem
        assertEquals("Вложенный пункт", item3.text)
        assertEquals(1, item3.indentLevel)

        val ord1 = blocks[3] as MarkdownBlock.OrderedListItem
        assertEquals("Первый", ord1.text)
        assertEquals("1.", ord1.number)

        val ord2 = blocks[4] as MarkdownBlock.OrderedListItem
        assertEquals("Второй", ord2.text)
        assertEquals("2.", ord2.number)
    }

    @Test
    fun `test parseMarkdown blockquote and horizontal rule`() {
        val md = """
            > Важная цитата
            ---
        """.trimIndent()

        val blocks = parseMarkdown(md)
        assertEquals(2, blocks.size)

        val quote = blocks[0] as MarkdownBlock.Blockquote
        assertEquals("Важная цитата", quote.text)

        assertTrue(blocks[1] is MarkdownBlock.HorizontalRule)
    }

    @Test
    fun `test parseMarkdown code block`() {
        val md = """
            ```kotlin
            fun test() = 42
            ```
        """.trimIndent()

        val blocks = parseMarkdown(md)
        assertEquals(1, blocks.size)

        val code = blocks[0] as MarkdownBlock.CodeBlock
        assertEquals("fun test() = 42", code.code)
        assertEquals("kotlin", code.language)
    }

    @Test
    fun `test buildMarkdownAnnotatedString inline styles`() {
        val text = "Это **жирный**, это *курсив*, это `код` и [ссылка](https://mpei.ru)"
        val annotated = buildMarkdownAnnotatedString(
            text = text,
            defaultColor = Color.Black
        )

        // Raw text content
        val raw = annotated.text
        assertTrue(raw.contains("Это жирный, это курсив, это  код  и ссылка"))

        // Span styles are applied
        assertTrue(annotated.spanStyles.isNotEmpty())
    }
}
