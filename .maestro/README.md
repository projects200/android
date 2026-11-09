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

실패 결과는 `{"status":"failed","reason":"<최초 고정 코드>","cleanupFailures":["private_file_failed","local_session_clear_failed"]}` 형식입니다. `reason`은 최초 실패를 유지하며 요청/세션 파일 삭제 실패와 로컬 세션 정리 실패는 `cleanupFailures`에 각각 중복 없이 기록합니다(실패가 없으면 빈 배열). 세션 파일 삭제가 실패해도 실패 결과 쓰기를 시도하고, 결과 쓰기도 실패하면 이전 성공 결과를 제거합니다. 예외 본문·서버 응답·인증 상태·ID·URL은 이 진단에 넣지 않습니다.

호스트는 `status=failed`일 때만 `reason`과 `cleanupFailures`를 해석합니다. 공통 `discovery_failed`, `refresh_failed`, `fixture_read_failed`, `fixture_delete_failed`, `member_mismatch`, `untrusted_session`, `dev_configuration_missing`, `private_file_failed`, `local_session_clear_failed`, `setup_failed`는 작업/기능과 무관하게 `bootstrap-<operation>-failed-<분류>`로 전달합니다. 등록 분류는 기존 `registration`, `registration-unregistered`, `registration-response-missing`, `registration-member-id-missing`, `registration-authentication-failed`, `registration-user-id-header-missing`, `registration-business-error`, `registration-network-error`, `registration-network-timeout`, `registration-parse-error`, `registration-unknown`, `registration-http-100`–`registration-http-599`를 유지합니다. secondary는 `bootstrap-<operation>-secondary-private-file`/`bootstrap-<operation>-secondary-local-session-clear`이며 최초 실패와 별개로 JUnit/console에 남깁니다. 배열 밖 값·알 수 없는 코드·접미사/개행이 붙은 문자열은 공개하지 않습니다. 알 수 없는 최초 reason은 `bootstrap-<operation>-failed`로 남기며 기능 전용 reason의 기존 작업/기능 제한은 유지합니다. HTTP·네트워크 분류만으로 실제 인증 실패의 근본 원인을 확정하지 않습니다.

기능 cleanup의 추가 진단은 **`operation=cleanup`과 해당 `feature`가 모두 일치할 때만** `bootstrap-cleanup-failed-<분류>`로 전달합니다. bootstrap 변환과 정리 보고서 필터는 같은 exact allowlist를 사용합니다. 현재 앱 cleanup과 도달 가능한 helper에서 외부로 전달하는 범위만 지원합니다.

| feature | 지원하는 고정 정리 분류 |
| --- | --- |
| `timer` | 요청 유효성(`invalid_feature_request`), snapshot 존재·소유권·입력 일치, 서버 소유권·삭제/잔존(`timer_server_cleanup_failed`)·baseline 보존, fixture 읽기, private 상태 파일 |
| `matching` | 요청·snapshot 존재/소유권, 장소·방 소유권/삭제(`matching_place_cleanup_failed`, `matching_room_cleanup_failed`)/baseline 보존, evidence 존재/소유권, API 읽기 |
| `profile` | 요청·복구 소유권/알림 snapshot, FCM 소유권·알림 형식, baseline 닉네임/성별/소개 길이·제어문자 검증, `profile_restore_failed` |
| `feed` | fixture 소유권·snapshot 형식/일치, validation 글 목록 보존, fixture 읽기/삭제/변경·타인 댓글 보호·정리 잔존, 소유 미디어 정리 |
| `chatting` | 전용 방 요청 유효성·방 존재·상대 일치·목록 읽기 |

offline 전용 범위는 아래 기존 규칙을 유지합니다. 다른 기능의 코드, 알 수 없는 값, 접미사·개행이 붙은 문자열과 임의 예외 본문은 정리 상세로 공개하지 않습니다. feature 메타데이터는 `prepare`/`cleanup`/`feature-observe`와 새 `offline-control`에서 허용하며 `offline-observe`는 기존 flat envelope를 유지합니다. 작업 단위 `offline-observe`는 timer 전용 오류 매핑을 선택하지 않습니다. profile의 영역별 복구가 내부에서 포착하는 세부 오류는 앱이 실제 반환하는 `profile_restore_failed`로만 전달합니다.

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

`^maestro-e2e-[a-f0-9]{32}(-edited)?$`에 정확히 맞는 제목은 **검증된 전용 계정에서 이 suite만 소유**합니다. 일반 기록 제목에 이 패턴을 사용하지 마세요. 공통 cleaner의 **서버 삭제 후보 선택**은 시작일~기기 오늘의 월별 운동 횟수를 읽고 기록이 있는 날짜만 조회하여 이 제목으로 제한합니다. cleaner는 다른 제목·다른 계정·범위 밖 기록에 서버 DELETE를 보내지 않습니다. 서버 오류나 계정 불일치 시 삭제를 진행하지 않습니다.
공통 cleaner는 제목의 namespace를 먼저 판별합니다. 소유하지 않은 pending 행은 서버 삭제 후보에서 건너뛰며 소유한 정착 행의 서버 정리를 막지 않습니다. 소유 행은 목록·상세 재확인과 삭제 직전 Room 보호 검사에서 대기 저널, 사진의 미동기화·로컬 파일·요청 ID 등 보호 상태가 없어야 합니다. 상세 제목이 일반 제목으로 바뀌면 pending 여부보다 소유권을 먼저 확인하여 서버 삭제에서 제외합니다.

이 선택 규칙은 개인 앱 데이터 보존을 보장하지 않습니다. **전용 DEV 계정과 전용 테스트 기기를 사용해야 합니다.** 정리가 성공하면 기존 coordinator의 `clearSession()`과 호스트의 `pm clear`가 로컬 세션·앱 데이터를 초기화합니다. offline 실행의 소유 pending·in-flight·불확실 상태는 기존 failClosed 경로로 정리를 실패시키며, 이 경우 세션 데이터 전체 제거와 호스트 앱 데이터 초기화를 진행하지 않고 복구 증거를 남깁니다.

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

