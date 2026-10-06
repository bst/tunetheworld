# Implementierungsplan: Saxophon-Stimmgerät (Android)

Oct 6, 2026 · @Sebastian

## Ziel und Regeln

Gebaut wird eine Android-App, die per Mikrofon die Tonhöhe eines Tenor- oder Altsaxophons erkennt und den Ton samt Abweichung in Prozent mit einem Zeiger anzeigt. Der Kammerton (Standard 440 Hz) ist einstellbar.

**Funktionsumfang Version 1**

- Ton erkennen und als Notenname mit Oktave anzeigen, standardmäßig in Saxophon-Griffschrift (transponiert, siehe nächster Abschnitt).
- Abweichung als Zahl („+12 %“) und als Zeiger auf einer Skala von −50 % bis +50 %.
- Kammerton einstellbar von 400 bis 480 Hz in 1-Hz-Schritten, dauerhaft gespeichert.
- Umschalter Anzeige mit drei Modi: „Tenorsax (B)“, „Altsax (Es)“ oder „Klingend (C)“. Standard ist Tenorsax.
- Sprache der Oberfläche umschaltbar zwischen Deutsch und Englisch, dauerhaft gespeichert. Die Notennamen folgen der Sprache (Deutsch mit B und H, Englisch mit B♭ und B).
- Zusätzlich klein die gemessene Frequenz in Hz.

**Harte Regeln für die Umsetzung**

1. Keine GPL- oder LGPL-Bibliotheken. Insbesondere kein TarsosDSP und kein Code daraus kopieren.
2. Erlaubte Abhängigkeiten: nur AndroidX (inklusive AppCompat für die Sprachumschaltung), Jetpack Compose, Kotlin Coroutines, DataStore, JUnit. Alle stehen unter Apache 2.0.
3. Die Tonhöhenerkennung (YIN) wird selbst in reinem Kotlin geschrieben, ohne Android-Abhängigkeiten, damit sie per Unit-Test prüfbar ist.
4. Kein NDK, kein C++, kein Oboe.
5. Code, Bezeichner und Kommentare durchgehend auf Englisch.
6. Alle sichtbaren Texte stehen in String-Ressourcen, nie fest im Code.
7. Jede Phase endet erst, wenn ihre Tests grün sind und die App baut.

## Fachlicher Hintergrund

Das Tenorsaxophon ist ein B-Instrument: Es klingt eine große None (14 Halbtöne) tiefer als notiert. Das Altsaxophon ist ein Es-Instrument: Es klingt eine große Sexte (9 Halbtöne) tiefer als notiert. Saxophonisten denken in Griffschrift, deshalb zeigt die App den geschriebenen Ton an, standardmäßig für Tenor.

Tonumfang Alt: tiefster Ton geschrieben B♭3 (klingend Des3, MIDI 49, 138,59 Hz), höchster Normalton geschrieben Fis6 (klingend A5, MIDI 81, 880,00 Hz). Das liegt komplett im Erkennungsbereich unten.

**Tonumfang und Erkennungsbereich**

| Ton (Griffschrift) | Klingend | MIDI klingend | Frequenz bei 440 Hz |
| --- | --- | --- | --- |
| Tiefes B3 (tiefster Ton) | As2 | 44 | 103,83 Hz |
| C4 | B2 | 46 | 116,54 Hz |
| A4 | G3 | 55 | 196,00 Hz |
| C5 (typischer Stimmton) | B3 | 58 | 233,08 Hz |
| Fis6 (höchster Normalton) | E5 | 76 | 659,26 Hz |
| ca. C7 (Altissimo) | B5 | 82 | 932,33 Hz |

Der Detektor sucht deshalb zwischen **90 Hz und 1100 Hz**. Das deckt auch extreme Kammertöne (400 bzw. 480 Hz) und leicht verstimmte Töne ab.

**Klangliche Besonderheiten**

- Das Saxophon ist sehr obertonreich. In der tiefen Lage ist der 2. Teilton oft lauter als der Grundton. Ein reiner FFT-Peak würde dann eine Oktave zu hoch anzeigen, YIN ist hier robust.
- Beim Tonansatz schwankt die Tonhöhe kurz. Vibrato lässt sie periodisch pendeln. Beides fängt die Glättung ab.

**Prozent-Anzeige**

Die Abweichung wird in Prozent eines Halbtons angegeben. 100 % entsprechen einem Halbton, also ist 1 % gleich 1 Cent. Die Skala reicht von −50 % bis +50 %, denn ab 50 % liegt man näher am Nachbarton.

**Formeln**

```latex
m = 69 + 12 \cdot \log_2\left(\frac{f}{f_{\mathrm{ref}}}\right)
```

- f = gemessene Frequenz, f\_ref = eingestellter Kammerton (z. B. 440 Hz).
- Klingende Note: n = round(m). Abweichung: prozent = (m − n) × 100.
- Angezeigte Note: n + 14 im Modus „Tenorsax (B)“, n + 9 im Modus „Altsax (Es)“, sonst n.
- Notenklasse (Index 0 bis 11) = Note mod 12. Oktave = floorDiv(Note, 12) − 1.
- Namen (deutsche Schreibweise, Index 0 bis 11): C, Cis, D, Es, E, F, Fis, G, Gis, A, B, H.
- Namen (englische Schreibweise, Index 0 bis 11): C, C♯, D, E♭, E, F, F♯, G, G♯, A, B♭, B.
- Achtung: „B“ ist auf Deutsch Index 10, auf Englisch Index 11. Gerechnet wird nur mit dem Index, der Name kommt erst bei der Anzeige dazu.

Der Kammerton bezieht sich immer auf das klingende A4. Die Transposition wird erst nach der Berechnung addiert.

## Technischer Rahmen und Architektur

