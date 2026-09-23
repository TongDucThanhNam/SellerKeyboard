# Seller Workflow Benchmark Protocol

The canonical fixture is [`benchmark-fixtures/seller-workflow-scenarios.json`](../benchmark-fixtures/seller-workflow-scenarios.json). It contains 40 privacy-safe scenarios spanning greetings, closings, price, discount, product details/link, shipping, order status, tracking, returns, warranty, payment/COD, address/phone reuse, bilingual replies, calculator quotes, and app profiles.

The comparison counts user-equivalent taps and keystrokes required to produce a reviewable draft. Each run starts with an empty editor in a distinct package and verifies the final UTF-8 SHA-256. SellerKeyboard receives no hidden customer or conversation data, and no path may auto-send. The customized profile opens Replies, focuses search, enters a unique shortcut, finishes search entry, selects the result, reviews it, and explicitly inserts it.

## API 36 reference result

The 2026-07-12 run used Android 16/API 36 on `emulator-5554`, locale `en-US`, with Gboard `15.1.08.726012951-preload-x86_64` (`versionCode` 163542536), selected subtype `1594443099`. Coordinates were resolved from each live accessibility surface and executed with real `input tap` events at fixed 80 ms key pacing.

| Mode | Exact drafts | Median interactions | Median measured time |
|---|---:|---:|---:|
| Gboard | 40/40 | 41 | 7,736.968 ms |
| SellerKeyboard default | 40/40 | 41 | 7,276.053 ms |
| SellerKeyboard customized | 40/40 | 8 | 5,268.334 ms |

The customized profile reduces median interactions by **80.49%**, exceeding the 50% release gate, and reduces median measured time by **31.91%** versus Gboard under this controlled protocol. Across all three modes, 120/120 editor hashes matched, with zero auto-sent messages and zero unresolved variables committed.

Artifacts: [summary](evidence/verification/seller-workflow-comparison/full-2026-07-12/summary.json), [all measurements](evidence/verification/seller-workflow-comparison/full-2026-07-12/measurements.json), [run transcript](evidence/verification/seller-workflow-comparison/full-2026-07-12/run.log), and twelve segmented MP4 recordings in the same directory.

This is same-emulator real-touch evidence, not human or physical-device timing. Physical-device usability remains part of the broader SC-20 device matrix; it is not required to repeat ordinary install/build iteration on Xiaomi.
