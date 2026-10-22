# 채팅 2계정·2기기 실제 CI

`.maestro/suites/chatting.json`은 공통 러너에 다음 네 시나리오를 등록합니다.
`ci`는 이 descriptor가 있는 PR에서 채팅 드라이버를 실행합니다.

| 리포트 이름 | 실제 완료 조건 |
| --- | --- |
| `chatting-send` | A→B와 B→A를 각각 입력창·전송 버튼으로 보내고 outgoing 서버 에코를 확인 |
| `chatting-receive` | 송신 전에 수신자의 준비·메시지 부재를 확인하고, 재진입 없이 양방향 incoming 말풍선을 확인 |
| `chatting-list-refresh` | 양방향 실시간 수신 뒤 A의 M2 미리보기와 B가 방을 비운 동안 받은 M3 미리보기를 정확한 방·상대 행에서 확인 |
| `chatting-history` | B의 기존 M1 확인→일반 뒤로가기→실제 소켓 종료→A의 UI M3 전송→B 목록 M3 확인→같은 방 재진입 후 M3와 M1 확인 |

`chatting-open`, `chatting-ready`, `chatting-away`는 드라이버의 단계 플로우입니다.
네 시나리오는 같은 실제 대화를 공유하므로 YAML 네 개를 따로 실행하거나 디렉터리 전체를
직접 `maestro test`에 넘기는 것은 이 프로토콜의 대체가 아닙니다.

## 필수 외부 입력

공통 세션 입력은 A의 `MAESTRO_AUTH_STATE_JSON`, `MAESTRO_MEMBER_ID`,
`MAESTRO_FIXTURE_START_DATE`입니다. 채팅은 아래 입력도 **모두** 요구합니다.

- `MAESTRO_PEER_AUTH_STATE_JSON`: B가 독립적으로 내보낸 실제 AppAuth 세션 JSON.
- `MAESTRO_PEER_MEMBER_ID`: B의 실제 등록 회원 ID.
- `MAESTRO_PEER_FIXTURE_START_DATE`: B 전용 계정의 fixture 범위 시작일(`YYYY-MM-DD`).
- `MAESTRO_CHAT_ROOM_ID`: A·B가 함께 속한 기존 DEV 전용 방의 양수 Long ID.

미설정·빈 입력은 preflight 실패입니다. A의 세션이나 회원 ID를 B에 재사용하지 않습니다.
같은 authState 또는 같은 회원 ID도 실패합니다. 각 기기의 기존 bootstrap이 실제 discovery,
토큰 갱신, registration-status 및 예상 회원 ID 검증을 수행합니다. JSON 모양만 검사해서
인증 성공으로 취급하거나 OAuth 콜백 상태를 만들지 않습니다.

`MaestroChattingFixture`는 각 계정의 실제 채팅 목록에서 동일한 방 ID와 반대 계정의 회원 ID를
확인합니다. 실제 이력 조회의 `opponentActive=true`, `blockActive=false`도 요구합니다.
닉네임은 이 응답에서 얻습니다. 임의 공개 회원·동명이인·임의 위치로 방을 대신 만들지 않습니다.
외부에서는 두 계정과 이 방을 다른 실행이나 수동 대화에 쓰지 않아야 합니다.

환경 메타데이터와 최신 CI preflight는 필수 입력이 모두 등록되었음을 확인했습니다.
실제 `run39001687162`에서는 smoke와 양쪽 actor의 인증·방/상대 준비를 통과했습니다.
이 결과가 저장된 refresh token의 영구 유효성이나 회전 후 재사용을 보장하지는 않습니다.
준비 단계 성공만으로 실제 두 기기의 채팅 성공을 주장할 수 없습니다.

## 기기와 실행 계약

컨트롤러의 승인된 CI는 공통 러너를 다음 형태로 호출합니다. 이 문서는 실행 결과가 아닙니다.

```bash
python3 .maestro/scripts/run-tests.py --suite chatting \
  --device emulator-5554 \
  --apk app/build/outputs/apk/e2e/app-e2e.apk \
  --configure-emulator
```

