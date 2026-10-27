# 타이머 Maestro CI

공통 인증 러너가 `.maestro/suites/timer.json`의 실제 다섯 플로우를 아래 순서대로 실행합니다.
`--suite timer`는 이 다섯 개만 선택합니다. `--suite ci`는 smoke와 현재 브랜치의 feature descriptor를 실행하고 로그인 화면을 마지막에 확인합니다. `--suite all`은 기존 운동 회귀 플로우도 포함합니다. `--suite regression`은 기존 운동 두 플로우이며 타이머 실행 명령이 아닙니다.

|플로우|필수 확인|
|---|---|
|`timer-simple-preset-crud`|00:41 생성, 화면 재진입, 00:42 수정, 데이터 유지 재실행, 생성한 프리셋 삭제|
|`timer-simple-run`|00:45 프리셋 실행, 남은 시간 감소, 2초 이상 일시정지 관찰, 재개 후 감소, 실제 자연 종료의 00:00, 생성한 프리셋 삭제|
|`timer-custom-crud`|실행별 제목과 5초 스텝 두 개 생성, 재진입, 제목·이름·시간 수정, 실제 핸들 드래그, 제출 직후와 재진입 뒤 위치별 이름·시간 유지, 생성한 타이머 삭제|
|`timer-custom-run`|30초 스텝 두 개의 진행·일시정지·재개, 활성 StepA→StepB, 반복 중 StepB→StepA, 명시 종료, 반복 없는 별도 실행의 자연 종료, 생성한 타이머 삭제|
|`timer-form-validation`|0초 프리셋 거절, 빈 제목 거절, 3초 스텝 거절과 해당 footer의 01:00 유지, 빈 스텝 이름 거절, 폼 유지와 미저장|

## 실행 전제

- 공통 러너/워크플로가 통합된 브랜치의 실제 e2e APK, Maestro 2.11.0, 한국어·Asia/Seoul 전용 에뮬레이터를 사용합니다. descriptor의 최소 API는 30입니다.
- 기존 인증 입력 `MAESTRO_AUTH_STATE_JSON`, `MAESTRO_MEMBER_ID`, `MAESTRO_FIXTURE_START_DATE` 세 개가 모두 필요합니다. 새 타이머 인증 값, 두 번째 계정, 미디어 입력은 없습니다. 실제 DEV 인증과 서버 member 검증은 공통 bootstrap이 수행합니다.
- `E2E_RUN_ID`는 공통 러너가 플로우마다 생성해서 전달하는 비밀이 아닌 ASCII UUID hex 표식입니다. 커스텀 생성·수정·삭제는 같은 표식을 유지합니다.
- 서버 심플 프리셋은 6개 상한 아래에 빈 자리가 하나 이상 있어야 합니다. 빈 자리가 없는 계정은 지원하지 않으며 기존 프리셋을 삭제해서 자리를 만들지 않습니다. 다른 서버 값이 41·42·45초여도 아래의 로컬 전용 선택자로 구분합니다.

인증 환경과 설치 전제를 갖춘 운영자가 사용할 명령입니다. 이 구현 작업에서 실행한 검증 명령은 아닙니다.

```bash
python3 .maestro/scripts/run-tests.py --suite timer --device emulator-5554 \
  --apk app/build/outputs/apk/e2e/app-e2e.apk
```

기본 시나리오 입력은 `features/timer.py`의 immutable `SimpleTimerInput`에서 한 번 선택합니다. `--timer-initial-seconds 53 --timer-updated-seconds 97`을 붙이면 CRUD의 생성·수정 입력과 독립 서버 기대값이 모두 53·97초로 바뀝니다. 실행 플로우에는 initial만 적용하며 updated는 CRUD 전용입니다. 입력 범위는 1~3599초이고, 긴 실행에는 자연 종료를 포함할 만큼 `--flow-timeout`도 늘려야 합니다.
CRUD의 생성·수정 시간은 서로 달라야 합니다. 호스트는 생략한 값에 기본값 41·42초를 적용한 뒤 같은 시간이면 장치 준비 전에 거절합니다. `timer-simple-run`은 실행 시간 하나를 검증하므로 initial과 updated를 같은 값으로 유지합니다.

