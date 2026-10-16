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

## 격리와 소유권

`fixture:false`, `requiredEnvironment:[]`, `driver:null`은 타이머 전용 서버 fixture나 Python hook이 없다는 뜻입니다. 인증 생략을 뜻하지 않습니다. 공통 러너는 **각 플로우마다** force-stop/`pm clear` → 실제 세션 prepare → 실제 UI 실행 → cleanup → 최종 clear를 수행합니다. 개인 설치가 아닌 전용 기기만 사용하세요.

새 UI 타이머는 현재 구현에서 UUID `localId`, `serverId=null`인 로컬 생성 대기 행입니다. 기존 서버 목록 GET은 별도 서버 행을 가져오며, UI 쓰기는 로컬 저장소를 변경합니다. 타이머 업로드 동기화나 타이머 서버 DELETE를 이 suite가 추가하지 않습니다. 최종 clear는 로컬 행·서비스 상태를 없애지만 기존 서버 타이머를 삭제하지 않습니다. 향후 업로드 동기화가 추가되면 이 정리 계약을 다시 검토해야 합니다.

심플 프리셋의 `simple_timer_local_preset_<초>`와 `simple_timer_local_menu_<초>`는 `serverId=null` 행에만 붙습니다. 서버 행은 `localId` 기반의 별도 태그를 사용합니다. 각 격리 플로우는 생성 전 해당 로컬 시간의 부재를 확인하고 프리셋 하나만 만들므로 이 태그가 가리키는 행을 소유합니다. 시간만 같은 서버 행은 실행·수정·삭제 대상이 아닙니다. 서버 목록이 늦게 도착하더라도 같은 시간의 서버 행이 로컬 전용 선택자가 되지 않습니다. 이 방식은 서버 목록이 비어 있다고 추정하는 부재 검사나 서버 행을 고르는 전역 시간 텍스트에 의존하지 않습니다.

커스텀 타이머는 `e2e611c-<E2E_RUN_ID>` / `e2e611r-<E2E_RUN_ID>`로 만들고 `custom_timer_local_<제목>` 카드로 재진입합니다. 같은 제목의 서버 행에는 이 로컬 태그가 없습니다. CRUD 수정 제목은 같은 표식에 `-ed`만 붙입니다. 검증 플로우의 `e2e611v-<E2E_RUN_ID>`는 저장하지 않습니다. 기존 서버 목록이 길면 실제 스크롤로 추가 카드와 소유한 카드를 찾습니다.

심플 CRUD 내부 재실행은 `launch-logged-in.yaml`의 `clearState:false`를 유지합니다. 중간 prepare나 clear를 넣지 않으므로 실제 Room 저장과 인증 유지가 검증 대상입니다.

## 텍스트·시간 입력과 실행 상태

`timer-ascii-keyboard.yaml`은 포커스된 Gboard의 실제 자판을 확인합니다. 첫 키가 `비읍`이면 관찰된 `다음 언어` 키를 한 번 누르고, 같은 위치의 `q/Q`를 확인한 뒤 ASCII 제목·이름을 입력합니다. secure subtype 설정값만으로 영문 입력을 보장하지 않으며, 입력 직후 필드의 정확한 문자열도 검사합니다. 다른 자판을 추측해서 넘기거나 `pasteText`로 우회하지 않습니다.

`timer-clear-text.yaml`은 기존 제목·이름을 길게 눌러 선택하고, `모두 선택` 메뉴가 있으면 누른 뒤 삭제합니다. 한 단어가 이미 전체 선택된 경우에는 해당 메뉴가 없습니다. 가운데 커서 앞만 지워 기존 문자열의 뒷부분이 남는 문제를 피하며, 교체 후 정확한 값과 빈 이름의 저장 거부는 기존 UI 검증으로 확인합니다.

`subflows/timer-set-time.yaml`은 실제 `minute_et`의 `point: "75%, 50%"`와 `second_et`의 `point: "90%, 50%"`(화면이 아닌 선택한 필드 기준)를 누른 뒤 두 자리를 지웁니다. 320dp 너비 화면에서 분·초 필드 겹침이 관찰되어, 분 탭은 초 필드 밖인 75% 지점을 사용합니다. 두 필드는 폭 100dp·중앙 정렬·최대 두 자리이므로 숫자 뒤에서 삭제하며, 포커스 시 `selectAll()`이 탭으로 해제되어도 뒷자리가 남지 않게 합니다. 분·초를 두 자리로 입력한 뒤 각 필드의 요청값을 확인하고 나서만 확인 버튼을 누릅니다. 제품의 60초 기본값과 범위 제한은 바꾸지 않습니다.

