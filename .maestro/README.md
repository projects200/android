# Maestro UI 테스트

앱 밖에서 기기를 조종하는 블랙박스 UI 테스트입니다. 앱에 의존성을 넣지 않고 `assembleDebug`로 나온 그 APK를 그대로 돌립니다.

## 준비

```bash
curl -fsSL "https://get.maestro.mobile.dev" | bash
export PATH="$PATH:$HOME/.maestro/bin"
```

Java 17 이상이 필요합니다. 프로젝트 JDK와 같습니다.

기기는 미리 준비해 둡니다. Maestro는 앱을 설치하지 않습니다.

```bash
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## 실행

```bash
# 수동 로그인한 기기에서 스모크 실행
maestro test .maestro/flows/smoke-tabs.yaml

# 로그인 없이 도는 것만
maestro test --include-tags=smoke --exclude-tags=requires-login .maestro/

# 플로우 하나를 고치면서 (저장할 때마다 재실행)
maestro test -c .maestro/flows/smoke-tabs.yaml

# CI용 리포트
maestro test --format junit --output build/maestro/report.xml .maestro/flows/smoke-tabs.yaml
```

실행 결과와 스크린샷은 `build/maestro/`에 쌓입니다.

## 기기 전제

`requires-login` 태그가 붙은 플로우는 **로그인된 기기**에서만 돕니다.

로그인은 Cognito OAuth라서 AppAuth가 Chrome 커스텀탭을 띄웁니다. 화면이 앱 밖으로 나가고 실제 계정 자격 증명이 필요해 자동화하지 않습니다. 기기에서 한 번 수동으로 로그인해 두면 그 뒤 플로우는 세션을 재사용합니다.

`login-screen`은 `clearState: true`로 세션을 지웁니다. 그래서 `config.yaml`의 `executionOrder`에서 맨 뒤에 둡니다. 이 플로우를 돌린 뒤에는 다시 수동 로그인이 필요합니다.

## 플로우

|파일|태그|무엇을 보나|
|---|---|---|
|`flows/login-screen.yaml`|`smoke`|세션을 지우고 로그인 화면이 뜨는지|
|`flows/smoke-tabs.yaml`|`smoke` `requires-login`|하단 탭 5개와 실제 피드 콘텐츠/빈 상태, 채팅 콘텐츠/성공한 빈 응답|
|`flows/exercise-form-entry.yaml`|`regression` `requires-login`|기록 탭에서 운동 기록 작성 화면으로 진입하는지|
|`flows/exercise-crud.yaml`|`regression` `requires-login`|필수값 누락, UI 생성/조회/수정, 재실행 뒤 영속성, 삭제 뒤 전체 목록 부재|

서브플로우는 `subflows/`에 있습니다. `launch-logged-out`은 세션을 지우고 로그인 화면까지, `launch-logged-in`은 세션을 유지한 채 하단 내비까지 갑니다.

### 운동 기록 CRUD 직접 실행

가입을 완료한 DEV 전용 계정으로 수동 로그인합니다. 제목은 실행마다 새로운 소문자 hex UUID 32자에 `maestro-e2e-`를 붙이고, 수정 제목은 같은 값에 `-edited`를 붙입니다. 다른 실행의 제목을 재사용하지 마세요.

`maestro test`의 `-e NAME=VALUE`로 다음 여섯 값을 모두 전달해야 합니다. 변수 없이 전체 `.maestro/`를 실행하지 마세요.

|변수|값|
|---|---|
|`EXERCISE_TITLE`|이번 실행의 예약 제목|
|`EDITED_EXERCISE_TITLE`|원제목 + `-edited`|
|`EXERCISE_START_HOUR`|기기의 서울 시간 정시−1시간, 두 자리|
|`EXERCISE_END_HOUR`|기기의 서울 시간 정시, 두 자리|
|`EXERCISE_START_DATE`|시작 시각의 날짜, `yyyy.MM.dd`|
|`EXERCISE_END_DATE`|종료 시각의 날짜, `yyyy.MM.dd`|

플로우는 `.maestro/flows/exercise-crud.yaml`을 선택합니다. 폼의 기본 날짜와 이 값이 일치해야 하므로 실행 중 정시/자정 경계를 넘기지 않도록 합니다. UI에서 시작 분을 `00`→`05`로 수정하고 재실행 뒤 저장값을 검사합니다.

삭제 검증은 선택 날짜의 성공한 목록/빈 상태와 RecyclerView 배치 완료를 기다립니다. 겹치는 느린 swipe마다 대상 제목의 부재를 검사하고, 최대 200회 안에 마지막 실제 행의 시간 접근성 레이블에 도달해야 통과합니다. 화면 밖에 남아 있는 기록을 삭제된 것으로 판단하지 않습니다.

성공한 실행은 UI에서 해당 기록을 삭제합니다. 중간 실패 시 시작 날짜의 원제목/수정제목 기록만 수동 삭제하고 다음 실행에 새 제목을 사용합니다. 다른 기록은 지우지 마세요. 자동 인증 복원과 실패 정리는 후속 #609 PR의 호스트 러너에서 제공합니다. 현재 직접 실행에는 그 준비/정리 절차가 없습니다.

## 셀렉터를 고르는 기준

XML 화면은 `android:id`를 `id` 셀렉터로 사용합니다. 피드 Compose 화면은 root의 `testTagsAsResourceId`로 `feed_content`, `feed_empty`를 노출합니다.

주의할 것이 셋 있습니다.

- **런타임에 바뀌는 텍스트는 `id`로 잡습니다.** 운동 기록 화면 하단 버튼은 XML에 `운동 기록하고 점수 얻기`로 적혀 있지만 `ExerciseMainFragment`가 점수에 따라 `운동 기록하고 N점 획득하기`나 `운동 기록하기`로 갈아끼웁니다
- **탭 라벨과 실제 콘텐츠를 구분합니다.** 피드는 `feed_content` 또는 `feed_empty`, 채팅은 `'<nickname>님과의 채팅'` 행 또는 `'채팅 목록에 채팅방이 없습니다'` 접근성 레이블로 검사합니다. 하단 내비의 선택 상태만으로 통과하지 않습니다
- **빈 화면은 조회 성공 뒤에만 인정합니다.** 초기 상태·로딩·실패·늦게 도착한 이전 응답을 성공한 빈 상태로 표시하지 않습니다. 채팅은 RecyclerView 목록 반영도 확인합니다

## 알아 둘 것

- `clearState`는 `adb shell pm clear`와 같습니다. 토큰과 Room 캐시, **권한까지** 초기화됩니다. `permissions: all: allow`만으로는 `POST_NOTIFICATIONS`가 안 잡혀 알림 다이얼로그가 플로우를 가로챕니다. `notifications: allow`를 따로 적습니다
- 콜드 스타트가 느립니다. 스플래시 동안 접근성 트리가 비어 있어 기본 7초 타임아웃으로는 모자랍니다. 진입 대기는 `extendedWaitUntil`로 잡습니다
- 에뮬레이터 RAM이 2GB면 `Process system isn't responding`이 뜹니다. `-memory 4096` 이상으로 띄웁니다
- 한글은 `inputText`로 입력할 수 없습니다. 안드로이드에서 ASCII만 지원합니다. 읽고 검증하는 것은 됩니다
- 카카오맵 마커는 접근성 트리에 올라오지 않습니다. 지도 위에 얹힌 일반 UI만 검증 대상입니다

## 디버깅

```bash
maestro studio       # 화면을 클릭하면 셀렉터를 만들어 줍니다
maestro hierarchy    # 지금 화면의 접근성 트리를 그대로 봅니다
```

실패하면 `build/maestro/<실행시각>/<플로우 이름>/`에 스크린샷과 그 시점의 접근성 트리 JSON이 남습니다. 둘을 같이 봐야 원인이 보입니다. 화면에는 보이는데 트리가 비어 있으면 앱이 아직 안 뜬 것입니다.

## 검증 상태

소스·호출부·플로우와 ViewModel 회귀 테스트를 정적으로 확인했습니다. 이 변경 작업에서는 빌드·단위 테스트·Maestro를 실행하지 않았습니다. 실제 기기에서 콘텐츠/빈 상태/조회 실패와 접근성 선택자를 확인해야 합니다.
