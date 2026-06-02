# Manual QA Matrix

This document provides a shared Manual QA matrix for evaluating keyboard behavior across various orientations, states, and target applications. 
This must be tested for all feature goals.

## Test Environments

- **Target OS Versions**: Android 10 (API 29) to Android 14 (API 34)
- **Orientations**: Portrait and Landscape

## Application Matrix

| App | Field Type | Key Behavior Expected | Focus / Blocker Checks |
|---|---|---|---|
| **Messenger** | Chat input | Enter sends message or moves to new line | Keyboard must not overlap input field |
| **Zalo** | Chat input | Enter sends message | Haptic feedback consistency |
| **Shopee Seller** | Search / Form | Action Done / Search | Text rendering without clipping |
| **Chrome** | URL / Search bar | Action Go / Search | Suggestion bar rendering / Autofill |
| **Gmail** | Email Body | Multi-line text, Enter = new line | Cursor placement, newline consistency |
| **Gmail** | To / Subject | Action Next / Done | Focus jumping to next field |

## Core Scenarios

1. **Orientation Switch**
   - [ ] Open keyboard in Portrait.
   - [ ] Rotate device to Landscape.
   - [ ] Verify keys resize properly without clipping or overlap.
   - [ ] Rotate back to Portrait and verify restoration.

2. **Haptic Feedback**
   - [ ] Enable haptic feedback in settings.
   - [ ] Type normal keys, Space, Delete, Enter. Ensure vibration triggers.
   - [ ] Disable haptic feedback in settings.
   - [ ] Type keys. Ensure no vibration triggers.

3. **Special Keys**
   - [ ] Snippet Keyboard: switch to snippet view, insert a snippet.
   - [ ] Calculator Keyboard: switch to calculator, evaluate simple expression, insert result.
   - [ ] State switching: Shift -> Caps -> Double Caps -> No Caps.
   - [ ] Symbol switching: `?123` -> Symbol -> `?ABC`.

4. **Input Connection Stability**
   - [ ] Rapid typing for 10 seconds: verify no crash, no lag > 100ms.
   - [ ] Long press delete: verify fast character deletion.
   - [ ] Pressing keys while switching pages (Keyboard -> Snippet -> Calculator) does not cause IndexOutOfBounds or crash.
