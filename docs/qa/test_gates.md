# Test Gates for Future Goals

Every feature goal must pass these test gates before being considered complete.

## Required Test Commands

1. **Build the debug APK**
   ```bash
   ./gradlew :app:assembleDebug
   ```
   *Gate:* Must compile successfully.

2. **Run Unit Tests**
   ```bash
   ./gradlew :app:testDebugUnitTest
   ```
   *Gate:* All existing and new unit tests must pass.

3. **Run Lint**
   ```bash
   ./gradlew :app:lintDebug
   ```
   *Gate:* Must pass. New lint errors should be fixed. Existing ones can be categorized.

4. **Run Instrumented Tests (if applicable)**
   ```bash
   ./gradlew :app:connectedDebugAndroidTest
   ```
   *Gate:* Must pass on emulator/device.

## Manual QA Gates

All features must also be verified against the shared [Manual QA Matrix](00_manual_qa_matrix.md).
