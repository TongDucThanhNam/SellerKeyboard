# Repository Guidelines

## Project Structure & Module Organization
- Main Android module: `app/`.
- Kotlin source: `app/src/main/java/com/terasumi/sellerkeyboard/` with clear domains:
  - `keyboard/`: IME UI, layouts, calculator/snippets keyboards.
  - `service/`: input method service (`SellerKeyboard`) and IME runtime integration.
  - `main/`: app screens and navigation.
  - `database/`: Room entities/DAO helpers.
  - `ui/theme/`: Material3 theme tokens.
- Resources: `app/src/main/res/` (`values*`, `drawable/`, `xml/`).
- Tests: `app/src/test/` (JVM) and `app/src/androidTest/` (device/instrumented).
- Planning docs live in `docs/` (current source-of-truth: `docs/eragear-keyboard-copilot-plan.md`).

## Build, Test, and Development Commands
- `./gradlew :app:assembleDebug`: build debug APK.
- `./gradlew :app:installDebug`: install debug build to emulator/device.
- `./gradlew :app:testDebugUnitTest`: run JVM unit tests.
- `./gradlew :app:connectedDebugAndroidTest`: run instrumentation/Compose UI tests.
- `./gradlew :app:lintDebug`: run Android lint.
- `./gradlew :app:bundleRelease`: build release AAB for Play upload checks.

## Coding Style & Naming Conventions
- Kotlin + Jetpack Compose, 4-space indentation.
- Prefer state hoisting and immutable UI models for keyboard rendering paths.
- Naming: `PascalCase` (types/composables), `camelCase` (functions/vars), `UPPER_SNAKE_CASE` (constants), `snake_case` (resource files/IDs).
- Keep package boundaries strict; avoid mixing UI/service/data logic.

## Testing Guidelines
- Unit tests use JUnit4; UI/integration uses AndroidJUnit4 + Espresso/Compose test libs.
- Name tests by behavior, e.g. `CalculatorEngineTest`, `KeyboardLayoutInstrumentedTest`.
- Any keyboard change should cover:
  - key behavior and mode switching,
  - calculator expression evaluation and error states,
  - orientation responsiveness (portrait/landscape).

## Commit & Pull Request Guidelines
- Use clear imperative commits, recommended format: `<type>(<scope>): <change>`.
- Example: `refactor(keyboard): hoist state and reduce recomposition`.
- PRs must include problem statement, approach, test commands/results, and UI screenshots/video when behavior changes.

## Security & Release Notes
- Never commit secrets (`.env.local`, API keys, tokens).
- Keep permissions minimal and review `AndroidManifest.xml` for every new capability.
- For published updates, keep `applicationId` unchanged and coordinate rebrand assets/listing copy in the same release window.