prepare/observe/cleanup은 동일한 문자열 parameters `initialSeconds`, `updatedSeconds`를 사용합니다. Kotlin은 숫자형 JSON, 선행 0, 범위 밖 값과 알 수 없는 키를 거절합니다. prepare가 `TIMER_INITIAL_*`, `TIMER_UPDATED_*`의 `SECONDS`(총초), `MINUTES_PART`, `SECONDS_PART`, `TIME`(`mm:ss`)을 반환합니다. private snapshot의 사전 입력과 관찰·정리 요청이 다르면 실패합니다. 생성 관찰은 실제 localId만 얻으며 생성 시간도 사전 입력과 대조합니다. 서버 관찰은 앱에서 읽은 시간을 기대값으로 다시 사용하지 않습니다.

## 격리와 소유권

`fixture:true`, `driver:"timer"`는 타이머 전용 Room 관찰과 서버 소유권 정리를 사용한다는 뜻입니다. 심플·커스텀 생성은 각각 생성 단계와 검증 단계를 나눕니다. 심플은 baseline과 현재 Room 행의 차이로 `TIMER_LOCAL_ID`를 확정합니다. 커스텀은 prepare가 만든 `TIMER_CUSTOM_TITLE`과 사전 localId 집합으로 새 소유 행 하나만 확인하고 `TIMER_CUSTOM_LOCAL_ID`를 반환합니다. 중복·누락·관찰 뒤 ID 교체는 실패하며 임의 첫 행을 선택하지 않습니다.

서버에 올라간 타이머는 실행 state에 기록된 `serverId`만 정리합니다. baseline 서버 ID와 일치하거나 확인되지 않은 추가 행은 삭제하지 않습니다. 정리 실패 시 recovery marker와 실행별 state를 남겨 다음 실행을 중단하고 같은 소유권으로 재시도합니다.

심플 프리셋과 메뉴의 선택자는 각각 `simple_timer_preset_<localId>`와 `simple_timer_menu_<localId>`입니다. `localId`는 서버 ID 연결과 시간 수정 뒤에도 유지됩니다. 시간 기반 선택자는 폐기했습니다.

커스텀 카드의 선택자는 항상 `custom_timer_<localId>`입니다. 제목 수정·서버 ID 배정·재실행으로 선택자가 바뀌지 않습니다. prepare의 `TIMER_CUSTOM_TITLE`은 `e2e611c-<E2E_RUN_ID>` / `e2e611r-<E2E_RUN_ID>`이며 CRUD 수정은 같은 표식에 `-ed`를 붙입니다. 검증 플로우의 `e2e611v-<E2E_RUN_ID>`는 저장하지 않습니다. ID는 행 선택에만 쓰며 제목·이름·시간·순서 단언은 그대로 유지합니다. 로컬과 CI는 서로 다른 전용 계정 또는 겹치지 않는 실행 시간을 사용해야 합니다.

## 텍스트·시간 입력과 실행 상태

`timer-ascii-keyboard.yaml`은 포커스된 Gboard의 실제 자판을 확인합니다. 첫 키가 `비읍`이면 관찰된 `다음 언어` 키를 한 번 누르고, 같은 위치의 `q/Q`를 확인한 뒤 ASCII 제목·이름을 입력합니다. secure subtype 설정값만으로 영문 입력을 보장하지 않으며, 입력 직후 필드의 정확한 문자열도 검사합니다. 다른 자판을 추측해서 넘기거나 `pasteText`로 우회하지 않습니다.

`timer-clear-text.yaml`은 기존 제목·이름을 길게 눌러 선택하고, `모두 선택` 메뉴가 있으면 누른 뒤 삭제합니다. 한 단어가 이미 전체 선택된 경우에는 해당 메뉴가 없습니다. 가운데 커서 앞만 지워 기존 문자열의 뒷부분이 남는 문제를 피하며, 교체 후 정확한 값과 빈 이름의 저장 거부는 기존 UI 검증으로 확인합니다.

`subflows/timer-set-time.yaml`은 실제 `minute_et`와 `second_et` ID를 `timer-clear-text.yaml`의 `FIELD_ID`로 전달해 전체 값을 교체합니다. 좌표 오프셋과 고정 두 번 삭제는 사용하지 않습니다. 다이얼로그는 고정 100dp 대신 divider 양옆의 남은 폭을 나누므로 두 입력 영역이 겹치지 않습니다. 두 자리 요청값을 각각 확인한 뒤에만 확인 버튼을 누릅니다. 제품의 기본값·숫자 길이·0~59 범위와 기존 포커스 선택 동작은 바꾸지 않습니다.