Eine Activity, Jetpack Compose, MVVM. Audio und Analyse laufen in einer Coroutine auf `Dispatchers.Default`, das UI liest nur einen `StateFlow`.

&#91;embedded content: Datenfluss · 5 Stufen im Hintergrund, 3 Bausteine in der Oberfläche\]

Nur die geglättete Frequenz verlässt den Hintergrund-Thread. Erst das ViewModel rechnet sie mit Kammerton und Modus in Note und Prozent um.

**Projekt-Setup**

- Sprache Kotlin, UI Jetpack Compose mit Material 3.
- `minSdk = 26`, `targetSdk` und `compileSdk` = aktuelle stabile Version.
- Paketname: `de.example.saxtuner` (vom Nutzer anpassbar).
- Abhängigkeiten: Compose BOM, `activity-compose`, `appcompat`, `lifecycle-viewmodel-compose`, `lifecycle-runtime-compose`, `datastore-preferences`, `kotlinx-coroutines-android`, JUnit 4.

**Dateien** (unter `app/src/main/java/de/example/saxtuner/`)

| Datei | Aufgabe | Android-frei? |
| --- | --- | --- |
| `dsp/YinPitchDetector.kt` | YIN-Algorithmus, liefert Frequenz und Konfidenz | ja |
| `dsp/SignalUtils.kt` | RMS-Berechnung für das Lautstärke-Gate | ja |
| `dsp/PitchSmoother.kt` | Median-Glättung und Oktavsprung-Schutz | ja |
| `dsp/PitchPipeline.kt` | Fenster, Pegel-Gate, YIN, Konfidenz-Filter und Glättung als eine Einheit | ja |
| `music/Transposition.kt` | Enum für Tenorsax (B), Altsax (Es) und Klingend (C) | ja |
| `music/NoteMapper.kt` | Frequenz zu Notenklasse, Oktave, Prozent | ja |
| `music/NoteNames.kt` | Notenklasse zu Namen, je nach Sprache | ja |
| `audio/AudioInput.kt` | Kapselt `AudioRecord`, liefert Sample-Blöcke | nein |
| `engine/TunerEngine.kt` | Aufnahme-Schleife, reicht Sample-Blöcke an `PitchPipeline` weiter, Ergebnis-Flow | nein |
| `data/SettingsRepository.kt` | Kammerton und Transposition per DataStore | nein |
| `ui/TunerViewModel.kt` | Verbindet Engine, Einstellungen und UI-State | nein |
| `ui/TunerScreen.kt` | Hauptbildschirm | nein |
| `ui/NeedleGauge.kt` | Zeigerinstrument als Compose-Canvas | nein |
| `ui/SettingsSheet.kt` | Kammerton, Transposition und Sprache einstellen | nein |
| `ui/PermissionScreen.kt` | Mikrofon-Berechtigung erklären und anfragen | nein |
| `MainActivity.kt` | Einstiegspunkt, Theme, Berechtigungs-Weiche | nein |
| `res/values/strings.xml` | Alle Texte auf Englisch (Standard) | – |
| `res/values-de/strings.xml` | Alle Texte auf Deutsch | – |
| `res/xml/locales_config.xml` | Unterstützte Sprachen: `en`, `de` | – |

**Datentypen**

```kotlin
data class PitchResult(val frequencyHz: Float, val confidence: Float)

enum class Transposition(val semitones: Int) {
    TENOR_BB(14),  // written pitch for B-flat tenor saxophone
    ALTO_EB(9),    // written pitch for E-flat alto saxophone
    CONCERT(0)     // sounding (concert) pitch
}

enum class AppLanguage(val tag: String) {
    DE("de"),
    EN("en")
}

data class NoteResult(
    val pitchClass: Int,     // 0..11, 0 = C, independent of language
    val octave: Int,         // scientific pitch notation, e.g. 5
    val percent: Float,      // -50f..+50f, 1 % = 1 cent
    val frequencyHz: Float
)

data class TunerSettings(
    val referenceHz: Int = 440,
    val transposition: Transposition = Transposition.TENOR_BB
)

data class TunerUiState(
    val note: NoteResult? = null,   // null = no tone detected
    val isActive: Boolean = false,  // true while a tone is sounding
    val settings: TunerSettings = TunerSettings()
)
```

Die Sprache steht bewusst nicht in `TunerSettings`. Android speichert sie selbst als App-Sprache (Phase 0 und 7). Rechenlogik und Tests arbeiten nur mit `pitchClass`, der Name entsteht erst in der Oberfläche über `NoteNames`. Die Anzeigetexte der Modi („Tenorsax (B)“ bzw. „Tenor sax (B♭)“, „Altsax (Es)“ bzw. „Alto sax (E♭)“) kommen aus den String-Ressourcen, nicht aus dem Enum.

**Zentrale Konstanten** (in `engine/TunerConfig.kt` als `object TunerConfig`)

| Name | Wert | Bedeutung |
| --- | --- | --- |
| `SAMPLE_RATE` | 44100 | Abtastrate in Hz |
| `WINDOW_SIZE` | 4096 | Analysefenster in Samples (ca. 93 ms) |
| `HOP_SIZE` | 1024 | Neue Samples pro Analyse (ca. 23 ms) |
| `MIN_FREQ_HZ` | 90 | Untere Suchgrenze |
| `MAX_FREQ_HZ` | 1100 | Obere Suchgrenze |
| `YIN_THRESHOLD` | 0.15 | Schwellwert für YIN |
| `MIN_CONFIDENCE` | 0.80 | Ergebnisse darunter verwerfen |
| `RMS_GATE` | 0.01 | Unter diesem Pegel keine Analyse (ca. −40 dBFS) |
| `MEDIAN_SIZE` | 5 | Anzahl Werte für die Median-Glättung |
| `HOLD_MS` | 800 | So lange bleibt der letzte Ton nach Stille sichtbar |
| `UI_INTERVAL_MS` | 40 | Maximal 25 UI-Updates pro Sekunde |

