package io.github.sh_sunfaded.schedulemanager.domain

import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationTextGoldenTest {
    private data class Case(val kind: FireKind, val title: String, val at: Long, val expected: String)

    private fun loadGolden(): List<Case> {
        val text = javaClass.classLoader!!.getResourceAsStream("golden/notification_text.golden")!!
            .bufferedReader(Charsets.UTF_8).readText()
        return text.lines()
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .map { line ->
                val (kind, title, at, expected) = line.split("|")
                Case(
                    FireKind.valueOf(kind),
                    title.replace("\\n", "\n"),
                    JstTime.toEpochMillis(LocalDateTime.parse(at)),
                    expected,
                )
            }
    }

    @Test
    fun goldenFileHasCases() {
        assertTrue(loadGolden().size >= 5)
    }

    @Test
    fun generatedTextMatchesGoldenWithZeroDiff() {
        val diffs = loadGolden().mapNotNull { c ->
            val actual = NotificationText.of(c.kind, c.title, c.at)
            if (actual == c.expected) null else "expected=${c.expected} actual=$actual"
        }
        assertEquals(emptyList<String>(), diffs)
    }

    @Test
    fun textIsSingleLineWithoutDecoration() {
        loadGolden().forEach { c ->
            val actual = NotificationText.of(c.kind, c.title, c.at)
            assertFalse(actual.contains('\n'))
            assertTrue(actual.endsWith("。"))
        }
    }

    @Test
    fun timeIsShownInJstRegardlessOfDefaultTimeZone() {
        val saved = java.util.TimeZone.getDefault()
        try {
            java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("America/New_York"))
            val at = JstTime.toEpochMillis(LocalDateTime.of(2026, 11, 27, 9, 30))
            assertEquals("買い物の期限です(09:30)。", NotificationText.due("買い物", at))
        } finally {
            java.util.TimeZone.setDefault(saved)
        }
    }
}
