package io.github.bst.tunetheworld.music

enum class AppLanguage(val tag: String) {
    DE("de"),
    EN("en")
}

object NoteNames {
    private val GERMAN = arrayOf(
        "C", "Cis", "D", "Es", "E", "F",
        "Fis", "G", "Gis", "A", "B", "H"
    )
    private val ENGLISH = arrayOf(
        "C", "C♯", "D", "E♭", "E", "F",
        "F♯", "G", "G♯", "A", "B♭", "B"
    )

    fun name(pitchClass: Int, language: AppLanguage): String {
        require(pitchClass in 0..11) { "pitchClass out of range: $pitchClass" }
        return when (language) {
            AppLanguage.DE -> GERMAN[pitchClass]
            AppLanguage.EN -> ENGLISH[pitchClass]
        }
    }

    // Every locale except German falls back to English
    fun languageOf(locale: java.util.Locale): AppLanguage =
        if (locale.language == "de") AppLanguage.DE else AppLanguage.EN
}