`--suite ci`는 smoke-tabs → `chatting`을 제외한 현재 트리의 `.maestro/suites/<feature>.json`(파일명 순, 각 flows 선언 순) → login-screen입니다. 플로우 수는 descriptor에서 결정하며 두 기기 채팅만 기본 선택에서 제외합니다. `matching-member-to-chat`과 smoke의 채팅 탭 검증은 유지합니다.

`all`은 채팅 4개와 기존 exercise 회귀 두 개까지 포함하며 두 기기가 필요합니다. **`regression`은 기존 운동 두 개만** 실행하며 전체 기능 회귀의 별칭이 아닙니다. `--suite <feature>`는 해당 descriptor만 실행하므로 `--suite chatting` 독립 실행도 유지합니다. descriptor가 없으면 그 기능을 실행했다고 주장하지 않으며 잘못된 descriptor/빈 flows/없는 파일/중복 흐름은 실패합니다. 기능 상세는 `.maestro/README-<feature>.md`를 참조합니다.

채팅 전용 job은 이번 변경에서 추가하지 않습니다. 기본 `ci` 성공은 채팅 4개 통과의 증거가 아니며, 채팅 제외가 System UI ANR의 원인 해결을 뜻하지 않습니다.

### offline suite

`offline` suite는 **API33 이상 전용 에뮬레이터 한 대**에서 기존 15개 흐름과 사진·충돌·세션 8개 사례를 실행합니다. `offline.py`/`offline_cases.py`가 실제 라디오 상태를 기록·차단·복구하고 사례마다 새 ownership을 사용합니다. 세션 전환은 별개 등록 계정 A/B의 `MAESTRO_PEER_AUTH_STATE_JSON`, `MAESTRO_PEER_MEMBER_ID`, `MAESTRO_PEER_FIXTURE_START_DATE`가 모두 필요합니다. 두 번째 기기나 채팅 방은 필요하지 않습니다. 기존 CI는 descriptor의 API·필수 환경을 그대로 읽으므로 새 workflow는 없습니다.

```bash
python3 .maestro/scripts/run-tests.py \
  --suite offline \
  --device emulator-5554 \
  --apk app/build/outputs/apk/e2e/app-e2e.apk \
  --configure-emulator
```

실행에는 primary `MAESTRO_AUTH_STATE_JSON`, `MAESTRO_MEMBER_ID`, `MAESTRO_FIXTURE_START_DATE`와 위 peer 3개 비밀이 필요합니다. 두 계정은 각각 현재 DEV Cognito 설정·refresh·서버 등록 회원으로 검증하며 같으면 실패합니다. 로컬 우선 읽기는 기본 동작이며 Remote Config 활성화는 필요하지 않습니다. 인증·fixture·서버 계약이 준비되지 않으면 skip/허용 실패로 처리하지 않습니다. 첫 저장 스냅샷 뒤 서버 갱신은 Room만 바꾸며, 표시 중인 목록·상세·수정 폼은 명시적 새로고침이나 로컬 복귀 조회까지 유지됩니다.

운동 시나리오는 온라인 준비 → 실제 망 단절 → UI 생성 → 오프라인 재실행 → 복구와 실제 생성 동기화 → 재단절과 UI 수정 → 오프라인 재실행 → 실제 수정 동기화 → 재단절과 UI 삭제 → 오프라인 재실행 → 실제 삭제 동기화 순서입니다. 각 생성·수정·삭제, 재실행, 동기화는 descriptor의 별도 플로우/JUnit 결과입니다. cache-missing 시나리오와 타이머 시나리오는 별도 ownership으로 유지합니다.
원제목과 `-edited` 제목, 시작 분 `00`→`05`, 종료 시각은 UI 동작 전 run 입력에서 정합니다. `exercise-local-created`/`updated`/`deleted`와 각 `-relaunch` 단계는 하나의 Room 트랜잭션으로 목록·상세·저널을 읽고 동일 localId·정확한 값·대기 상태를 확인합니다. 로컬 관찰은 recovery ownership과 현재 로컬 인증/회원을 검증하며 서버 인증·Repository refresh·Worker/동기화 use case 호출을 하지 않습니다.
생성 대기는 null serverId·서버 버전/확인 날짜 부재와 READY 저널을 요구합니다. 로컬 삭제는 이미 서버에 연결된 같은 ID의 `DELETE_PENDING`이며, 미전송 생성의 물리 취소나 서버 삭제 ACK로 대신하지 않습니다. 기준 ID와 orphan 검색은 날짜 밖·상세만 남은 원제목/수정제목 행도 포함합니다.
`exercise-created-synced`, `exercise-updated-synced`, `exercise-deleted-synced`는 실제 DEV 인증 후 독립 GET과 Room ACK를 최대 120초, 2초 간격으로 함께 관찰합니다. 생성에서 얻은 서버 ID는 private snapshot에 저장하고 수정·삭제에서도 같은 ID를 요구합니다. 생성/수정은 정확한 서버 목록·상세와 같은 localId의 SYNCED·저널/사진 대기 없음이 모두 필요합니다. 삭제는 캡처한 서버 ID의 부재와 원 localId의 목록·상세·저널·사진 제거가 모두 필요합니다. 빈 응답·이전 값·중복·식별자 교체·ACK 누락은 성공으로 대체하지 않습니다.
실패/중단/타임아웃 뒤에도 원래 라디오를 먼저 복구합니다. 정리는 원제목·수정제목과 저장한 local/server ID를 함께 확인하고, 소유한 pending·in-flight·사진·불확실 상태는 직접 서버 DELETE나 앱 데이터 제거로 숨기지 않습니다. 현재 경로는 pending 로컬 생성도 자동 취소하지 않고 private snapshot/recovery를 보존한 채 실패합니다. 안전하게 정착한 소유 행만 기존 서버 정리를 허용합니다. 관찰 실패는 정리 성공으로 통과하지 않으며 뒤의 미실행 단계는 `offline-phase-not-executed`입니다.
정리 실패는 `report.xml`의 각 해당 시나리오 플로우에 `fixture-cleanup-failed`와 검토된 고정 진단 코드(운동 정리 안전성·스냅샷/복구 소유권·fixture 읽기/삭제·인증/전송 범주 등)를 함께 남깁니다. offline 전용 정리 코드는 `status=failed`, `operation=cleanup`, `feature=offline`인 결과에만 적용합니다. 공통 인증/private 파일/로컬 세션·fixture 코드는 다른 기능과 작업에서도 전달합니다. `offline_exercise_cleanup_unsafe`는 prepare·offline-observe·feature-observe에서 해당 작업의 일반 실패로 남깁니다. ordinary/timer/offline/chatting은 공통 `safe_cleanup_failure_codes` 계약으로 정리 진단만 선별하고 최초 prepare/UI 실패와 secondary를 각각 보존합니다. 임의 예외·미검토 문자열·알 수 없는 앱 reason은 공개하지 않습니다. 정리 실패 뒤 snapshot/recovery/앱 데이터를 보존하고 후속 시나리오를 중단하는 정책은 바뀌지 않습니다.

