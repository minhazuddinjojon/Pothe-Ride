package com.potheride.app.core.i18n

import com.potheride.app.core.format.AppLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StringsTest {

    private fun allGetters() = Strings::class.java.methods
        .filter { it.parameterCount == 0 && it.returnType == String::class.java }
        .sortedBy { it.name }

    @Test
    fun everyStringIsPresentAndNonBlankInBothLanguages() {
        for (getter in allGetters()) {
            val en = getter.invoke(EnglishStrings) as String
            val bn = getter.invoke(BanglaStrings) as String
            assertTrue("${getter.name} is blank in English", en.isNotBlank())
            assertTrue("${getter.name} is blank in Bangla", bn.isNotBlank())
        }
    }

    @Test
    fun banglaStringsActuallyContainBengaliScript() {
        // Catches the copy-paste failure where an English string is pasted into the
        // Bangla implementation to make it compile and then never translated.
        val bengaliRange = '\u0980'..'\u09FF'
        val untranslated = allGetters().filter { getter ->
            val bn = getter.invoke(BanglaStrings) as String
            bn.none { it in bengaliRange }
        }.map { it.name }

        assertEquals("these Bangla strings contain no Bengali characters: $untranslated", 0, untranslated.size)
    }

    @Test
    fun theTwoLanguagesAreNotAccidentallyIdentical() {
        val identical = allGetters().filter { getter ->
            getter.invoke(EnglishStrings) == getter.invoke(BanglaStrings)
        }.map { it.name }
        assertEquals("these strings are identical in both languages: $identical", 0, identical.size)
    }

    @Test
    fun theResolverPicksTheRightImplementation() {
        assertEquals(BanglaStrings, stringsFor(AppLanguage.BANGLA))
        assertEquals(EnglishStrings, stringsFor(AppLanguage.ENGLISH))
    }
}
