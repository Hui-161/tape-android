# Tape – Analyse & Verbesserungsplan

**Datum:** 2026-09-26
**Stand des Codes:** Analyse auf `cf01a06` („Complete core feature implementation …“); Runde 1 umgesetzt in `2fe682f`…`87faacd`
**Status:** Vorschlag – alle Befunde und Maßnahmen sind fachlich zu prüfen, bevor sie umgesetzt oder produktiv eingesetzt werden.

---

## TL;DR

Die App kompiliert (Debug und Release/R8), Lint meldet keine Errors. Die **Kernfunktion ist aber sehr wahrscheinlich defekt**:
Taps auf das Kamerabild erreichen das ViewModel nicht, deshalb lassen sich keine Messpunkte setzen. Dazu kommen
mehrere Fehler im Onboarding/ARCore-Check, eine nicht „ehrliche“ Genauigkeitsanzeige (Kernversprechen der App),
nicht ladende Schriften, Material-Standardfarben (Violett) statt der Tape-Palette und eine
`INTERNET`-Berechtigung trotz „alles lokal“-Versprechen. Es gibt keine echten Tests und kein CI.

Empfohlene Reihenfolge: **Test-/CI-Fundament → Kernfunktion reparieren → Messqualität → UX → Design → Datenschutz → Wartung.**

**Update 2026-09-26:** Runde 1 ist umgesetzt, siehe Abschnitt 0.

---

## 0. Stand nach Runde 1 (2026-09-26)

**Kontext (Rückmeldung):** allgemeines Maßband über die Kamera, live · private Nutzung · Gerät: Nothing Phone (4a) Plus
mit Android 17. Damit entfallen Play-Store-Vorgaben, MDM und Firmenfreigaben; Priorität hat zuverlässiges Live-Messen auf
diesem Gerät. Der Fadenkreuz-Modus (vorher optional in Phase 2) ist deshalb jetzt die Kernbedienung.

**ARCore-Unterstützung des Geräts:** In der offiziellen Liste (developers.google.com/ar/devices, abgerufen am 2026-09-26)
stehen *Nothing Phone (4a)* und *(4a) Pro*, jeweils mit Depth-API; ein *(4a) Plus* ist nicht aufgeführt. Der erste
Gerätetest zeigt aber: ARCore läuft auf dem Gerät (Tracking und Live-Linie funktionieren).

**Umgesetzt in Runde 1:**

| Befund | Stand |
|---|---|
| B1 Taps | behoben – Punkte per Fadenkreuz und „+“-Taste statt Tippen aufs Kamerabild |
| B2 ARCore-Check | behoben – erneute Abfrage bei `UNKNOWN_CHECKING`; „unbekannt“ gilt nicht als „nicht unterstützt“ |
| B3 Anker/Lebenszyklus | behoben – Punkte an die Session gebunden; Drehung ohne Session-Neustart (`configChanges`) |
| B4 verspätete/stumme Taps | behoben – „+“ nur aktiv, wenn das Fadenkreuz auf einer Fläche liegt; Hinweise bei Tracking-Problemen |
| B6 Genauigkeit | teilweise – distanzabhängiges Modell (±cm, Stufe); **Kalibrierung auf dem Gerät offen** |
| B7 Wände/Depth/Hit-Test | behoben – horizontal und vertikal, Depth wenn verfügbar, `isPoseInPolygon` |
| B8 INTERNET | behoben – gemergtes Manifest fordert nur noch die Kamera an |
| B9 Speichern | teilweise – Rückmeldung, keine Duplikate; Label-Eingabe offen |
| B10 Einheiten | behoben – der Chip speichert die Einheit app-weit |
| B11 Onboarding | behoben – Start direkt im Messscreen, Back-Stack, Re-Check nach den Einstellungen |
| B12 Farbrollen | behoben – abgesichert durch `ThemeColorRolesTest` |
| B14 Tests | teilweise – 47 JVM-Unit-Tests; CI offen |
| B18 Rekomposition | behoben – Zeichenzustand getrennt vom Screen-Zustand |
| B19/B20 Details | teilweise – Label verdeckt das Fadenkreuz nicht, Display bleibt an, Haptik, dritter Punkt startet neu statt zu verwerfen |

**Erster Gerätetest (2026-09-26) und Korrekturen:**

| Befund | Beobachtung | Korrektur |
|---|---|---|
| B22 Reichweite | Tür aus ca. 15 m gemessen: 14,84 m ±1,43 m. Die Punkte liegen auf dem Bildschirm richtig, aber in falscher Tiefe; der Abstand besteht fast nur aus Tiefenfehler. | `TargetSelector`: keine Punkte über 5 m (rotes Fadenkreuz, Hinweis „zu weit entfernt“); Ebenen bis 10 cm hinter einem Depth-Treffer werden bevorzugt (`8cb6f99`) |
| B23 Label | Bei senkrechten Linien lag das Label auf der Linie und verdeckte den unteren Endpunkt. | Label neben der Linie entlang ihrer Normalen (Bemaßungsstil); verdeckt nie Fadenkreuz oder Endpunkte (`b0a81ef`) |
| B24 Kontrast | Liste/Einstellungen-Symbole auf heller Fassade kaum sichtbar. | Dunkle Hinterlegung wie Badge/Einheiten-Chip, Verlauf hinter Statusleiste und HUD (`b0a81ef`) |