타이머 시나리오는 `features/offline.py`의 immutable `OfflineTimerInput`에서 UI 변경 전에 `initialSeconds=45`, `updatedSeconds=75`, `stepName=offline-step`을 한 번 선택합니다. 예를 들어 `--offline-initial-seconds 53 --offline-updated-seconds 97 --offline-step-name set-639`을 위 명령에 붙이면 실제 UI 입력과 독립 서버 GET의 시간·단계명 기대값이 함께 바뀝니다. 초는 5~3599, 단계명은 영숫자로 시작하는 1~40자의 ASCII 영숫자·공백·`_`·`-`만 허용합니다. 미저장 draft의 `02:00`은 별도의 폼 보존 기대값입니다.
호스트는 장치 준비 전에 잘못된 단계명과 같은 생성·수정 시간을 인자 오류로 거절합니다. 초 하나만 지정해도 생략한 값의 기본값 45·75초를 적용한 뒤 서로 달라야 합니다.

prepare/cleanup의 문자열 parameters는 `startedAt`, `endedAt`, `initialSeconds`, `updatedSeconds`, `stepName`입니다. prepare가 run-owned `TIMER_CREATE_TITLE`, `TIMER_ORIGINAL_TITLE`, `TIMER_UPDATED_TITLE`, `TIMER_DELETE_TITLE`, `TIMER_DRAFT_TITLE`, `TIMER_STEP_NAME`, `TIMER_INITIAL_*`/`TIMER_UPDATED_*`(`SECONDS` 총초, `MINUTES_PART`, `SECONDS_PART`, `TIME`)를 반환하며 driver는 반환값을 버리지 않고 UI에 전달합니다. 알 수 없는 키·JSON 숫자·선행 0·범위 밖 입력은 실패합니다.

`offline-observe`는 위 다섯 문자열 입력과 `memberId`, `runId`, `flow`, `stage`를 받고 실제 DEV 인증과 서버 GET을 유지합니다. `timers-online`은 기존/삭제 대상의 initial 시간, `timers-synced`는 생성 initial·수정 updated 시간과 정확한 단계명·개수 1·순서 0을 확인합니다. post-action 앱 값을 기대값으로 만들지 않습니다. private `maestro-offline-state.json`은 사전 입력·회원/flow/run 소유권·기준 localId와 확인한 생성 ID를 보존합니다. 관찰·정리에서 입력이나 소유권이 바뀌면 실패하고 최종 성공한 앱 데이터 제거 전까지 저널을 지우지 않습니다.
준비가 스냅샷 저장 전에 실패했다면 정리는 coordinator의 동일 ownership 확인을 유지하고 실행 이름과 서버 상세를 재확인한 행만 삭제합니다. 스냅샷이 있으면 기존 입력·소유권 검증을 생략하지 않습니다. 서버 변경 전과 각 DELETE 직전에 운동의 실행 제목·캡처 ID에 대한 읽기 전용 보호 검사를 수행합니다. pending·저널·사진·불확실 잔존은 직접 취소/삭제하지 않으며 정리를 실패시켜 원 private snapshot·복구 표식·앱 데이터를 남깁니다. 실패를 통과시키기 위한 사용자 데이터 제거는 하지 않습니다. 공개 보고서에는 `fixture-cleanup-failed`와 검토된 고정 진단 코드만 남기고 임의 예외·서버 본문·결과 파일은 내보내지 않습니다.

타이머 Room ID 관찰은 `feature-observe`의 `timers-local-online`(기존/삭제)·`timers-local-created`(오프라인 생성) 단계입니다. `TIMER_ORIGINAL_LOCAL_ID`, `TIMER_DELETE_LOCAL_ID`, `TIMER_CREATE_LOCAL_ID`를 실제 새 소유 행에서만 얻습니다. 운동 로컬 단계와 함께 정확한 allowlist만 네트워크 인증 없이 Room을 읽습니다. 서버 검증 단계의 실제 인증을 대체하지 않습니다. 타이머 카드 선택은 `custom_timer_<localId>`이며 제목과 시간은 별도로 단언합니다.
각 플로우의 완료 표시는 필수 UI와 독립 관찰을 모두 마친 뒤에만 기록합니다. 타이머의 생성 ID 관찰과 후속 draft UI가 실패한 경우에도 해당 플로우는 완료되지 않습니다.

목록 부재 검사는 `timer_list_scroll`과 운동의 실제 `scroll_view`를 swipe 시작점으로 삼습니다. 겹치는 중간 구간마다 부재를 확인하고 실제 끝 경계까지 필수 검사하며, bounded 반복 한도 소진은 성공이 아닙니다. 타이머는 호출자가 UP 검색을 시작할 수 있는 목록 끝, 운동은 캘린더 위치로 복귀합니다. 날짜 선택은 정확한 ISO 날짜와 선택적인 ` 동기화 대기` 상태만 허용합니다. Maestro 2.11.0이 날짜 표현식을 보간하도록 정규식 끝 앵커 `$`는 `${...}` 밖에 둡니다. 인접 월의 같은 일 숫자는 선택하지 않으며 기존의 현재/직전 월 이동 범위를 넓히지 않습니다. pending 상태 설명은 그대로 노출됩니다.
운동 목록에서 상세로 들어가기 전에는 제목 행을 `centerElement: true`로 화면 안쪽까지 스크롤합니다. 화면 범위 안에 있는 행도 하단 고정 작성 버튼에 가릴 수 있으므로, 단순 가시성 판정만으로 탭하지 않습니다. 제목·시각·상세의 실제 값 검사는 유지하며 고정 좌표나 탐색 우회로 대체하지 않습니다.

