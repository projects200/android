# Matching feature CI

`.maestro/suites/matching.json` adds three real, separately authenticated flows to the shared runner:

1. `matching-place-crud`: keyword search → exact real result → registration → server reload → rename → deletion → server reload.
2. `matching-place-validation`: blank-name rejection, then real keyword search/result/registration-screen entry with location permission denied.
3. `matching-member-to-chat`: real map marker → configured member/location row → profile → existing dedicated room, then location-permission denial.

Use the shared runner with `--suite matching`. The shared `ci` selection discovers this descriptor. The shared `regression` selection remains the exercise pair; adding this descriptor does not redefine that selection. Direct Maestro invocation does not prepare these fixtures.

## Required configuration

The shared primary authentication contract remains `MAESTRO_AUTH_STATE_JSON`, `MAESTRO_MEMBER_ID`, and `MAESTRO_FIXTURE_START_DATE`. The existing Kakao native/REST keys, real DEV backend, refreshed session, Google fused-location provider, and Kakao SDK/catalog network access must work.

The descriptor additionally requires these exact names. The shared workflow reads their configured GitHub Secrets only in authenticated steps. Do not print their values.

| Name | Required meaning |
| --- | --- |
| `MAESTRO_PEER_MEMBER_ID` | An explicitly authorized, registered dedicated peer, different from the primary member. No public-user fallback exists. |
| `MAESTRO_MATCHING_LEFT_TOP_LATITUDE` | Northern edge of a real bounding box containing the peer's existing exercise location. |
| `MAESTRO_MATCHING_LEFT_TOP_LONGITUDE` | Western edge of that bounding box. |
| `MAESTRO_MATCHING_RIGHT_BOTTOM_LATITUDE` | Southern edge, strictly below the northern edge. |
| `MAESTRO_MATCHING_RIGHT_BOTTOM_LONGITUDE` | Eastern edge, strictly above the western edge. |
| `MAESTRO_MATCHING_SEARCH_QUERY` | Nonblank printable ASCII keyword accepted by the real Kakao keyword-search API. Android Maestro `inputText` must be able to type it. |
| `MAESTRO_MATCHING_CATALOG_PLACE_NAME` | Exact name of one unique real result for that keyword, both near the resolved peer location and near the application's default Seoul City Hall location. |
| `MAESTRO_MATCHING_MARKER_POINT` | Calibrated percentage point, for example the format `x%, y%`, on the actual SDK marker/cluster containing that peer after recentering. There is no default point. |
| `MAESTRO_MATCHING_ROOM_ID` | An existing dedicated primary↔peer room. The room must be the primary account's only room for this peer. It is a baseline and must not be exited during this suite. |

Bounds use decimal degrees. Antimeridian-crossing or inverted bounds are not supported. The application DTO does not expose a Kakao catalog ID, so the suite uses the source-supported exact result name and takes its actual address/coordinates from the API. It does not invent a result ID, address, peer location, or room. Supply a sufficiently specific real ASCII query; this repository does not claim that an arbitrary English keyword has a stable Korean catalog result.

The primary account must have fewer than ten places. Preparation never deletes baseline places to create capacity. Preparation resolves the exact peer with `GET members`, checks its current `GET profile` identity, and chooses its lowest positive location ID within the configured bounds. Device coordinates come from that actual location, not the supplied bounds or a hardcoded public location. A missing peer, empty/invalid location, duplicate/missing catalog result, full place list, or missing/ambiguous dedicated room fails before the UI can mutate feature data.

PR commit `b7659923` already contains this diagnostic boundary. The host exposes a reviewed fixed category only for a `matching` `prepare` result with `status: "failed"` and an exact allowlisted string `reason`. The 19 literal public messages begin with `bootstrap-prepare-failed-matching-`. For example, `bootstrap-prepare-failed-matching-existing-peer-room-required` identifies the dedicated-room prerequisite without disclosing room or member IDs. These error messages contain no parameter values, value lengths, identifiers, coordinates, tokens, exception text, or server response bodies. Extra result fields remain private. Unknown or non-string reasons, `setup_failed`, and unexpected statuses retain generic bootstrap failures. Nonmatching preparations and cleanup/export failed results stay generic. Malformed JSON retains `invalid-bootstrap-result`. This diagnostic boundary does not change successful-result handling, private-file retention, or recovery.

The pre-existing-room prerequisite intentionally avoids creating unsolicited peer-side state without peer authorization. This suite exercises the actual UI create/find-room API and validates the returned existing room identity; it does not claim coverage of creating a brand-new two-sided room or message exchange. The independent chatting suite owns two-account exchange coverage.

## Actual assertions and ownership

