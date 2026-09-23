# Convex Backup Smoke Test

Use this checklist for `convex-backend-goal.md` success criterion #15.

## Prerequisites

- Convex CLI is authenticated on this machine.
- `.env.local` exists and contains `CONVEX_DEPLOYMENT` and `CONVEX_URL`.
- Android SDK platform tools are available. On this machine, use:
  `C:\Android\Sdk\platform-tools\adb.exe`
- A real device or emulator is visible in `adb devices`.

## Backend

1. Export the current dev deployment before pushing schema changes:
   ```powershell
   bunx convex export --path "$env:TEMP\sellerkeyboard-convex-before.zip" --env-file .env.local
   ```
2. Push and validate the dev deployment:
   ```powershell
   bunx convex dev --once --env-file .env.local
   ```
3. Run the backend self-auth and snippet backup smoke script:
   ```powershell
   bun scripts/convex-backend-smoke.ts
   ```

## Android

1. Build and install the debug APK:
   ```powershell
   .\gradlew.bat :app:assembleDebug
   C:\Android\Sdk\platform-tools\adb.exe install -r app\build\outputs\apk\debug\app-debug.apk
   ```
2. Open the app and create a local text snippet.
3. In the Home screen Cloud backup card, register a disposable test email and password.
4. Tap **Backup now** and verify a success snackbar.
5. Delete the local snippet from the Home screen.
6. Tap **Restore**, confirm the restore dialog, and verify the snippet title/content returns.
7. Open a text field, switch to SellerKeyboard, open the snippet keyboard, and verify the restored snippet inserts its text.
8. Disable network or sign out and verify local snippet list/edit/delete still works without login.

Record the device/emulator name, test account email, and pass/fail notes in the PR or goal final report. Do not record passwords or session tokens.