`WINDOW_SIZE` ist bewusst 4096: YIN vergleicht ein Teilfenster von 2048 Samples mit Verschiebungen bis 490 Samples (44100 / 90 Hz). Das passt mit Reserve ins Fenster und gibt in der tiefen Lage stabile Werte.

## Umsetzung in Phasen

Die Phasen bauen aufeinander auf und werden strikt in dieser Reihenfolge umgesetzt. Phasen 1 bis 3b sind reine Rechenlogik ohne Android und werden zuerst per Unit-Test abgesichert. Phase 3b prüft die ganze Erkennungskette zusätzlich an echten Saxophon-Aufnahmen.

### Phase 0: Projekt anlegen

1. In Android Studio ein neues Projekt „Empty Activity“ (Compose) mit Kotlin anlegen, Paket `de.example.saxtuner`, `minSdk 26`.
2. Abhängigkeiten aus dem Abschnitt „Technischer Rahmen“ in `app/build.gradle.kts` eintragen, über den Version Catalog `libs.versions.toml`.
3. In `AndroidManifest.xml` eintragen: `<uses-permission android:name="android.permission.RECORD_AUDIO" />`. Die Activity bekommt `android:screenOrientation="portrait"`.
4. Am `<application>`-Tag `android:localeConfig="@xml/locales_config"` setzen und `res/xml/locales_config.xml` mit den Sprachen `en` und `de` anlegen.
5. Damit AppCompat die gewählte Sprache auch unter Android 12 und älter speichert, im Manifest innerhalb von `<application>` eintragen:

   ```xml
   <service
       android:name="androidx.appcompat.app.AppLocalesMetadataHolderService"
       android:enabled="false"
       android:exported="false">
       <meta-data
           android:name="autoStoreLocales"
           android:value="true" />
   </service>
   ```
6. Das App-Theme in `res/values/themes.xml` muss von `Theme.AppCompat.DayNight.NoActionBar` erben, weil `MainActivity` von `AppCompatActivity` erbt. Compose nutzt weiterhin sein eigenes `MaterialTheme`.
7. `engine/TunerConfig.kt` mit allen Konstanten aus der Tabelle anlegen.
8. `res/values/strings.xml` (Englisch) und `res/values-de/strings.xml` (Deutsch) mit allen Texten aus der Tabelle in Phase 7 anlegen.

**Fertig, wenn:** die App baut und auf dem Gerät einen leeren Bildschirm zeigt.

### Phase 1: Noten-Berechnung

Dateien: `music/Transposition.kt`, `music/NoteMapper.kt`, `music/NoteNames.kt`. Die Datentypen stehen im Architektur-Abschnitt. `NoteMapper` liefert nur die Notenklasse, den Namen bestimmt `NoteNames` je nach Sprache.

```kotlin
object NoteMapper {
    fun map(frequencyHz: Float, referenceHz: Int, transposition: Transposition): NoteResult {
        val midiExact = 69.0 + 12.0 * log2(frequencyHz.toDouble() / referenceHz)
        val nearest = midiExact.roundToInt()              // nearest sounding note (MIDI number)
        val percent = ((midiExact - nearest) * 100.0).toFloat()
        val display = nearest + transposition.semitones   // transpose only after rounding
        return NoteResult(
            pitchClass = Math.floorMod(display, 12),
            octave = Math.floorDiv(display, 12) - 1,
            percent = percent,
            frequencyHz = frequencyHz
        )
    }
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
```

**Fertig, wenn:** alle Tests für `NoteMapper` aus dem Test-Abschnitt grün sind.

### Phase 2: YIN-Tonhöhenerkennung

Datei: `dsp/YinPitchDetector.kt`. Reines Kotlin, keine Android-Imports. Alle Arrays werden im Konstruktor einmal angelegt, `detect()` allokiert nichts.

```kotlin
class YinPitchDetector(
    private val sampleRate: Int,
    windowSize: Int,
    minFreqHz: Float,
    maxFreqHz: Float,
    private val threshold: Float = 0.15f
) {
    private val w = windowSize / 2                                   // integration window
    private val tauMin = floor(sampleRate / maxFreqHz).toInt().coerceAtLeast(2)
    private val tauMax = ceil(sampleRate / minFreqHz).toInt().coerceAtMost(w - 2)
    private val diff = FloatArray(tauMax + 2)
    private val cmnd = FloatArray(tauMax + 2)

    fun detect(buffer: FloatArray): PitchResult? {
        require(buffer.size >= w + tauMax + 1)

        // Step 1: difference function
        for (tau in 1..tauMax + 1) {
            var sum = 0f
            for (j in 0 until w) {
                val d = buffer[j] - buffer[j + tau]
                sum += d * d
            }
            diff[tau] = sum
        }

        // Step 2: cumulative mean normalized difference
        cmnd[0] = 1f
        var running = 0f
        for (tau in 1..tauMax + 1) {
            running += diff[tau]
            cmnd[tau] = if (running == 0f) 1f else diff[tau] * tau / running
        }

        // Step 3: first local minimum below the threshold
        var tau = tauMin
        var found = -1
        while (tau <= tauMax) {
            if (cmnd[tau] < threshold) {
                while (tau + 1 <= tauMax && cmnd[tau + 1] < cmnd[tau]) tau++
                found = tau
                break
            }
            tau++
        }
        if (found == -1) return null

        // Step 4: parabolic interpolation for sub-sample accuracy
        val s0 = cmnd[found - 1]; val s1 = cmnd[found]; val s2 = cmnd[found + 1]
        val denom = s0 - 2f * s1 + s2
        val refined = if (denom == 0f) found.toFloat() else found + 0.5f * (s0 - s2) / denom

        return PitchResult(
            frequencyHz = sampleRate / refined,
            confidence = (1f - s1).coerceIn(0f, 1f)
        )
    }
}
```