**Zweiter Gerätetest (Bildschirmvideo, 2026-09-26):** Türhöhe 1,90 m (±7,5 cm, aus ca. 3,5 m) bzw. 1,87 m (±12,1 cm, aus
ca. 5 m), Heizkörper 0,75 m (±3,5 cm); die Ergebnisse bleiben nach dem Setzen stabil. Referenzmaße stehen noch aus.

| Befund | Beobachtung | Korrektur |
|---|---|---|
| B25 Label-Bereich | Startpunkt unter dem Bildrand: Label über Hinweis und Speichern-Button (t = 7 s); Messung hinter dem Nutzer: Label über der Statusleiste (t = 18 s). | Label nur im freien Bereich zwischen HUD und Bedienleiste, am sichtbaren Teil der Linie (Liang-Barsky-Clipping); kein Label, wenn die Linie nicht sichtbar ist (`19c119f`) |
| B26 Genauigkeits-Hinweise | Punkte aus 3,5–5 m gesetzt → meist „Geringe Genauigkeit“, ohne Hinweis, wie es besser geht. | Ziel weiter als 3 m: „Mit + setzen – aus der Nähe wird es genauer“; ungenaues Ergebnis: „Ungenau – aus der Nähe neu messen“ (`084ee40`) |

**Neuer Befund B21 – 16-KB-Speicherseiten:** `libfilament-jni.so`, `libfilament-utils-jni.so` und `libgltfio-jni.so` aus
SceneView 2.2.1 (Filament 1.52.0) sind nur 4-KB-aligned; die ARCore- und AndroidX-Bibliotheken sind bereits 16-KB-aligned.
Auf Geräten mit 16-KB-Kernel laden solche Bibliotheken nicht bzw. nur im Kompatibilitätsmodus. SceneView 2.3.3 bringt
Filament 1.68.2 (16-KB-aligned, geprüft) und ARCore 1.52, setzt aber Kotlin 2.2, compileSdk 36, AndroidX core 1.17 und
Compose 1.10 voraus, also ein Toolchain-Update. Die `ARScene`-API ist zwischen 2.2.1 und 2.3.3 bis auf einen Import identisch.

**Runde 2 (Vorschlag):**
1. Toolchain-Update inkl. SceneView 2.3.3 (B15, B21), compileSdk/targetSdk 36.
2. IBM Plex lokal einbinden (B5), `statusBarColor` durch Edge-to-Edge-Konfiguration ersetzen.
3. Alle Screens auf Deutsch (B16), Fuß/Zoll-Darstellung für imperiale Einheiten.
4. Speichern mit Label; Saved-Liste: Tippen öffnet Aktionen, Löschen mit Undo, Genauigkeit anzeigen; Room-Schema exportieren und migrieren (B13).
5. Genauigkeitsmodell mit Referenzmessungen auf dem Nothing Phone kalibrieren (B6).
6. CI mit GitHub Actions (B14).

**Gerätetest-Checkliste (Runde 1):**
1. Erststart: Welcome → Kamera erlauben → Messscreen; ggf. Aufforderung, „Google Play-Dienste für AR“ zu installieren.
2. Zweiter Start: Die App öffnet direkt den Messscreen.
3. Handy langsam über Boden oder Tisch bewegen: Der Hinweis wechselt, das Fadenkreuz wird amber.
4. „+“ setzt Punkt A (Vibration); eine gestrichelte Linie folgt live dem Fadenkreuz, mit Abstand und ±-Wert.
5. „+“ setzt Punkt B, das Ergebnis steht fest; Speichern zeigt „Messung gespeichert“, der Eintrag erscheint in der Liste.
6. Eine Wand oder einen Türrahmen anvisieren: Das Fadenkreuz wird auch auf senkrechten Flächen amber.
7. Referenzmessung aus 0,5–3 m Abstand: eine bekannte Länge (z. B. 1 m Zollstock) messen, Anzeige und ±-Wert notieren.
   Aus mehr als 5 m Entfernung lassen sich keine Punkte setzen (Hinweis „zu weit entfernt“).
8. Handy drehen: Die Messung bleibt erhalten. Zu „Gespeichert“ und zurück: kein Absturz, die Messung beginnt neu.
9. Einheiten-Chip antippen: Die Einheit wechselt, auch in der Liste.

---

## 1. Was die App heute kann (Ist-Analyse)