Maestro 2.11.0의 [`YamlElementSelector`](https://github.com/mobile-dev-inc/Maestro/blob/c8c361be1cc89b2e742f185466a302a22778be6b/maestro-orchestra/src/main/java/maestro/orchestra/yaml/YamlElementSelector.kt)와 [`Orchestra.tapOnElement`](https://github.com/mobile-dev-inc/Maestro/blob/c8c361be1cc89b2e742f185466a302a22778be6b/maestro-orchestra/src/main/java/maestro/orchestra/Orchestra.kt)가 필드 상대 좌표를 지원합니다. 같은 버전의 [`MaestroDriverService.eraseAllText`](https://github.com/mobile-dev-inc/Maestro/blob/c8c361be1cc89b2e742f185466a302a22778be6b/maestro-android/src/androidTest/java/dev/mobile/maestro/MaestroDriverService.kt)는 선택 필드의 전체 교체가 아니라 Delete 키를 반복합니다.

`timer-hide-keyboard.yaml`은 실제 CI의 Gboard 리소스가 보일 때만 키보드를 내립니다. 같은 버전의 Android `hideKeyboard`는 키보드가 없어도 Back을 보내므로 무조건 호출하면 폼이나 다이얼로그가 닫힐 수 있습니다. 커스텀 CRUD·실행은 키보드를 내리고 폼의 스텝 시간을 확인한 뒤 `custom_timer_form_submit_btn`을 누릅니다. 생성·수정 뒤에는 폼 필드가 사라지고 기존 상세 선택자가 나타나야 통과합니다. 빈 제목·빈 이름 검증도 같은 제출 버튼으로 검사하며 IME의 ‘완료’를 제출로 취급하지 않습니다.

진행·재개는 현재 시간 텍스트가 바뀔 때까지 필수 대기한 뒤 양수 감소를 비교합니다. 일시정지는 최소 2초 관찰 구간 동안 실제 시간·정지 상태를 반복 단언합니다. 존재하지 않는 선택자의 선택적 타임아웃을 sleep으로 사용하지 않습니다.

`custom_timer_active_step_<order>`는 실제 강조 중인 `currentStepIndex`를 반영합니다. StepA/StepB 이름의 단순 노출이나 남은 시간 증가만으로 전환을 통과시키지 않습니다. 반복을 시작 전에 켠 뒤 StepB→StepA 동안 실행 상태와 반복 상태를 확인합니다. 반복 없는 자연 종료는 별도 실행에서 실행 상태와 StepB 도달을 먼저 확인한 후 정지·첫 스텝·초기 시간으로 판정합니다. Fragment가 `isTimerFinished`를 받으면 즉시 reset하므로 순간적인 finished 플래그의 태그를 기다리지 않습니다. Maestro의 대기 제한은 마지막 UI 조작부터 누적되므로 첫 전환은 한 스텝+15초, 반복 복귀·자연 종료는 두 스텝+15초로 계산합니다.

커스텀 두 스텝은 같은 30초입니다. 이 suite는 서로 다른 길이의 단계에 대한 서비스 구현의 정확성을 입증하지 않습니다. 드래그는 실제 `draggableHandle`에서 시작하는 swipe이며 순서 단언을 생략하지 않습니다.

## 검증 상태

`441155ae`의 [Maestro CI](https://github.com/projects200/android/actions/runs/38882786646)에서 기본 smoke 두 개와 심플 CRUD·실행·입력 유효성 검사가 통과했습니다. 영문 제목·단계명 입력과 커스텀 생성도 통과했지만, CRUD 제목 교체에 기존 문자열이 남았고 반복 복귀 대기는 두 번째 단계가 끝나기 전에 종료됐습니다. 같은 커밋의 [일반 CI](https://github.com/projects200/android/actions/runs/38882786596)는 성공했습니다. 전체 선택·누적 대기 수정 후 다섯 플로우 전체 통과는 확인 필요입니다. 기존 서버 데이터를 삭제하거나 플로우를 생략해 통과시키지 않습니다. 로컬에서는 빌드·테스트·Maestro를 실행하지 않았습니다.