Rechenaufwand pro Aufruf: 2048 × 491 ≈ 1 Mio. Multiplikationen. Das dauert auf aktuellen Handys etwa 1–3 ms und ist bei 43 Aufrufen pro Sekunde unkritisch.

**Fertig, wenn:** alle Tests für `YinPitchDetector` grün sind.

### Phase 3: Pegel und Glättung

**`dsp/SignalUtils.kt`:** Funktion `fun rms(buffer: FloatArray, from: Int, length: Int): Float` = Wurzel aus dem Mittelwert der Quadrate.

**`dsp/PitchSmoother.kt`:** Klasse mit `fun add(freqHz: Float): Float` und `fun reset()`.

1. Ringpuffer `FloatArray(MEDIAN_SIZE)` plus Zähler für die Anzahl gefüllter Werte. Ein zweites Array gleicher Größe dient als Sortier-Kopie, damit nichts allokiert wird.
2. `add()` gibt den Median der vorhandenen Werte zurück.
3. Oktavsprung-Schutz: Weicht ein neuer Wert um mehr als 0,8 Halbtöne vom aktuellen Median ab (`abs(12 * log2(neu / median)) > 0.8`), wird er nicht übernommen. Ein Zähler `jumpCount` wird erhöht und der bisherige Median zurückgegeben.
4. Liegen 3 solche Abweichler in Folge innerhalb von 0,8 Halbtönen zueinander, war es ein echter Tonwechsel: Puffer leeren, neuen Wert eintragen, `jumpCount = 0`.
5. Jeder normale Wert setzt `jumpCount = 0`.
6. `reset()` leert alles. Wird bei Stille aufgerufen.

**Fertig, wenn:** alle Tests für `PitchSmoother` grün sind.

### Phase 3b: Pipeline und Validierung mit echten Samples

Ziel: Die komplette Erkennungskette wird ohne Gerät und ohne Emulator an echten Saxophon-Aufnahmen geprüft. Dafür liegt die Verarbeitungslogik in einer Android-freien Klasse, die später auch die `TunerEngine` benutzt.

**`dsp/PitchPipeline.kt`:** Klasse mit `fun process(block: FloatArray, length: Int): Float?` (`null` = kein verwertbarer Ton) und `fun reset()`. Sie enthält den Fenster-Schiebepuffer (`WINDOW_SIZE`), das RMS-Gate, `YinPitchDetector`, den Konfidenz-Filter (`MIN_CONFIDENCE`) und `PitchSmoother`. Die Logik entspricht exakt der Schleife aus Phase 4, nur ohne `AudioInput` und Flow. Keine Allokation in `process()`.

**Samples:** Verzeichnis `samples/alt saxophone/` im Projektstamm (neben `app/`, Leerzeichen im Namen beachten). Es enthält vier Altsaxophon-Aufnahmen (AIFF, 44,1 kHz, 24 Bit, Stereo, ca. 3 bis 4 s). Sie gehören nicht in die App, nur in die Tests.

| Datei | Klingender Ton | MIDI | Soll-Frequenz bei 440 Hz | Besonderheit |
| --- | --- | --- | --- | --- |
| `AltoSax.NoVib.ff.Db3.stereo.aif` | Des3 | 49 | 138,59 Hz | tiefster Altton, ohne Vibrato |
| `AltoSax.NoVib.ff.D3.stereo.aif` | D3 | 50 | 146,83 Hz | ohne Vibrato |
| `AltoSax.NoVib.ff.C5.stereo.aif` | C5 | 72 | 523,25 Hz | ohne Vibrato |
| `AltoSax.vib.ff.F5.stereo.aif` | F5 | 77 | 698,46 Hz | mit Vibrato |

Die Dateinamen nennen den **klingenden** Ton. Das ist am Signal geprüft: Eine grobe YIN-Messung ergab 139,6 / 147,0 / 531,3 / 700,0 Hz, also Des3 / D3 / C5 / F5. Der Erkennungsbereich 90 bis 1100 Hz deckt alle vier Töne ab.

Jede Datei wird in zwei Modi geprüft: **Klingend (C)** gegen den Dateinamen und **Altsax (Es)** gegen die Griffschrift (klingend + 9 Halbtöne):

| Datei | Klingend (Notenklasse / Oktave) | Altsax (Notenklasse / Oktave) | Name Altsax DE / EN |
| --- | --- | --- | --- |
| Db3 | 1 / 3 | 10 / 3 | B3 / B♭3 |
| D3 | 2 / 3 | 11 / 3 | H3 / B3 |
| C5 | 0 / 5 | 9 / 5 | A5 / A5 |
| F5 | 5 / 5 | 2 / 6 | D6 / D6 |

**Testhilfen** (nur in `app/src/test/`, kein Produktionscode, keine Bibliothek):

1. `AiffReader`: liest AIFF direkt aus den Bytes. Chunk `COMM` (Kanäle, Bit-Tiefe, Abtastrate als 80-Bit-Extended-Float) und Chunk `SSND` (Offset beachten). 24-Bit-Big-Endian-Integer in Float von −1 bis +1 wandeln und die zwei Kanäle zu Mono mitteln. Dateien mit anderer Abtastrate als 44100 Hz oder anderer Bit-Tiefe als 24 lassen den Test mit klarer Meldung fehlschlagen.
2. Der Test füttert die Pipeline in Blöcken zu `HOP_SIZE` und sammelt pro Block das Ergebnis, danach wird jede Frequenz mit `NoteMapper` (Kammerton 440, Klingend) umgerechnet.
3. Der Pfad zum Sample-Ordner kommt aus einer Gradle-Testeigenschaft (`systemProperty("samples.dir", ...)` in `app/build.gradle.kts`), nicht aus einem festen Pfad im Code. Fehlt das Verzeichnis, schlägt der Test fehl und wird nicht übersprungen.

