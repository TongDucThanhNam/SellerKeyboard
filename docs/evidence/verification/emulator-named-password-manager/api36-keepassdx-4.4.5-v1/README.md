# API 36 named password-manager inline Autofill evidence

Captured on 2026-07-12 with `scripts/verify-named-password-manager-autofill.ps1`.

- Provider: KeePassDX 4.4.5 libre (`com.kunzisoft.keepass.libre`).
- Provider source: <https://github.com/Kunzisoft/KeePassDX/releases/tag/4.4.5>.
- Project and published signing-certificate digest: <https://github.com/Kunzisoft/KeePassDX>.
- Data boundary: a disposable local KeePass vault containing only fictional controlled values; no
  account, cloud sync, real credential, or submitted content.
- Flow: visible `Sign in with KeePassDX` inline chip, provider-owned entry picker, visible named
  credential chip, exact username/password fill in the distinct benchmark host.
- Privacy: exact-UID accounting for SellerKeyboard, KeePassDX, and the host; all record zero RX/TX
  delta and zero controlled-value log occurrences.
- Restoration: original Autofill service, IME, hard-keyboard setting, package presence, and fixture
  vault presence are restored exactly.

`summary.json` is the machine-readable authority. Screenshot and window artifacts are hashed in
that summary; the verifier retains no provider APK, master key database, or installed KeePassDX
package after completion.
