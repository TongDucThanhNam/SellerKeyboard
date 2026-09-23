# Clipboard Platform Boundaries

Review date: 2026-07-11

Primary sources:

- https://developer.android.com/privacy-and-security/risks/secure-clipboard-handling
- https://developer.android.com/about/versions/10/privacy/changes

## Product behavior

- SellerKeyboard reads the system clipboard only while it is the active/default input method and the current editor privacy policy allows capture.
- Android 10+ restricts clipboard access to the focused app or default IME; SellerKeyboard does not attempt to bypass that boundary.
- Clipboard listeners stop when the input view finishes and remain disabled in sensitive/private editors.
- Clips carrying the platform sensitive flag are rejected from history. OTP/password-like content and probable payment-card digit sequences are also rejected.
- Stored text is trimmed and capped at 1,000 UTF-16 code units. Unpinned history is capped at 20 records and expires after 30 days; pinned items remain until explicit removal.
- Seller identifiers such as phone and tracking values are visually obscured but can be pasted exactly after a user action.
- Parsing produces separate exact fragments without changing the raw source. Clipboard content is never sent to the candidate engine, cloud classifier, or optional template sync.

## Expected platform limitations

- Some editors/device builds can delay or omit clipboard callbacks; manual refresh occurs when an allowed input view starts.
- Cross-profile/device-policy clipboard restrictions can return no clip, which is treated as an empty state rather than an error.
- Rich images are not retained in clipboard history; product image insertion uses the separately reviewed rich-content path.
- Older source apps might not mark secrets as sensitive. Local keyword/card heuristics reduce exposure but cannot infer every secret, so users retain delete/clear and history-disable controls.

Device evidence must cover API 26, 29/30 boundary behavior, 31+, private/password editors, marked-sensitive clips, cross-profile restrictions where available, pin/expiry/clear, and foreground/default-IME access.
