# Maestro UI 테스트

Maestro는 APK 밖에서 실제 화면을 조작하는 블랙박스 테스트입니다. **자동 실행은 `assembleE2e`와 Python 호스트 러너를 사용합니다.** 기존 debug/release 동작을 우회하지 않고 실제 DEV 계정의 AppAuth 세션을 복원·갱신합니다. 일반 debug/release APK에는 준비 Activity가 없습니다.

앱 ID는 모든 빌드에서 `com.project200.undabang`입니다. e2e는 debug 서명과 DEV Cognito/API 설정을 사용합니다. 같은 ID의 release 앱에 서명이 다른 APK를 덮어쓰면 설치가 실패합니다. 러너는 기존 앱을 자동 제거하지 않습니다. 개인 기기보다 전용 에뮬레이터를 권장합니다.

## 도구와 기기 전제

- Python 3.10 이상(표준 라이브러리만 사용), JDK 17, Android SDK의 `adb`, Maestro **2.11.0**.
- 기기의 언어는 한국어(`ko-KR`), 시간대는 `Asia/Seoul`. DEV API/Cognito에 연결할 수 있어야 합니다.
- 에뮬레이터는 RAM 4096MB 이상. ARM translation을 사용하는 경우 **Android 11/API30 `google_apis` `x86_64`**처럼 ARM 실행을 지원하는 이미지가 필요합니다.
- ARM APK 필터(`armeabi-v7a`, `arm64-v8a`)는 그대로 유지합니다. [공식 ARM translation 안내](https://android-developers.googleblog.com/2020/03/run-arm-apps-on-android-emulator.html)는 Android 11 Google APIs 이미지의 ARM 실행을 설명합니다. 모든 x86 이미지가 이를 지원하는 것은 아닙니다. 러너의 `--apk`는 APK의 실제 ARM 라이브러리와 기기 `ro.product.cpu.abilist`의 교집합을 **설치 전에** 검사하며, 지원 ABI가 없으면 실패합니다.
- `--device`에는 `adb devices`의 serial을 사용합니다. 생략하면 연결된 정상 기기가 정확히 하나여야 합니다.
- `--configure-emulator`는 실제 에뮬레이터인지 확인한 뒤 `adb root`, 한국어/시간대 설정, 재부팅을 수행합니다. Google APIs처럼 root 가능한 이미지가 필요합니다. 물리 기기는 거절합니다. 옵션 없이 실행할 때는 기기의 설정을 먼저 맞춰야 합니다.

설치 예시(개발자가 실행할 명령이며 이 변경 작업에서 실행한 검증이 아닙니다):

```bash
export MAESTRO_VERSION=2.11.0
curl -fsSL https://get.maestro.mobile.dev | bash
export PATH="$HOME/.maestro/bin:$PATH"
maestro --version
```

[실제로 게시된 `cli-2.11.0` 릴리스](https://github.com/mobile-dev-inc/maestro/releases/tag/cli-2.11.0)를 고정합니다. 러너는 다른 CLI 버전을 거절합니다.

## DEV 빌드 준비

기존 `local.properties`에 DEV Cognito 설정과 Kakao 키가 있어야 합니다. `google-services.json`은 현재 패키지 이름으로 등록한 **DEV Firebase** 파일을 `app/src/e2e/google-services.json`에 둡니다. 기존 debug 파일이 같은 DEV 설정이면 복사할 수 있습니다. 두 파일 모두 추적/업로드하지 않습니다.

```bash
mkdir -p app/src/e2e
cp app/src/debug/google-services.json app/src/e2e/google-services.json
./gradlew :app:lintE2e :app:testE2eUnitTest :app:assembleE2e
```

산출물은 `app/build/outputs/apk/e2e/app-e2e.apk`입니다. 새 e2e 빌드의 Firebase/Kakao debug 서명 등록도 외부 서비스에서 유효해야 합니다. 기존 dev CI는 e2e Firebase 설정을 요구하지 않도록 `lintDebug`로 검증 변형을 명시하며 기존 debug 테스트/빌드/배포는 유지합니다.

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

이 파일을 `cat`해서 CI 로그에 넣거나 셸 인자로 전달하지 마세요. 비밀 저장소의 보안 입력 경로로 `authState` 객체의 JSON과 `memberId`를 각각 등록합니다. 내보내기 파일 전체는 `MAESTRO_AUTH_STATE_JSON`이 아닙니다. 토큰 만료/철회/회전 정책에 따라 재로그인과 재내보내기가 필요합니다. 실패한 인증을 로그인된 척 통과시키는 폴백은 없습니다. 로컬 파일과 CI 비밀을 함께 갱신하고 이전 파일/비밀을 폐기하세요.

## 무인 실행: 실제 세션을 매 플로우에 독립 복원

로그인 전용을 제외한 suite에는 아래 세 환경 변수가 **모두 필수**입니다.

|환경 변수|내용|
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
python3 .maestro/scripts/run-tests.py --suite all --device emulator-5554
python3 .maestro/scripts/run-tests.py --suite login --device emulator-5554
```

`--apk`를 생략하면 설치된 e2e 앱을 사용합니다. 러너는 **앱 데이터를 지우기 전에** 준비 컴포넌트와 `run-as` 가능 여부를 확인합니다. 일반 debug/release 앱을 대신 초기화하지 않습니다. protected suite는 비밀이 없으면 기기에 손대기 전에 실패하며 일부 비로그인 테스트만 실행해 성공으로 처리하지 않습니다.

각 플로우의 순서는 다음과 같습니다:

1. 앱 강제 종료와 `pm clear`로 이전 로컬 인증·Room 캐시·권한을 지웁니다.
2. 보호 플로우는 앱 private 파일에 `prepare` 요청을 stdin으로 전달합니다. 요청은 `authState`, `memberId`, `fixtureStartDate`를 포함합니다. Intent extra/deep link에는 인증 정보가 없습니다.
3. e2e Activity가 DEV discovery와 가져온 endpoint/client를 검증하고 실제 토큰을 갱신합니다. 서버 등록 상태/member ID를 확인한 뒤 예약 기록만 정리합니다. `ready` 결과가 아니면 Maestro를 실행하지 않습니다.
4. 실제 UI 플로우를 한 개 실행합니다. Maestro 환경에는 인증/CI 비밀을 상속하지 않습니다. 전달하는 `-e` 값은 UUID 기반 제목 두 개와 기기 한국 시간의 시작/종료 날짜·시간 네 개뿐입니다.
5. 성공, 단언 실패, 타임아웃, 준비 실패, 처리 가능한 중단 신호 모두 `finally`에서 `cleanup`을 요청합니다. 로컬 세션이 사라졌어도 실제 인증을 다시 복원·갱신한 뒤 서버 member ID를 확인합니다. 마지막에는 앱 데이터를 다시 지웁니다. 테스트나 정리 중 하나라도 실패하면 종료 코드는 **1**입니다.

`--flow-timeout` 기본값은 플로우당 900초입니다. bootstrap 작업은 300초 제한입니다. SIGINT/SIGTERM은 실행 중 Maestro 자식을 종료하고 정리를 시도합니다. **SIGKILL, 기기 단절, GitHub 강제 종료/전체 job 타임아웃은 정리 완료를 보장할 수 없습니다.** 다음 정상 prepare가 같은 날짜 범위의 중단 기록을 정리합니다. 정리 실패는 JUnit 실패로 기록되며 숨기지 않습니다.

## fixture 삭제 범위와 병렬 실행

`^maestro-e2e-[a-f0-9]{32}(-edited)?$`에 정확히 맞는 제목은 **검증된 전용 계정에서 이 suite만 소유**합니다. 일반 기록 제목에 이 패턴을 사용하지 마세요. 준비/정리는 시작일~기기 오늘의 월별 운동 횟수를 읽고 기록이 있는 날짜만 조회하여 이 제목만 삭제합니다. 다른 제목·다른 계정·범위 밖 기록은 삭제하지 않습니다. 서버 오류나 계정 불일치 시 삭제를 진행하지 않습니다.

UI가 생성·수정·삭제를 수행합니다. bootstrap은 테스트를 통과시키려고 기록을 대신 생성하지 않습니다. 같은 계정의 실행은 직렬화해야 합니다. **로컬 실행끼리 또는 로컬과 CI가 같은 계정을 동시에 사용하면 서로의 예약 기록을 지울 수 있습니다.** 병렬 실행에는 서로 다른 DEV 계정을 사용하세요.

## suite와 플로우(#607, #608)

|파일|태그|러너 suite|검증|
|---|---|---|---|
|`flows/smoke-tabs.yaml`|`smoke`, `requires-login`|`smoke`, `all`|5개 하단 탭, 실제 피드 콘텐츠/빈 상태, 채팅 서버 응답 콘텐츠/빈 상태|
|`flows/exercise-form-entry.yaml`|`regression`, `requires-login`|`regression`, `all`|실제 기록 폼 진입과 필드 표시|
|`flows/exercise-crud.yaml`|`regression`, `requires-login`|`regression`, `all`|필수값 누락, UI 생성/조회/수정, 재실행 후 영속성, UI 삭제와 전체 목록 부재 검사|
|`flows/login-screen.yaml`|`smoke`|`login`, `smoke`, `all`|clearState 뒤 실제 로그인 화면/버튼|
 
### 기능별 확장 플로우

|파일|태그|검증|
|---|---|---|
|`flows/feed-post-crud.yaml`|`regression`, `requires-login`|피드 작성·수정·삭제와 목록·상세 재조회|
|`flows/feed-like.yaml`|`regression`, `requires-login`|좋아요·취소와 상세 재진입 후 저장 상태|
|`flows/feed-comment.yaml`|`regression`, `requires-login`|댓글·답글 계층, 개수, 본인 댓글 삭제|
|`flows/feed-photo.yaml`|`regression`, `requires-login`|Photo Picker 첨부와 상세 재조회, 기존 사진 교체|
|`flows/feed-form-validation.yaml`|`regression`, `requires-login`|빈 본문 저장 방지와 사진 선택 취소|

피드 플로우는 실행별 고유 ASCII 본문을 사용하고 같은 플로우에서 만든 글·댓글을 정리합니다. 사진 업로드는 미리보기가 아니라 상세 재조회와 이미지 로딩 성공 상태로 확인합니다.

`login-screen`은 세션을 지우므로 직접 Maestro 전체 실행의 `config.yaml`에서도 마지막입니다. 호스트 러너는 앞 플로우의 로그인 상태에 의존하지 않고 매번 복원합니다. 직접 실행한 뒤에는 다시 수동 로그인해야 합니다.

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

## CI 연결 범위

이 단계는 로컬에서 사용할 수 있는 실제 인증/정리 호스트 러너까지 포함합니다. GitHub Actions의 보호 환경·비밀 설정 생성·동일 계정 직렬화·smoke/regression 실행·아티팩트 업로드는 후속 #610 PR에서 추가합니다.

## 결과와 실패 조사

호스트 러너의 안전한 결과는 `build/maestro/<UTC시각>-<run ID>/`에 있습니다:

- `report.xml`: 실제 Maestro 종료 코드/JUnit과 준비·정리 결과를 합친 플로우별 JUnit. 빈 실행/skip/리포트 부재는 실패입니다.
- `<flow>/console.log`, `debug-*.log`, `debug-*.json`: 인증 값/JWT/인증 관련 줄을 제거한 Maestro 출력과 debug 정보. 원본 subprocess 예외/traceback을 출력하지 않습니다.
- `<flow>/screenshot-*.png`: **Maestro 실행 중** 생성된 PNG만 보관합니다. 준비/내보내기 화면은 캡처하지 않습니다. 원본 텍스트에서 민감 정보가 감지되면 이미지도 보관하지 않습니다.

Maestro의 HOME/Java user.home은 실행별 임시 디렉터리로 격리하며 analytics, 업데이트 확인, AI 알림을 끕니다. 원본 CLI 임시 출력은 finally에서 제거하고 업로드하지 않습니다. authState는 호스트 메모리/앱 private stdin 경로에만 전달됩니다. 앱은 e2e에서 자격 증명을 포함할 수 있는 Timber 로깅을 사용하지 않습니다. raw logcat을 수집하지 않습니다.

stdout의 고정 실패 코드와 JUnit을 먼저 보고 안전한 hierarchy/스크린샷을 함께 확인하세요. 원본 인증 파일, APK, google-services/local.properties, raw logcat은 업로드하지 마세요.

## 검증 상태와 남은 외부 준비

이번 구현에서는 **빌드·lint·formatter·테스트·앱/에뮬레이터·설치 명령을 실행하지 않았습니다.** 문서/소스/공개 CLI 옵션·릴리스·ARM translation 안내만 정적으로 확인했습니다. 위 명령은 후속 실행 방법이며 실행 성공 증거가 아닙니다.

최초 실기 검증에는 실제 DEV 전용 등록 계정, 유효한 내보낸 세션, Firebase/Kakao debug 등록과 APK의 ARM ABI를 지원하는 기기가 필요합니다. Compose/XML ID 노출, Android Toast 필수값 단언, 캘린더/시간 선택, CRUD 후 재진입과 정리 실패 경로를 확인해야 합니다. 세션 비밀과 서버 fixture가 실제로 준비되었다고 가정하지 않습니다.