| Bereich | Umgesetzt | Einschränkungen (Details in Abschnitt 3) |
|---|---|---|
| Onboarding | Welcome-Screen, Kamera-Berechtigung inkl. „Einstellungen öffnen“ nach Ablehnung, ARCore-Verfügbarkeitscheck, „Unsupported“-Screen | Läuft bei **jedem Kaltstart**; ARCore-Check fehlerhaft (B2); kein Re-Check nach Rückkehr aus den Einstellungen |
| AR-Messung | `ARScene` (SceneView 2.2.1) mit Kamerabild und Ebenen-Visualisierung; 2-Punkt-Zustandsmaschine `IDLE → POINT_A → BOTH`; Projektion der Anker auf den Bildschirm; Linie und Distanz-Label, live aktualisiert | **Taps kommen nicht an (B1)**; nur horizontale Ebenen (B7); keine Depth-API |
| Genauigkeitsanzeige | Badge aus dem ARCore-Tracking-Status („Good tracking ±1 cm“ / „Low accuracy ±3 cm“ / „No tracking“) | Feste Werte, unabhängig von Distanz und Gerät (B6) |
| Einheiten | cm / m / in / ft; Tap auf den Chip wechselt; Standard-Einheit in den Settings (DataStore), Default nach Locale | Chip-Wechsel wird nicht gespeichert, Saved-Liste zeigt andere Einheit (B10); Fuß nur dezimal |
| Speichern | Room-DB (Distanz, Einheit, Konfidenz, Zeitstempel) | Kein Label, kein Feedback, Doppelspeichern möglich (B9) |
| Gespeicherte Messungen | Liste (neueste zuerst), relative Zeit, Umbenennen, Teilen als Text, Löschen, Leerzustand | Aktionen nur per Long-Press, Löschen ohne Undo, Konfidenz nicht sichtbar |
| Einstellungen | Standard-Einheit, Theme (System/Dunkel/Hell), Versionsanzeige | Auswahl-Chips in Material-Violett (B12) |
| Datenschutz | Kein Konto, keine Analytics, Backup/Datentransfer komplett deaktiviert | `INTERNET`-Berechtigung über SceneView (B8) |
| Technik | Kotlin 2.1, Compose (BOM 2024.12), Hilt, Room 2.7, DataStore, typsichere Navigation, R8 im Release | 1 Dummy-Test, kein CI, veraltete Abhängigkeiten |

**Soll/Ist gegenüber README/`execution.md`:** Nicht umgesetzt sind u. a. Wand-Erkennung, Teilen als Bild, Undo,
Label beim Speichern, Speicher-Toast, eine „aus Tracking-Qualität + Distanz“ abgeleitete Konfidenz,
Tablet-Zweispalten-Layout, der „locked card“-Hinweis auf v1.1 im Unsupported-Screen und die Links auf LICENSE und
Quellcode im About-Bereich. Die README nennt veraltete Versionen (Kotlin 2.0.21, Compose 2024.09.03) und verweist auf eine
`LICENSE`-Datei, die im Repository fehlt.

---

## 2. Test-Setup & Ergebnisse (2026-09-26)

**Umgebung:** Android SDK unter `/opt/android-sdk` (cmdline-tools `16111833` mit neuem `android`-CLI,
`platforms;android-35`, `build-tools;34.0.0/35.0.0`, `platform-tools` 37.0.1), JDK 21, Gradle 8.10.2 (Wrapper).
Der Container hat **kein KVM**, deshalb ist kein Emulator und damit kein ARCore-Test möglich. Alles, was AR betrifft, muss
auf einem echten ARCore-Gerät verifiziert werden.

| Prüfung | Ergebnis |
|---|---|
| `./gradlew assembleDebug` | ✔ erfolgreich, 4 Compiler-Warnungen (Deprecations), APK 50 MB |
| `./gradlew assembleRelease` (R8) | ✔ erfolgreich, unsigniertes Universal-APK **32 MB** (4 ABIs, Filament ≈ 6–7 MB je ABI) |
| `./gradlew testDebugUnitTest` | ✔ 1/1 – einziger Test ist `2 + 2 == 4` |
| `./gradlew lintDebug` | 0 Errors, 77 Hinweise (75 Warnungen, 2 Infos): 42× veraltete Abhängigkeiten/AGP, 22× „Typos“ im (kaputten) Zertifikats-Base64, 11× ungenutzte Ressourcen, 2× Autoboxing-State |
| Gemergtes Manifest | enthält `android.permission.INTERNET` (Quelle: `io.github.sceneview:sceneview:2.2.1`) |
| Robolectric-Verifikation (temporär, nicht eingecheckt) | Tap-Bug reproduziert inkl. Gegenprobe und Lösungsnachweis; wirksame Farbrollen ausgelesen; Screenshots von 7 Screens |

**Hinweis Build:** Maven Central antwortete wiederholt mit HTTP 429 (Rate-Limit). Mit `--max-workers=2` und
Wiederholung lief der Build stabil. Für CI sollte ein Gradle-Cache genutzt werden.

