# bp-tracker — Implementation Plan

Status: implemented (v0.1.1 released). This document is the source of truth for the first implementation pass; §11 is the changelog.

## 1. Goals

- Record systolic / diastolic / heart rate with minimal friction: open the app → immediately a numpad → type digits → confirm. No other UI interaction needed to get in a number.
- The raw digit stream is **auto-parsed** into `SYS / DIA / HR` with realism checks. The user never presses "next field".
- After a valid parse the app shows the parsed entry with **OK / Edit / Cancel**. OK saves it with a timestamp.
- History view with a 7-day **chart** (default) and a **spreadsheet-style list**, togglable. Chart: X = time (7 days, 2 measurements/day accommodated), Y = BP as a bar range (diastolic → systolic), heart rate as a thin, muted dotted line.
- Architecture kept as simple as possible; GUI code structured for easy iteration. UI should be responsive and polished, not barebone.

## 2. Key technology decisions

| Decision | Choice | Why | Alternative (if it hurts) |
|---|---|---|---|
| Language/UI | Kotlin + Jetpack Compose (Material 3) | Native Android target; Compose = modern, animated, themeable UI with the least code; trivially extensible | Flutter (only if multi-platform ever wanted) |
| Module layout | Single Gradle module `app` | Nothing to coordinate across modules | Multi-module later if it grows |
| Storage | Room (KSP) | Time-ordered queries, deletes, upgrade path; standard, tiny surface | JSON file / DataStore (weaker queries) |
| Navigation | State-driven (sealed `Screen`), no navigation library | Only 2 screens; fewer dependencies, easier to edit | Navigation Compose (if more screens appear) |
| Charts | Hand-rolled `Canvas` drawing (~250 lines) | The look is custom (range bars + dotted HR line); zero dependency, full control over styling, easy to restyle | Vico (vinnl) library if hand-rolling fights us |
| Concurrency | Kotlin coroutines + `Flow` (Room → `StateFlow`) | Standard, minimal ceremony | — |

Targets: minSdk 26, target/compileSdk 35+, Kotlin 2.x, AGP 8.x.

## 3. UX flow

1. **Cold start → Entry mode.** Always. (Deliberate per spec: the app is a measurement tool, opening it means taking a measurement.)
2. Entry screen: big live display on top, numpad (1–9, 0, wide ⌫, Cancel) below, confirm bar that slides up when a valid parse exists.
3. Display states while typing:
   - `waiting` — fewer than 6 digits; shows the digit stream, muted "keep typing" hint.
   - `parsed` — the whole stream parses to a unique valid reading: big `121 / 81` + `71 bpm`, green accent. Confirm bar active: **OK** (primary), **Edit**, **Cancel**.
   - `unresolved` — 6–9 digits but no valid delineation: digit stream shown, amber hint "not a valid reading yet". OK disabled. (User keeps typing or backspaces.)
   - `too long` — 10+ digits: red "too many digits", OK disabled; only ⌫ or Cancel escape.
4. **OK** → haptic + save with `System.currentTimeMillis()` timestamp → slide to Review (Chart tab).
5. **Edit** → back to the numpad with the digits still loaded (backspace to fix a mistyped digit; typing appends and re-parses live).
6. **Cancel** → discard everything, fresh numpad (or to Review if a reading was already made this visit — default: fresh entry).
7. Review screen: title + "＋ New entry" button; segmented toggle **Chart | List** (default Chart).
8. Haptic tick on every digit; subtle pressed-state animations; everything animated between states (no janky jumps).

## 4. Parsing spec (core logic)

### 4.1 Rules

Input: a string of decimal digits `d` of length `n`, built key-by-key.

Candidate delineations split `d` into 3 consecutive parts (sys, dia, hr), each 2 or 3 digits:

