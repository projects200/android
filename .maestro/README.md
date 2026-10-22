# Maestro UI 테스트

Maestro는 APK 밖에서 실제 화면을 조작하는 블랙박스 테스트입니다. **자동 실행은 `assembleE2e`와 Python 호스트 러너를 사용합니다.** 기존 debug/release 동작을 우회하지 않고 실제 DEV 계정의 AppAuth 세션을 복원·갱신합니다. 일반 debug/release APK에는 준비 Activity가 없습니다.

앱 ID는 모든 빌드에서 `com.project200.undabang`입니다. e2e는 debug 서명과 DEV Cognito/API 설정을 사용합니다. 같은 ID의 release 앱에 서명이 다른 APK를 덮어쓰면 설치가 실패합니다. 러너는 기존 앱을 자동 제거하지 않습니다. 개인 기기보다 전용 에뮬레이터를 권장합니다.

## 도구와 기기 전제

- Python 3.10 이상(표준 라이브러리만 사용), JDK 17, Android SDK의 `adb`, Maestro **2.11.0**.
- 기기의 언어는 한국어(`ko-KR`), 시간대는 `Asia/Seoul`. DEV API/Cognito에 연결할 수 있어야 합니다.
- 에뮬레이터는 RAM 4096MB 이상. CI는 Ubuntu/KVM에서 선택된 descriptor의 최대 `apiLevel`인 `google_apis` `x86_64` 이미지를 사용합니다. 실제 실행 이미지의 API는 해당 CI 설정·결과로 확인하며 matching descriptor의 `apiLevel:30`만 보고 전체 CI가 API30이라고 단정하지 않습니다.
- ARM APK 필터(`armeabi-v7a`, `arm64-v8a`)는 그대로 유지합니다. [공식 ARM translation 안내](https://android-developers.googleblog.com/2020/03/run-arm-apps-on-android-emulator.html)는 Android 11 Google APIs 이미지의 ARM 실행을 설명합니다. 모든 x86 이미지가 이를 지원하는 것은 아닙니다. 러너의 `--apk`는 APK의 실제 ARM 라이브러리와 기기 `ro.product.cpu.abilist`의 교집합을 **설치 전에** 검사하며, 지원 ABI가 없으면 실패합니다.
- `--device`에는 `adb devices`의 serial을 사용합니다. 생략하면 연결된 정상 기기가 정확히 하나여야 합니다.
- `--configure-emulator`는 실제 에뮬레이터인지 확인한 뒤 `adb root`, 한국어/시간대 설정, 재부팅을 수행합니다. 재부팅 후에는 현재 IME의 공개 APK metadata에서 영어(`en-US`) ASCII keyboard subtype을 찾아 선택합니다. **앱/UI 언어는 한국어이고 시간대는 서울인 상태를 유지합니다.** SDK Build Tools의 `aapt2`가 PATH 또는 `ANDROID_HOME`/`ANDROID_SDK_ROOT` 아래 `build-tools/<version>/`에 있어야 합니다. Google APIs처럼 root 가능한 이미지가 필요합니다. 물리 기기는 설정 전에 거절하며, 옵션 없이 실행할 때는 입력기를 포함한 기기 설정을 먼저 맞춰야 합니다.

설치 예시(개발자가 실행할 명령이며 이 변경 작업에서 실행한 검증이 아닙니다):

```bash
export MAESTRO_VERSION=2.11.0
curl -fsSL https://get.maestro.mobile.dev | bash
export PATH="$HOME/.maestro/bin:$PATH"
maestro --version
```

[실제로 게시된 `cli-2.11.0` 릴리스](https://github.com/mobile-dev-inc/maestro/releases/tag/cli-2.11.0)를 고정합니다. CI는 릴리스 ZIP의 SHA-256 `5384593cb4e7a106489e75a821d157dd43f4e438df6bc308b72e82c685e1283a`도 검사합니다. 러너는 다른 CLI 버전을 거절합니다.

### 한국어 UI와 ASCII 입력

고정한 [Maestro AndroidDriver의 `inputText`](https://github.com/mobile-dev-inc/Maestro/blob/c8c361be1cc89b2e742f185466a302a22778be6b/maestro-client/src/main/java/maestro/drivers/AndroidDriver.kt#L546-L558)는 ASCII를 [Android keycode](https://github.com/mobile-dev-inc/Maestro/blob/c8c361be1cc89b2e742f185466a302a22778be6b/maestro-android/src/androidTest/java/dev/mobile/maestro/MaestroDriverService.kt#L517-L576)로 보냅니다. 한국어 Gboard에서는 이 키가 한글 자모로 바뀔 수 있습니다. 같은 버전의 `pasteText`도 `inputText`로 돌아가므로 대안이 아닙니다.

러너는 [AAPT2의 공개 `dump xmltree`/`dump resources`](https://developer.android.com/tools/aapt2#dump)로 **현재 설치된** IME service의 `android.view.im` XML을 찾습니다. 그 XML에 선언된 `en-US`/`en_US`, `keyboard`, `isAsciiCapable=true`인 비보조 subtype 하나를 선택합니다. 명시적 ID는 그대로 사용하고, ID가 없거나 0이면 [Android `InputMethodSubtype`의 계산 규칙](https://github.com/aosp-mirror/platform_frameworks_base/blob/android-13.0.0_r83/core/java/android/view/inputmethod/InputMethodSubtype.java#L293-L310)에 따라 원래 locale·mode·extra 문자열과 boolean 기본값을 Java UTF-16/signed 32-bit hash로 계산합니다. `languageTag`를 locale 대신 hash에 넣거나 해석하지 못한 resource 참조를 문자열로 hash하지 않습니다. ID·APK 내부 파일명·Gboard private 설정을 추측하지 않습니다. Android의 [`selected_input_method_subtype` 설정과 observer](https://github.com/aosp-mirror/platform_frameworks_base/blob/android-11.0.0_r48/services/core/java/com/android/server/inputmethod/InputMethodManagerService.java#L1111-L1118)를 사용하며 현재 IME의 enabled subtype도 영어 하나로 제한합니다. 이 제한은 Maestro의 비ASCII 입력 후 `ime set` 복원에서도 한국어 자동 선택으로 돌아가지 않게 합니다. 다른 enabled IME는 보존합니다.

XML config가 기본값 또는 `v<버전>`뿐이면 기기 SDK 이하에서 가장 높은 버전을 선택합니다(기본값은 0). 로케일 등 다른 qualifier가 하나라도 있으면 config를 추측해 고르지 않고 모든 XML의 유일한 영어 ASCII subtype ID가 같을 때만 진행하며, 중복 config·적용 가능한 버전 없음·variant 간 ID 불일치는 거절합니다.

`aapt2`가 없으면 `emulator-ascii-input-requires-aapt2`, 단일 APK/공개 metadata를 해석할 수 없으면 `emulator-ime-metadata-unavailable`, 유일한 영어 ASCII subtype의 ID를 확인할 수 없으면 `emulator-ascii-subtype-unavailable`로 setup을 중단합니다. 설정 readback이 다르면 `emulator-ascii-input-not-configured`로 실패합니다. 실제 UI 입력의 정확성은 기존 플로우의 값 assertion으로 계속 확인하며 이 설정만으로 E2E 통과를 주장하지 않습니다.

## DEV 빌드 준비

기존 `local.properties`에 DEV Cognito 설정과 Kakao 키가 있어야 합니다. `google-services.json`은 현재 패키지 이름으로 등록한 **DEV Firebase** 파일을 `app/src/e2e/google-services.json`에 둡니다. 기존 debug 파일이 같은 DEV 설정이면 복사할 수 있습니다. 두 파일 모두 추적/업로드하지 않습니다.

```bash
mkdir -p app/src/e2e
cp app/src/debug/google-services.json app/src/e2e/google-services.json
./gradlew :app:lintE2e :app:testE2eUnitTest :app:assembleE2e
```

산출물은 `app/build/outputs/apk/e2e/app-e2e.apk`입니다. 새 e2e 빌드의 Firebase/Kakao debug 서명 등록도 외부 서비스에서 유효해야 합니다. CI는 아래 비밀로 설정 파일을 생성합니다. `configure-ci.py`는 빈 값, 잘못된 JSON/날짜, 다른 Firebase 패키지를 거절하고 값은 출력하지 않습니다.

## 전용 계정과 최초 세션 내보내기(한 번 필요한 외부 준비)

무인 실행마다 수동 로그인된 에뮬레이터를 재사용하지 않습니다. 다만 **진짜 DEV 전용 계정을 만들고 OAuth 로그인/회원가입을 완료하는 최초 작업**은 운영자가 해야 합니다. 개인/운영 계정이나 PROD 세션은 사용하지 마세요. 준비 컴포넌트는 등록 여부와 서버 member ID를 확인하며 임의 토큰이나 가짜 로그인을 허용하지 않습니다.

1. 위 e2e APK를 빌드합니다. 아래 로그인 전용 러너로 기기를 설정하고 정확한 APK를 설치합니다. 이 명령은 세션 비밀이 필요 없고 실행 후 앱 데이터를 비웁니다.
2. 기기에서 앱을 열고 실제 DEV 계정으로 OAuth 로그인과 회원가입을 완료합니다. 앱이 정상 회원 화면을 보여야 합니다.
3. 저장소 밖의 소유자 전용 디렉터리(권한 `0700`)를 만든 뒤 세션을 내보냅니다. 출력 파일은 새 파일이어야 합니다. 러너는 상대 경로, 저장소 내부, 심볼릭 링크, 공개 디렉터리, 기존 파일을 거절합니다.

```bash
python3 .maestro/scripts/run-tests.py --suite login \
  --device emulator-5554 --configure-emulator \
  --apk app/build/outputs/apk/e2e/app-e2e.apk

# 위 명령 뒤 기기에서 최초 OAuth 로그인/회원가입을 완료합니다.
mkdir -p "$HOME/.config/undabang-e2e"
chmod 700 "$HOME/.config/undabang-e2e"
python3 .maestro/scripts/run-tests.py --device emulator-5554 \
  --export-session "$HOME/.config/undabang-e2e/maestro-session.json"
```

내보내기는 private 요청 `{"operation":"export"}`를 전달합니다. 앱이 계정을 검증하고 `files/maestro-session.json`에 실제 `authState`와 검증된 `memberId`를 씁니다. 호스트는 ADB 파이프로 읽어 `0600` 파일에 저장하며 JSON을 화면/로그/argv/아티팩트에 출력하지 않습니다. bootstrap 성공 직후에는 요청/결과의 본문과 `.new`/`.bak`만 제거하고, 세션 본문과 sidecar는 호스트가 읽을 때까지 보존합니다. 완료·실패·처리 가능한 중단 시 앱을 강제 종료한 뒤 `files/maestro-request.json`, `files/maestro-result.json`, `files/maestro-session.json`과 각 파일의 `.new`/`.bak`만 정확한 allowlist로 제거합니다. 와일드카드나 디렉터리 삭제는 하지 않습니다. 제거 실패도 명령 실패이며 작성한 호스트 파일은 제거합니다. **내보내기는 기존 로그인을 지우지 않습니다.** 기기를 폐기하거나 다음 격리 실행으로 로컬 세션을 비우세요.

앱 private I/O는 `adb shell -T run-as`를 사용합니다. `-T`는 PTY를 금지하며 shell-v2가 stdin, 분리된 stdout/stderr와 원격 종료 코드를 전달합니다. shell-v2 미지원 기기는 명령 실행 전에 실패하며 `exec-out` 폴백은 없습니다. 요청 JSON은 stdin으로만 전달하고 자격 증명을 argv나 ADB/원격 셸 환경에 넣지 않습니다. ADB stdout/stderr 원문도 로그에 포함하지 않고 고정 실패 코드만 보고합니다.

결과 파일이 아직 없을 때만 고정 probe가 종료 코드 `3`을 반환해 기존 0.5초 간격으로 기다립니다. 파일이 있으면 `cat`의 실패 상태를 그대로 전달합니다. `run-as`·쓰기·읽기·삭제의 다른 nonzero는 즉시 실패합니다. 종료 코드 `0`이라도 빈 본문/잘못된 JSON은 `invalid-bootstrap-result`로 즉시 실패하며, 요청 작업에 맞는 `ready`/`cleaned`/`exported`가 아니면 성공하지 않습니다.

이 파일을 `cat`해서 CI 로그에 넣거나 셸 인자로 전달하지 마세요. 비밀 저장소의 보안 입력 경로로 `authState` 객체의 JSON과 `memberId`를 각각 등록합니다. 내보내기 파일 전체는 `MAESTRO_AUTH_STATE_JSON`이 아닙니다. 토큰 만료/철회/회전 정책에 따라 재로그인과 재내보내기가 필요합니다. 실패한 인증을 로그인된 척 통과시키는 폴백은 없습니다. 로컬 파일과 CI 비밀을 함께 갱신하고 이전 파일/비밀을 폐기하세요.

## 무인 실행: 실제 세션을 매 플로우에 독립 복원

로그인 전용을 제외한 suite에는 아래 세 값이 **모두 필수**입니다. 로컬도 CI도 동일합니다.

|환경 변수/CI 비밀|내용|
|---|---|
|`MAESTRO_AUTH_STATE_JSON`|검증된 전용 DEV 계정의 AppAuth `authState` JSON **객체**|
|`MAESTRO_MEMBER_ID`|같은 내보내기의 서버 `memberId`|
|`MAESTRO_FIXTURE_START_DATE`|예약 테스트 기록을 관리하기 시작한 날짜, `YYYY-MM-DD`|

시작일은 첫 실행에서 생성할 기록 날짜 이하로 정하고 이후 실행마다 오늘 날짜로 앞당기지 않습니다. 폼은 기기 정시에서 1시간 전을 시작으로 사용하므로 첫 실행이 자정 직후면 전날도 범위에 포함해야 합니다. 러너는 CRUD 기록 날짜가 정리 범위 밖이면 UI 실행 전에 실패합니다. 오래된 중단 기록을 정리할 수 있도록 같은 전용 계정의 fixture 시작일을 유지합니다.

로컬에서는 세션 파일을 값 노출 없이 환경으로 전달하는 보조 명령을 사용합니다. 다음 날짜는 형식 예시이며 실제 전용 계정의 fixture 관리 시작일로 설정하세요.

```bash
export MAESTRO_FIXTURE_START_DATE=2026-10-01
python3 .maestro/scripts/run-with-session.py \
  "$HOME/.config/undabang-e2e/maestro-session.json" --suite all \
  --device emulator-5554 --apk app/build/outputs/apk/e2e/app-e2e.apk
```

`run-with-session.py`는 저장소 밖, 현재 사용자 소유의 비공개 일반 파일만 읽습니다. `authState`/`memberId`를 호스트 러너의 환경에 넣고 즉시 같은 프로세스를 러너로 교체합니다. 환경을 출력하거나 임시 인증 파일을 만들지 않습니다. 비밀 관리 도구가 이미 세 환경 변수를 주입했다면 직접 실행합니다:

```bash
python3 .maestro/scripts/run-tests.py --suite smoke --device emulator-5554 \
  --apk app/build/outputs/apk/e2e/app-e2e.apk
python3 .maestro/scripts/run-tests.py --suite regression --device emulator-5554
python3 .maestro/scripts/run-tests.py --suite ci --device emulator-5554
python3 .maestro/scripts/run-tests.py --suite all --device emulator-5554
python3 .maestro/scripts/run-tests.py --suite login --device emulator-5554
```

`--apk`를 생략하면 설치된 e2e 앱을 사용합니다. 러너는 **앱 데이터를 지우기 전에** 준비 컴포넌트와 `run-as` 가능 여부를 확인합니다. 일반 debug/release 앱을 대신 초기화하지 않습니다. protected suite는 비밀이 없으면 기기에 손대기 전에 실패하며 일부 비로그인 테스트만 실행해 성공으로 처리하지 않습니다.

각 플로우의 순서는 다음과 같습니다:

1. 앱 강제 종료와 `pm clear`로 이전 로컬 인증·Room 캐시·권한을 지웁니다.
2. 보호 플로우는 앱 private 파일에 `prepare` 요청을 stdin으로 전달합니다. 요청은 `authState`, `memberId`, `fixtureStartDate`를 포함합니다. Intent extra/deep link에는 인증 정보가 없습니다.
3. e2e Activity가 DEV discovery와 가져온 endpoint/client를 검증하고 실제 토큰을 갱신합니다. 서버 등록 상태/member ID를 확인한 뒤 예약 운동 기록을 정리합니다. `fixture:true` 기능은 선택한 Hilt handler의 prepare도 수행합니다. `ready` 결과가 아니면 Maestro를 실행하지 않습니다.
4. 기능의 `before_flow` hook, 실제 UI 플로우, `after_flow` hook 순서로 실행합니다. Maestro 환경에는 인증/CI 비밀을 상속하지 않습니다. 기기 한국 시간의 운동 변수, ASCII `E2E_RUN_ID`, 명시한 비밀 없는 기능 변수만 전달합니다.
5. 성공, 단언 실패, 타임아웃, 준비 실패, 처리 가능한 중단 신호 모두 `finally`에서 `cleanup`을 요청합니다. 실제 인증/member 확인 후 기능 cleanup을 먼저 수행하고 기존 운동 정리·세션 제거를 수행합니다. 성공하면 앱 데이터를 지웁니다. 테스트나 정리 중 하나라도 실패하면 종료 코드는 **1**입니다.

기능 prepare 전에 앱 private `maestro-feature-recovery.json`에 feature/flow/runId/memberId ownership을 기록합니다(인증/parameters는 저장하지 않음). 기능 handler는 cleanup이 성공해도 snapshot과 ownership 증거를 삭제하지 않습니다. 공통 coordinator의 기능 cleanup·운동 정리·세션 제거가 모두 성공한 뒤 호스트의 최종 `pm clear`가 성공할 때 앱 private 증거를 함께 제거합니다.

기능 cleanup 뒤 운동 정리나 세션 제거가 실패해도 snapshot과 recovery marker를 보존하고 다음 플로우를 실행하지 않습니다. handler는 동일 feature/flow/runId/memberId와 원래 parameters로 cleanup을 다시 받아도 복구를 완료해야 합니다. 이미 복구된 상태는 허용하지만 소유권 불일치나 실제 복구 실패는 계속 오류로 처리합니다. coordinator는 세 단계가 성공한 뒤에만 recovery marker를 제거하므로, 최종 `pm clear`가 실패하면 남은 snapshot은 그대로 보존됩니다.

다음 실행의 `Device.clear()`도 recovery marker가 있으면 `pm clear`를 거절합니다. 복구에는 보존된 앱 데이터, 원래 metadata/parameters와 유효한 전용 인증으로 동일 feature cleanup이 필요합니다. marker나 snapshot을 수동 삭제해 실패를 숨기지 마세요. CI 호스트 폐기 뒤까지 앱 private 데이터가 보존된다는 보장은 없습니다.

`--flow-timeout` 기본값은 플로우당 900초입니다. bootstrap 작업은 300초 제한입니다. SIGINT/SIGTERM은 실행 중 Maestro 자식을 종료하고 정리를 시도합니다. **SIGKILL, 기기 단절, GitHub 강제 종료/전체 job 타임아웃은 정리 완료를 보장할 수 없습니다.** 운동 namespace의 중단 기록은 다음 정상 prepare가 같은 날짜 범위에서 정리합니다. 기능 recovery marker가 남았다면 먼저 원 ownership으로 복구해야 합니다. 정리 실패는 JUnit 실패로 기록되며 숨기지 않습니다.

## fixture 삭제 범위와 병렬 실행

`^maestro-e2e-[a-f0-9]{32}(-edited)?$`에 정확히 맞는 제목은 **검증된 전용 계정에서 이 suite만 소유**합니다. 일반 기록 제목에 이 패턴을 사용하지 마세요. 준비/정리는 시작일~기기 오늘의 월별 운동 횟수를 읽고 기록이 있는 날짜만 조회하여 이 제목만 삭제합니다. 다른 제목·다른 계정·범위 밖 기록은 삭제하지 않습니다. 서버 오류나 계정 불일치 시 삭제를 진행하지 않습니다.

UI가 생성·수정·삭제를 수행합니다. bootstrap은 테스트를 통과시키려고 기록을 대신 생성하지 않습니다. CI는 `maestro-dev-dedicated-account` concurrency group으로 같은 계정의 job을 한 번에 하나만 실행합니다. `cancel-in-progress: false`로 실행 중인 job을 자동 취소하지 않으며, `queue: max`로 최대 100개의 pending job을 대기시킵니다. **로컬 실행끼리 또는 로컬과 CI가 같은 계정을 동시에 사용하면 서로의 예약 기록을 지울 수 있습니다.** 병렬 실행에는 서로 다른 DEV 계정을 사용하세요.

## suite와 플로우(#607, #608)

|파일|태그|러너 suite|검증|
|---|---|---|---|
|`flows/smoke-tabs.yaml`|`smoke`, `requires-login`|`smoke`, `ci`, `all`|5개 하단 탭, 실제 피드 콘텐츠/빈 상태, 채팅 서버 응답 콘텐츠/빈 상태|
|`flows/exercise-form-entry.yaml`|`regression`, `requires-login`|`regression`, `all`|실제 기록 폼 진입과 필드 표시|
|`flows/exercise-crud.yaml`|`regression`, `requires-login`|`regression`, `all`|필수값 누락, UI 생성/조회/수정, 재실행 후 영속성, UI 삭제와 전체 목록 부재 검사|
|`flows/login-screen.yaml`|`smoke`|`login`, `smoke`, `ci`, `all`|clearState 뒤 실제 로그인 화면/버튼|

`login-screen`은 세션을 지우므로 직접 Maestro 전체 실행의 `config.yaml`에서도 마지막입니다. 호스트 러너는 앞 플로우의 로그인 상태에 의존하지 않고 매번 복원합니다. 직접 실행한 뒤에는 다시 수동 로그인해야 합니다.

### 기능 descriptor와 공유 계약

`--suite ci`는 smoke-tabs → 현재 트리의 모든 `.maestro/suites/<feature>.json`(파일명 순, 각 flows 선언 순) → login-screen입니다. `all`은 여기에 기존 exercise 회귀 두 개를 포함합니다. **`regression`은 기존 운동 두 개만** 실행하며 전체 기능 회귀의 별칭이 아닙니다. `--suite <feature>`는 해당 descriptor만 실행합니다. descriptor가 없으면 그 기능을 실행했다고 주장하지 않으며 잘못된 descriptor/빈 flows/없는 파일/중복 흐름은 실패합니다. 기능 상세는 `.maestro/README-<feature>.md`를 참조합니다.

descriptor 스키마(모든 필드 필수):

```json
{"flows":["feature-flow"],"apiLevel":30,"fixture":true,"requiredEnvironment":[],"driver":null}
```

- `fixture:true`: `MaestroFeatureFixture`의 `prepare(MaestroFeatureRequest): Map<String,String>`, `cleanup(MaestroFeatureRequest)`를 같은 요청으로 호출합니다. request는 `feature`, `flow`, `runId`, 검증된 `memberId`, private `parameters: JSONObject`입니다. 비어 있는 공통 Hilt multibinding으로 기능 없는 기준 트리도 지원하며, 요청한 handler가 없으면 실패합니다.
- `.maestro/scripts/features/<feature>.py`가 있으면 `driver:null`이어도 `before_flow(core, device, flow, variables, credentials, run_id) -> dict`와 `after_flow(...) -> None` hook을 자동 사용합니다. hook 실패도 backend cleanup을 거칩니다. `E2E_RUN_ID`는 플로우당 16자리 UUID hex이며 prepare/UI/cleanup 간 동일합니다.
- `driver:"<feature>"`는 `run_suite(args, core)` 전체 수명 소유자입니다. 실제 두 기기 채팅이 사용하며 양 기기 인증·locale·ARM ABI 설치 검증·정리를 직접 수행합니다. `--peer-device`로 별도 serial을 주거나 driver가 같은 AVD의 읽기 전용 두 번째 emulator를 만듭니다. 반환 결과는 선언한 모든 `(flow_name, seconds, [fixed_error_codes])`여야 하며 누락/잘못된 결과는 실패입니다.
- 공통 Python API: `read_credentials(prefix="MAESTRO_")`(peer는 `MAESTRO_PEER_`), `Device.bootstrap(operation, credentials=None, timeout=300, feature=None, flow=None, run_id=None, parameters=None) -> dict`, `run_maestro(device, flow, output, redactor, timeout, fixture_start_date=None, variables=None)`. feature metadata 네 필드는 prepare/cleanup에 함께 전달합니다. driver의 `args.output`은 공통 결과 `Path`입니다.
- `requiredEnvironment` 값은 명시한 이름 그대로 private parameters에만 전달합니다. 비밀 변수나 auth/token/FCM 반환은 거절합니다. 모든 가족을 하나의 계정 잠금 안에서 순차 실행하고, 실제 선택한 플로우 이름의 JUnit을 하나로 합칩니다. 입력 preflight 실패도 선택 플로우를 실패로 기록하며 smoke로 대체하지 않습니다.

CRUD 입력은 ASCII 제목과 숫자를 사용합니다. 러너는 준비 완료 뒤 Maestro 실행 직전 기기 시간으로 `EXERCISE_START_HOUR`, `EXERCISE_END_HOUR`, `EXERCISE_START_DATE`, `EXERCISE_END_DATE`를 계산합니다. 날짜 형식은 `yyyy.MM.dd`, 시간은 두 자리입니다. 시작은 기기 정시−1시간, 종료는 정시입니다. UI 수정에서는 시작 분을 `00`→`05`로 바꿉니다. CLI 시작~폼 진입 사이 정시/날짜 경계를 넘으면 기본 날짜와 기대값이 달라져 실패할 수 있으며 자동 재시도하지 않습니다.

`subflows/assert-exercise-records-absent.yaml`은 수정 후 재실행 시 원제목의 부재를 검사합니다. 삭제 직후와 삭제 후 재실행 시에는 같은 날짜의 원제목·수정제목 부재를 함께 검사합니다. `ABSENT_RECORD_PATTERN`은 CRUD 호출부가 넘기는 내부 subflow 환경값이며 추가 호스트 입력이 아닙니다.

현재 viewport의 `assertNotVisible` 하나로 끝내지 않습니다. 캘린더 헤더부터 화면 20%의 느리고 겹치는 swipe(`duration: 2000ms`, 최대 200회)마다 대상 제목이 없는지 확인하며, 기록이 남아 있으면 마지막 실제 행의 시간 TextView 접근성 레이블 `'운동 기록 목록 마지막 항목: <HH:mm ~ HH:mm>'`에 도달해야 통과합니다. 빈 목록은 정확한 날짜의 성공한 빈 응답 레이블이 필요합니다. 제한된 횟수 안에 끝에 도달하지 못하거나 요청·목록 검사에 실패하면 실패합니다. 이전 날짜·초기 빈 화면을 성공으로 처리하지 않습니다. 호스트의 플로우 제한(기본 900초)을 넘겨도 실패하며 자동 재시도하지 않습니다.

### 수동 UI 개발(격리 CI와 다른 경로)

debug/e2e APK를 직접 설치하고 **수동 로그인한 기기**에서 `maestro test`를 실행할 수도 있습니다. 이 경로에는 호스트 준비/정리·CI 계정 검증이 없습니다. 세션이 만료되면 수동 로그인해야 합니다. CRUD는 필요한 제목/날짜 변수를 직접 공급해야 하므로 안전한 정리까지 필요한 실행은 호스트 러너를 권장합니다.

```bash
# 수동 로그인 상태에서 각각 선택해 실행합니다. CRUD 변수는 필요하지 않습니다.
maestro test .maestro/flows/smoke-tabs.yaml
maestro test .maestro/flows/exercise-form-entry.yaml
# 로그인 화면 선택은 앱 데이터를 지웁니다. 다음 보호 플로우 전에 다시 로그인하세요.
maestro test --include-tags smoke --exclude-tags requires-login .maestro/
maestro studio
maestro hierarchy
```

마지막 두 명령은 **기기 화면/접근성 트리 자체를 표시**합니다. OAuth 화면이나 자격 증명이 있는 화면에서 출력·캡처·업로드하지 마세요. manual 실행의 원본 로그도 CI 안전 아티팩트와 같다고 간주하지 않습니다.

XML ID는 `id` 셀렉터를 사용합니다. Compose의 `feed_content`, `feed_empty`, `exercise_*` 태그는 screen root의 `testTagsAsResourceId`로 ID를 노출합니다. 채팅의 성공한 서버 응답은 `'<nickname>님과의 채팅'` 행 또는 `'채팅 목록에 채팅방이 없습니다'` 빈 상태 접근성 레이블로 단언합니다. 운동 목록은 성공한 응답과 RecyclerView diff 반영 뒤 `'<yyyy-MM-dd> 운동 기록 목록'` 또는 `'<yyyy-MM-dd>에 운동 기록이 없습니다'`를 노출합니다. 레이블의 ISO 날짜는 실제 조회·반영된 날짜이며, 플로우는 선택한 날짜와 일치하는 레이블만 준비 완료로 인정합니다. 내비 라벨만 보거나 optional 단언으로 성공 처리하지 않습니다. 마커처럼 접근성 트리에 없는 지도 내부 요소는 검사하지 않습니다.

`clearState`는 권한도 초기화합니다. 플로우의 `notifications: allow`를 유지하세요. 콜드 스타트는 `extendedWaitUntil`로 기다립니다. 한글 입력은 Maestro Android `inputText`로 지원되지 않지만 한글 표시 검증은 가능합니다.

프로필 smoke 플로우는 작은 화면에서 화면 밖 콘텐츠를 화면 누락으로 오인하지 않도록 `preferred_exercise_tv`까지 스크롤한 뒤 `선호 운동` 표시를 단언합니다.

## CI: 신뢰된 dev push/PR 기능 suite와 수동 regression

`.github/workflows/android-maestro.yml`은 기존 `android-dev-ci.yml`/릴리스 CI와 분리되어 있습니다.

|트리거|ref/신뢰 조건|suite/결과|
|---|---|---|
|`push`|`refs/heads/dev`만 허용|`ci`(smoke + 현재 트리의 실제 기능)|
|`push`|다른 branch/tag ref|트리거 필터에서 제외, 비밀 미제공|
|`pull_request`|base가 `dev`, 같은 저장소 head, 작성자 association `OWNER`/`MEMBER`/`COLLABORATOR`/`CONTRIBUTOR`|`ci`(smoke + 현재 트리의 실제 기능)|
|`pull_request`|base가 `dev`, fork head 또는 비신뢰 작성자|비밀 없는 거절 job 실패, 인증 job 미실행|
|`pull_request`|다른 base branch|트리거 필터에서 제외, 비밀 미제공|
|`workflow_dispatch`|ref `refs/heads/dev`, 선택한 suite|`regression`, `all`, `ci`|
|`workflow_dispatch`|다른 ref|비밀 없는 거절 job 실패, 인증 job 미실행|

- 인증 suite를 실행하지 않은 skip은 인증 통과가 아닙니다. 거절 job은 미실행 상태를 명시하며 **실패**합니다. `pull_request_target`은 사용하지 않습니다.
- 비공개 조직 멤버는 `pull_request`에서 `CONTRIBUTOR`로 표시될 수 있지만 fork head와 첫 기여 계정(`FIRST_TIME_CONTRIBUTOR`/`FIRST_TIMER`/`NONE`)은 계속 거절하므로, 커밋 이력이 없는 신규 비공개 멤버는 조직 멤버십을 공개하거나 `dev` push/수동 실행(`dev` ref)을 사용해야 합니다.
- 세 실행 경로 모두 **`maestro-dev` 환경**과 기존 `maestro-dev-dedicated-account` concurrency group을 유지합니다. `deployment: false`로 새 Deployment 기록 생성을 끄되, 환경 Secrets와 ref 제한 정책은 계속 적용됩니다. 기존 Deployment 기록과 Actions 검사·로그는 유지됩니다.
- 인증 실행에는 외부 `maestro-dev` 정책에서 `dev`와 각 대상 PR의 정확한 `refs/pull/<번호>/merge` ref를 허용해야 합니다. 워크플로는 이 정책을 생성하거나 넓히지 않습니다. required reviewers 설정도 별도 환경 정책이며 `deployment: false`의 효과가 아닙니다.
- 기존 dev CI는 `lintDebug`, `testDebugUnitTest`, `assembleDebug`와 기존 Ktlint/Firebase 배포 검사를 유지합니다. 기존 master CI는 `lintRelease`, `testReleaseUnitTest`와 릴리스 빌드·배포를 유지합니다. 두 일반 CI는 e2e Firebase 파일을 요구하지 않도록 변형별 검증을 분리합니다.
- Maestro job은 계정 없는 공통 Python 동작 테스트를 먼저 실행하고, `.maestro/scripts/features/`가 있으면 그 디렉터리도 별도로 discovery합니다. 이후 제공한 DEV 설정의 `:app:lintE2e`, `:app:testE2eUnitTest`, `:app:assembleE2e`를 실행한 뒤 기능별 인증 입력을 검증합니다. 두 번째 계정 입력이 없어도 소스 빌드 결과는 확인할 수 있지만 실제 UI 실행은 실패로 차단하며 smoke 통과로 대체하지 않습니다. JDK17, 선택된 최대 API Google APIs x86_64/KVM/4096MB, 한국어/서울 시간대, 설치 전 실제 ARM ABI 확인과 순차 실행·finally 정리를 사용합니다.
- CI 에뮬레이터는 호스트 기본 시간대에 의존하지 않도록 `-timezone Asia/Seoul`로 시작합니다. `--configure-emulator`의 재부팅 후 한국어·서울 시간대 검증은 유지합니다.
- SDK 설정은 `packages: 'platform-tools'`를 명시해 액션 기본 목록의 구형 `tools` 설치를 제외합니다. `sdkmanager`는 액션이 별도로 설치하는 `cmdline-tools`를 사용합니다.
- KVM 설정은 러너 계정을 기록하고, udev 이벤트 처리가 끝날 때까지 최대 30초 기다린 뒤 `/dev/kvm` 권한을 확인합니다. `Enable KVM` 단계에만 명령 추적을 켜므로 실패한 명령을 로그에서 구분할 수 있으며, 장치가 없거나 읽기·쓰기 권한이 없으면 계속 실패 처리합니다.

`maestro-dev` 환경에 등록할 비밀:

|비밀|용도|
|---|---|
|`GOOGLE_SERVICES_JSON_DEV_BASE64`|DEV Firebase JSON의 줄바꿈 없는 base64|
|`COGNITO_USER_POOL_ID_DEV`|DEV 사용자 풀|
|`COGNITO_APP_CLIENT_ID_DEV`|최초 세션을 발급한 동일 DEV AppAuth client|
|`COGNITO_REGION_DEV`|DEV Cognito region|
|`KAKAO_NATIVE_APP_KEY_DEV`|DEV 앱 native key(`KAKAO_NATIVE_APP_KEY` 환경으로 전달)|
|`KAKAO_REST_API_KEY`|기존 REST key|
|`MAESTRO_DEBUG_KEYSTORE_BASE64`|**필수**: CI e2e 전용 고정 debug keystore의 줄바꿈 없는 base64. release 키 사용 금지|
|`MAESTRO_AUTH_STATE_JSON`|내보낸 AppAuth 객체 JSON|
|`MAESTRO_MEMBER_ID`|검증된 전용 계정 member ID|
|`MAESTRO_FIXTURE_START_DATE`|고정 fixture 관리 시작일|
|`MAESTRO_PEER_AUTH_STATE_JSON`|채팅의 별도 계정에서 내보낸 실제 AppAuth 세션|
|`MAESTRO_PEER_MEMBER_ID`|matching/채팅의 실제 알려진 peer ID(주 계정으로 대체 금지)|
|`MAESTRO_PEER_FIXTURE_START_DATE`|채팅 peer의 고정 fixture 관리 시작일|
|`MAESTRO_CHAT_ROOM_ID`|채팅 두 계정 전용으로 사전 준비한 실제 방|
|`MAESTRO_MATCHING_LEFT_TOP_LATITUDE`, `MAESTRO_MATCHING_LEFT_TOP_LONGITUDE`|matching의 실제 peer 조회 경계 좌상단|
|`MAESTRO_MATCHING_RIGHT_BOTTOM_LATITUDE`, `MAESTRO_MATCHING_RIGHT_BOTTOM_LONGITUDE`|matching의 실제 peer 조회 경계 우하단|
|`MAESTRO_MATCHING_SEARCH_QUERY`|matching의 실제 ASCII 검색어|
|`MAESTRO_MATCHING_CATALOG_PLACE_NAME`|실제 Kakao 검색 결과에서 유일하게 일치하는 장소명|
|`MAESTRO_MATCHING_ROOM_ID`|matching 두 계정의 검증할 기존 전용 방(정리 시 보존)|

peer/matching 비밀은 선택된 descriptor가 요구할 때만 필수입니다. workflow는 정확히 이 이름을 authenticated preflight와 emulator 실행 단계에만 전달합니다. 누락 시 **이름만** 표시하고 실패합니다. CI 계정 잠금은 기능별 job으로 나누지 않습니다. GitHub concurrency의 `queue: max`는 최대 100개의 pending job을 대기시키며, 용량을 넘는 job은 취소됩니다. FIFO 순서는 workflow dispatch 순서가 아니라 job이 concurrency 대기에 들어간 순서입니다([공식 문서](https://docs.github.com/en/actions/how-tos/write-workflows/choose-when-workflows-run/control-workflow-concurrency)).

필수 비밀 누락은 구성 단계에서 이름만 표시하고 실패합니다. 잘못된 세션·등록되지 않은 계정·만료/철회·다른 DEV pool/client·잘못된 member ID는 준비 실패입니다. CI에 개인 OAuth 자격 증명을 입력하거나 테스트마다 브라우저 로그인을 조작하지 않습니다. 원본 인증 파일, APK, google-services/local.properties, raw logcat은 업로드하지 않습니다.

### 고정 CI e2e debug 서명

- CI는 필수 비밀 `MAESTRO_DEBUG_KEYSTORE_BASE64`를 `$RUNNER_TEMP/maestro/debug.keystore`에 복원하고 권한을 `0600`으로 설정합니다. `keytool -list`로 alias `androiddebugkey`와 store password `android`를 확인하며 비밀 누락·복원·검사 실패 시 빌드를 진행하지 않습니다.
- 빌드는 `-Pmaestro.e2e.signing.store.file`, `-Pmaestro.e2e.signing.store.password`, `-Pmaestro.e2e.signing.key.alias`, `-Pmaestro.e2e.signing.key.password`를 전달합니다. store/key password는 `android`, alias는 `androiddebugkey`입니다.
- Gradle은 네 속성이 모두 있으면 `maestroE2e`를 e2e에만 적용하고, 로컬에서 생략하면 기존 debug 서명을 사용합니다. 일반 debug/release 서명은 변경하지 않습니다.
- 운영자는 패키지 `com.project200.undabang`과 실제 서명 인증서를 올바른 Kakao/Firebase 앱에 등록해야 합니다. 이 진단 변경은 키 생성·비밀 등록·외부 서비스 등록을 수행하지 않으며, 고정 서명만으로 SDK 인증 성공이나 지도 실패 원인을 확정하지 않습니다.

## 결과와 실패 조사

호스트 러너의 안전한 결과는 `build/maestro/<UTC시각>-<run ID>/`에 있습니다:

- `report.xml`: 실제 Maestro 종료 코드/JUnit과 준비·정리 결과를 합친 플로우별 JUnit. 빈 실행/skip/리포트 부재는 실패입니다.
- `<flow>/console.log`, `debug-*.log`, `debug-*.json`: 인증 값/JWT/인증 관련 줄을 제거한 Maestro 출력과 debug 정보. 원본 subprocess 예외/traceback을 출력하지 않습니다.
- `<flow>/screenshot-*.png`: **Maestro 실행 중** 생성된 PNG만 보관합니다. 준비/내보내기 화면은 캡처하지 않습니다. 원본 텍스트에서 민감 정보가 감지되면 이미지도 보관하지 않습니다.
- `<flow>/device-log.log`: Maestro 비정상 종료 시 logcat 최근 500줄 중 `[MEASURE]`/`WebSocket`/`Connectivity`/`AndroidRuntime`/`FATAL EXCEPTION`/`OkHttp`가 있는 줄만 선별하고 인증 값/JWT/인증 관련 줄 제거 규칙을 적용한 로그입니다.
- `<flow>/last-anr-summary.json`: 이어서 조회한 마지막 ANR의 고정 process/reason 분류와 load average/전체 CPU 수치만 보관합니다. Android 14의 들여쓴 `Reason:`도 허용하며 process·load·CPU 정보가 없으면 `unknown`/`null`로 남깁니다. 마지막 기록은 이전 사건일 수 있습니다.
- `<flow>/anr-events-summary.json`: events 버퍼의 `am_anr` 태그를 한 번 조회해 기기 시각 `timestamp_epoch`(Unix 초)와 고정 process/reason 분류를 시각 기준 최신 8건까지 보관합니다. 모든 상태에 `scope: device-history`를 명시하며 현재 플로우의 사건으로 분류하지 않습니다.
- `chatting/diagnostics/<phase>/{checkpoint,last-anr-summary,anr-events-summary}.json`: A의 `peer-ready-before-primary-prepare`(B 사용 가능 직후·A 설정 전), `primary-fixture-ready`(A prepare·identity 확인 후·B 설정 전), `receiver-ready-before-primary-launch`(B 준비 barrier 후·A 최초 open 직전), `failure`(A가 존재하는 실패·중단 시 정리 전) 기록입니다. 각 조회는 5초로 제한하며 metadata는 고정 phase, 호스트 캡처 시작/끝 epoch·monotonic과 얻을 수 있는 기기 wall epoch·uptime만 기록합니다.
- `chatting/diagnostics/host-resources.log`: B 생성/부팅 또는 명시적 peer 처리 전부터 A 최초 open 결과까지 Linux 호스트를 1초 간격으로 수집하는 JSONL입니다. 이전 단계 실패·중단 시에도 fixture 정리 전에 중단합니다. CPU capacity·구간 busy/iowait, memory/swap, load, 지원되는 PSI의 허용 숫자와 source 상태만 보관하며 첫 CPU 구간·counter reset은 미측정으로 남깁니다. 최대 5400 samples와 출력 크기 제한을 적용하고 수집 종료·잘림·coverage gap을 고정 상태로 기록합니다. 파일/종료 기록 부재는 수집 완료나 정상 부하의 증거가 아닙니다.

이벤트 요약의 `status`는 `captured`(파싱한 이벤트), `partial`(인식 불가 행도 있어 불완전한 이벤트), `empty`(빈 버퍼/헤더만), `unrecognized`(파싱할 수 없는 내용), `unavailable`(조회 실패)로 구분합니다. checkpoint의 마지막 ANR도 같은 상태 이름으로 빈 보고서·일부 정보·조회 불가를 구분하고 기기 clock은 `captured`/`partial`/`unrecognized`/`unavailable`을 기록합니다. 개별 `unknown` 분류나 빈 결과·파일 부재는 ANR이 없었다는 증거가 아닙니다. 두 ANR 요약은 원문·사용자 데이터·패키지·PID·창 제목·Intent·URL·토큰·예외 본문을 공개하지 않습니다. 읽기·파싱·쓰기 실패는 원래 UI 실패나 fixture 정리를 대체하지 않습니다.

ANR 자료는 기기 과거 이력이며 재부팅으로 지워질 수 있습니다. 이벤트 시각과 실패 hierarchy/로그 시점을 대조하되 호스트·기기 wall clock의 차이/변경과 수집 창을 고려해야 합니다. 캡처 시각은 ANR 발생 시각이 아니며 fixture-ready는 UI 건강 확인이 아닙니다. 호스트 주기 수집은 연속 측정이 아니고 checkpoint의 adb 조회도 실행 부하·순서를 조금 바꿀 수 있습니다. 깨끗한 snapshot이나 sample 사이 간격만으로 순간 contention 또는 측정하지 않은 기기 하위 시스템의 문제를 배제하지 않습니다. 이 변경은 ANR 수정이나 근본 원인 확정이 아닙니다.

Maestro의 HOME/Java user.home은 실행별 임시 디렉터리로 격리하며 analytics, 업데이트 확인, AI 알림을 끕니다. 원본 CLI 임시 출력은 finally에서 제거하고 업로드하지 않습니다. authState는 호스트 메모리/앱 private stdin 경로에만 전달됩니다. 앱은 e2e에서 자격 증명을 포함할 수 있는 Timber 로깅을 사용하지 않습니다. raw logcat과 dumpsys 원문은 파일로 저장하거나 업로드하지 않습니다.

CI는 실패해도 `always()`로 상태 파일과 안전한 XML/log/JSON/PNG만 7일 보관합니다. `workflow-status.log`만 있으면 빌드/설정/에뮬레이터 단계에서 UI 실행 전 실패한 상태일 수 있습니다. 이 파일은 테스트 통과 증거가 아닙니다. stdout의 고정 실패 코드와 JUnit을 먼저 보고 안전한 hierarchy/스크린샷을 함께 확인하세요.

## 검증 상태와 남은 외부 준비

사용자 보고 run `39007766770`에서는 고정 keystore 복원과 APK 빌드가 성공했고 smoke·피드·타이머·로그인은 통과했습니다. matching 세 플로우는 모두 `matching_map_ready` 대기에서 실패했으며 SDK callback의 종류·코드는 아직 수집되지 않았습니다. 이번 SDK 진단 변경은 정적 확인만 했고 로컬 빌드·테스트·기기 실행은 수행하지 않았습니다. 고정 서명 이후에도 실패 원인은 미확정입니다.
이번 ANR checkpoint·호스트 주기 진단은 정적으로만 검토했으며 로컬 Python 테스트·빌드·lint·formatter·adb·Maestro는 실행하지 않았습니다. 게시 후 승인된 CI의 unittest·APK 빌드·실제 인증 Maestro 결과를 별도로 확인해야 하며 진단 추가 후에도 기존 ANR로 실패할 수 있습니다.

저장소 루트에서 사용하는 표준 라이브러리 회귀 테스트 명령:

```bash
python3 -m unittest discover -s .maestro/scripts -p 'test_*.py'
if [ -d .maestro/scripts/features ]; then
  python3 -m unittest discover -s .maestro/scripts/features -p 'test_*.py'
fi
```

`test_run_tests.py`의 로컬 셸 하네스는 실제 adb/기기/계정 없이 stdin 전달, 원격 종료 상태, JSON 검증과 실패 시 export 파일 제거를 검사합니다. 하네스 통과도 실기기 export 증거는 아닙니다. 후속 기기 검증에서는 인증된 세션/member ID 쌍, 비ASCII JSON 왕복과 private 파일 제거를 별도로 확인해야 합니다.
공통 입력 회귀 테스트는 공개 IME metadata의 service/resource 연결, 영어 ASCII subtype 선택, ID 생략/0의 Android hash 계산(UTF-16·overflow·기본값 포함), 모호한 subtype/자동 선택 ID 거절, 다른 enabled IME 보존, 설정 미반영과 물리 기기 거절 경계를 검사합니다. 이 입력 수정의 unittest·에뮬레이터·Maestro 실행도 수행하지 않았습니다.
`test_feature_suites.py`와 `MaestroFeatureFixtureTest`는 descriptor/API 선택, 입력 선검사, 소유권 및 복구 실패 시 clear 중단, hook 실패 후 teardown, driver 누락 결과를 검사합니다. coordinator 회귀 테스트는 기능 cleanup 성공 뒤 운동 정리 또는 세션 제거가 실패하면 recovery 완료/성공 응답을 쓰지 않고, 동일 ownership으로 인증·기능 cleanup·후속 정리를 다시 수행하는지 검사합니다. 기능별 snapshot 보존과 반복 복구는 각 기능 fixture 테스트가 담당하며, 단위 테스트 통과만으로 실제 서버 복구를 검증했다고 보지 않습니다.

실기 검증에는 실제 DEV 전용 등록 계정, 유효한 내보낸 세션과 기본 CI 비밀, 선택 기능의 실제 peer/room/지도 입력, 보호된 `maestro-dev` 환경, Firebase/Kakao debug 등록, KVM/ARM translation이 있는 기기가 필요합니다. 일반 smoke 성공은 기능 성공 증거가 아닙니다. 실제 flow 이름별 원격 결과, API33 미디어/권한·알림, 채팅 두 기기, 서버 복구 실패 경로를 확인해야 합니다. prepare/cleanup의 refresh 결과를 GitHub Secret에 되쓰지 않으므로 갱신 토큰 rotation/만료 정책에 따라 다시 내보내기가 필요할 수 있습니다. 영구 인증이나 미검증 password-login을 제공하지 않습니다.