---

## 3. Befunde (priorisiert)

Legende Schwere: **P0** = Kernfunktion/Absturz · **P1** = wichtig (Ehrlichkeit, UX, Datenschutz, Datenverlust) · **P2** = Qualität/Wartung

### P0 – Kernfunktion

**B1 – Taps erreichen das ViewModel nicht, es lassen sich keine Punkte setzen.**
`MeasureScreen.kt:111-113` hängt `detectTapGestures` an den umschließenden `Box`. Die Kamera ist ein `ARScene`
(Compose-`AndroidView` mit `SceneView`). Nachweis aus den Quellen der verwendeten Versionen:
1. `SceneView.onTouchEvent()` (SceneView 2.2.1, `SceneView.kt:714-731`) gibt für die nicht klickbare SurfaceView **immer `true`** zurück.
2. `PointerInteropFilter` (compose-ui 1.7.6) reicht DOWN/UP bereits im *Initial*-Pass an die View weiter und **markiert die Änderung als consumed**, wenn die View das Event annimmt.
3. `detectTapGestures` → `awaitFirstDown(requireUnconsumed = true)` ignoriert bereits verbrauchte DOWN-Events.

Robolectric-Test mit exakt diesem Aufbau und demselben `onTouchEvent`-Vertrag: 0 Taps im Eltern-`Box`.
Die Gegenprobe mit einer passiven View liefert 1 Tap, ein Tap über den View-eigenen Gesture-Listener ebenfalls 1.
**Fix:** `ARScene(onGestureListener = rememberOnGestureListener(onSingleTapConfirmed = { e, _ -> viewModel.enqueueTap(e.x, e.y) }))`
und `pointerInput` am `Box` entfernen.

**B2 – ARCore-Check schickt unterstützte Geräte auf „Unsupported“.**
`PermissionScreen.kt:67-72` prüft einmalig `!availability.isSupported`. ARCore liefert beim ersten Aufruf,
typischerweise wenn „Google Play Services for AR“ noch nicht installiert ist, `UNKNOWN_CHECKING`. Laut Google-Doku
soll dann nach ca. 200 ms erneut abgefragt werden. `UNKNOWN_CHECKING`, `UNKNOWN_TIMED_OUT` und `UNKNOWN_ERROR`
ergeben `isSupported == false` (per Test bestätigt). Unterstützte Geräte landen dadurch beim Erststart auf „This device doesn't support AR“.
Zudem erledigt SceneView Berechtigung und `ArCoreApk.requestInstall()` bereits selbst (`ARCore.checkPermissionAndInstall`).
**Fix:** Bei `isTransient` erneut abfragen; „unbekannt“ nicht als „nicht unterstützt“ behandeln (Retry-UI); Installation SceneView überlassen.

**B3 – Veraltete Anker nach Navigation/Rotation (Folgefehler von B1).**
`ARScene` zerstört beim Verlassen des Screens die `ARSceneView` und schließt damit die Session (`onRelease = { destroy() }`).
Das `MeasureViewModel` bleibt auf dem Back-Stack und behält `phase` und `anchorA/B` (`MeasureViewModel.kt:64-65`).
Nach der Rückkehr (oder einer Rotation, da keine `configChanges` gesetzt sind) greift `onFrame` auf Anker einer
geschlossenen Session zu. Das kann Ausnahmen oder Abstürze auslösen; mindestens steht die UI auf „BOTH“ ohne sichtbare Punkte.
Auch `onCleared()` (`:209-213`) detacht nach dem Schließen der Session. **Fix:** Messung in `onSessionCreated` zurücksetzen,
Anker-Zugriffe absichern, ViewModel-Zustand an den Session-Lebenszyklus koppeln.

**B4 – Verspätete und stumme Taps.**
Wenn nicht getrackt wird, kehrt `onFrame` vor dem Verarbeiten zurück (`MeasureViewModel.kt:102-105`). Der Tap bleibt
gespeichert und wird Sekunden später an einer inzwischen anderen Stelle angewendet. Trifft ein Tap keine Ebene
(`:113-115`), passiert nichts: kein Hinweis, kein Haptik-Feedback. Bei verlorenem Tracking bleiben die Punkte an
veralteten Bildschirmpositionen stehen.

**B5 – Schriften laden nie (Design-Typografie fehlt komplett).**
`res/values/font_certs.xml`: Beide Zertifikate sind ungültig. Die Base64-Länge ist kein Vielfaches von 4, die
DER-Daten sind abgeschnitten (888 von 1196 bzw. 657 von 1095 Bytes), `openssl x509` kann sie nicht lesen.
Der Google-Fonts-Provider kann so nicht verifiziert werden; die App fällt stillschweigend auf die Systemschrift zurück
(kein IBM Plex, keine Mono-Ziffern für Messwerte). **Fix:** IBM Plex Sans/Mono (SIL OFL) lokal unter `res/font`
einbinden und die GMS-Font-Abhängigkeit entfernen. Das funktioniert auch offline und ohne Google-Dienste.