**`SampleValidationTest`:** Pro Datei werden nur Blöcke gewertet, in denen die Pipeline einen Wert liefert („gewertet“). Erwartet wird:

| Prüfung | Erwartet |
| --- | --- |
| Anteil gewerteter Blöcke | mindestens 70 % aller Blöcke nach dem ersten gefüllten Fenster (Ausklang und Stille am Ende dürfen fehlen) |
| Notenklasse und Oktave | in mindestens 95 % der gewerteten Blöcke gleich dem Soll-Ton aus der Tabelle |
| Oktavsprünge | kein einziger gewerteter Block mit einer Abweichung von mehr als 600 Cent zum Soll-Ton (also keine Oktav- oder Quintfehler) |
| Median der Abweichung | Betrag unter 30 % (echte Spieler sind nicht exakt gestimmt, deshalb großzügiger als bei Synthetik) |
| Stabilität (ohne Vibrato, 3 Dateien) | mittlere Abweichung der Prozentwerte um ihren Median höchstens 5 % |
| Vibrato (F5) | Notenklasse und Oktave bleiben F5 in mindestens 95 % der gewerteten Blöcke, der Prozentwert pendelt sichtbar (Spannweite der Prozentwerte größer als 10 %), der Median liegt im Bereich ±30 % |
| Einschwingen | der erste gewertete Block liegt höchstens 300 ms nach dem ersten Block, in dem das Fenster voll ist |

Die Schwellen sind Startwerte. Liegt ein Wert daneben, gibt der Test zuerst die Messung aus (Tabelle pro Datei: Anteil gewertet, Median-Frequenz, Median-Prozent, Spannweite). Der Agent darf dann **ohne Rückfrage** die Schwellen dieser Tabelle und die Tuning-Konstanten in `TunerConfig` (`YIN_THRESHOLD`, `MIN_CONFIDENCE`, `RMS_GATE`, `MEDIAN_SIZE`) anpassen, bis alle vier Samples bestehen. Grenzen dabei:

- Die Änderung muss fachlich begründet sein (Messwert zeigt die Ursache). Schwellen nicht einfach so weit lockern, dass der Test nichts mehr prüft. Die Oktavfehler-Grenze (600 Cent) und die Prüfung von Notenklasse und Oktave bleiben unverändert.
- Nach jeder Konstanten-Änderung müssen alle Tests aus Phase 1 bis 3 weiter grün sein, insbesondere die synthetischen YIN-Tests.
- Jede Änderung wird mit Begründung und den Messwerten vorher/nachher im Abschlussbericht der Phase genannt.

**Ausgabe:** Der Test schreibt pro Datei eine Zeile mit den Messwerten ins Testprotokoll (`println`), damit die Ergebnisse auch bei grünem Test sichtbar sind.

**Fertig, wenn:** `SampleValidationTest` für alle vier Dateien grün ist. Erst danach beginnt Phase 4.

### Phase 4: Audio-Aufnahme und Engine

**`audio/AudioInput.kt`** kapselt `AudioRecord`:

1. Audioquelle wählen: Liefert `AudioManager.getProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED)` den Wert `"true"`, dann `MediaRecorder.AudioSource.UNPROCESSED`. Sonst `MediaRecorder.AudioSource.VOICE_RECOGNITION`. Niemals `MIC`, denn das aktiviert oft Rauschunterdrückung und Pegelautomatik.
2. Format: `SAMPLE_RATE`, `CHANNEL_IN_MONO`, `ENCODING_PCM_FLOAT`.
3. Puffergröße in Bytes: Maximum aus `getMinBufferSize(...) * 4` und `HOP_SIZE * 4 * 4`.
4. Nach dem Erzeugen `state == AudioRecord.STATE_INITIALIZED` prüfen. Sonst Fehler werfen.
5. Methoden: `start()`, `read(dest: FloatArray): Int` mit `AudioRecord.READ_BLOCKING`, `stop()` mit `stop()` und `release()`.
6. Klasse mit `@RequiresPermission(Manifest.permission.RECORD_AUDIO)` markieren.

**`engine/TunerEngine.kt`** stellt `fun frequencies(): Flow<Float?>` bereit. `null` heißt: kein verwertbarer Ton. Die gesamte Analyse steckt in `PitchPipeline` (Phase 3b), die Engine liest nur Blöcke und reicht sie weiter.

```kotlin
fun frequencies(): Flow<Float?> = flow {
    val hop = FloatArray(TunerConfig.HOP_SIZE)
    pipeline.reset()
    audioInput.start()
    try {
        while (currentCoroutineContext().isActive) {
            val n = audioInput.read(hop)
            if (n < 0) error("AudioRecord read failed: $n")
            // Returns null while the window is still filling, on silence, or on low confidence
            emit(pipeline.process(hop, n))
        }
    } finally {
        audioInput.stop()
    }
}.flowOn(Dispatchers.Default)
```

**Fertig, wenn:** Phase 3b grün ist und ein temporärer `Log.d`-Aufruf im ViewModel beim Spielen plausible Frequenzen zeigt (Stimmton C5 = ca. 233 Hz).

### Phase 5: Einstellungen speichern

**`data/SettingsRepository.kt`** mit DataStore Preferences, Dateiname `settings`.

- Schlüssel: `reference_hz` (Int, Standard 440) und `transposition` (String, Enum-Name, Standard `TENOR_BB`).
- `val settings: Flow<TunerSettings>`.
- `suspend fun setReferenceHz(hz: Int)` begrenzt den Wert mit `coerceIn(400, 480)`.
- `suspend fun setTransposition(t: Transposition)`.
- Unbekannte gespeicherte Enum-Werte fallen auf `TENOR_BB` zurück.

