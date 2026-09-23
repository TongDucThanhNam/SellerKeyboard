# API 36 named-app account-gate evidence — 2026-07-12

This is boundary evidence, not a named-app compatibility pass. The clean API 36 emulator had zero
Android account records. SellerKeyboard was the active IME, and Google Autofill was configured as
`com.google.android.gms/.autofill.service.AutofillService`.

Installed targets inspected without entering credentials or submitting content:

- Gmail `2025.03.02.732962214.Release`
- Google Docs `2.25.100.0.x86_64.alldpi`
- Google Play services `25.26.35`

Gmail completed its first-run introduction, exposed only **Add an email address**, and refused
**Take me to Gmail** with **Please add at least one email address**. Google Docs exposed a **Skip**
action for carrier account discovery, but that action immediately opened the Google Account sign-in
screen. Therefore neither package exposes a real account-free editor on this emulator image.

No account was added, no credential was entered, no email/document content was created, and no
message or document was submitted. A real Gmail/Docs compose matrix and provider-owned inline
autofill selection remain external test-account/provider acceptance work; they must not be inferred
from package installation alone.

Artifacts:

- `gmail-add-account.png` — SHA-256
  `BD772166263B05861B0FE779AA8C81791FD83805C28F29DED917FB4123DC9EC0`
- `gmail-account-required.png` — SHA-256
  `A9C0A00140B70A6B1203CEC76BF7719D16280E15676E4B0D70ACB36AEE6BA5BC`
- `docs-account-discovery-skip.png` — SHA-256
  `D5212C69D7164EEB5F1377A73A5287156BCA8A5BACF6E1C4B1E7F0013CAC2D14`
- `docs-sign-in-required.png` — SHA-256
  `0DA7D302ADBD2FD7276416AB6C9F17B3A3D68A35F1771752FDCEC6C002211C22`