### P1 – Ehrlichkeit, Messqualität, UX, Datenschutz

**B6 – Genauigkeitsanzeige ist nicht „ehrlich“.**
`MeasureViewModel.kt:249-253` und `MeasureUiState.kt:39-43`: Die Konfidenz hängt nur vom Tracking-Status ab.
Angezeigt werden fest „±1 cm“ bzw. „±3 cm“, egal ob 30 cm oder 5 m entfernt und egal welches Gerät. Das
widerspricht Designprinzip Nr. 3 und `execution.md` („derived from tracking quality + distance“).

**B7 – Wände werden nicht erkannt, Depth-API ungenutzt, Hit-Test zu großzügig.**
Die App übergibt keine `sessionConfiguration`, SceneView setzt keinen `planeFindingMode`. Damit gilt der ARCore-Default
`HORIZONTAL`, obwohl die README „horizontal and vertical surfaces“ verspricht. `DepthMode` bleibt `DISABLED`.
Der Hit-Test prüft nicht `Plane.isPoseInPolygon()`, akzeptiert also Treffer auf der gedachten Verlängerung einer Ebene
außerhalb der erkannten Fläche.

**B8 – `INTERNET`-Berechtigung trotz „nichts wird hochgeladen“.**
Wird von `sceneview:2.2.1` in das gemergte Manifest eingeschleust. Tape braucht sie nicht, weil keine Modelle per URL geladen werden.
**Fix:** `<uses-permission android:name="android.permission.INTERNET" tools:node="remove" />`, plus eine CI-Prüfung des gemergten Manifests.

**B9 – Speichern ohne Rückmeldung.**
`MeasureViewModel.kt:186-207`: `isSaving` wird nie `true`, `justSaved` wird von der UI nicht ausgewertet, und das
Label kann nicht eingegeben werden. Mehrfaches Tippen erzeugt Duplikate. Die Konfidenz wird als Magic Number (0.9/0.5/0.1) gespeichert.

**B10 – Einheiten nicht global.**
`cycleUnit()` (`MeasureViewModel.kt:166-169`) ändert nur den lokalen Zustand. Saved-Liste und Settings zeigen eine andere
Einheit (`execution.md`: „viewfinder + saved list agree“). Die Startwerte sind uneinheitlich (CM/M/M), was kurzes Flackern verursacht.

**B11 – Onboarding-/Navigationsfluss.**
`TapeNavGraph.kt:34` startet immer mit Welcome. Bei erteilter Berechtigung ist trotzdem ein Tap auf „Allow camera access“
nötig, also zwei Taps bis zum Messen bei jedem Kaltstart. „Zurück“ aus dem Messscreen führt zu Welcome, weil nur
Permission gepoppt wird (`:47-51`). Nach „Einstellungen öffnen“ wird die Berechtigung bei Rückkehr nicht erneut geprüft,
der Nutzer hängt bis zum Neustart. Fehlermeldungen unterscheiden nicht zwischen „Gerät nicht unterstützt“,
„Kamera belegt“ und „ARCore veraltet“.

**B12 – Unvollständiges Farbschema (Material-Violett sichtbar).**
`Theme.kt:23-68` definiert u. a. `secondaryContainer`, `surfaceContainer*` und `tertiaryContainer` nicht. Per Robolectric
ausgelesen (Dunkel): `secondaryContainer=#4A4458`, `onSecondaryContainer=#E8DEF8`, `surfaceContainerLow=#1D1B20`,
`surfaceContainerHigh=#2B2930`. Das sind Material-Baseline-Farben, keine aus `Color.kt`. Sichtbar ist das an den
Einstellungs-Chips, den Wert-Chips der Saved-Liste, am Bottom-Sheet und an Dialogen. Im Light-Theme erscheinen
entsprechend Lavendeltöne. `window.statusBarColor` ist deprecated und bei targetSdk 35 auf Android 15+ wirkungslos; auf älteren Versionen überschreibt es die transparenten Edge-to-Edge-Leisten mit Vollfarbe (`:80-88`).

**B13 – Datenverlust bei Schema-Änderung.**
`TapeDatabase.kt:9` setzt `exportSchema = false`, `DatabaseModule.kt:22` nutzt `fallbackToDestructiveMigration()`
(deprecated). Die nächste DB-Änderung löscht alle gespeicherten Messungen ohne Rückfrage.

### P2 – Qualität & Wartung

