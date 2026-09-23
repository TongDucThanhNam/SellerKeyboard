# Google Play Data Safety and Privacy Review

Review date: 2026-07-11

Status: implementation-side review complete; Play Console declarations, published privacy-policy URL, voice-provider acceptance, and optional-sync product decision remain release-blocking external sign-off.

Primary policy source: https://support.google.com/googleplay/android-developer/answer/10144311

## Product data map

| Data or capability | Default behavior | Leaves device | Retention/control | Proposed Play disclosure |
|---|---|---|---|---|
| Typed, composing, selected, and surrounding text | Active editor session only | No during normal typing/candidates | Bounded session; cleared on field/service lifecycle | Not collected or shared |
| Password/authentication text | Context features fail closed | No | Not retained | Not collected or shared |
| Candidate and personal dictionary data | Local engine and local user words | No | User can remove/clear/disable learning | Not collected or shared |
| Clipboard history/fragments | Local, optional, bounded; marked-sensitive clips rejected | No | 20 unpinned/30 days; pin/delete/clear controls | Not collected or shared |
| Templates, products, profiles, usage ranking | Local by default | No by default | Edit/archive/delete/reset/export controls | Not collected unless optional sync ships |
| Emoji recents | Local | No | Clearable | Not collected or shared |
| Voice audio | Explicit tap; Android speech provider | Provider-dependent | SellerKeyboard stores no audio | Prominent in-app disclosure; provider behavior must be accepted before release |
| Voice transcript | Review state, then explicit insert/discard | No app-side upload | Cleared on discard/session lifecycle | Not collected or shared by SellerKeyboard |
| Translation text | Explicit draft/selection only | Input/output processed on-device by ML Kit on API 30+; scoped native guard prevents observed provider full-text log emission | Text not retained by controller | Input/output is not sent to Google; disclose model/config downloads and ML Kit operational metrics |
| Translation model | Explicit Wi-Fi download | Google model download request | User can delete model | Operational data/model delivery, not user text collection |
| Product image URL/content | Explicit reviewed product action plus seller ownership/authorization attestation; production accepts HTTPS only | Approved image host and target editor when chosen; rejected cleartext/legacy sources are not fetched | Shared cache capped at five files with oldest-first eviction; image files are not synced | Disclose only if remote image feature ships with approved ownership/host policy |
| App package identifier | Exact active editor package for local profile | No | Local binding, removable with app data/profile controls | Not collected or shared |
| Optional Convex account/template backup | Deferred from current release UI; client/auth/backup lazies remain cold | No current user path | Re-enabling requires login/logout/preview/backup/delete controls and approval | Not shipped; account/user-content declarations required if re-enabled |

## Permission and capability audit

| Manifest item | Reason | Decision |
|---|---|---|
| `INTERNET` | Explicit ML Kit model/config delivery and operational metrics, remote product image, and optional backup | Retain; device network attribution and Play disclosure required |
| `RECORD_AUDIO` | Explicit Android speech recognition | Retain with pre-permission disclosure and interruptible flow |
| `VIBRATE` | Optional key haptics | Retain |
| Media/storage read permissions | Document picker grants are sufficient | Removed |
| `QUERY_ALL_PACKAGES` | Not needed for exact active-editor profile | Not declared |
| Accessibility service | Prohibited for seller assistance | Not declared or implemented |
| Android automatic cloud backup | Could copy local keyboard data unexpectedly | Disabled |

## Security and user control

- Normal typing initializes no cloud client; optional cloud repositories are lazy.
- Sensitive mode centrally suppresses candidates, learning, clipboard, seller tools, translation, voice, and rich content.
- Logs and trace section names contain no typed/selected/template/clipboard/voice text. Translation additionally has a fail-fast exact-UID canary gate in debug and non-debuggable minified variants after ML Kit 17.0.3 was observed emitting its source through native logs.
- Import validates schema and data, previews count/pack identity, and requires confirmation.
- Destructive structured-reply operations require confirmation; dictionary, clipboard, recents, settings, ranking, models, and local records expose clear/delete controls.
- No seller workflow invokes the target application's Send action.
- Product-image records require explicit authorization and HTTPS. Legacy unauthorized or cleartext records fail closed to the complete product text/link fallback; an API 36 loopback probe proves the production path issues zero HTTP requests for a rejected source.

## Release sign-off still required

1. Publish an accurate privacy policy and enter its URL in the Play listing.
2. Reconcile the final build with the Play Console Data safety questionnaire.
3. Approve the current sync deferral or separately authorize a credentialed template-only sync design; decide production remote-image ownership/host policy.
4. Record the remaining physical/independent network captures for production image hosts and optional sync; emulator exact-UID plus deterministic server captures now cover ordinary typing, clipboard, seller tools, local/remote image commit, deterministic voice result/error/cancel, real voice-provider start/cancel, and translation download/use.
5. Declare ML Kit device/app information, identifiers, performance metrics, API configuration, input/output sizes, events/errors, and configured translation languages as required by the final Play Console questionnaire; Google states translation input/output itself stays on-device.
6. Validate account/data deletion if optional sync ships.
7. Obtain product/legal approval for the installed speech provider's possible network processing.

No Play declaration should claim that voice-provider or optional-sync processing is absent until the final release configuration and device evidence prove that claim.
