# Project: Android Assistant for MPEI Students (ru.mpei.assistant) - Compose Performance Optimization

## Architecture
The application is a Jetpack Compose Android client for MPEI students, featuring Material 3 theming, Room local persistence, and EncryptedSharedPreferences.
- **Data Layer**:
  - `ru.mpei.assistant.data.model`: Data classes (`Lesson`, `SearchEntity`, `BarsSubject`, `ControlMeasure`).
  - `ru.mpei.assistant.data.local`: Room Database, DAOs, `SessionStorage` (EncryptedSharedPreferences).
  - `ru.mpei.assistant.data.repository`: `ScheduleRepository`, `BarsRepository`.
- **UI Layer**:
  - Navigation: `ru.mpei.assistant.ui.navigation.Navigation.kt` (`MainScreen`, `NavHost`).
  - Schedule: `ScheduleScreen.kt`, `LessonCard.kt`, `GroupSelectDialog.kt`.
  - BARS: `BarsScreen.kt`, `SubjectCard.kt`.
  - Settings: `SettingsScreen.kt`.
  - Theme: `ru.mpei.assistant.ui.theme` (`Theme.kt`, `Color.kt`).
- **Optimization Strategy**:
  - Stable Compose skipping via `@Immutable` on UI data models and `@Stable` on repositories.
  - Efficient Lazy list recycling via `key` and `contentType` on `LazyColumn` / `LazyRow` / `HorizontalPager`.
  - Elimination of main-thread GC allocations, redundant parsing, and synchronous disk/crypto I/O in composition phases.
  - Isolating animations and state invalidations using `derivedStateOf`, `remember`, and lambda modifiers (`Modifier.graphicsLayer { ... }`, `Modifier.drawBehind { ... }`).

## Feature Inventory
| # | Feature | Description | Milestone | Source |
|---|---------|-------------|-----------|--------|
| 1 | Model & Repo Stability Annotations | Annotate `Lesson`, `SearchEntity`, `BarsSubject`, `ControlMeasure` with `@Immutable` and repositories with `@Stable` | M1 | Survey (Explorers 1, 2, 3) |
| 2 | Navigation Allocation Cleanup | Lift `NAVIGATION_ITEMS` to top-level constant and avoid lambda recreation in `Navigation.kt` | M1 | Survey (Explorer 3) |
| 3 | Build & Test Wrapper Baseline | Provide root Gradle wrapper and document JDK 21 environment for verified `./gradlew` execution | M1 | Survey (Explorer 3) |
| 4 | Schedule Room Flow Memoization | Remember `scheduleRepository.getLessonsFlow(currentGroupId)` across recompositions | M2 | Survey (Explorer 1) |
| 5 | Schedule Root Scope Recomposition Isolation | Remove dead code `selectedDayLessons` and top-level reads of `selectedDayIndex` | M2 | Survey (Explorer 1) |
| 6 | Schedule Pager Keys & Constant-Time Lookup | Pre-group semester lessons by date (`lessonsByDate`) and add key to `HorizontalPager` | M2 | Survey (Explorer 1) |
| 7 | Schedule LazyColumn Key & ContentType | Specify `key = { it.dbId }` and `contentType = { "lesson" }` in `ScheduleScreen.kt` | M2 | Survey (Explorer 1) |
| 8 | GroupSelectDialog Key & ContentType | Specify `key = { it.id }` and `contentType = { "search_group" }` in `GroupSelectDialog.kt` | M2 | Survey (Explorer 1) |
| 9 | Day Chips Memoization & Draw Isolation | Precompute `DayChipUiModel` per week and isolate chip rendering with static shapes & drawBehind | M2 | Survey (Explorer 1) |
| 10 | Schedule Derived States & Animated Transitions | Implement `derivedStateOf` for pager sync and button visibility; add `AnimatedVisibility` and `Modifier.animateItem()` | M2 | Survey (Explorer 1) |
| 11 | LessonCard Allocation Optimization | Hoist `LessonCardShape` and `BadgeShape` to static constants, optimize badge color lookup | M2 | Survey (Explorer 1) |
| 12 | BARS Room Flow & Auth State Memoization | Remember `barsRepository.getSubjectsFlow()` and decouple auth state from synchronous crypto reads | M3 | Survey (Explorer 2) |
| 13 | BARS LazyColumn Key, ContentType & AnimateItem | Specify `key = { it.id }`, `contentType = { "subject_card" }`, and `Modifier.animateItem()` | M3 | Survey (Explorer 2) |
| 14 | SubjectCard JSON Parser Hoisting | Hoist singleton `SubjectJson` and parse with `remember(subject.controlMeasuresJson)` | M3 | Survey (Explorer 2) |
| 15 | SubjectCard Debt Traversal Memoization | Memoize `hasDebt` via `remember(controlMeasures)` | M3 | Survey (Explorer 2) |
| 16 | SubjectCard Static Constants & Allocations Elimination | Hoist static shapes, colors, and animation specs to eliminate allocations during scroll | M3 | Survey (Explorer 2) |
| 17 | SubjectCard Expand/Collapse GraphicsLayer Animation | Animate arrow rotation via `Modifier.graphicsLayer { rotationZ = ... }` with `animateFloatAsState` | M3 | Survey (Explorer 2) |
| 18 | SubjectCard Child Item Keys | Add `key(km.id)` inside badge and row `forEach` loops | M3 | Survey (Explorer 2) |
| 19 | KmQuickBadge LayoutNode Flattening | Flatten `Surface + Box` into single `Box` with `Modifier.clip().background()`, eliminate `Pair` | M3 | Survey (Explorer 2) |
| 20 | SettingsScreen LazyColumn Migration | Migrate `SettingsScreen.kt` from `Column(verticalScroll)` to `LazyColumn` | M4 | Survey (Explorer 3) |
| 21 | SettingsScreen Key & ContentType Implementation | Add `key` and `contentType` for header items and settings card items | M4 | Survey (Explorer 3) |
| 22 | SettingsScreen State & Recomposition Isolation | Isolate switch toggles and dialog states into dedicated card composables | M4 | Survey (Explorer 3) |
| 23 | Unit Test Suite & Regression Prevention | Add comprehensive unit tests in `app/src/test/` verifying UI state models and logic | M5 | Acceptance Criteria |
| 24 | Final Build & Test Verification | Run `./gradlew assembleDebug` and `./gradlew test` verifying zero errors, zero regressions, and NO screenshots | M5 | Acceptance Criteria |