이번 병합에서는 코드·빌드·테스트·린트·포매터·에뮬레이터를 실행하지 않았습니다. file-backed Room 회귀 테스트는 생성·수정·삭제 대기와 DB close/reopen 뒤 동일 ID, 서버 GET과 Room ACK의 동시 충족, 삭제 ACK 뒤 완전 부재를 검사합니다. 별도 Room 회귀는 미전송 `CREATE_PENDING` 생성을 재개방 후 취소하고 다시 재개방해 완전 부재를 검사하며, 실제 Worker 동기화 E2E를 대신하지 않습니다. 잘못된 값·중복/orphan·교체 ID·다른 회원·in-flight/저널/사진 잔존과 DELETE 직전 새 대기도 거절합니다. Python 회귀 테스트는 관찰·후속 UI 실패의 미완료 처리, 라디오 복구/증거 보존, 공개 XML 진단의 비밀 차단을 다룹니다. 운영 검증 대상은 Python driver tests, `:app:testE2eUnitTest`, data 단위 테스트, 타이머 UI instrumentation 및 실제 DEV E2E입니다. 단위 테스트 fake나 관찰 ID를 E2E 성공값으로 대신하지 않으며 로컬과 CI는 서로 다른 전용 계정 또는 겹치지 않는 실행 시간을 사용해야 합니다.

#### 사진·충돌·세션 확장(9/9)

| 사례 | 실제 사용자 동작 | 독립 판정 |
| --- | --- | --- |
| `offline-photo-create` | 망 단절 중 A 첨부 생성, A 제거·B 추가/본문 수정, 원본 제거 뒤 재실행, 사진 전송 보류 중 재실행·복구, 마지막 UI 삭제 | `exercise-case-local-created/local-edited/retained`, `baseline-synced/photos-pending/complete/ui-deleted-synced`로 동일 local/server ID, 앱 소유 파일 SHA·서버 사진 ID 집합·내용·삭제 의도와 대기 없음 대조 |
| `offline-conflict-local` / `offline-conflict-server` | 소유 서버 기록의 독립 PATCH와 실제 UI 편집의 순서를 바꿔 두 대표 충돌 발생, 선택창 없이 해결, 미저장 폼 유지 | 독립 version 증가·`editedAt` echo와 최종 서버/Room 본문·사진·시각·버전을 대조. 로컬 최신은 원래 키·오래된 기준 버전의 직접 ACK, 서버 최신은 원래 요청의 확정 거절 후 서버 본문 채택을 요구 |
| `offline-body-retry` | 소유 PATCH만 차단, production Worker 다섯 번째 실제 실패 뒤 오프라인 버튼 부재 확인, 같은 상세 Activity에서 온라인 버튼 탭 | `retry-exhausted`가 WorkManager terminal FAILED/idle과 원래 snapshot을 확인한 뒤에만 버튼 탭. 완료는 독립 GET+Room ACK로 판정 |
| `offline-remote-delete` | 소유 서버 기록 독립 삭제 → 안내·휴지통 → 취소 → 재실행 → 명시 승인 | `remote-deleted/discard-cancelled/retained/discarded`로 취소 원문·사진 유지와 승인한 행만 제거 확인 |
| `offline-session-retain` | 미전송 사진이 있는 상태의 강제 이탈, 같은 A 계정 LoginActivity 복귀, 재개·UI 삭제 | `session-pending/forced-retained/primary-restored`와 실제 BODY/사진 ACK 대조. 강제 이탈은 세션 정책 전이이며 실제 OAuth 만료 시험은 아님 |
| `offline-session-logout` | 손실 경고 취소·로그아웃 승인, A 재로그인 후 남아 있는 서버 본문 UI 삭제 | `session-logout-cancelled/logout-approved`와 승인 삭제 proof, UI 삭제 ACK, `session-primary-restored-clean` 확인 |
| `offline-session-adoption` | 강제 이탈 → B 채택 취소 → A 복귀 → B 채택 승인 → 실제 B 로그아웃 → A 복귀·서버 본문 UI 삭제 | `session-peer-pending/cancelled/approved`, B 독립 GET `session-peer-isolated`, 원 소유 A 복원·UI 삭제 ACK·빈 로컬 proof 확인 |

표의 축약 운동 단계에는 `exercise-case-` 접두사가 붙습니다. 모든 전송 대상 본문·첨부·삭제는 실제 UI로 만들며 fixture가 Worker를 호출하거나 Room 성공 상태를 쓰지 않습니다. e2e 전용 private transport fault는 run·member·session·기록·정확한 PATCH/사진 endpoint만 일치할 때 `IOException`을 발생시킵니다. 독립 fixture API·다른 기록·서버 전체는 차단하지 않으며 이것을 서버가 생성한 실패라고 보고하지 않습니다.

두 충돌 사례는 알려진 본문 시각을 비교하는 실서버 계약을 검사합니다. `offline-conflict-local`은 로컬 `editedAt`이 서버보다 엄격히 최신이므로 오래된 `baseVersion`을 그대로 보낸 원래 요청의 HTTP 200 `ACK`를 요구합니다. 최초 캡처의 키·기준 버전·시각은 UI 저장 증거와 같아야 하며, 모든 전송의 키·기준 버전·시각·직렬화 본문 SHA·의도한 본문 SHA가 동일해야 합니다. 마지막 ACK 전에는 `INJECTED`/`IO_ERROR`만 허용하며, `CONFLICT` 뒤 새 키로 rebase하는 이전 CAS 경로는 이 사례의 통과 조건이 아닙니다.

`offline-conflict-server`는 같은 원래 요청의 확정 `EXERCISE_EDIT_CONFLICT`와 이후 PATCH/ACK 부재를 요구합니다. 클라이언트는 독립 GET으로 확인한 서버 본문을 채택해야 하며 최종 서버/Room 본문·사진과 서버 승자의 시각·버전도 일치해야 합니다. 두 사례 모두 먼저 관측한 서버 version이 원래 로컬 기준보다 증가해야 하며, 시각 선행 조건이 없거나 동률이면 임의 시각으로 성공을 만들지 않습니다.