기존 두 번째 장치가 있으면 `--peer-device <B의 ADB serial>`을 추가합니다.
A와 B serial은 달라야 합니다. 없으면 드라이버는 A의 `emu avd name`으로 동일한 설치 AVD를
찾아 `emulator -avd <name> -read-only -no-snapshot -port <빈 짝수 포트>`로 두 번째 인스턴스를
띄웁니다. 콘솔/ADB 포트 쌍을 확인하고 실제 `sys.boot_completed=1`까지 폴링합니다.
기존 AVD 이미지는 수정하지 않습니다. 종료 시 read-only 인스턴스의 임시 변경은 폐기됩니다.
CI의 A와 자동 생성 B는 `-read-only`·`-gpu swiftshader`를 사용합니다.
자동 시작은 에뮬레이터 A와 SDK의 `emulator` 도구를 요구합니다. 실기기 A라면
`--peer-device`로 독립 장치를 지정합니다.

자동 시작 중 프로세스가 종료되면 `chat-peer-emulator-exited`, 240초 안에 부팅되지 않으면
`chat-peer-emulator-boot-timeout`으로 실패합니다. 시작 예외에서는 기존 SIGTERM/SIGKILL·wait
정리를 유지합니다. stdout/stderr는 `DEVNULL`로 버립니다. 정상 부팅한 B는 계속 실행하며,
teardown의 복구용 B 보존 판단은 바꾸지 않습니다.

드라이버는 `--apk`를 필수로 받아 **같은 정확한 E2E APK를 양쪽에 설치**합니다.
공통 `Device.install`의 실제 APK ARM 라이브러리/유효 ABI 검사를 양쪽에서 유지합니다.
양쪽 모두 `ko-KR`·`Asia/Seoul`, E2E setup component, `run-as`가 필요합니다.
자동 생성 B는 locale/timezone 설정 후 재부팅하고 다시 검증합니다.
DEV 인증·REST·WebSocket 서비스 접근도 필요합니다. 연결 실패는 실제 단계 실패로 남습니다.