### Phase 6: ViewModel

**`ui/TunerViewModel.kt`** als `AndroidViewModel` (kein DI-Framework nötig).

1. Erzeugt `SettingsRepository`, `AudioInput`, `YinPitchDetector`, `PitchSmoother` und `TunerEngine`.
2. `val uiState: StateFlow<TunerUiState>`.
3. `fun start()` startet einen Job, der `engine.frequencies().sample(TunerConfig.UI_INTERVAL_MS)` mit `repository.settings` kombiniert (`combine`). Ein zweiter Aufruf von `start()` bei laufendem Job tut nichts.
4. `fun stop()` bricht den Job ab.
5. Halte-Logik: Bei einer Frequenz wird `NoteMapper.map(...)` aufgerufen, `isActive = true` gesetzt und die Zeit gemerkt. Bei `null` bleibt die letzte Note stehen mit `isActive = false`. Erst nach `HOLD_MS` ohne Ton wird `note = null`.
6. `fun setReferenceHz(hz: Int)` und `fun setTransposition(t)` rufen das Repository in `viewModelScope` auf.

### Phase 7: Oberfläche

**`ui/PermissionScreen.kt`:** Kurzer Text, warum das Mikrofon nötig ist, und ein Button. Anfrage über `rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission())`. Wurde dauerhaft abgelehnt, führt der Button zu den App-Einstellungen (`Settings.ACTION_APPLICATION_DETAILS_SETTINGS`).

**`MainActivity.kt`:** Erbt von `AppCompatActivity` (nicht `ComponentActivity`), damit die Sprachumschaltung auch unter Android 12 und älter funktioniert. Zeigt `PermissionScreen`, solange die Berechtigung fehlt, sonst `TunerScreen`. Beim Zurückkehren aus den Einstellungen den Status erneut prüfen.

**`ui/TunerScreen.kt`**, von oben nach unten:

1. Obere Leiste: Titel `app_title` und rechts ein Zahnrad, das das `SettingsSheet` öffnet.
2. Kleine Infozeile `info_line`, z. B. „A4 = 440 Hz · Tenorsax (B)“ oder „A4 = 440 Hz · Alto sax (E♭)“.
3. Notenname sehr groß (ca. 96 sp), die Oktave klein daneben. Der Name kommt aus `NoteNames.name(note.pitchClass, NoteNames.languageOf(LocalConfiguration.current.locales[0]))`.
4. `NeedleGauge` in voller Breite, Seitenverhältnis 2:1.
5. Prozentwert mit Vorzeichen, z. B. „+12 %“ oder „−8 %“, gerundet auf ganze Zahlen.
6. Frequenz klein mit einer Nachkommastelle, formatiert mit der aktuellen Locale: `String.format(locale, "%.1f Hz", hz)`. Ergibt „233,1 Hz“ auf Deutsch und „233.1 Hz“ auf Englisch.
7. Ohne Ton: Notenname „–“ und Hinweis `hint_play`.

Zusätzlich im `TunerScreen`:

- `LifecycleStartEffect` ruft beim Start `viewModel.start()` und beim Stopp `viewModel.stop()` auf.
- `LocalView.current.keepScreenOn = true` per `DisposableEffect`, damit der Bildschirm beim Stimmen nicht ausgeht.
- Zustand mit `collectAsStateWithLifecycle()` lesen.
- Barrierefreiheit: `semantics { contentDescription = stringResource(R.string.a11y_reading, name, octave, percentRounded) }`.

**`ui/NeedleGauge.kt`**, Signatur `@Composable fun NeedleGauge(percent: Float?, modifier: Modifier)`:

1. Zeichnen mit `Canvas`. Mittelpunkt bei (Breite / 2, Höhe × 0,9), Radius = 90 % von min(Breite / 2, Höhe × 0,9).
2. Skala als Bogen: `startAngle = 210f`, `sweepAngle = 120f`. In Compose zeigt 270° nach oben.
3. Winkel des Zeigers = `270f + percent * 1.2f`. Damit liegt −50 % bei 210° und +50 % bei 330°.
4. Skalenstriche alle 10 %, länger bei 0 und ±50 %. Beschriftung „−50“, „0“, „+50“ mit `rememberTextMeasurer`, außerdem too\_low links und too\_high rechts aus den String-Ressourcen.
5. Farbzonen auf dem Bogen: ±5 % grün, bis ±15 % gelb, außen rot (je halbtransparent).
6. Zeigerfarbe nach Betrag: bis 5 % grün, bis 15 % gelb, darüber rot. Bei `percent == null` grau, 40 % Deckkraft, Zeiger auf 0.
7. Bewegung: `animateFloatAsState(target, spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessLow))`.
8. Zeigerende: `center + radius * (cos(rad), sin(rad))`, mit `rad = Math.toRadians(winkel)`.

**`ui/SettingsSheet.kt`** als `ModalBottomSheet`:

- Kammerton: Zeile mit „−“, Wert „440 Hz“, „+“ (±1 Hz). Darunter `Slider` von 400 bis 480 mit `steps = 79`. Button `reset_reference`.
- Anzeige: `SingleChoiceSegmentedButtonRow` mit `mode_tenor`, `mode_alto` und `mode_concert`.
- Sprache: `SingleChoiceSegmentedButtonRow` mit „Deutsch“ und „English“. Sprachnamen stehen immer in ihrer eigenen Sprache und werden nicht übersetzt.
- Aktuelle Sprache: Ist `AppCompatDelegate.getApplicationLocales()` leer, gilt die Systemsprache über `NoteNames.languageOf(...)`. Sonst deren erster Eintrag.
- Sprachwechsel: `AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(language.tag))`. Android speichert die Wahl selbst und startet die Activity neu. Kammerton und Modus bleiben durch DataStore erhalten, die Aufnahme startet über `LifecycleStartEffect` wieder.
- Änderungen werden sofort gespeichert und wirken sofort.