합성 A/B PNG만 MediaStore에 준비합니다. 전체 갤러리의 파일명·촬영시각·상대 경로와 같은 초의 외부 이미지 모호성을 검사한 뒤 feed와 같은 native 촬영시각 설명+thumbnail selector로 선택합니다. 앱 소유 복사·해시 검증 전에는 소유 원본도 제거하지 않습니다. UI는 URI/URL 대신 사진 수와 실제 Coil `onSuccess`의 loaded tag만 노출합니다.

`photos-pending`은 최초 생성의 BODY ACK 후에도 사진 대기·점수 receipt 미소비를 확인합니다. A→B 교체에서는 생산 전송 순서(사진 삭제 → 본문 수정 → B 업로드)에 맞춰 삭제/추가 의도와 기존 receipt 상태를 고정합니다. 최종 목록 진입·재진입·재실행의 `points-consumed/points-stable`은 실제 receipt 신원·점수·소비 상태·중복 부재를 읽기 전용으로 대조하며 0점도 정상 계약입니다. quota를 초기화하거나 fixture가 receipt를 소비하지 않습니다.

연결/Worker 전후에 같은 폼의 미저장 설명과 화면상 bounds, 같은 목록의 제목·선택 ISO 날짜·행 bounds를 메모리에서 비교합니다. 상세의 실패 안내가 생기는 동안에도 본문 bounds를 비교합니다. hierarchy 원문·외부 필드·스크린샷을 추가 저장하지 않습니다. 색·아이콘·글자 확대·세부 노출 조합은 기존 `ExerciseRetrySurfaceSmokeTest` 범위입니다.

`offline-control`은 공통 `feature=offline`, `flow`, `runId`, 5개 문자열 parameters, 원 소유 `memberId`와 **root `action`**을 받습니다. `stage-primary-login`/`stage-peer-login`만 root `authState`+`targetMemberId` 쌍을 허용합니다. 결과는 `controlled`여야 하며 `observed`로 대체하지 않습니다. 이 작업과 관찰은 force-stop/일반 authenticate/Main 재진입을 하지 않습니다. staged 로그인 직후에는 LoginActivity만 직접 열어 제품 확인창을 실제 탭하며 자동 승인은 없습니다.

기본 bootstrap timeout 300초는 유지합니다. `exercise-case-retry-exhausted` 관찰에만 750초를 전달해 기본 지수 backoff의 실제 다섯 시도를 기다립니다. Worker 상태는 공개 `getWorkInfosForUniqueWorkFlow(...).first()`로 조회마다 한 번 읽고, 기존 종료·실패·성공 조건으로 판정합니다. 버튼 전에 Main 재진입이나 재인증으로 새 작업을 예약하지 않습니다. 세션 복원은 PHOTOS fault를 유지한 온라인 상태에서 실제 등록 검증을 진행하며, 승인한 로컬 폐기는 이미 ACK된 A 서버 본문 삭제를 대신하지 않습니다. A로 복귀한 뒤 해당 서버 본문을 UI에서 삭제하고 production Worker ACK까지 확인한 후 정리합니다.

`offline/<case>/result.json`은 `passed`, `preparation-failed`, `functional-failed`, `interrupted`, `cleanup-failed`를 구분합니다. JUnit의 미실행은 실패 코드로 남기며 준비·정리 실패가 UI 통과로 바뀌지 않습니다. 앱 버전은 실제 설치 metadata에서 읽으며, 버전 코드는 API 26 호환 `PackageInfoCompat.getLongVersionCode`로 가져옵니다. 서버 계약은 독립 PATCH/GET에서 관측한 version/editedAt 증거만 기록합니다. 없는 증거는 `unavailable`이며 PR 병합을 DEV 배포로 추정하지 않습니다. primary/peer 비밀을 함께 redact하고 기존 실패 증거 정책을 재사용합니다.
확장 사례의 native picker·계정 화면에는 테스트 소유가 아닌 사진·문구가 섞일 수 있으므로 CLI 텍스트는 고정 flow/action/오류 코드만 남기며 새 스크린샷은 공개하지 않습니다. 기존 15개 흐름의 실패 증거·안전 진단 수집은 유지합니다. 서버 version/clock 선행 조건 실패는 UI 생성 뒤 발견돼도 준비 실패로 분류하며, 정리까지 실패한 경우 `cleanupStatus`와 `failureStage`를 함께 기록합니다.

실패 시 먼저 원래 라디오와 소유 transport fault 복구를 시도합니다. 복구·정리 안전성이 확인되지 않으면 private snapshot·복구 표식·미전송 본문·앱 소유 사진을 보존하고 후속 사례를 실행하지 않습니다. 정상 사례도 마지막 실제 UI 삭제 ACK 또는 명시 승인한 기기 폐기 proof 없이 fixture cleanup 성공을 받지 못합니다.

결정적 세부 경계는 기존 테스트와 구분합니다. `ExerciseSyncUiStorageTest`는 동일 시각의 서버 승리, 불명 시각 보존, 늦은 로컬 편집/삭제, 점수 receipt를 검사합니다. `ExerciseSyncRepositoryTest`는 사진 응답 유실의 동일 키 재생, 늦은 계정·세션 응답, 일반 404와 명명된 원격 삭제의 구분, stale 폐기 확인을 검사합니다. 두 테스트의 기존 CAS 회귀는 확정 거절 뒤 새 요청키·최신 기준 버전으로 rebase하되 본문·시각을 보존하는 경로를 유지합니다. `ExerciseSyncTransportTest`는 HTTP 직렬화의 `editId`·`baseVersion`·`editedAt` 계약을 검사합니다. 이 단위/Room/HTTP 시험은 실서버 clock 기반 직접 ACK 사례와 별개이며, 실서버 E2E 통과나 실서버 rebase 수행 증거가 아닙니다.