- Search uses the real search input, search button, result list and selected result. Neither reverse geocoding nor a profile deep link replaces that path. Exact catalog address assertions remain in the registration screen and persisted list after registration/rename.
- The fixture saves full primary-place values and room ID/peer ownership before launching UI. The run-owned place names are `e2e-m-<E2E_RUN_ID>` and that exact name plus `-edit`. Maestro does not generate an unrelated timestamp.
- List semantics distinguish `places_loading`, `places_loaded` and `places_error`. Deletion waits for the row to disappear after successful DELETE before navigation; reopening requires a completed server load, not just the static title.
- Private debug-only UI evidence records actual loaded place values, actual UI create/find-room responses, and actual denied-permission callbacks. The evidence file is armed only by the e2e fixture, contains no credentials, is app-private mode 0600, and is never exported as a public artifact. Normal/release execution does not create this file.
- `after_flow` calls the fixture's read-only `phase=verify` preparation branch with the same run identity. It requires registered and renamed place evidence to share one new server ID and the actual catalog address/coordinates. It checks final `GET places` against the complete baseline. For chat, it checks the UI response room ID, exact peer/location, a real denied-permission callback, `GET rooms.otherMemberId`, and unchanged baseline room ownership. A room title alone is not identity evidence.
- Only an armed fixture in a debuggable app exposes rendered member/location identity, after the actual SDK redraw path. The same boundary gates the rendered-member collection, ID-string joins, map machine semantics, and member/search row machine descriptions. Release and unarmed debug screens retain normal rendering and readable child labels, without internal-ID spoken labels. The E2E member row selector still includes both IDs; a wrong/unavailable calibrated point fails instead of selecting the first public row.
- Permission denial and allowed-permission/null-location are different production branches. This suite asserts the real permission-denial guidance and absence of room entry. It does not claim null-location coverage. Register/search/list/map navigation uses explicit destinations, and room/profile navigation uses Back before accessing the previously hidden bottom tabs. Saved map browsing position remains available when location permission is denied.

## Safe map diagnostics