| n | splits |
|---|---|
| 6 | (2,2,2) |
| 7 | (2,2,3), (2,3,2), (3,2,2) |
| 8 | (2,3,3), (3,2,3), (3,3,2) |
| 9 | (3,3,3) |
| n < 6 | incomplete — `waiting` |
| n > 9 | impossible — `too long` |

A split is **valid** iff (ranges are named constants, one place to tune):

- `70 ≤ sys ≤ 250`
- `30 ≤ dia ≤ 150`
- `30 ≤ hr ≤ 220`
- `sys > dia`

Result:
- exactly one valid split → `Parsed(sys, dia, hr)`
- zero valid splits → `Unresolved` (or `TooLong` if n > 9)
- more than one → `Ambiguous(candidates)` — defensive branch; with the ranges above this is provably unreachable (see 4.3), but the code handles it anyway so future range tweaks can't produce silent mis-parses (it would show "keep typing" or ask to disambiguate).

### 4.2 Examples (required behaviors)

| Typed | Result |
|---|---|
| 1,2,1,8,1,7,1 | **121 / 81 / 71** ✓ |
| 9,8,6,9,5,9 | **98 / 69 / 59** ✓ |
| 1,1,8,6,6,6 | no valid delineation (118/66/6 fails hr; 11/86/66 fails sys; 118/6/66 fails dia) → `Unresolved`, cannot be OK'd ✓ |

### 4.3 Uniqueness argument (why the UX can be so simple)

With the ranges above, **at most one split can ever be valid**:

- n=6, n=9: only one split exists.
- n=7: splits (2,2,3) and (2,3,2) require the 1st digit ∈ {7,8,9} (sys 2-digit ≥ 70), while (3,2,2) requires 1st digit ∈ {1,2} (sys ≤ 250) — mutually exclusive. Between (2,2,3) and (2,3,2): the shared sys is 70–99; for (2,3,2) to hold, dia = cde ≤ 150 forces digit c ∈ {0,1}, which makes (2,2,3)'s dia = cd < 30 — contradiction.
- n=8: same first-digit argument separates (2,3,3) [digit 1 ∈ {7,8,9}] from (3,2,3)/(3,3,2) [digit 1 ∈ {1,2}]; between (3,2,3) and (3,3,2) both share sys=abc ≤ 250, and (3,2,3)'s dia = de ≤ 150 with dia' = def ≤ 150 for (3,3,2) is compatible only if… in practice exhaustive in the unit tests.

So in practice the display is always in a deterministic single state — no ambiguity UI ever shows, but the branch exists for safety.

### 4.4 Implementation

- `domain/BpParser.kt`: pure Kotlin, zero Android imports.
  - `object Ranges { sys, dia, hr: ClosedRange<Int> }` — tunable constants, documented here.
  - `sealed interface ParseResult { Waiting; Parsed(sys,dia,hr); Unresolved; TooLong; Ambiguous(results) }`
  - `fun parse(digits: String): ParseResult`
- State machine lives in the ViewModel (`EntryState`), transitions: `digit`, `backspace`, `cancel`, `edit`, `ok`. The parser is re-run on every key — it's O(1) (≤ 3 candidates), no incremental logic needed.

### 4.5 Unit tests (JVM, no Android)

- The three examples above (121/81/71, 98/69/59, 118666 rejected).
- Boundaries: 70/40/40 (min valid), 250/150/220 (max valid, n=9), 251… / 26 / 23 (each out of range), sys ≤ dia rejected (e.g. 909050… 90/90/50 → rejected, 90/80/50 accepted), leading-zero streams (e.g. 0,9,0,8,0,6,0,6 → 090/80/60… note: split (3,2,2) = 090 → value 90 ✓ valid — test asserts the numeric semantics, `090` == 90), n=5 → Waiting, n=10 → TooLong, every n=7/8/8 permutation over a small digit grid asserting never >1 valid split (exhaustive uniqueness check).

## 5. Data model & persistence