`MaestroOfflineTransportTest`는 응답·요청 본문 보존, 원문·비밀의 trace 미기록, 소유권과 정리를 검사합니다. Robolectric 4.14.1의 `Os.stat`은 소유자·그룹·기타 권한을 구분하지 않으므로 이 JVM 테스트로 Unix 파일 모드를 단언하지 않습니다. 실제 Android writer의 `Os.fchmod(..., 0600)`은 유지하며, 기기에서의 파일 권한 검증은 별도입니다.

사진 오류 안내·사진 수동 재시도·복구 UI는 #659 선행 기능입니다. 이 확장은 사진의 실제 보존·전송·삭제·중단 복구를 다루며 #660의 본문 재시도 버튼으로 사진 복구 UI를 대신하지 않습니다. 본 변경에서는 Python·Kotlin·Maestro·빌드·기기 실행을 수행하지 않았습니다. 위 23개 사례의 실제 DEV 통과, 서버 계약 배포 상태와 기기 동작은 승인된 기존 CI 결과에서 별도로 확인해야 합니다.


descriptor 스키마(모든 필드 필수):

```json
{"flows":["feature-flow"],"apiLevel":30,"fixture":true,"requiredEnvironment":[],"driver":null}
```

- `fixture:true`: `MaestroFeatureFixture`의 `prepare(MaestroFeatureRequest): Map<String,String>`, `cleanup(MaestroFeatureRequest)`를 같은 요청으로 호출합니다. request는 `feature`, `flow`, `runId`, 검증된 `memberId`, private `parameters: JSONObject`입니다. 비어 있는 공통 Hilt multibinding으로 기능 없는 기준 트리도 지원하며, 요청한 handler가 없으면 실패합니다.
- `.maestro/scripts/features/<feature>.py`가 있으면 `driver:null`이어도 `before_flow(core, device, flow, variables, credentials, run_id) -> dict`와 `after_flow(...) -> None` hook을 자동 사용합니다. hook 실패도 backend cleanup을 거칩니다. `E2E_RUN_ID`는 플로우당 16자리 UUID hex이며 prepare/UI/cleanup 간 동일합니다.
- `driver:"<feature>"`는 `run_suite(args, core)` 전체 수명 소유자입니다. 실제 두 기기 채팅이 사용하며 양 기기 인증·locale·ARM ABI 설치 검증·정리를 직접 수행합니다. `--peer-device`로 별도 serial을 주거나 driver가 같은 AVD의 읽기 전용 두 번째 emulator를 만듭니다. 반환 결과는 선언한 모든 `(flow_name, seconds, [fixed_error_codes])`여야 하며 누락/잘못된 결과는 실패입니다.
- 공통 Python API: `read_credentials(prefix="MAESTRO_")`(peer는 `MAESTRO_PEER_`), `Device.bootstrap(operation, credentials=None, timeout=300, feature=None, flow=None, run_id=None, parameters=None, action=None) -> dict`, `run_maestro(device, flow, output, redactor, timeout, fixture_start_date=None, variables=None)`. feature metadata 네 필드는 prepare/cleanup에 함께 전달하며 `offline-control`에는 root action을 추가합니다. driver의 `args.output`은 공통 결과 `Path`입니다.
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
|`push`|`refs/heads/dev`만 허용|`ci`(현재 19개, 두 기기 채팅 4개 제외)|
|`push`|다른 branch/tag ref|트리거 필터에서 제외, 비밀 미제공|
|`pull_request`|base가 `dev`, 같은 저장소 head, 작성자 association `OWNER`/`MEMBER`/`COLLABORATOR`/`CONTRIBUTOR`|`ci`(현재 19개, 두 기기 채팅 4개 제외)|
|`pull_request`|base가 `dev`, fork head 또는 비신뢰 작성자|비밀 없는 거절 job 실패, 인증 job 미실행|
|`pull_request`|다른 base branch|트리거 필터에서 제외, 비밀 미제공|
|`workflow_dispatch`|ref `refs/heads/dev`, 선택한 suite|`regression`, `all`, `ci`|
|`workflow_dispatch`|다른 ref|비밀 없는 거절 job 실패, 인증 job 미실행|

- 인증 suite를 실행하지 않은 skip은 인증 통과가 아닙니다. 거절 job은 미실행 상태를 명시하며 **실패**합니다. `pull_request_target`은 사용하지 않습니다.
- 비공개 조직 멤버는 `pull_request`에서 `CONTRIBUTOR`로 표시될 수 있지만 fork head와 첫 기여 계정(`FIRST_TIME_CONTRIBUTOR`/`FIRST_TIMER`/`NONE`)은 계속 거절하므로, 커밋 이력이 없는 신규 비공개 멤버는 조직 멤버십을 공개하거나 `dev` push/수동 실행(`dev` ref)을 사용해야 합니다.
- 세 실행 경로 모두 **`maestro-dev` 환경**과 기존 `maestro-dev-dedicated-account` concurrency group을 유지합니다. `deployment: false`로 새 Deployment 기록 생성을 끄되, 환경 Secrets와 ref 제한 정책은 계속 적용됩니다. 기존 Deployment 기록과 Actions 검사·로그는 유지됩니다.
- 인증 실행에는 외부 `maestro-dev` 정책에서 `dev`와 각 대상 PR의 정확한 `refs/pull/<번호>/merge` ref를 허용해야 합니다. 워크플로는 이 정책을 생성하거나 넓히지 않습니다. required reviewers 설정도 별도 환경 정책이며 `deployment: false`의 효과가 아닙니다.
- 기존 dev CI는 `lintDebug`, `testDebugUnitTest`, `assembleDebug`와 기존 Ktlint/Firebase 배포 검사를 유지합니다. 기존 master CI는 `lintRelease`, `testReleaseUnitTest`와 릴리스 빌드·배포를 유지합니다. 두 일반 CI는 e2e Firebase 파일을 요구하지 않도록 변형별 검증을 분리합니다.
- Maestro job은 계정 없는 공통 Python 동작 테스트를 먼저 실행하고, `.maestro/scripts/features/`가 있으면 그 디렉터리도 별도로 discovery합니다. 이후 제공한 DEV 설정의 `:app:lintE2e`, `:app:testE2eUnitTest`, `:app:assembleE2e`를 실행한 뒤 기능별 인증 입력을 검증합니다. 소스 빌드 뒤 선택된 descriptor의 필수 입력이 없으면 실제 UI 실행은 실패로 차단하며 smoke 통과로 대체하지 않습니다. JDK17, 선택된 최대 API Google APIs x86_64/KVM/4096MB, 한국어/서울 시간대, 설치 전 실제 ARM ABI 확인과 순차 실행·finally 정리를 사용합니다.
- CI 에뮬레이터는 호스트 기본 시간대에 의존하지 않도록 `-timezone Asia/Seoul`로 시작합니다. `--configure-emulator`의 재부팅 후 한국어·서울 시간대 검증은 유지합니다.
- SDK 설정은 `packages: 'platform-tools'`를 명시해 액션 기본 목록의 구형 `tools` 설치를 제외합니다. `sdkmanager`는 액션이 별도로 설치하는 `cmdline-tools`를 사용합니다.
- 빌드·캐시 복원 전 러너의 미사용 사전 설치 툴체인 `/usr/share/dotnet`, `/usr/local/.ghcup`, `/usr/share/swift`만 제거하며 Android SDK/NDK·JDK·Python·Gradle 캐시와 저장소·테스트 아티팩트는 유지합니다. 정리 전후 워크스페이스·홈과 SDK/시스템 이미지 설치 후 에뮬레이터 시작 직전의 실제 `ANDROID_AVD_HOME` 파일시스템을 `df -h`로 기록합니다. userdata 공간 부족은 앱·Maestro 실행 전 실패입니다.
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

