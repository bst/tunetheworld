package io.github.bst.tunetheworld.music

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class NoteNamesTest {
    @Test
    fun namesPerLanguage() {
        assertEquals("C", NoteNames.name(0, AppLanguage.DE))
        assertEquals("C", NoteNames.name(0, AppLanguage.EN))
        assertEquals("Es", NoteNames.name(3, AppLanguage.DE))
        assertEquals("E♭", NoteNames.name(3, AppLanguage.EN))
        assertEquals("B", NoteNames.name(10, AppLanguage.DE))
        assertEquals("B♭", NoteNames.name(10, AppLanguage.EN))
        assertEquals("H", NoteNames.name(11, AppLanguage.DE))
        assertEquals("B", NoteNames.name(11, AppLanguage.EN))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsOutOfRange() {
        NoteNames.name(12, AppLanguage.DE)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsNegative() {
        NoteNames.name(-1, AppLanguage.EN)
    }

    @Test
    fun languageFromLocale() {
        assertEquals(AppLanguage.DE, NoteNames.languageOf(Locale.GERMANY))
        assertEquals(AppLanguage.DE, NoteNames.languageOf(Locale.Builder().setLanguage("de").setRegion("AT").build()))
        assertEquals(AppLanguage.EN, NoteNames.languageOf(Locale.US))
        assertEquals(AppLanguage.EN, NoteNames.languageOf(Locale.FRANCE))
    }
}
