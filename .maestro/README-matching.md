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

In user-reported CI run `39007766770` (API 34), fixed-keystore restoration and APK building succeeded; smoke, feed, timer, and login passed. All three matching flows still failed while waiting for `matching_map_ready`. No SDK callback category/code was collected, so the cause remains unknown; fixed signing alone does not exclude every authentication issue.

Each flow writes `<flow>/matching-map-diagnostics.json` before fixture cleanup or the final app-data clear, including Maestro assertion, timeout, interruption, and runner failures. The optional shared `collect_diagnostics` hook does not replace `after_flow` verification. A collection failure adds only `feature-diagnostics-failed`; the original UI failure remains, and fixture cleanup still runs. Failed restoration still preserves private recovery state instead of clearing it. Other features without this hook keep their existing lifecycle.

The artifact schema is `{"schemaVersion":1,"status":"ok","finalState":"failed","events":[{"sequence":1,"attempt":1,"state":"loading"},{"sequence":2,"attempt":1,"state":"failed","category":"initialization","sdkErrorCode":1001}]}`. `sequence` is the original evidence-array position, including gaps for private non-map events; `attempt` is the sequence of that MapView's actual start. Re-entry/recreation starts a new attempt. Map states are `loading`, `ready`, and `failed`; a failed attempt cannot later become ready. The existing `matching_map_loading` and `matching_map_ready` selectors retain their meaning, and SDK failure exposes `matching_map_failed` only inside the existing debug/armed boundary. Unarmed/release screens do not expose E2E tags or member IDs; failures show only the existing generic error text.

Only the SDK's public integer `MapAuthException.getErrorCode()` and supported direct network exception types are classified:

| Category | Public evidence / limitation |
| --- | --- |
| `auth` | 401, 403, 1003 (invalid app key). Does not prove which external credential/hash setting is wrong. |
| `network` | -2, -3, -4, 499, or a direct connection, socket-timeout, unknown-host, or SSL exception. Direct exceptions have `sdkErrorCode:null`. |
| `initialization` | 1001. Initialization failed before authentication communication; a key/hash issue is possible, not established. |
| `rendering` | 1002. SDK render-view failure; does not identify its underlying cause. |
| `request` / `quota` | 400 / 429 respectively. |
| `unknown` | -1, an unrecognized signed 32-bit SDK code, or another callback exception with no SDK code. No message/cause-based guesses. |

`finalState` is the latest attempt's observed state. Valid evidence without map events yields `not_observed`; loading or no callback is not an authentication diagnosis. Missing/unreadable evidence, invalid schema/types/order, mismatched run identity, and a file over 1 MiB yield fixed `status` values `read_failed`, `invalid_evidence`, `stale_evidence`, and `size_limit` respectively, with `finalState:"unknown"` and no events. At most 1000 source events are accepted. Booleans are not integer codes or sequence numbers. Unknown map fields/categories and inconsistent code/category pairs fail closed.

The collector uses bounded app-private `run-as` reads, checks the current run ID, and builds new allowlisted JSON objects. It never exports the original root/events, member/place/room identifiers, coordinates, URLs, tokens, exception messages/causes/stacks/classes, or logcat. Raw evidence remains app-private; the artifact is mode 0600 and contains no run ID. Existing logcat exclusion and raw Maestro temporary-file cleanup remain unchanged. Inspect this artifact alongside the existing sanitized flow artifacts. Successful collection alone does not prove Kakao registration, SDK readiness, or a successful device run.

## Final cleanup and failure recovery

The shared lifecycle always invokes feature cleanup before normal session cleanup, including failed flows. Cleanup collects exact run-marker places outside the baseline, rechecks ownership, deletes them through the authenticated real API, then re-reads the server. It preserves baseline IDs and all baseline place values. It never sweeps timestamps, all places, or all rooms.

Cleanup only exits a new primary-side room when the actual UI response recorded that exact ID for the configured peer and the room did not exist in the snapshot. Existing dedicated/baseline rooms are never exited. Account-level `DELETE chat-rooms` is not a claim of global deletion or peer-history removal; the supported success path reuses the required existing room and creates no peer-side fixture. Unexpected room changes fail verification. Unrelated/unproven new rooms are not deleted, and cleanup fails rather than masking their presence.

After a complete restoration check, feature cleanup retains its private snapshot and evidence unchanged. The shared coordinator must complete feature cleanup, exercise cleanup, and session clear before the host performs its final successful `pm clear`, which removes these files. If a later shared stage fails, a retry with the same member, run, and flow rechecks ownership and the baseline without deleting data twice. Restoration or identity failures still fail cleanup and preserve recovery evidence; the common recovery marker prevents a blind `pm clear`. If preparation stops before arming UI, cleanup can finish without a feature mutation. `after_flow` restores both location permissions even if its server verification fails; a failed Maestro stage is still cleaned by the shared backend-finally lifecycle and isolated app-data cleanup after successful restoration. Do not run this suite on a personal physical device.

## Unexecuted verification and runtime prerequisites

No local build, test, formatter, linter, adb, Python, or Maestro command was executed for this implementation. The checked-in unit tests cover exact ownership boundaries, debug/armed evidence, SDK code classification without exception details, terminal failed readiness, delayed deletion/load state, and the stale reverse-geocoding response race. Android instrumentation tests cover real private JSON append order, map recreation, unchanged non-map evidence, 0600 permissions, and the existing 1000-event limit. Python tests cover strict safe artifact projection, synthetic-secret exclusion, malformed/stale/oversized evidence, original Maestro failure preservation, and diagnostic/fixture-cleanup failure ordering, in addition to fixture inputs and permission restoration. Their presence is not a passing result.

The controller must verify the common integration first, then run the matching unit tests, `:app:testE2eUnitTest`, the Python matching contract tests, and the authenticated `--suite matching` remotely. Remote evidence must name all three flows, prove the real persisted ID/address and room-peer checks, and include cleanup after a mid-flow failure. Also force a failure after feature cleanup, then retry cleanup with the same request: the retained snapshot/evidence must remain byte-for-byte unchanged until the final successful host clear, the second feature cleanup must succeed, and baseline data must remain unchanged. Observe the actual selected CI image's display/density/orientation (API 34 in the user-reported run, rather than assuming API 30 from the matching descriptor), actual marker/cluster point, saved-camera restoration after permission revocation, Kakao SDK rendering, real keyword result stability, location delivery, and permission/toast hierarchy. Compare release/unarmed accessibility with armed E2E descriptions. Source inspection has not calibrated the marker point or proved these runtime conditions. Missing external inputs must remain explicit failures, not skipped flows or fabricated fixtures.