```kotlin
@Entity(data class Reading(
  @PrimaryKey(autoGenerate) val id: Long = 0,
  val ts: Long,        // epoch millis at save
  val sys: Int, val dia: Int, val hr: Int
))
```

- `ReadingDao`: `insert`, `delete(reading)`, `since(ts): Flow<List<Reading>>`, `all(): Flow<List<Reading>>` (desc).
- `ReadingRepository`: thin wrapper exposing `last7Days: StateFlow<List<Reading>>` and `all: StateFlow<List<Reading>>` (single Room DB, `bp.db` v1, no migrations planned).
- No sync/cloud in v1.

## 6. UI architecture

One `MainActivity` → one root composable. File layout (the whole GUI is ~6 files, each single-responsibility):

```
src/main/kotlin/app/bp/
  MainActivity.kt
  BpApp.kt                 // root: sealed Screen state, animated transitions
  data/
    Reading.kt             // entity + dao + database
    ReadingRepository.kt
  domain/
    BpParser.kt
  ui/
    BpViewModel.kt         // EntryState machine + reading flows (one VM, deliberately)
    theme/Theme.kt         // M3, dynamic color (Android 12+) + fixed fallback palette, typography
    entry/EntryScreen.kt   // display, numpad, confirm bar
    review/ReviewScreen.kt // tabs: Chart | List, header, "＋ New entry"
    review/BpChart.kt      // pure Canvas drawing, no state of its own
    review/ReadingList.kt  // LazyColumn spreadsheet
```

### Entry screen details

- Display: the digit stream in a mono display with the last digit highlighted in a chip; when parsed, the big human form — three labeled groups `121 / 81 / 71` under `SYS · DIA · HR` (mono, values in primary color). Color/size shifts per state (waiting=muted, parsed=primary, unresolved=error red, too long=error red). The display `Box` takes all flexible space (`weight(1f)`) and centers its content.
- Numpad: 4 rows (1–9, then 0 / ⌫ / empty gap), **fixed 56 dp keys** anchored at the bottom in the thumb zone, spring press-down animation + haptic tick. ⌫ is a regular-width key. Backspace on empty stream = no-op.
- Top row: title + `FilledTonalButton` Cancel — always visible, disabled while the stream is empty.
- Confirm bar: hidden until `parsed` (and not editing); slides in **in the layout flow above the numpad** — structurally impossible to push off-screen. OK (flexible-width primary, checkmark icon), Edit, Cancel.
- **Layout rule (learned in v0.1.0): the numpad must never take weighted space.** In v0.1.0 the pad's rows used `Modifier.weight(1f)`, so the pad greedily filled the whole screen, squeezed the display to zero height and pushed the confirm bar off-screen — entry was unusable on device (see §11).

### Review screen details

- Header: "Blood pressure" + date range subtitle (last 7 days) + "＋ New entry".
- Toggle: M3 `SingleChoiceSegmentedButtonRow` — Chart (default) / List.
- **Chart** (`BpChart`): see §7.
- **List** (`ReadingList`): spreadsheet look — sticky column header row (Date/Time · SYS · DIA · HR), rows in a `LazyColumn`, numbers in `fontFeatureSettings = "tnum"`, right-aligned; day group headers ("Mon 24 Nov"); newest first; swipe-right to delete with an undo Snackbar; empty state with CTA to add a reading.

### Responsiveness / prettiness budget

- Material 3 dynamic color (auto-adapts to user wallpaper on 12+), dark mode, custom typography scale, rounded corners, 250ms standard easing on all state changes, haptics on digit + confirm, staggered chart entrance animation. No splash/onboarding.

## 7. Chart spec (custom Canvas)