- **B14 Tests/CI:** Keine echten Tests, kein CI. Die Projektionsmathematik nutzt `android.opengl.Matrix` im ViewModel und ist deshalb auf der JVM nicht ohne Robolectric testbar.
- **B15 Abhängigkeiten:** AGP 8.7.3 → 9.4.1, Compose-BOM 2024.12.01 → 2026.09.00, Room 2.7.0 → 2.8.5, Navigation 2.8.5 → 2.10.2 u. a. (Lint). ARCore kommt nur transitiv mit (1.43.0) und sollte explizit deklariert werden. **targetSdk 35:** Nach Googles jährlichem Rhythmus verlangt Play seit 31.08.2026 API 36 für neue Apps und Updates (bitte verifizieren; relevant nur bei Play-Store-Vertrieb).
- **B16 Lokalisierung:** Alle UI-Texte sind hart kodiert (nur Englisch), `strings.xml` enthält nur `app_name`. Eine deutsche Übersetzung ist ohne Umbau nicht möglich.
- **B17 APK-Größe:** Universal-Release 32 MB. Mit AAB bzw. ABI-Splits lädt jedes Gerät nur seine ABI. x86/x86_64 sind im Release unnötig. Filament (3D-Engine) wird nur für Kamerahintergrund und Ebenen genutzt; ein schlanker eigener Renderer (ARCore-Samples) wäre eine größere, aber mögliche Option.
- **B18 Performance:** `MeasureScreen` wird pro Kamerabild neu komponiert (Punkte liegen im selben `StateFlow` wie der restliche UI-Zustand). Besser: Overlay-Zustand trennen und erst in der Draw-Phase lesen.
- **B19 Doku/Kommentare veraltet oder falsch:** Das Threading-KDoc behauptet GL-Render-Thread; SceneView ruft `onSessionUpdated` aber über den `Choreographer` auf dem Main-Thread auf. Dazu „Stub for now“-Kommentare, die leere Methode `MeasurementRepository.rename()`, der lokale Pfad des Originalautors in `Color.kt:8`, die HTML-Prototypen doppelt (Repo-Root und `docs/`, ≈ 1 MB) und die fehlende `LICENSE`.
- **B20 Details:** Das Distanz-Label ist nicht zentriert (`MeasureScreen.kt:141-153`, linke obere Ecke auf dem Mittelpunkt); die Default-Einheit wird über das Sprach-Tag statt über die Region/ICU-Maßsystem ermittelt; Fuß-Werte erscheinen dezimal („2.76 ft“ statt 2′ 9⅛″); Aktionen in der Saved-Liste sind nur per Long-Press erreichbar; ein Hinweistext hat nur ≈ 10 sp; der Bildschirm bleibt beim Messen nicht an; es gibt kein Haptik-Feedback; die Messung wird durch einen versehentlichen dritten Tap verworfen.

---

## 4. Umsetzungsplan

Branch-Namen sind themenbezogen vorgeschlagen. Jede Phase endet mit grünem CI; AR-relevante Punkte zusätzlich mit einem Test auf einem echten ARCore-Gerät.

### Phase 0 – Fundament: Tests & CI
Branches: `test/robolectric-infrastruktur`, `ci/github-actions`
- Robolectric + Compose-UI-Test einrichten; die Verifikationstests aus dieser Analyse als Regressionstests übernehmen (B1, B2).
- Unit-Tests: `UnitSystem` (Umrechnung/Formatierung), Zustandsmaschine `MeasureViewModel` (Projektionsmathe in reines Kotlin auslagern), `SavedViewModel`, `SettingsViewModel`, Default-Einheit.
- Screenshot-Tests (z. B. Roborazzi, nach dem Abhängigkeits-Update) für alle Nicht-AR-Screens in Hell und Dunkel.
- GitHub Actions: `assembleDebug`, `testDebugUnitTest`, `lintDebug`, Prüfung des gemergten Manifests auf unerwünschte Berechtigungen, APK als Artefakt.
- **Abnahme:** CI grün; Tests decken Umrechnung, Zustandsübergänge und Tap-Weiterleitung ab.

### Phase 1 – Kernfunktion reparieren (P0)
Branches: `fix/ar-tap-erkennung`, `fix/arcore-verfuegbarkeit`, `fix/ar-session-lebenszyklus`
1. Taps über `ARScene(onGestureListener = …)` statt über den `Box` (B1).
2. ARCore-Check: bei `isTransient` erneut abfragen, „unbekannt“ mit Retry, Installation SceneView überlassen, differenzierte Fehlermeldungen (B2, B11).
3. Session-Lebenszyklus: Reset in `onSessionCreated`, abgesicherte Anker-Zugriffe, `createAnchor()`-Ausnahmen abfangen (B3).
4. Taps ohne Tracking verwerfen, Hinweis und Haptik bei Fehltreffern, Punkte bei Tracking-Verlust ausblenden (B4).
5. Threading-Doku korrigieren und vereinfachen (B19).
- **Abnahme (Gerät):** Zwei Punkte setzen → Distanz erscheint; Measure → Saved → zurück ohne Absturz; Erststart ohne installiertes ARCore führt zur Installationsaufforderung statt zu „Unsupported“.

