# 채팅 2계정·2기기 실제 CI

`.maestro/suites/chatting.json`은 공통 러너에 다음 네 시나리오를 등록합니다.
`ci`는 이 descriptor가 있는 PR에서 채팅 드라이버를 실행합니다.

| 리포트 이름 | 실제 완료 조건 |
| --- | --- |
| `chatting-send` | A→B와 B→A를 각각 입력창·전송 버튼으로 보내고 outgoing 서버 에코를 확인 |
| `chatting-receive` | 송신 전에 수신자의 준비·메시지 부재를 확인하고, 재진입 없이 양방향 incoming 말풍선을 확인 |
| `chatting-list-refresh` | B의 M1 미리보기를 B의 답장 전에 확인하고, A의 M2 미리보기도 정확한 방·상대 행에서 확인 |
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
   incoming M1을 확인합니다. B의 목록 미리보기를 확인한 다음 B가 UI로 M2를 답장합니다.
   A도 M2 부재를 송신 전에 확인하고 재진입 없이 incoming M2를 받습니다.
4. 이력 단계에서 B는 M1을 먼저 확인하고 키보드를 숨긴 뒤 일반 뒤로가기를 사용합니다.
   목록 표시·입력창 부재·실제 `onClosed`/`onFailure` 종료 확인이 끝나야 A의 M3 전송을 허용합니다.
   B는 밖에 남아 같은 행의 M3 미리보기를 확인한 뒤에만 재진입합니다.
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
  다른 상대를 선택하지 않습니다. M1·M2·M3는 같은 run ID의 짧은 ASCII 고유 문구입니다.

위의 방·준비·소켓 machine 레이블과 빈 목록 확인용 수집은 채팅 모듈 `BuildConfig.DEBUG`
안에서만 실행합니다. 라이브러리 convention은 BuildConfig를 생성하고, 앱 `e2e` 변형은
`debug` 라이브러리 fallback을 사용합니다. release는 이 수집 코루틴과 selector 전용 목록
callback을 시작하지 않으며, 기존 닉네임·미리보기·빈 화면 문구의 사람용 접근성을 유지합니다.
준비 상태의 결합 flow도 구독할 때만 실행합니다.

자료는 공통 artifact 디렉터리의 `chatting/<단계>/<a|b>/<flow>/`에 분리됩니다.
공통 Redactor에는 **파싱한 양쪽 credentials와 방 ID**를 넣습니다. 원본 logcat,
bootstrap 요청/세션 파일, 토큰, socket ticket URL은 업로드하지 않습니다.
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

upstream `d33810cc`의 일반 [CI run `39010694430`](https://github.com/projects200/android/actions/runs/39010694430)은 통과했습니다.
앞선 `01ef57a1`이 들여쓰기·빈 줄 네 오류를 정리했고, `d33810cc`는 준비 완료 마커에서 진단 접미사를 분리했습니다.
두 Kotlin 파일의 기능 코드는 기존 로컬 수정과 같으며, 대조한 diff에는 설명 주석만 남습니다.
같은 head의 [Maestro run `39010694372`](https://github.com/projects200/android/actions/runs/39010694372)은 실패했습니다.
`smoke-tabs`, 양쪽 actor 준비, B의 방 진입·준비 확인, A의 방 진입을 통과한 뒤
`a-to-b-send` 단계에서 실패했습니다. 드라이버는 단계 실패를 즉시 전파하므로 앞의 세 UI 단계가
통과했음을 구분할 수 있습니다. `chatting-send`에는 `chat-a-to-b-send-failed`·
`chat-scenario-not-completed`, 나머지 세 채팅 시나리오에는 `chat-scenario-not-completed`가 남았습니다.
송신 플로우는 준비 재확인·기존 메시지 부재·입력·전송 버튼·outgoing 표시 대기를 포함하므로,
단계 코드만으로 실제 네트워크 송신 실패나 특정 UI 명령 실패를 확정하지 않습니다.
이후 feed/timer/login 11개 플로우는 `not-executed-after-unsafe-cleanup-or-interruption`으로
기록됐으며 실행되지 않았습니다. 공통 `run_suite`는 채팅 드라이버 결과에 오류가 하나라도
있으면 후속 실행을 중단하므로, 이 표기만으로 실제 cleanup 실패나 interruption을 확정할 수는
없습니다. 이 실행의 채팅 결과에는 별도의 fixture cleanup·final clear·peer stop 실패 코드나
`chat-run-interrupted`가 없습니다. 11개를 독립적인 UI 실패로 계산하지 않습니다.
기존 단계별 artifact의 로그·스크린샷 등은 추가 확인이 필요합니다.
**실제 UI 실패 원인은 아직 확인되지 않았으며, 전체 CI나 채팅이 통과한 상태가 아닙니다.**

진단 출력 디렉터리를 먼저 만드는 수정은 upstream `698c2f9e`에 이미 포함돼 있습니다.
이 로컬 변경은 `save_failure_logcat`에서 발생한 운영 오류(`RunnerFailure`·`OSError`)만
격리해 원래 `maestro-flow-failed`를 유지하도록 했습니다. `RunInterrupted`는 그대로
전파하며 `KeyboardInterrupt`도 잡지 않습니다. 다른 진단·정리 경로는 바꾸지 않았습니다.
500줄·30초 logcat 수집 제한, 기존 필터·Redactor, 원본 로그 비공개 방침도 유지했습니다.
실제 파일시스템과 Redactor를 쓰는 기존 회귀 테스트 두 개(첫 중첩 단계의 정제된 진단 보존,
logcat 비정상 종료)를 유지했습니다. 수집 중 `RunnerFailure("tool-timeout")`, 기기 진단
파일만의 `OSError`, `RunInterrupted` 전파를 각각 확인하는 테스트 세 개를 추가했습니다.
앞의 두 경우는 실제 console 파일과 원래 실패 코드를 확인하며, 쓰기 오류의 합성 비공개
문구가 공개되지 않는지도 확인합니다. **운영 오류 격리와 회귀 테스트는 로컬 변경이며 미실행입니다.**

게시된 Kotlin 변경은 준비 마커를 기존 정확한 방·상대 selector로 유지하며 상세 상태는
로딩 중에만 노출합니다. 준비 조건·flow 구독·DEBUG 수집 수명과 기존 Python/주고받기
selector·assertion은 바꾸지 않았습니다. 일반 CI 통과는 게시된 코드의 결과이며,
아직 게시하지 않은 logcat 운영 오류 격리나 실제 B UI 실패 해결을 검증한 결과는 아닙니다.
이 로컬 변경에서는 코드·설치·빌드·테스트·lint/formatter·adb·emulator·Maestro를 실행하지
않았습니다. 커밋·push나 PR 게시도 하지 않았습니다.
기존 ViewModel 회귀 테스트는 REST 100→조기 소켓 102→누락 101의 커서·정렬·중복 제거와
상대 프로필→Back의 유예 내 복귀 후 준비 상태·소켓 재연결 부재를 확인하도록 작성됐습니다.
컨트롤러는 단계별 artifact에서 실제 실패한 UI 명령과 방·상대 식별자, 소켓 상태를 확인해야
합니다. 이후 승인된 CI에서 실제 두 계정/기기의 네 시나리오와 단계별 실패 전파·복구 보존을 검증해야 합니다.
정적 소스와 문서는 실제 CI 통과나 UI 성공의 증거가 아닙니다.