- **Domain**: 7 calendar days (today + previous 6).
- **X axis**: 7 bands, one label each ("Mon 24"). Readings within a day sorted by timestamp; **up to 3 bars per band** side by side (first reading = left = "morning", second = "night" — positional, not clock-based; a 3rd rare reading gets a 3rd thinner bar). Bar slot width adapts: 1 bar centered, 2 side-by-side, 3 slim.
- **Y axis**: mmHg, scale = [min(50, dataMin−10) … max(150, dataMax+15)] — adapts to the data with a floor/ceiling so one odd reading can't squash the chart. Horizontal gridlines every 20, labels at left. (HR 30–120 fits comfortably on the same axis — no second axis needed.)
- **BP bars**: vertical rounded segment from dia (bottom) to sys (top), ~16dp wide, rounded caps, primary color with the systolic cap slightly darker/emphasized.
- **HR**: single thin (1.5dp) **dotted** polyline through the HR points (same x-centers as the bars), muted secondary color, tiny 3dp dots — clearly subordinate to the BP bars.
- Empty band: just grid (the gap communicates missed days).
- Implementation: `drawChart(canvas, size, readings, palette)` as a near-pure drawing function inside `BpChart.kt`; entrance animation = bars grow from their diastolic edge, staggered ~30ms/band, HR line fades in after. Tap on a bar → tooltip with exact values (cheap version: highlight + small overlay text; full selection UX = Phase 2).
- Perf: ≤ 21 bars + 21 points — trivially fast; recomposition only on data change.

## 8. Milestones

1. **Scaffold**: Gradle project (Kotlin DSL, wrapper), Compose + M3 theme, `MainActivity` + placeholder Entry/Review, builds & runs on an emulator. Git repo already initialized.
2. **Parser**: `BpParser` + full unit test suite (green, including exhaustive uniqueness checks).
3. **Entry end-to-end**: numpad → live parse → OK/Edit/Cancel → Room save → transition to Review.
4. **Review**: chart first, then list, toggle, delete + undo, empty states.
5. **Polish**: dynamic color, dark mode, animations, haptics, app icon/name, final pass.

Phase 2 candidates (not in v1): CSV export, explicit morning/night labels, target-range band shading on the chart, bar selection/detail, widget, wear OS.

## 9. Defaults adopted (flag if any disagree)

- Cold start always lands on Entry (per spec), even after a previous visit ended in Review.
- "Morning"/"night" bars = 1st/2nd reading of the day by timestamp, not by clock time.
- Realism ranges are deliberately wide (70–250 / 30–150 / 30–220) so real (including hypertensive or elderly) readings are never silently rejected; only absurd ones (e.g. HR = 6) fail. All ranges in one constants object.
- `sys > dia` is required (strict); pulse pressure < 10 is *not* rejected in v1.
- List view shows all history (scrolls); chart is fixed to 7 days.

## 10. Testing

