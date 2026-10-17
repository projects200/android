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

## Final cleanup and failure recovery

The shared lifecycle always invokes feature cleanup before normal session cleanup, including failed flows. Cleanup collects exact run-marker places outside the baseline, rechecks ownership, deletes them through the authenticated real API, then re-reads the server. It preserves baseline IDs and all baseline place values. It never sweeps timestamps, all places, or all rooms.

Cleanup only exits a new primary-side room when the actual UI response recorded that exact ID for the configured peer and the room did not exist in the snapshot. Existing dedicated/baseline rooms are never exited. Account-level `DELETE chat-rooms` is not a claim of global deletion or peer-history removal; the supported success path reuses the required existing room and creates no peer-side fixture. Unexpected room changes fail verification. Unrelated/unproven new rooms are not deleted, and cleanup fails rather than masking their presence.

After a complete restoration check, feature cleanup retains its private snapshot and evidence unchanged. The shared coordinator must complete feature cleanup, exercise cleanup, and session clear before the host performs its final successful `pm clear`, which removes these files. If a later shared stage fails, a retry with the same member, run, and flow rechecks ownership and the baseline without deleting data twice. Restoration or identity failures still fail cleanup and preserve recovery evidence; the common recovery marker prevents a blind `pm clear`. If preparation stops before arming UI, cleanup can finish without a feature mutation. `after_flow` restores both location permissions even if its server verification fails; a failed Maestro stage is still cleaned by the shared backend-finally lifecycle and isolated app-data cleanup after successful restoration. Do not run this suite on a personal physical device.

## Unexecuted verification and runtime prerequisites

No local build, test, formatter, linter, adb, Python, or Maestro command was executed for these added regressions and documentation. The checked-in unit tests cover exact ownership boundaries, the debug/armed evidence boundary, delayed deletion/load state, and the stale reverse-geocoding response race. Python tests cover literal identity patterns, missing/invalid fixture inputs, and permission restoration after a verification failure. The local patch extends the two transport tests already published in `b7659923` and adds malformed/unreviewed-reason coverage. These regressions exercise reviewed matching categories, confidentiality, exact operation/feature/status scope, and private-result retention. The local additions do not change the production runner and have not executed. The remote result below covers the published head, not these local test additions. Test presence and source inspection are not passing results.

Latest published head `579ffaf7` restores `swiftshader_indirect` and retains the fixed E2E debug keystore.
Normal [CI run `39012430657`](https://github.com/projects200/android/actions/runs/39012430657) passed.
[Maestro run `39012430640`](https://github.com/projects200/android/actions/runs/39012430640) passed all 12 common flows again, but all three matching flows failed with `maestro-flow-failed`.
The previous `software` [run `39009277393`](https://github.com/projects200/android/actions/runs/39009277393) passed keystore restoration, the account-free tests, E2E APK validation/build, and input validation.
All 15 UI flows then failed with `maestro-flow-failed`, including smoke, feed, timer, and login; none were skipped or unexecuted.
Control [run `39007766770`](https://github.com/projects200/android/actions/runs/39007766770) used the same fixed keystore with `swiftshader_indirect`.
That run passed preparation and all 12 common flows, but all three matching flows failed with `maestro-flow-failed`.
The only source change from control head `13d263db` to `dbcdaed5` was the GPU option.
The new run's emulator log confirms that `software` selected lavapipe/swangle.
Run `39012430640` confirms this: restoring `swiftshader_indirect` removed the 12 common-flow failures.
The original three matching failures remain unresolved at `579ffaf7`.
Key restoration and APK build success do not prove Kakao SDK authentication or map readiness.
Exact failed commands and screen state still require sanitized per-flow artifacts; the original matching root cause remains unknown.

The controller must verify the common integration first, then run the matching unit tests, `:app:testE2eUnitTest`, the Python transport and matching contract tests, and the authenticated `--suite matching` remotely. Remote evidence must name all three flows, prove the real persisted ID/address and room-peer checks, and include cleanup after a mid-flow failure. Also force a failure after feature cleanup, then retry cleanup with the same request: the retained snapshot/evidence must remain byte-for-byte unchanged until the final successful host clear, the second feature cleanup must succeed, and baseline data must remain unchanged. The combined `ci` run selected API 34, while the standalone matching descriptor declares API 30. Record the selected suite and actual device API. Observe that device's display/density/orientation, actual marker/cluster point, saved-camera restoration after permission revocation, Kakao SDK rendering, real keyword result stability, location delivery, and permission/toast hierarchy. Compare release/unarmed accessibility with armed E2E descriptions. Source inspection has not calibrated the marker point or proved these runtime conditions. Missing external inputs must remain explicit failures, not skipped flows or fabricated fixtures.