`timer-hide-keyboard.yaml`은 실제 CI의 Gboard 리소스가 보일 때만 키보드를 내립니다. 같은 버전의 Android `hideKeyboard`는 키보드가 없어도 Back을 보내므로 무조건 호출하면 폼이나 다이얼로그가 닫힐 수 있습니다. 커스텀 CRUD·실행은 키보드를 내리고 폼의 스텝 시간을 확인한 뒤 `custom_timer_form_submit_btn`을 누릅니다. 생성·수정 뒤에는 폼 필드가 사라지고 기존 상세 선택자가 나타나야 통과합니다. 빈 제목·빈 이름 검증도 같은 제출 버튼으로 검사하며 IME의 ‘완료’를 제출로 취급하지 않습니다.

진행·재개는 현재 시간 텍스트가 바뀔 때까지 필수 대기한 뒤 양수 감소를 비교합니다. 일시정지는 최소 2초 관찰 구간 동안 실제 시간·정지 상태를 반복 단언합니다. 존재하지 않는 선택자의 선택적 타임아웃을 sleep으로 사용하지 않습니다.

`custom_timer_active_step_<order>`는 실제 강조 중인 `currentStepIndex`를 반영합니다. StepA/StepB 이름의 단순 노출이나 남은 시간 증가만으로 전환을 통과시키지 않습니다. 반복을 시작 전에 켠 뒤 StepB→StepA 동안 실행 상태와 반복 상태를 확인합니다. 반복 없는 자연 종료는 별도 실행에서 실행 상태와 StepB 도달을 먼저 확인한 후 정지·첫 스텝·초기 시간으로 판정합니다. Fragment가 `isTimerFinished`를 받으면 즉시 reset하므로 순간적인 finished 플래그의 태그를 기다리지 않습니다. Maestro의 대기 제한은 마지막 UI 조작부터 누적되므로 첫 전환은 한 스텝+15초, 반복 복귀·자연 종료는 두 스텝+15초로 계산합니다.

커스텀 두 스텝은 같은 30초입니다. 이 suite는 서로 다른 길이의 단계에 대한 서비스 구현의 정확성을 입증하지 않습니다. 드래그는 실제 `draggableHandle`에서 시작하는 swipe이며 순서 단언을 생략하지 않습니다.

실제 목록 Column은 `timer_list_scroll`, 심플 카드는 `simple_timer_card`를 노출합니다. 오프라인 부재 검사는 이 스크롤 중앙에서 UP으로 천천히 이동하며 매 구간과 실제 끝의 추가 카드를 검사합니다. directional swipe의 끝은 화면 10%이므로 이동량은 대략 반 viewport 이하가 되어 관찰 영역을 겹치며, 마지막 필수 단언 없이 반복 한도 소진을 성공으로 취급하지 않습니다. 화면 크기별 실제 clipping·fling 여부는 로컬·CI에서 확인해야 합니다.

## 검증 상태

`441155ae`의 [Maestro CI](https://github.com/projects200/android/actions/runs/38882786646)에서 기본 smoke 두 개와 심플 CRUD·실행·입력 유효성 검사가 통과했습니다. 영문 제목·단계명 입력과 커스텀 생성도 통과했지만, CRUD 제목 교체에 기존 문자열이 남았고 반복 복귀 대기는 두 번째 단계가 끝나기 전에 종료됐습니다. 같은 커밋의 [일반 CI](https://github.com/projects200/android/actions/runs/38882786596)는 성공했습니다. 전체 선택·누적 대기 수정 후 다섯 플로우 전체 통과는 확인 필요입니다. 기존 서버 데이터를 삭제하거나 플로우를 생략해 통과시키지 않습니다. 로컬에서는 빌드·테스트·Maestro를 실행하지 않았습니다.

이번 이식성 변경의 테스트는 작성만 했습니다. 운영자는 Python driver tests와 `:app:testE2eUnitTest`, 타이머 모듈 instrumentation의 `TimerPortabilityTest`를 실행해야 합니다. instrumentation은 320dp/600dp의 실제 XML 입력 bounds, 중복 제목에서의 ID 선택, rename/serverId 변경과 긴 목록의 실제 scroll anchors를 검사합니다. 로컬·CI Maestro에서는 전체 교체·키보드·재실행·동기화 뒤 ID 유지와 중간 구간의 삭제 잔존 실패도 확인해야 합니다. 실제 API/Room/UI E2E는 유지하며 단위 테스트 fake를 E2E 성공으로 취급하지 않습니다.
