# Eragear Keyboard Copilot Transition Plan

## Goal
Transition from **Seller Keyboard** to **Eragear Keyboard Copilot** in a controlled way, with keyboard quality hardening first (responsive layout, re-render performance, haptic UX), then full product rebrand.

## Execution Status (2026-03-04)
1. ✅ Keyboard hardening implementation landed in code:
   - responsive `Keyboard.kt` sizing (constraint-driven),
   - state-hoisted toolbar/page switching in `KeyboardContent.kt`,
   - haptic integration in IME service and key actions,
   - typed calculator button model + reducer/evaluator split.
2. ✅ Unit-test scaffold added for calculator engine logic (`CalculatorEngineTest`).
3. ⏳ Remaining before Phase 1 closure:
   - run full Gradle tests/compile in CI with full JDK toolchain,
   - complete manual device matrix for portrait/landscape + haptic UX consistency.

## Scope Guardrails
1. Keep `applicationId` stable for published updates.
2. Keyboard quality work is Phase 1 priority; rebrand rollout starts after Phase 1 acceptance.
3. Every phase must ship with measurable checks (render, latency, crash-free behavior).

## Phase 0 - Baseline and Instrumentation (1 sprint)
1. Capture baseline keyboard behavior across portrait/landscape and small/large screens.
2. Add profiling checklist:
   - Compose recomposition counts for keyboard pages.
   - Frame timing and jank while typing quickly.
   - Memory churn from keyboard mode switches.
3. Freeze acceptance metrics for Phase 1:
   - No key clipping on supported screen sizes.
   - Stable key hit-target size.
   - No visible lag during rapid typing.

## Phase 1 - Keyboard Hardening (current priority)

### 1. Responsive layout foundation
1. Replace ratio-based fixed sizing in `Keyboard.kt` with constraint-driven sizing (`BoxWithConstraints` + adaptive metrics).
2. Normalize row spacing and key width policy for both orientations.
3. Remove duplicated portrait/landscape sizing logic where possible.

### 2. Re-render/performance optimization
1. Hoist keyboard UI state and avoid transient allocations in composables.
2. Convert static key definitions to stable immutable models.
3. Remove redundant click layers in `KeyboardContent.kt` (single click target per icon).
4. Keep expensive derived values behind `remember`/`derivedStateOf`.

### 3. Haptic UX
1. Add keyboard haptic controller in IME service (`SellerKeyboard`), with API-safe vibrator path.
2. Trigger short feedback on all key actions (char/function/delete/enter).
3. Add setting toggle (on/off) and default to enabled.

### 4. `CalculatorKeyboard.kt` refactor
1. Split UI and logic: extract calculator reducer/evaluator module.
2. Replace string-driven button handling with typed key model.
3. Use shared key rendering path for portrait/landscape with adaptive grid.
4. Fix behavior:
   - `=` should not corrupt expression history.
   - Clear/delete/newline behavior must be deterministic.
   - Send expression/result actions must be isolated and testable.

### 5. Phase 1 test gates
1. Unit tests for calculator reducer and expression evaluation edge cases.
2. Compose tests for layout integrity and key press actions.
3. Manual matrix: orientation, dark/light mode, common chat targets.

## Phase 2 - Rebrand to Eragear Keyboard Copilot
1. Update product naming in app resources, Play listing, icon, screenshots, and description.
2. Keep package identity stable; do not rotate `applicationId`.
3. Add migration messaging: “Formerly Seller Keyboard” for initial rollout window.
4. Align keyboard terminology with AI Copilot direction (snippets -> copilot actions where applicable).

## Deliverables
1. Keyboard hardening PR set (layout, performance, haptics, calculator).
2. Rebrand PR set (assets, strings, listing copy).
3. QA sign-off checklist and rollout checklist for Play production.