기본 `ci`는 채팅 전용 `MAESTRO_PEER_AUTH_STATE_JSON`, `MAESTRO_PEER_FIXTURE_START_DATE`, `MAESTRO_CHAT_ROOM_ID`를 요구하지 않습니다. matching의 `MAESTRO_PEER_MEMBER_ID`, `MAESTRO_MATCHING_ROOM_ID`와 지도 입력은 계속 필수입니다. `all`과 `chatting`은 채팅 입력도 모두 요구합니다.

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
- `<flow>/driver-connectivity-summary.json`: `run_maestro` 실패 뒤 선택한 기기를 읽기 전용으로 조회한 연결 요약입니다. `adb: {status, state?}`의 state는 `device`/`offline`/`unauthorized`/`bootloader`/`recovery`/`sideload`/`rescue`만 허용합니다. `processes: {status, app_present?, test_present?}`는 정확한 `dev.mobile.maestro`와 `dev.mobile.maestro.test` 이름의 존재만 boolean으로 기록합니다. 이름은 고정 버전의 [applicationId](https://github.com/mobile-dev-inc/Maestro/blob/c8c361be1cc89b2e742f185466a302a22778be6b/maestro-android/build.gradle.kts#L37-L47)와 [instrumentation 대상](https://github.com/mobile-dev-inc/Maestro/blob/c8c361be1cc89b2e742f185466a302a22778be6b/maestro-client/src/main/java/maestro/drivers/AndroidDriver.kt#L125-L135)에서 확인했습니다. `forwards: {status, tcp_ports?: [{local, remote}]}`는 선택 serial에 정확히 일치하는 TCP→TCP 쌍만 정렬/중복 제거하여 최대 32개 기록합니다. 다른 기기·다른 socket 종류·serial·PID·process 목록·stdout/stderr·환경·예외 본문은 저장하지 않습니다.
- `<flow>/driver-failure-summary.json`: 실패한 CLI의 private 로그를 삭제·redaction 전에 읽어 초기 `deviceInfo` RPC 오류, 후속 `deviceInfo` 오류, UI 단언 실패를 구분합니다. `status`는 `captured`/`unrecognized`/`unavailable`이며, RPC 오류에는 표준 gRPC 상태·정확한 `error in frame handler` 문구 여부·허용된 cause class만 기록합니다. 다음 timestamp의 teardown 로그는 같은 예외 원인으로 합치지 않습니다. 임의 메시지·주소·헤더·본문은 복사하지 않습니다.
- `chatting/diagnostics/<phase>/{checkpoint,last-anr-summary,anr-events-summary}.json`: A의 `peer-ready-before-primary-prepare`(B 사용 가능 직후·A 설정 전), `primary-fixture-ready`(A prepare·identity 확인 후·B 설정 전), `receiver-ready-before-primary-launch`(B 준비 barrier 후·A 최초 open 직전), `failure`(A가 존재하는 실패·중단 시 정리 전) 기록입니다. 각 조회는 5초로 제한하며 metadata는 고정 phase, 호스트 캡처 시작/끝 epoch·monotonic과 얻을 수 있는 기기 wall epoch·uptime만 기록합니다.
- `chatting/diagnostics/host-resources.log`: B 생성/부팅 또는 명시적 peer 처리 전부터 A 최초 open 결과까지 Linux 호스트를 1초 간격으로 수집하는 JSONL입니다. 이전 단계 실패·중단 시에도 fixture 정리 전에 중단합니다. CPU capacity·구간 busy/iowait, memory/swap, load, 지원되는 PSI의 허용 숫자와 source 상태만 보관하며 첫 CPU 구간·counter reset은 미측정으로 남깁니다. 최대 5400 samples와 출력 크기 제한을 적용하고 수집 종료·잘림·coverage gap을 고정 상태로 기록합니다. 파일/종료 기록 부재는 수집 완료나 정상 부하의 증거가 아닙니다.

이벤트 요약의 `status`는 `captured`(파싱한 이벤트), `partial`(인식 불가 행도 있어 불완전한 이벤트), `empty`(빈 버퍼/헤더만), `unrecognized`(파싱할 수 없는 내용), `unavailable`(조회 실패)로 구분합니다. checkpoint의 마지막 ANR도 같은 상태 이름으로 빈 보고서·일부 정보·조회 불가를 구분하고 기기 clock은 `captured`/`partial`/`unrecognized`/`unavailable`을 기록합니다. 개별 `unknown` 분류나 빈 결과·파일 부재는 ANR이 없었다는 증거가 아닙니다. 두 ANR 요약은 원문·사용자 데이터·패키지·PID·창 제목·Intent·URL·토큰·예외 본문을 공개하지 않습니다. 읽기·파싱·쓰기 실패는 원래 UI 실패나 fixture 정리를 대체하지 않습니다.

연결 요약의 각 source `status`는 `captured`(관측 성공), `unrecognized`(잘못된 형식/크기 제한 초과), `unavailable`(조회 실패 또는 전체 예산 소진으로 생략)입니다. `captured`의 false/빈 TCP 배열은 관측한 부재이고, 나머지 상태에서는 boolean/port를 생략하여 미측정을 부재로 해석하지 않습니다. 세 ADB 조회 전체에 하나의 monotonic 5초 예산을 적용하고, 각 명령에는 양수인 남은 시간만 전달합니다. 예산이 소진되면 후속 조회는 하지 않습니다. 파싱 입력은 64 KiB/2048행으로 제한합니다. 재연결·forward 생성/삭제·driver 재시작·재시도·기존 timeout 증가는 하지 않습니다. 수집/쓰기 실패는 원래 실패를 대체하거나 fixture 정리를 막지 않으며 중단 신호는 계속 전파합니다. Maestro 종료 후 관측이므로 정상 teardown으로 driver/forward가 이미 제거됐을 수 있고, 이 요약만으로 실패 당시 연결 상태나 원인을 확정할 수 없습니다.
실패 분류기는 추가 ADB 조회 없이 최대 2초의 순회 예산으로 256개 경로·16개 로그·총 1 MiB를 읽습니다. 로그 하나는 최대 256 KiB/4,096줄이며 symlink와 logcat 파일은 제외합니다. cause는 `java.io.EOFException`, `java.io.IOException`, `java.net.SocketException`, `java.net.SocketTimeoutException`, `java.net.ConnectException`, `io.grpc.StatusRuntimeException`만 허용합니다. cause 기록 부재는 `unavailable`, 미허용 class만 있으면 `unrecognized`입니다. 수집 실패는 원래 오류·정리를 바꾸지 않고 중단 신호는 전파합니다. 고정 Maestro 버전은 오류 변환 시 원래 gRPC cause chain을 보존하지 않을 수 있으므로, 이 요약만으로 전송 실패의 근본 원인이나 드라이버 사망을 단정하지 않습니다.

ANR 자료는 기기 과거 이력이며 재부팅으로 지워질 수 있습니다. 이벤트 시각과 실패 hierarchy/로그 시점을 대조하되 호스트·기기 wall clock의 차이/변경과 수집 창을 고려해야 합니다. 캡처 시각은 ANR 발생 시각이 아니며 fixture-ready는 UI 건강 확인이 아닙니다. 호스트 주기 수집은 연속 측정이 아니고 checkpoint의 adb 조회도 실행 부하·순서를 조금 바꿀 수 있습니다. 깨끗한 snapshot이나 sample 사이 간격만으로 순간 contention 또는 측정하지 않은 기기 하위 시스템의 문제를 배제하지 않습니다. 이 변경은 ANR 수정이나 근본 원인 확정이 아닙니다.

Maestro의 HOME/Java user.home은 실행별 임시 디렉터리로 격리하며 analytics, 업데이트 확인, AI 알림을 끕니다. 원본 CLI 임시 출력은 finally에서 제거하고 업로드하지 않습니다. authState는 호스트 메모리/앱 private stdin 경로에만 전달됩니다. 앱은 e2e에서 자격 증명을 포함할 수 있는 Timber 로깅을 사용하지 않습니다. raw logcat과 dumpsys 원문은 파일로 저장하거나 업로드하지 않습니다.

CI는 실패해도 `always()`로 상태 파일과 안전한 XML/log/JSON/PNG만 7일 보관합니다. 합성 입력만 사용하는 `:app:testE2eUnitTest`의 JUnit XML(`app/build/test-results/testE2eUnitTest/*.xml`)과 HTML 보고서(`app/build/reports/tests/testE2eUnitTest/**`)는 별도 `e2e-unit-test-results-${{ github.run_id }}-${{ github.run_attempt }}` artifact로 7일 보관하며, 계정·세션·설정 파일은 업로드하지 않습니다. 이 단위 테스트 결과는 실제 기기 UI 통과 증거가 아닙니다. `workflow-status.log`만 있으면 빌드/설정/에뮬레이터 단계에서 UI 실행 전 실패한 상태일 수 있습니다. 이 파일은 테스트 통과 증거가 아닙니다. stdout의 고정 실패 코드와 JUnit을 먼저 보고 안전한 hierarchy/스크린샷을 함께 확인하세요.

## 검증 상태와 남은 외부 준비

사용자 보고 run `39007766770`에서는 고정 keystore 복원과 APK 빌드가 성공했고 smoke·피드·타이머·로그인은 통과했습니다. matching 세 플로우는 모두 `matching_map_ready` 대기에서 실패했으며 SDK callback의 종류·코드는 아직 수집되지 않았습니다. 이번 SDK 진단 변경은 정적 확인만 했고 로컬 빌드·테스트·기기 실행은 수행하지 않았습니다. 고정 서명 이후에도 실패 원인은 미확정입니다.
이번 ANR checkpoint·호스트 주기 진단은 정적으로만 검토했으며 로컬 Python 테스트·빌드·lint·formatter·adb·Maestro는 실행하지 않았습니다. 게시 후 승인된 CI의 unittest·APK 빌드·실제 인증 Maestro 결과를 별도로 확인해야 하며 진단 추가 후에도 기존 ANR로 실패할 수 있습니다.
`deviceInfo` 실패 분류와 종료 후 연결 요약은 진단 자료이며 전송 오류의 수정이 아닙니다. `INTERNAL - error in frame handler`만으로 드라이버 사망·에뮬레이터 성능 부족을 단정하지 않습니다. 새 실행에서는 Python 회귀 테스트와 실제 Maestro 결과를 별도로 확인하며, 재시도·재연결로 실패를 숨기지 않습니다.

이번 기본 `ci` 선택 변경에서는 로컬 코드 실행·빌드·테스트·lint·formatter·기기 검증을 수행하지 않았습니다. 승인된 원격 CI에서 Python 회귀 테스트·APK 빌드와 채팅을 제외한 19개 플로우의 실제 결과를 확인해야 합니다. 기본 `ci` 성공과 명시적 `chatting`/`all`의 채팅 검증 결과는 별개입니다.

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
