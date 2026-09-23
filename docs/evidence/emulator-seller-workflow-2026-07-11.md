# Connected seller workflow evidence — 2026-07-11

The API 36 zero-touch gate now drives SellerKeyboard's actual Compose IME UI against the neutral
editor package `com.terasumi.sellerkeyboard.benchmarkhost`. The authoritative artifact is
`verification/emulator-connected/latest/seller-workflow/summary.json`; its parent summary records
`sellerWorkflowVerified=true`.

## Proven flow

| Flow | Connected result |
|---|---|
| Favorite ranking | Smart mode places the favorite shipping reply above the higher-usage non-favorite warranty reply |
| Favorite persistence | Removes and restores the shipping favorite, polling the debug database bridge after each real UI tap |
| Category ordering | Category mode places Orders above Returns and Shipping |
| Recent ordering | Recent mode places the newest warranty fixture above the shipping fixture |
| Slash shortcut | Touch keypad enters `/track-connected`; only the tracking reply remains visible before review |
| Plain reply | Opens `Connected Shipping Reply`, reviews it, and commits `Your order ships today.` into the distinct-package editor |
| Parameter safety | `Tracking code: {{tracking_code}}` renders with the insert parent disabled while unresolved |
| Parameter fill | Touch keypad enters `trk123`, enables insertion, and commits exactly `Tracking code: trk123` |
| Command search | Touch keypad searches `calculator`; the `Calculator and seller formulas` command opens the Calculator surface |
| General calculator | Touches `1`, `+`, `2`, `=`, then commits `3` |
| Seller calculator | Touch keypad enters price `100000` and discount `20`, then commits formatted `80.000₫` |

All-window UIAutomator XML is retained for favorite, category, recent, shortcut, plain, unresolved
parameter, resolved parameter, general-calculator, and seller-calculator checkpoints; key committed
states also retain screenshots. Debug seeding snapshots and restores any rows using the three
deterministic IDs; it does not retain editor text in the bridge.

## Network and log attribution

The whole sequence runs inside an exact package-UID attribution window. Supported UID netstats for
SellerKeyboard UID `10216` remain at `0/0` RX/TX-byte deltas. None of the four controlled seller
strings—plain reply, resolved tracking reply, warranty fixture, or formatted seller-calculator result—occur in
SellerKeyboard's UID-scoped logcat. The machine-readable summary and raw netstats/log artifacts are
under `verification/emulator-connected/latest/seller-workflow/network-attribution/`; the workflow
and parent summaries expose the same zero-delta/zero-occurrence assertions.

## Production defects exposed and fixed

1. Android's ICU regex compiler rejected the template placeholder pattern because the closing
   braces were not escaped, although the desktop JVM accepted it. Opening reply review killed the
   IME. Both closing braces are now explicitly escaped and the JVM boundary fixture covers adjacent
   placeholders with whitespace.
2. The Compose root did not reserve the IME navigation-bar inset. Bottom review actions and the
   main key row could render beneath the system Back/switcher bar. Root navigation-bar padding now
   moves the touch surface above the system controls; all-window evidence verifies the separation.
3. Search, template variables, and seller-calculator fields used platform text fields inside the
   IME. A software IME cannot invoke itself to type into those fields, so touch entry was visually
   present but unusable. They now use local state-hoisted fields plus a compact alphanumeric keypad
   with explicit accessibility labels and alphabetic/numeric modes; target-editor focus and data
   remain separate. API 36 reports 48 dp key height, and the final keypad action ends 81 px above
   the IME navigation bar. Horizontal QWERTY width remains a documented compact-surface exception
   pending the SC-20 physical accessibility decision.

## Reproduction

```powershell
.\scripts\run-emulator-connected.ps1
```

This closes SC-10, SC-11, and SC-13 implementation/evidence gaps. Named third-party apps, visible
TalkBack/Switch Access, and physical-OEM acceptance remain independently tracked under SC-20.
