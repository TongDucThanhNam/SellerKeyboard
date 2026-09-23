# API 36 connected rich-content target matrix — 2026-07-11

The default headless `SellerKeyboardSmoke` AVD ran
`scripts/verify-rich-content-targets.ps1` as part of the ordinary
`scripts/run-emulator-connected.ps1` gate. The neutral `benchmark-host` app runs under UID `10218`,
separate from SellerKeyboard UID `10216`; a shell-restricted debug IME bridge exercised the real
`InputConnection.commitContent` path across that UID boundary. The bridge is absent from
SellerKeyboard benchmark/release manifests, and the neutral host is a separate benchmark APK rather
than part of the product APK.

Three contracts passed:

- `image/jpeg` advertised and accepted: the target requested the temporary URI permission, opened
  the FileProvider URI, verified the JPEG signature bytes, and displayed
  `RICH_ACCEPTED:image/jpeg`;
- `image/jpeg` advertised but rejected: SellerKeyboard preserved and committed
  `Seller product | VND 100000 | https://example.invalid/product`;
- MIME unsupported: SellerKeyboard committed the identical fallback without attempting rich
  delivery.

The machine-readable result and per-case UI XML are under
`verification/emulator-connected/latest/rich-content-targets/`. The parent summary records
`richContentTargetCases: 3` and the full gate passed. The entire sequence is wrapped by exact
SellerKeyboard UID `10216` attribution: supported netstats remain at `0/0` RX/TX-byte deltas and
the controlled fallback text occurs zero times in UID-scoped logcat. Raw attribution artifacts are
under `rich-content-targets/network-attribution/`.

This proves cross-UID MIME negotiation, URI grant/read behavior, target rejection handling, and
lossless text/link fallback against a neutral reference app. It does not substitute for named messaging/commerce apps, product-image
ownership and cache-retention approval, or third-party URI-permission behavior on physical OEM
devices.

The same three cross-UID contracts pass on the API 26 endpoint artifact at
`verification/emulator-connected/SellerKeyboardApi26/latest/rich-content-targets/`, including
exact UID `10077`, supported `0/0` RX/TX-byte deltas, and zero fallback-text log occurrences through
the verified-PID logcat fallback.

## Remote fetch boundary

API 36 also runs the same target matrix with unique per-run HTTP image URLs through an
`adb reverse` loopback fixture. The debug-only cleartext transport avoids external hosts and does
not alter release manifests. The server log proves exactly one fetch for the accepting target and
one for the rejecting target; unsupported MIME produces zero requests because policy commits text
fallback before Glide starts. All three editor outcomes match the local matrix, and the remote URL
plus fallback text occur zero times in exact-UID logs. Evidence is under
`verification/emulator-connected/latest/remote-rich-content/`.

This establishes deterministic remote fetch/no-fetch behavior and log attribution. A production
HTTPS host, image ownership/deletion terms, and named third-party targets remain unapproved.