에뮬레이터 옵션 근거: [동일 AVD의 여러 read-only 인스턴스](https://developer.android.com/studio/releases/emulator#28-0-16),
[콘솔/ADB 포트·스냅샷 옵션](https://developer.android.com/studio/run/emulator-commandline).

## 실제 준비 상태와 단계 순서

1. 드라이버는 A와 B를 각각 clear/authenticate/feature prepare하고 검증된 방·상대 값을 받습니다.
   이 준비를 마친 후에는 마지막 teardown 전까지 bootstrap을 상태 조회용으로 호출하지 않습니다.
2. B가 정확한 방에 먼저 들어갑니다. 최초 REST 이력과 누락 이력이 모두 성공하고 해당 방의
   실제 WebSocket `onOpen` 상태여야 접미사 없는 `chat-ready:<room>:peer:<member>`가 노출됩니다.
   입력창 활성이나 임의 대기 시간만으로 준비를 대신하지 않습니다.
3. B에서 M1 부재를 확인한 뒤 A가 UI로 M1을 보냅니다. B는 같은 화면·ViewModel·소켓을 유지한 채
   incoming M1을 확인하고 바로 M2를 답장합니다. A도 M2 부재를 송신 전에 확인하고 재진입 없이
   incoming M2를 받습니다. A가 방을 나간 뒤 목록의 M2 미리보기를 확인합니다.
4. 이력 단계에서 B는 M1을 먼저 확인하고 일반 뒤로가기를 사용합니다. 목록 표시·입력창 부재·
   실제 `onClosed`/`onFailure` 종료 확인이 끝나야 A의 M3 전송을 허용합니다. B는 밖에 남아 같은
   행의 M3 미리보기를 확인한 뒤에만 재진입합니다.
5. B는 최신 incoming M3와 앞서 받은 incoming M1을 모두 확인합니다. M1이 화면 밖이면
   메시지 목록을 위로 스크롤합니다. 전송은 REST API로 대체하지 않습니다.

소켓 상태는 실제 콜백에서 나옵니다. 방을 나간 동안 늦게 도착한 티켓 응답은 새 소켓을 열지
않으며, 이전 소켓의 콜백이 새 연결을 준비/종료 상태로 바꾸지 못하게 세대·소켓 객체를 확인합니다.
서버 종료 확인이 오지 않으면 away 단계가 timeout으로 실패합니다. 가짜 마커·`sleep 3`·존재하지
않는 selector의 optional 대기는 사용하지 않습니다.

최초 REST 이력의 마지막 ID만 누락 이력 조회 커서로 사용합니다. 먼저 받은 소켓 메시지가
더 최신이어도 커서를 건너뛰지 않으며, 후속 이력은 소켓 목록과 ID 중복을 제거해 ID 순서로
병합합니다. 상대 프로필에서 5초 유예 안에 돌아와 방 뷰가 다시 만들어지면, 기존 소켓을
재연결하지 않고 새로 로드한 이력의 누락 동기화를 끝낸 뒤 준비 상태가 됩니다.

## 정확한 selector와 자료

- 방 행: `chat-room:<실제 roomId>:peer:<실제 상대 memberId>`.
- 방 준비: `chat-ready:<동일 roomId>:peer:<동일 상대 memberId>`와 정확히 일치하며 진단 접미사는 없습니다.
  로딩 중에만 `chat-loading:<동일 roomId>:peer:<동일 상대 memberId>:<진단 상태>`로
  `historyLoaded`·`historySynced`·`socketRoom`·`socket`·`chatState`를 노출합니다.
- 방 밖 실제 종료: `chat-disconnected:<동일 roomId>`.
- outgoing/incoming은 서로 다른 `outgoing_message_tv` / `incoming_message_tv`입니다.
- 목록은 정확한 방 부모의 `last_message_tv`와 상대 닉네임 레이블을 함께 확인합니다.
  공간 기준 `below`나 닉네임 부분 일치는 쓰지 않습니다.
- 외부 닉네임/ID/메시지는 Java regex literal로 인용합니다. 닉네임의 정규식 특수문자도
  다른 상대를 선택하지 않습니다. M1·M2·M3는 같은 실행의 숫자 식별자를 공유하는 짧은 `1-<숫자>`·`2-<숫자>`·`3-<숫자>`입니다.

위의 방·준비·소켓 machine 레이블과 빈 목록 확인용 수집은 채팅 모듈 `BuildConfig.DEBUG`
안에서만 실행합니다. 라이브러리 convention은 BuildConfig를 생성하고, 앱 `e2e` 변형은
`debug` 라이브러리 fallback을 사용합니다. release는 이 수집 코루틴과 selector 전용 목록
callback을 시작하지 않으며, 기존 닉네임·미리보기·빈 화면 문구의 사람용 접근성을 유지합니다.
준비 상태의 결합 flow도 구독할 때만 실행합니다.

자료는 공통 artifact 디렉터리의 `chatting/<단계>/<a|b>/<flow>/`에 분리됩니다.
공통 Redactor에는 **파싱한 양쪽 credentials와 방 ID**를 넣습니다. 원본 logcat,
bootstrap 요청/세션 파일, 토큰, socket ticket URL은 업로드하지 않습니다.
실패 시 `last-anr-summary.json`은 마지막 시스템 ANR의 프로세스·원인 **분류**와 숫자 CPU/load만
기록합니다. Android 14의 들여쓴 `Reason:`도 허용하며 process·load·CPU 정보가 없으면
`unknown`/`null`로 남깁니다. 별도 `anr-events-summary.json`은 events 버퍼의 `am_anr` 태그를
한 번 조회해 기기 시각 `timestamp_epoch`(Unix 초)와 고정 process/reason 분류를 시각 기준
최신 8건까지 기록합니다. 모든 이벤트 상태에 `scope: device-history`를 명시하며 현재 플로우의
사건으로 분류하지 않습니다. `status`는 `captured`(파싱한 이벤트), `partial`(인식 불가 행도
있는 불완전한 결과), `empty`(빈 버퍼/헤더만), `unrecognized`(인식 불가 내용),
`unavailable`(조회 실패)입니다. `unknown` 분류·빈 결과·파일 부재는 ANR 부재의 증거가 아닙니다.
두 ANR 요약은 원본 로그/dumpsys·사용자 데이터·패키지·PID·사용자 ID·원인 원문·창 제목·
Intent·URL·토큰·예외 본문을 공개하지 않습니다. 읽기·파싱·쓰기 실패는 원래 UI 실패나
fixture 정리를 대체하지 않습니다.

A의 별도 checkpoint는 `chatting/diagnostics/<phase>/`에 `checkpoint.json`과 두 ANR 요약을
기록합니다. phase는 B 사용 가능 직후·A 설정 전 `peer-ready-before-primary-prepare`,
A prepare·identity 검증 후·B 설정 전 `primary-fixture-ready`, B receiver-ready barrier 후·A
최초 open 직전 `receiver-ready-before-primary-launch`, A가 존재하는 실패·중단의 정리 전
`failure` 네 고정 값입니다. 각 진단 조회는 5초로 제한합니다. checkpoint는 호스트 캡처
시작/끝 epoch·monotonic과 얻을 수 있는 기기 wall epoch·uptime을 기록합니다.
checkpoint의 마지막 ANR도 `scope: device-history`와 위 상태로 빈 보고서·일부 정보·조회 불가를
구분하고 기기 clock은 `captured`/`partial`/`unrecognized`/`unavailable`을 사용합니다.

`chatting/diagnostics/host-resources.log`는 B 생성/부팅 또는 명시적 peer 처리 전부터
A 최초 open의 성공/실패 결과까지 호스트를 1초 간격으로 수집하는 JSONL입니다. 앞선 단계의
실패·중단 시에도 sampler를 fixture 정리 전에 중단합니다. Linux CPU capacity·구간 busy/iowait,
memory/swap·load·지원되는 PSI의 허용 숫자와 source 상태만 보관합니다. 첫 CPU 구간과
counter reset은 미측정이며 최대 5400 samples·출력 크기 제한, 수집 종료·잘림·coverage gap을
표시합니다. 프로세스 이름·인수·환경 변수나 원본 `/proc` 텍스트는 보관하지 않습니다.
파일 또는 종료 기록이 없으면 수집 완료나 정상 부하로 해석하지 않습니다. 기존 JSON/log
artifact glob이 이 자료도 업로드하며 별도 workflow 설정은 없습니다.

마지막 ANR과 이벤트는 과거 기록이며 재부팅으로 지워질 수 있습니다. 이벤트 시각과 실패
hierarchy·로그 시점을 대조하되 호스트/기기 wall clock의 차이·변경과 캡처 창을 고려합니다.
캡처 시각을 ANR 시작으로 보거나 fixture-ready를 UI 건강 확인으로 보지 않습니다.
호스트 주기 측정은 연속 측정이 아니며 adb 진단 자체도 실행 부하·순서에 영향을 줄 수 있습니다.
깨끗한 checkpoint나 sample 사이 간격만으로 순간 contention 또는 미측정 기기 하위 시스템의
문제를 배제할 수 없습니다. 최근 이벤트만으로 현재 실패와의 인과관계를 확정하지 않습니다.
이 진단 변경은 ANR 수정이나 근본 원인 확정이 아닙니다.
상위 report는 네 시나리오를 모두 기재합니다. 예를 들어 수신 실패는
`chat-a-to-b-live-receive-failed`이고, 이후 미완료 시나리오는 `chat-scenario-not-completed`로
실패합니다. smoke나 선행 단계 성공을 채팅 전체 성공으로 계산하지 않습니다.

## 보존과 정리

기존 전용 방과 메시지는 이 실행이 새로 소유한 데이터가 아닙니다. fixture cleanup은 같은
room/peer 멤버십만 재확인하며 방 탈퇴·메시지 삭제·차단 변경을 하지 않습니다. 새 run 태그
메시지는 방의 이력으로 보존됩니다. 다른 방이나 다른 사용자의 데이터는 건드리지 않습니다.
양쪽 cleanup에는 prepare와 동일한 feature/flow/runId/parameters를 보냅니다.
cleanup 실패 시 해당 기기의 private recovery marker를 지우거나 `pm clear`하지 않습니다.
자동 생성 B의 정리에 실패하면 드라이버는 해당 에뮬레이터도 강제 종료하지 않고 복구용으로
남기며 실패 코드를 기록합니다. CI 호스트가 종료되면 이 임시 인스턴스까지 영구 보존된다고
보장하지 않습니다.

## 검증 범위

upstream `ad0d097b`의 일반 [CI run `39016562465`](https://github.com/projects200/android/actions/runs/39016562465)은 통과했습니다.
그러나 아래 두 실제 Maestro 실행은 실패했습니다. 일반 CI나 준비 단계 성공은 채팅 성공의 증거가 아닙니다.

| 실제 실행 | 관측한 실패 |
| --- | --- |
| `d33810cc` / [run `39010694372`](https://github.com/projects200/android/actions/runs/39010694372) | 양쪽 방 진입 뒤 `a-to-b-send`의 정확한 outgoing 대기 실패. 실패 hierarchy에는 한국어 Gboard의 `비읍` 키와 ASCII 메시지가 한글 자모로 바뀐 outgoing 말풍선이 있습니다. |
| `ad0d097b` / [run `39016562471`](https://github.com/projects200/android/actions/runs/39016562471) | B의 방 진입·준비를 통과한 뒤 A의 `launch-logged-in.yaml`이 `matching_nav_graph`를 60초 안에 찾지 못했습니다. 실패 hierarchy에는 **System UI 응답 없음** 팝업과 시스템 상태 표시줄만 있고 앱 노드는 없습니다. `device-log.log`는 AndroidRuntime 한 줄뿐입니다. |

최신 자료는 System UI ANR가 화면을 막았음을 보여주지만 ANR를 유발한 원인·시점이나
A의 실제 인증/진입 상태를 확정하지 못합니다. MainActivity는 업데이트 확인·인증 진입이
끝날 때까지 splash를 유지하고 `Content`에서 navigation을 만듭니다. 현재 hierarchy만으로
로그인 손실이나 member ID 문제로 단정하지 않으며 인증·계정·앱 진입 코드는 변경하지 않았습니다.
이전 실행의 outgoing 말풍선도 송신자 식별 자체가 동작했음을 보여줍니다.

이 변경은 기존 `6d8aa4a1` 수정과 `ad0d097b`의 변경을 함께 반영합니다.
숫자 메시지·입력창 정확한 값 assertion·B가 나가지 않고 즉시 답장하는 upstream 순서를
유지하고, 기존 `feed-ascii-keyboard.yaml` 호출 뒤 입력하도록 했습니다. 제거됐던 정확한
outgoing 서버 에코 대기(기존 60초)를 복구했습니다. 양방향 incoming·A의 M2 목록·B의 M3
목록·재진입 뒤 기존 M1/새 M3 확인은 유지합니다.

최신 ANR의 원인은 아직 확정할 수 없어 재시도·추가 UI 대기·ANR 팝업 닫기 대신 진단만
추가했습니다. 기존 실패 경로의 30초 제한 `dumpsys activity lastanr` 요약과 실패 logcat
격리를 보존하고 events 요약·네 checkpoint·호스트 주기 수집을 추가합니다. 원문은 파일로
쓰지 않습니다. 채팅 드라이버는 진단의 운영 오류를 UI 결과와 격리하며 `RunInterrupted`와
`KeyboardInterrupt`의 중단 의미와 기존 fixture 복구를 보존합니다. 회귀 테스트는 분류·민감
원문 배제·미지원/부분 수집·읽기/쓰기 실패·중단과 준비/최초 open 실패의 정리 순서를 다룹니다.
기존 actor/메시지·각 송수신·목록·이력의 acceptance는 바꾸지 않습니다.

**이 변경에 대한 로컬 코드·설치·빌드·테스트·lint/formatter·adb·emulator·Maestro 실행은
없습니다. 아래 원격 검증이 끝나기 전에는 전체 CI·채팅 성공을 주장하지 않습니다.**
정적 확인 범위는 실패 hierarchy/로그, bootstrap 인증→앱 진입→방 준비 경로, 전체 Python
교환 순서와 실패 계약, 송신 helper/입력/outgoing 및 기존 incoming·목록·이력 selector입니다.
컨트롤러는 승인된 원격 CI의 Python 회귀 테스트·APK 빌드 결과와 실제 두 계정·기기의 네
시나리오를 확인해야 합니다. ANR가 재발하면 같은 단계 hierarchy, 네 checkpoint와 이벤트
시각, 호스트 주기 자료를 대조합니다. 진단 PR의 CI도 기존 ANR로 실패할 수 있습니다.
새 비밀값이나 대체 계정은 필요하다고 확인되지 않았으며, 정상 응답하는
두 기기와 기존 DEV 인증·REST·WebSocket 접근이 실제 검증의 외부 전제입니다.