**Texte** (Schlüssel in `strings.xml`; Englisch ist der Standard in `values/`, Deutsch in `values-de/`)

| Schlüssel | Englisch | Deutsch |
| --- | --- | --- |
| `app_title` | Sax Tuner | Sax-Stimmgerät |
| `settings` | Settings | Einstellungen |
| `info_line` | `A4 = %1$d Hz · %2$s` | `A4 = %1$d Hz · %2$s` |
| `reference_pitch` | Reference pitch | Kammerton |
| `reset_reference` | Reset to 440 Hz | Auf 440 Hz zurücksetzen |
| `display_mode` | Display | Anzeige |
| `mode_tenor` | Tenor sax (B♭) | Tenorsax (B) |
| `mode_alto` | Alto sax (E♭) | Altsax (Es) |
| `mode_concert` | Concert (C) | Klingend (C) |
| `language` | Language | Sprache |
| `hint_play` | Play a note | Spiel einen Ton |
| `too_low` | flat | zu tief |
| `too_high` | sharp | zu hoch |
| `a11y_reading` | `%1$s %2$d, %3$+d percent` | `%1$s %2$d, %3$+d Prozent` |
| `permission_title` | Microphone needed | Mikrofon benötigt |
| `permission_text` | The tuner listens to your instrument through the microphone. Nothing is recorded or sent. | Das Stimmgerät hört dein Instrument über das Mikrofon. Es wird nichts gespeichert oder gesendet. |
| `permission_button` | Allow microphone | Mikrofon erlauben |
| `open_settings` | Open settings | Einstellungen öffnen |

### Phase 8: Test auf dem Gerät

Die Abnahme erfolgt mit den Kriterien im nächsten Abschnitt. Gemessene Rechenzeit pro `detect()`-Aufruf einmal per `System.nanoTime()` loggen und danach wieder entfernen.

## Tests und Abnahme

Unit-Tests liegen unter `app/src/test/java/de/example/saxtuner/` und laufen ohne Gerät mit `./gradlew test`. Abweichungen werden in Cent gemessen: `1200 * log2(gemessen / erwartet)`.

**`NoteMapperTest`** (Toleranz für Prozent: ±0,1). Geprüft werden `pitchClass`, `octave` und `percent`. Die Namensspalte dient nur der Lesbarkeit.

| Frequenz | Kammerton | Modus | pitchClass | Oktave | Prozent | Name DE / EN |
| --- | --- | --- | --- | --- | --- | --- |
| 440,00 Hz | 440 | Klingend | 9 | 4 | 0 | A / A |
| 440,00 Hz | 440 | Tenorsax | 11 | 5 | 0 | H / B |
| 233,08 Hz | 440 | Klingend | 10 | 3 | 0 | B / B♭ |
| 233,08 Hz | 440 | Tenorsax | 0 | 5 | 0 | C / C |
| 103,83 Hz | 440 | Tenorsax | 10 | 3 | 0 | B / B♭ |
| 659,26 Hz | 440 | Tenorsax | 6 | 6 | 0 | Fis / F♯ |
| 138,59 Hz | 440 | Altsax | 10 | 3 | 0 | B / B♭ |
| 440,00 Hz | 440 | Altsax | 6 | 5 | 0 | Fis / F♯ |
| 880,00 Hz | 440 | Altsax | 6 | 6 | 0 | Fis / F♯ |
| 446,41 Hz | 440 | Klingend | 9 | 4 | +25 | A / A |
| 435,00 Hz | 440 | Klingend | 9 | 4 | −19,8 | A / A |
| 442,00 Hz | 442 | Klingend | 9 | 4 | 0 | A / A |
| 440,00 Hz | 442 | Klingend | 9 | 4 | −7,9 | A / A |

**`NoteNamesTest`**

| Aufruf | Erwartet |
| --- | --- |
| `name(0, DE)` und `name(0, EN)` | „C“ |
| `name(3, DE)` / `name(3, EN)` | „Es“ / „E♭“ |
| `name(10, DE)` / `name(10, EN)` | „B“ / „B♭“ |
| `name(11, DE)` / `name(11, EN)` | „H“ / „B“ |
| `name(12, DE)` | `IllegalArgumentException` |
| `languageOf(Locale.GERMANY)` und `languageOf(Locale("de", "AT"))` | `DE` |
| `languageOf(Locale.US)` und `languageOf(Locale.FRANCE)` | `EN` |

**`YinPitchDetectorTest`** (Detektor mit 44100 Hz, Fenster 4096, 90 bis 1100 Hz)

Hilfsfunktion im Test: `fun tone(f0: Double, harmonics: FloatArray, n: Int = 4096): FloatArray` erzeugt eine Summe von Sinustönen bei f0, 2·f0, 3·f0 … mit den angegebenen Amplituden.

| Testfall | Signal | Erwartet |
| --- | --- | --- |
| Reiner Sinus Mitte | 440 Hz, Amplitude 0,5 | Abweichung unter 1 Cent |
| Reiner Sinus tief | 103,83 Hz | Abweichung unter 1 Cent |
| Reiner Sinus hoch | 1000 Hz | Abweichung unter 1 Cent |
| Saxophon-ähnlich | 116,54 Hz, Teiltöne 0,4 / 1,0 / 0,7 / 0,5 / 0,3 | 116,54 Hz unter 2 Cent, **nicht** 233 Hz |
| Mit Rauschen | 440 Hz Sinus 0,5 plus Zufallsrauschen 0,1 (`Random(42)`) | Abweichung unter 3 Cent |
| Stille | 4096 Nullen | `null` |
| Nur Rauschen | Zufallswerte −0,5 bis 0,5 (`Random(42)`) | `null` oder Konfidenz unter 0,8 |
| Laufzeit | 1000 Aufrufe mit 440-Hz-Sinus | zusammen unter 5 Sekunden auf dem PC |

