# Emulator cross-app profile evidence — 2026-07-11

The API 26 and API 36 zero-touch gates now prove SC-14 through real Android IME sessions rather than
only repository/coordinator tests. The endpoint artifacts are
`verification/emulator-connected/SellerKeyboardApi26/latest/cross-app-profiles/summary.json` and
`verification/emulator-connected/latest/cross-app-profiles/summary.json`; the parent
summaries record `crossAppProfilesVerified=true`.

## Observed transitions

| Active editor package | Resolved profile | Effective settings verified |
|---|---|---|
| `com.terasumi.sellerkeyboard` | `connected_seller_profile` / Connected Seller | Vietnamese, number row on, Light, Tall, `replies|calculator`, `connected-seller-pack` |
| `com.terasumi.sellerkeyboard.benchmarkhost` | `connected_host_profile` / Connected Host | English, number row off, Dark, Compact, `products|clipboard`, `connected-host-pack` |
| `com.terasumi.sellerkeyboard.benchmarkhost` after binding removal and editor reconnection | `global` / Global | Vietnamese+English, number row on, System, Standard, full default toolbar, no preferred pack |

The script snapshots any existing profiles, bindings, and global keyboard preferences before the
run and restores them in `finally`. It seeds only two known debug profile IDs and does not request
`QUERY_ALL_PACKAGES` or enumerate installed applications. The bridge exposes package/profile and
effective preference metadata only; it does not expose editor text.

## Reproduction

```powershell
.\scripts\run-emulator-connected.ps1
```

The runner silently installs the debug, test, and neutral-host APKs, resets/reselects the IME after
instrumentation on Android versions that expose the reset command (and enable/selects it on older
versions) to avoid Android retaining a selected ID without a live binder after package replacement,
and then runs `scripts/verify-cross-app-profiles.ps1` as part of the integrated gate.

This closes the SC-14 implementation/evidence requirement. It does not replace named
commerce/messaging-app, physical-OEM, or recreation-video acceptance tracked by SC-20.