## Milestones
| # | Name | Scope | Dependencies | Status |
|---|------|-------|-------------|--------|
| M1 | Core Stability & Architecture | Features 1, 2, 3 (Models, Repositories, Navigation, Gradle wrapper) | none | DONE |
| M2 | Schedule UI Optimization | Features 4, 5, 6, 7, 8, 9, 10, 11 (ScheduleScreen, LessonCard, GroupSelectDialog) | M1 | DONE |
| M3 | BARS UI Optimization | Features 12, 13, 14, 15, 16, 17, 18, 19 (BarsScreen, SubjectCard) | M1 | DONE |
| M4 | Settings UI Optimization | Features 20, 21, 22 (SettingsScreen LazyColumn & Keys) | M1 | DONE |
| M5 | Test Suite & Final Verification | Features 23, 24 (Unit tests, assembleDebug, test, Acceptance Criteria validation) | M2, M3, M4 | IN_PROGRESS |

## Interface Contracts
### Data Models & Repositories ↔ UI Screens
- `ScheduleModels.kt`:
  - `@Immutable data class Lesson(...)`
  - `@Immutable data class SearchEntity(...)`
- `BarsModels.kt`:
  - `@Immutable data class ControlMeasure(...)`
  - `@Immutable data class BarsSubject(...)`
- `Repositories.kt` & `SessionStorage.kt`:
  - Annotated with `@Stable`
- UI Composables receive stable instances or primitive values; collections wrapped in `remember` / `collectAsState`.

## Code Layout
- `app/src/main/java/ru/mpei/assistant/data/model/`: Data models
- `app/src/main/java/ru/mpei/assistant/data/local/`: Local database, DAOs, SessionStorage
- `app/src/main/java/ru/mpei/assistant/data/repository/`: Repositories
- `app/src/main/java/ru/mpei/assistant/ui/navigation/`: Navigation & MainScreen
- `app/src/main/java/ru/mpei/assistant/ui/schedule/`: ScheduleScreen, LessonCard, GroupSelectDialog
- `app/src/main/java/ru/mpei/assistant/ui/bars/`: BarsScreen, SubjectCard
- `app/src/main/java/ru/mpei/assistant/ui/settings/`: SettingsScreen
- `app/src/test/java/ru/mpei/assistant/`: Unit tests