**`PitchSmootherTest`**

| Eingaben nacheinander | Erwartete Rückgabe des letzten `add` |
| --- | --- |
| 439, 441, 440, 442, 438 | 440 |
| 440 viermal, dann 880 | 440 (Ausreißer ignoriert) |
| 440 viermal, dann 330 dreimal | 330 (echter Tonwechsel) |
| 440 viermal, `reset()`, dann 300 | 300 |

**`SampleValidationTest`** siehe Phase 3b: Die vier Altsaxophon-Aufnahmen in `samples/alt saxophone/` laufen durch `PitchPipeline` und `NoteMapper`. Der Test läuft mit `./gradlew test` ohne Gerät.

**Abnahme auf dem Gerät**

Als Testquelle dient ein Tongenerator auf einem zweiten Gerät (beliebige App oder Webseite mit einstellbarer Frequenz) und das echte Saxophon.

- [ ] Generator 233,08 Hz, Modus Tenorsax: Anzeige „C5“, Zeiger bei 0 ±2 %.
- [ ] Generator 440 Hz, Modus Klingend: Anzeige „A4“, 0 ±2 %.
- [ ] Generator 446,4 Hz, Modus Klingend: „A4“, +25 ±2 %.
- [ ] Kammerton auf 442 Hz, Generator 442 Hz: 0 ±2 %.
- [ ] Tiefes B und hohes Fis auf dem Saxophon werden ohne Oktavsprünge erkannt.
- [ ] Bei gehaltenem Ton schwankt die Anzeige höchstens um ±2 %.
- [ ] Der Zeiger reagiert nach Tonbeginn in unter 150 ms spürbar.
- [ ] Nach Tonende bleibt die Note ca. 0,8 s grau stehen, dann erscheint „Spiel einen Ton“.
- [ ] Modus Altsax: Generator 138,59 Hz zeigt „B♭3“ (Englisch) bzw. „B3“ (Deutsch).
- [ ] Kammerton und Modus bleiben nach Neustart der App erhalten.
- [ ] Geht die App in den Hintergrund, verschwindet die Mikrofon-Anzeige von Android.
- [ ] Berechtigung verweigert: Erklärung erscheint, der Weg zu den Einstellungen funktioniert.
- [ ] Bildschirm bleibt während der Nutzung an.
- [ ] Sprache English: alle Texte englisch. Tiefster Tenorton zeigt „B♭3“, Generator 440 Hz im Modus Tenorsax zeigt „B5“, Frequenz mit Punkt.
- [ ] Sprache Deutsch: dieselben Töne zeigen „B3“ und „H5“, Frequenz mit Komma.
- [ ] Sprachwahl bleibt nach Neustart erhalten. Bei frischer Installation folgt die App der Systemsprache (Deutsch ergibt Deutsch, alles andere Englisch).

## Hinweise für den Agenten

Die häufigsten Fehler bei Stimmgeräten entstehen nicht im Algorithmus, sondern beim Audio-Setup und beim Threading.

**Typische Fehler vermeiden**

- Keine Allokationen in der Aufnahme-Schleife oder in `detect()`. Keine `List<Float>`, kein `toFloatArray()`, kein `copyOf()`.
- `AudioRecord.read` nie auf dem Main-Thread aufrufen. Die Engine läuft per `flowOn(Dispatchers.Default)`.
- `AudioRecord` immer im `finally` stoppen und freigeben, sonst bleibt das Mikrofon belegt.
- Die Transposition (+14 Tenor, +9 Alt) erst **nach** dem Runden addieren. Der Kammerton gilt immer für das klingende A4.
- Für negative Werte `Math.floorMod` und `Math.floorDiv` verwenden, nicht `%` und `/`.
- Nicht `MediaRecorder.AudioSource.MIC` verwenden.
- Die UI nicht mit jedem Messwert neu zeichnen. Der Wert wird per `sample()` gedrosselt, der Zeiger per Animation geglättet.
- Prozent-Text auf ganze Zahlen runden, sonst flackert er.
- Intern immer mit `pitchClass` rechnen, nie mit Notennamen. „B“ bedeutet auf Deutsch und Englisch verschiedene Töne.
- `MainActivity` muss von `AppCompatActivity` erben und das Theme von `Theme.AppCompat`, sonst stürzt die App beim Start ab oder die Sprachumschaltung wirkt nicht.

**Arbeitsweise**

1. Phasen strikt nacheinander. Erst zur nächsten Phase, wenn Build und Tests grün sind.
2. Bei fehlschlagendem YIN- oder Sample-Test zuerst die Indizes prüfen: Puffergröße, `tauMax + 1` und Interpolations-Nachbarn.
3. Keine zusätzlichen Bibliotheken einbauen, auch nicht „nur für Tests“. Bei Bedarf beim Nutzer nachfragen.
4. Keine Abweichung vom Plan ohne Rückfrage, außer bei offensichtlichen Tippfehlern im Code.
5. Code, Bezeichner und Kommentare auf Englisch. Sichtbare Texte nur über String-Ressourcen, immer in beiden Sprachen gleichzeitig pflegen.

**Bewusst nicht in Version 1**

Referenzton-Ausgabe, weitere Instrumente (Sopran- und Baritonsax lassen sich später über neue `Transposition`-Werte ergänzen), Strobe-Anzeige, Verlaufskurve und Querformat.