The collector preserves map-start evidence even when a flow later fails after SDK readiness. In [Maestro run `39012430640`](https://github.com/projects200/android/actions/runs/39012430640), all three matching flows passed `matching_map_ready`; their later failures are separate from map initialization. Readiness alone does not prove marker hit testing or chat navigation.

Each flow writes `<flow>/matching-map-diagnostics.json` before fixture cleanup or the final app-data clear, including Maestro assertion, timeout, interruption, and runner failures. The optional shared `collect_diagnostics` hook does not replace `after_flow` verification. A collection failure adds only `feature-diagnostics-failed`; the original UI failure remains, and fixture cleanup still runs. Failed restoration still preserves private recovery state instead of clearing it. Other features without this hook keep their existing lifecycle.

The artifact schema is `{"schemaVersion":1,"status":"ok","finalState":"failed","events":[{"sequence":1,"attempt":1,"state":"loading"},{"sequence":2,"attempt":1,"state":"failed","category":"initialization","sdkErrorCode":1001}],"labelClicks":[]}`. `sequence` is the original evidence-array position, including gaps for private non-map events; `attempt` is the sequence of that MapView's actual start. Re-entry/recreation starts a new attempt. Map states are `loading`, `ready`, and `failed`; a failed attempt cannot later become ready. The existing `matching_map_loading` and `matching_map_ready` selectors retain their meaning, and SDK failure exposes `matching_map_failed` only inside the existing debug/armed boundary. Unarmed/release screens do not expose E2E tags or member IDs; failures show only the existing generic error text.

`labelClicks` records actual SDK label callbacks as `{"sequence":3,"attempt":1,"kind":"cluster"}`. The fixed kinds are `cluster`, `place`, and `unknown`; they contain no tags, coordinates, or identities. A callback must belong to the current ready attempt. Dispatch uses the rendered label's own cluster/place tag, not a new coordinate comparison. `place` means the actual callback selected a primary-place label; an empty array does not prove a calibrated point hit a peer marker. These diagnostics do not replace the exact peer/location row or room-ownership assertions.

Only the SDK's public integer `MapAuthException.getErrorCode()` and supported direct network exception types are classified:

| Category | Public evidence / limitation |
| --- | --- |
| `auth` | 401, 403, 1003 (invalid app key). Does not prove which external credential/hash setting is wrong. |
| `network` | -2, -3, -4, 499, or a direct connection, socket-timeout, unknown-host, or SSL exception. Direct exceptions have `sdkErrorCode:null`. |
| `initialization` | 1001. Initialization failed before authentication communication; a key/hash issue is possible, not established. |
| `rendering` | 1002. SDK render-view failure; does not identify its underlying cause. |
| `request` / `quota` | 400 / 429 respectively. |
| `unknown` | -1, an unrecognized signed 32-bit SDK code, or another callback exception with no SDK code. No message/cause-based guesses. |

`finalState` is the latest attempt's observed state; label clicks do not change it. Valid evidence without map events yields `not_observed`; loading or no callback is not an authentication diagnosis. Missing/unreadable evidence, invalid schema/types/order, mismatched run identity, and a file over 1 MiB yield fixed `status` values `read_failed`, `invalid_evidence`, `stale_evidence`, and `size_limit` respectively, with `finalState:"unknown"` and empty `events`/`labelClicks`. At most 1000 source events are accepted. Booleans are not integer codes, attempts, or sequence numbers. Unknown fields/categories/kinds and inconsistent code/category pairs fail closed.

The collector uses bounded app-private `run-as` reads, checks the current run ID, and builds new allowlisted JSON objects. It never exports the original root/events, member/place/room identifiers, coordinates, URLs, tokens, exception messages/causes/stacks/classes, or logcat. Raw evidence remains app-private; the artifact is mode 0600 and contains no run ID. Existing logcat exclusion and raw Maestro temporary-file cleanup remain unchanged. Inspect this artifact alongside the existing sanitized flow artifacts. Successful collection alone does not prove Kakao registration, SDK readiness, or a successful device run.

## Final cleanup and failure recovery

The shared lifecycle always invokes feature cleanup before normal session cleanup, including failed flows. Cleanup collects exact run-marker places outside the baseline, rechecks ownership, deletes them through the authenticated real API, then re-reads the server. It preserves baseline IDs and all baseline place values. It never sweeps timestamps, all places, or all rooms.

Cleanup only exits a new primary-side room when the actual UI response recorded that exact ID for the configured peer and the room did not exist in the snapshot. Existing dedicated/baseline rooms are never exited. Account-level `DELETE chat-rooms` is not a claim of global deletion or peer-history removal; the supported success path reuses the required existing room and creates no peer-side fixture. Unexpected room changes fail verification. Unrelated/unproven new rooms are not deleted, and cleanup fails rather than masking their presence.

After a complete restoration check, feature cleanup retains its private snapshot and evidence unchanged. The shared coordinator must complete feature cleanup, exercise cleanup, and session clear before the host performs its final successful `pm clear`, which removes these files. If a later shared stage fails, a retry with the same member, run, and flow rechecks ownership and the baseline without deleting data twice. Restoration or identity failures still fail cleanup and preserve recovery evidence; the common recovery marker prevents a blind `pm clear`. If preparation stops before arming UI, cleanup can finish without a feature mutation. `after_flow` restores both location permissions even if its server verification fails; a failed Maestro stage is still cleaned by the shared backend-finally lifecycle and isolated app-data cleanup after successful restoration. Do not run this suite on a personal physical device.

## Unexecuted verification and runtime prerequisites

No local build, test, formatter, linter, adb, Python, or Maestro command was executed for this patch. Preserved tests cover ownership, debug/armed evidence, SDK code classification, terminal failed readiness, delayed deletion/load state, stale reverse-geocoding responses, private append order, map recreation, 0600 permissions, and the 1000-event limit. The local `test_run_tests.py` transport regressions remain intact: reviewed failure categories, confidentiality, exact operation/feature/status scope, malformed reasons, and private-result retention. Added regressions cover consumed-search-result replay, actual-label dispatch without coordinate matching, and safe label-click projection. Test presence is not a passing result.

Latest incoming head `3b1f1d5` has successful general CI; [Maestro run `39014869132`](https://github.com/projects200/android/actions/runs/39014869132) was cancelled, not passed. The last completed [Maestro run `39012430640`](https://github.com/projects200/android/actions/runs/39012430640) at `579ffaf7` passed all 12 common flows on API 34 with `swiftshader_indirect` and the fixed keystore. All three matching flows failed. This patch changes neither GPU selection nor signing.

The received sanitized `debug-001.log` and `debug-005.json` artifacts establish three separate failing paths:

- **Place CRUD:** creation, server reload, and exact address checks passed. Center-tap plus `eraseText: 200` left the original name suffix after rename; the saved row did not match the exact edited selector. The flow now uses the timer suite's select-all deletion pattern and checks exact input before saving. Persisted ID/address and final deletion checks remain unchanged.
- **Validation:** blank-name submission stayed on registration. Back returned to search; the search Back button hid a replayed result overlay instead of returning to `places_loaded`. The final hierarchy was still the search screen. Selecting or dismissing results now clears the ViewModel's replayed result, so view recreation does not reopen a consumed list. The flow still requires the actual list and map destinations and repeats the real search with location denied.
- **Member to chat:** readiness and the exact rendered peer/location pattern passed. The configured point reached the place list, not `rv_members`. The old dispatcher guessed label kind by recomputing clusters and exact coordinate equality, then treated every miss as a primary place. Dispatch now uses the actual SDK label tag. The artifacts do not identify the clicked tag or establish correct point calibration; `labelClicks` supplies only that missing safe callback kind on the next run. No coordinates, real-marker interactions, peer selectors, or existing-room checks were replaced.

The controller must run matching unit tests, `:app:testE2eUnitTest`, instrumentation evidence tests, Python transport/feature-hook tests, and authenticated `--suite matching` remotely. The next evidence must name all three flows, prove persisted ID/address and room-peer checks, and show cleanup after a mid-flow failure. Force a failure after feature cleanup and retry with the same request: snapshot/evidence must remain unchanged until final successful host clear, and baseline data must remain intact. Record the selected suite/device API: combined `ci` used API 34, while the matching descriptor declares API 30. Marker calibration on that device, actual label kind, saved-camera restoration after denial, real catalog stability, and release/unarmed accessibility remain runtime checks. Missing prerequisites must fail explicitly; they must not cause skipped assertions or fabricated fixtures.