### Phase 2 – Messqualität & ehrliche Genauigkeit
Branches: `feat/ar-konfiguration`, `feat/ehrliche-genauigkeit`
1. Session-Konfiguration: `HORIZONTAL_AND_VERTICAL`, `DepthMode.AUTOMATIC` falls unterstützt, Light-Estimation deaktivieren (ohne 3D-Modelle nicht benötigt), Autofokus beibehalten (B7).
2. Hit-Test: `isPoseInPolygon`, Ebenen bevorzugen, bei Depth-Unterstützung `DepthPoint` als Fallback (B7).
3. Konfidenzmodell aus Tracking-Status und `trackingFailureReason`, Distanz Kamera→Punkt, Depth-Verfügbarkeit und Ebenentyp/-größe. Ausgabe als Stufe plus geschätzte Fehlerspanne (±cm und ±%), mit Klartext-Grund („zu dunkel“, „zu schnelle Bewegung“). Empirisch kalibrieren mit Referenzlängen (z. B. 30 cm / 1 m / 2 m) auf 2–3 Zielgeräten (B6).
4. Konfidenz typisiert speichern und in der Saved-Liste anzeigen.
5. Optional: Fadenkreuz-Modus mit Live-Vorschaulinie (wie iOS Measure). Das ist präziser als Fingertipps, weil der Finger das Ziel nicht verdeckt.
- **Abnahme (Gerät):** Die angezeigte Fehlerspanne enthält den wahren Wert in ≥ 90 % der Referenzmessungen.

### Phase 3 – UX-Flüsse
Branches: `fix/onboarding-fluss`, `feat/speichern-feedback`, `fix/einheiten-global`
1. Onboarding nur einmal (DataStore-Flag); Permission-Screen überspringen, wenn die Berechtigung erteilt ist; „Zurück“ aus Measure beendet die App; Re-Check bei `ON_RESUME` (B11).
2. Speichern: optionaler Label-Dialog, Snackbar „Gespeichert“, `isSaving`, Save nach dem Speichern deaktivieren; „Letzten Punkt entfernen“; Teilen direkt aus dem Messscreen (B9).
3. Einheiten-Chip global speichern (B10); Fuß/Zoll-Darstellung mit Brüchen für imperiale Nutzer.
4. Saved-Liste: Tap öffnet Aktionen, Löschen mit Undo-Snackbar, Konfidenz anzeigen.
5. Distanz-Label zentrieren, Bildschirm beim Messen anlassen, Haptik (B20).

### Phase 4 – Design-Treue & Barrierefreiheit
Branches: `fix/schriften-lokal`, `fix/farbrollen-theme`
1. IBM Plex lokal einbinden, `ui-text-google-fonts` und `font_certs.xml` entfernen (B5); tabellarische Ziffern (`fontFeatureSettings = "tnum"`).
2. Farbschema vervollständigen (alle `*Container`-, `surfaceContainer*`-, `inverse*`-Rollen, Light-`error`/`outlineVariant`); `enableEdgeToEdge(SystemBarStyle …)` statt `statusBarColor` (B12). Laut Repo-Regel `.vscode/themes/tape-warm-amber.json` im selben Commit mitziehen.
3. Barrierefreiheit: Mindestschriftgröße 12 sp, Kontraste, `onLongClickLabel`, TalkBack-Ansage der Distanz.
- **Abnahme:** Screenshot-Tests ohne Material-Violett; Schriften auf dem Gerät sichtbar IBM Plex.

### Phase 5 – Datenschutz, Daten & Robustheit
Branches: `fix/keine-internet-berechtigung`, `fix/room-migrationen`
1. `INTERNET` entfernen und in CI absichern (B8).
2. Room: `exportSchema = true` mit Schema-Verzeichnis (Room-Gradle-Plugin), Migrationstests, destruktiven Fallback entfernen (B13).
3. Datensicherung entscheiden: lokalen Gerätewechsel (`device-transfer`) erlauben, Cloud-Backup weiter aus – oder Export/Import (CSV/JSON).
4. ARCore-Datenschutzhinweis (Google-Vorgabe für ARCore-Apps; Wortlaut in der ARCore-Doku prüfen) in About/Onboarding ergänzen; Formulierung „nothing is uploaded“ mit Datenschutz prüfen.

### Phase 6 – Wartung & Plattform
Branches: `chore/abhaengigkeiten-update`, `chore/target-sdk-36`, `feat/lokalisierung-de`, `chore/apk-groesse`, `docs/readme-fork`
1. Abhängigkeiten schrittweise aktualisieren (AGP 9 bringt einen Gradle-9-Umstieg mit), jeweils mit CI (B15).
2. `compileSdk`/`targetSdk` 36; Edge-to-Edge, Predictive Back und große Bildschirme testen.
3. Texte in Ressourcen auslagern, deutsche Übersetzung (`values-de`); Default-Einheit per `android.icu.util.LocaleData.getMeasurementSystem` (ab API 24 verfügbar) (B16, B20).
4. APK-Größe: AAB bzw. ABI-Splits, x86 aus dem Release, `material-icons-extended` durch Einzel-Icons ersetzen; einen schlankeren Renderer als Option bewerten (B17).
5. `com.google.ar:core` explizit deklarieren, SceneView-Update prüfen.
6. Doku: README für den Fork (Versionen, Repo-URL, Build-Schritte), `LICENSE` ergänzen (MIT-Text mit Original-Copyright; mit IT/Recht klären), lokale Pfade und doppelte HTML-Dateien entfernen (B19).
7. Performance: Overlay-Zustand trennen (B18).

