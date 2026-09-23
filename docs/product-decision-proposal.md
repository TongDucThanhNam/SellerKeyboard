# SellerKeyboard release product decisions

Status: **PROPOSED — product-owner approval is still required**  
Contract: `sellerkeyboard-product-decisions-v1`  
Prepared: 2026-07-12

This packet resolves the ambiguity in `GOAL.md` without pretending that an implementation agent
can provide product approval. The proposed values match the current implementation and the safest
release boundary already exercised by the automated evidence. Approval does not waive any physical
device, accessibility, privacy-capture, upgrade, or named-app evidence gate.

## Recommended decisions

| Decision | Proposed release choice | Why this is the conservative current choice |
|---|---|---|
| Reference devices and apps | Keep Xiaomi Redmi Note 11 `2201117TG`, Android 13/API 33, as the lower-midrange reference. Product owner must name a second current-mainstream physical device and the production seller-messaging, commerce, and rich-content apps. Chrome, Google Messages, and Gmail are the default browser/SMS/email targets. | Xiaomi evidence already exists; API 36 emulator coverage is broad, but it is not a second physical device or product confirmation. |
| Candidate and workflow thresholds | Independent top-3 recall `>= 85%`; protected-token mutation `0%`; deterministic autocorrection undo `100%`; seller-workflow interaction reduction `>= 50%`. | These are the explicit goal thresholds. Current evidence is `98.75%`, `0%`, `100%`, and `80.49%`. |
| Voice provider network behavior | Prefer Android on-device recognition. Permit the installed provider's network fallback only after an explicit Voice tap and the pre-permission disclosure. Keep voice disabled in sensitive/private editors; never auto-insert or send. | This exactly matches the repository implementation and disclosure. SellerKeyboard retains no audio and keeps the final transcript only in the review state. |
| Candidate dictionary and licensing | Ship only the project-authored compact seller lexicon under the repository MIT license plus user-created personal-dictionary entries. Do not import a third-party corpus until its provenance, redistribution license, and notices are recorded. | No third-party word list is currently present or needed for the measured release corpus. |
| Product images, cache, and sync | Accept only seller-owned or otherwise authorized HTTPS image sources. Keep at most five temporary shared-content files, evict oldest first, and retain them only until displaced or Android/app cache data is cleared. Do not sync image files in this release. | This matches the enforced record/load boundary, bounded internal cache, and current deferred-sync boundary. Legacy unauthorized or non-HTTPS images are omitted without losing the reviewed text/link fallback; an API 36 production-path probe proves rejected cleartext causes no HTTP request. |
| Optional template sync | Keep it out of the current release. | The Home route exposes no sync UI and connected evidence shows the Convex dependencies remain cold. Re-enabling needs a separate schema/privacy/account/deletion approval. |
| Compact and one-handed geometry | Standard/tall modes retain at least 48 dp rows. User-selected Compact is the explicit exception: 44 dp portrait and 40 dp landscape. One-handed mode remains 78% width and must not shrink row heights further. | These are the implemented values. Physical same-device accessibility acceptance remains a separate release gate. |
| Language switching | Keep both Android subtype/platform switching and long-press Space VI/EN toggling for bilingual profiles. Do not add a dedicated globe key in this release. | This matches the current behavior specification and avoids consuming another primary-key slot. |
| Stale product data | Mark manually maintained product data stale after 30 days. Show a review warning but allow explicit insertion after review; never fabricate updates or auto-send. | This matches `ProductReplyComposer` and the current review surface. |
| Seller-app accounts | Use dedicated non-production test accounts and fictional customers/orders only. Never use production customer data. If accounts are unavailable, the corresponding named-app evidence remains blocked rather than being replaced by neutral-host evidence. | This keeps product confirmation and privacy evidence honest. |

## Approval procedure

1. Copy `docs/product-decisions.proposed.json` to `docs/product-decisions.approved.json`.
2. Replace every `REPLACE_WITH_...` value with an exact device or app name.
3. Change the document and every decision status from `PROPOSED` to `APPROVED`.
4. Fill `approval.approvedBy` and an ISO-8601 `approval.approvedAt` timestamp.
5. Run:

   `powershell -ExecutionPolicy Bypass -File scripts/verify-competitive-keyboard.ps1 -EvaluateReleaseReadinessOnly -ProductDecisionApprovalPath docs/product-decisions.approved.json`

The verifier checks the complete contract, rejects placeholders, and refuses to treat even a valid
structured release-exception file as product approval. A product choice that differs from these values requires
the implementation/specification and this contract validator to change together before it can pass.