- Parser: pure JVM unit tests (above) — the highest-value tests in the project.
- UI: Robolectric + Compose UI tests (`ui-test-junit4` + `robolectric`, `isIncludeAndroidResources = true`), running on the JVM — no emulator needed in this environment. `EntryScreenTest` drives the real screen with synthetic clicks at phone size (`@Config(sdk = [34], qualifiers = "w412dp-h915dp-420dpi")` — Robolectric's qualifier parser requires the `dpi` suffix on the density token):
  - display + numpad visible before any input (regression guard for the v0.1.0 layout bug);
  - typing `1218171` shows the parsed groups + confirm bar, OK registers the save;
  - typed digits are shown live (last-digit chip) and backspace returns to the hint.
  - Note: the test harness's digit state must be Compose snapshot state (`mutableStateOf`) — a plain `var` updates but never invalidates composition, so screens under test never re-render.
- UI: manual on a real device per release (v0.1.0 device testing is what caught the §11 layout bug; Robolectric tests now guard the regression).

## 11. Changelog

### v0.1.2 (2026-11-26)

Device feedback after v0.1.1 — three fixes:

- **Dark theme (the big one).** The app ignored night mode entirely; text that is black-on-light in the day theme stayed black on the dark window and was barely visible.
  - Platform side: `Theme.BpTracker` is now a DayNight pair — `values/themes.xml` → `Theme.Material.Light.NoActionBar`, new `values-night/themes.xml` → `Theme.Material.NoActionBar` (window chrome + the brief pre-Compose moment).
  - Compose side, root cause: M3's `MaterialTheme` does **not** provide `LocalContentColor` (its default is `Color.Black`); only `Surface`/`Scaffold` do. The review screen was fine because its root is a `Scaffold`; the entry screen's root was a bare `Column`, so its plain `Text`s (the "New reading" title) rendered pure black in dark mode. Fix: entry root is now an M3 `Surface(color = background, contentColor = onSurface)` — the same pattern `Scaffold` uses.
  - Verified by pixel, not by declaration: new `DarkModeTest` (3 tests) runs Robolectric with the `night` qualifier (`w412dp-h915dp-night-420dpi` — note the strict qualifier ordering: `night` must precede the density), forces `dynamicColor = false` for the deterministic TealDark palette, captures the real window via `PixelCopy.request`, and asserts: dark background, light title, dark-teal digit chip.
  - **Gotcha:** Compose's `captureToImage()` does not work under Robolectric — `forceRedraw` posts a runnable to the paused main looper and waits on the Compose test clock only, so it always times out (`ComposeTimeoutException` after 2000 ms). The working path: `createAndroidComposeRule(ComponentActivity::class.java)` (exposes `.activity`), idle the main looper, then `PixelCopy.request(window, null, bitmap, listener, Handler(mainLooper))` — Robolectric's `ShadowPixelCopy` renders the window and delivers the listener. Node→pixel mapping via `SemanticsNode.boundsInWindow`.
- **Numpad clearance.** The bottom key row sat at the very bottom edge, in the zone where the auto-hide gesture-nav bar reveals itself. The 16 dp spacer is now a non-interactive `EntryFooter` strip (testTag `entry-footer`) carrying the format hint `e.g. 1218171 → 121 / 81 / 71`, which was previously in the empty display (where it only lived until the first digit). The keys are lifted off the bottom edge; the strip is informational, never competes for touch.
- **Delineated chart/list.** With few entries the chart and list floated on the bare background and looked unfinished. Both now render inside a `ContentCard`: M3 `Surface` with `surfaceContainerLow` fill, 1 dp `outlineVariant` border, 20 dp radius, 12 dp inner padding (M3 1.3.1 `Surface` has no `contentPadding` parameter — the inner `Box` applies it). List row backgrounds adjusted to sit inside the card (`surfaceContainer` day headers, transparent rows).
- Version 0.1.1 → 0.1.2 (versionCode 3).

### v0.1.1 (2026-11-26)

- **Entry screen redesign — fix for the v0.1.0 device bug.** v0.1.0 shipped with numpad rows using `Modifier.weight(1f)` and no fixed key height: the pad greedily filled the entire screen, the live display was squeezed to zero height (typed digits invisible), the confirm bar was pushed off-screen (OK never appeared), and the quarter-screen keys made one-handed typing hard. The redesign:
  - top row: title + prominent `FilledTonalButton` Cancel (always visible; disabled while the stream is empty);
  - display takes all flexible space (`weight(1f)`), shows hint / live digit stream with highlighted last digit / parsed `SYS · DIA · HR` labeled groups / error states;
  - confirm bar slides in **in the layout flow above the numpad** — structurally impossible to push off-screen;
  - numpad: fixed 56 dp keys, 4 rows anchored at the bottom (thumb zone), spring press animation + haptic tick.
- **Robolectric UI tests** added (3 tests, §10) as the regression guard for the layout bug.
- Version 0.1.0 → 0.1.1 (versionCode 2).

### v0.1.0 (2026-11-26)

- First release: parser + entry + 7-day chart/list. Device testing found the entry layout bug fixed in v0.1.1.