### Fork-spezifisch (geklärt am 2026-09-26)
- **Einsatzzweck:** allgemeines Maßband über die Kamera, live – das Prinzip „One job“ bleibt.
- **Zielgerät:** Nothing Phone (4a) Plus mit Android 17; ARCore-Unterstützung siehe Abschnitt 0.
- **Vertrieb:** private Nutzung per APK; Play-Store-Vorgaben und MDM entfallen.

---

## 5. Übersicht: Aufwand & Wirkung

| ID | Befund | Schwere | Aufwand | Phase |
|---|---|---|---|---|
| B1 | Taps kommen nicht an | P0 | klein | 1 |
| B2 | ARCore-Check (`UNKNOWN_CHECKING`) | P0 | klein | 1 |
| B3 | Veraltete Anker nach Navigation/Rotation | P0 | klein–mittel | 1 |
| B4 | Verspätete/stumme Taps | P0 | klein | 1 |
| B5 | Schriften laden nie | P0 (Design) | klein | 4 |
| B6 | Konfidenz nicht ehrlich | P1 | mittel | 2 |
| B7 | Keine Wände/Depth, großzügiger Hit-Test | P1 | klein | 2 |
| B8 | `INTERNET`-Berechtigung | P1 | sehr klein | 5 |
| B9 | Speichern ohne Feedback/Duplikate | P1 | klein | 3 |
| B10 | Einheiten nicht global | P1 | sehr klein | 3 |
| B11 | Onboarding bei jedem Start, Back-Stack | P1 | klein–mittel | 3 |
| B12 | Farbrollen/Material-Violett | P1 | klein | 4 |
| B13 | Datenverlust bei Schema-Änderung | P1 | klein | 5 |
| B14 | Keine Tests/CI | P2 | mittel | 0 |
| B15 | Veraltete Abhängigkeiten/targetSdk | P2 | mittel–groß | 6 |
| B16 | Keine Lokalisierung | P2 | mittel | 6 |
| B17 | APK-Größe | P2 | klein (Splits) / groß (Renderer) | 6 |
| B18 | Rekomposition pro Frame | P2 | klein–mittel | 6 |
| B19 | Veraltete Doku/Kommentare, fehlende LICENSE | P2 | klein | 1/6 |
| B20 | UX-Details | P2 | klein | 3 |

---

## Anhang A – Verifikationstest zu B1 (gekürzt)

```kotlin
// Gleicher onTouchEvent-Vertrag wie io.github.sceneview.SceneView (2.2.1)
private class SceneViewLikeView(context: Context) : View(context) {
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!super.onTouchEvent(event)) return true
        return false
    }
}

@Test
fun parentDetectTapGestures_neverFires_whenChildBehavesLikeSceneView() {
    var taps = 0
    rule.setContent {
        Box(Modifier.fillMaxSize().testTag("root")
            .pointerInput(Unit) { detectTapGestures { taps++ } }) {   // wie MeasureScreen.kt:111-113
            AndroidView(factory = ::SceneViewLikeView, modifier = Modifier.fillMaxSize())
        }
    }
    rule.onNodeWithTag("root").performTouchInput { click(center) }
    rule.waitForIdle()
    assertEquals(0, taps)   // bestanden → Tap erreicht den Box nicht
}
// Gegenprobe mit passiver View (onTouchEvent = false): taps == 1 (bestanden)
// Lösungsnachweis über View-eigenen GestureDetector: taps == 1 (bestanden)
```

## Anhang B – Reproduktion der Prüfungen

```bash
export ANDROID_HOME=/opt/android-sdk          # bzw. lokaler SDK-Pfad in local.properties (sdk.dir=…)
./gradlew assembleDebug --max-workers=2        # bei HTTP 429 von Maven Central wiederholen
./gradlew testDebugUnitTest lintDebug assembleRelease --max-workers=2
# Gemergtes Manifest auf Berechtigungen prüfen:
grep uses-permission app/build/intermediates/merged_manifest/debug/processDebugMainManifest/AndroidManifest.xml
```

---

*Hinweis: Die Befunde beruhen auf Quellcode-Analyse, Build/Lint und JVM-Tests (Robolectric). AR-Verhalten konnte mangels
Hardware-Virtualisierung nicht auf einem Gerät oder Emulator geprüft werden. Code, Skripte und Konfigurationen aus
diesem Plan müssen vor produktivem Einsatz geprüft und getestet werden.*
