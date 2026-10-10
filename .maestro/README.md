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
# 전체
maestro test .maestro/

# 로그인 없이 도는 것만
maestro test --include-tags=smoke --exclude-tags=requires-login .maestro/

# 플로우 하나를 고치면서 (저장할 때마다 재실행)
maestro test -c .maestro/flows/smoke-tabs.yaml

# CI용 리포트
maestro test --format junit --output build/maestro/report.xml .maestro/
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
|`flows/smoke-tabs.yaml`|`smoke` `requires-login`|하단 탭 5개가 눌리고 각 화면이 뜨는지|
|`flows/exercise-form-entry.yaml`|`regression` `requires-login`|기록 탭에서 운동 기록 작성 화면으로 진입하는지|

서브플로우는 `subflows/`에 있습니다. `launch-logged-out`은 세션을 지우고 로그인 화면까지, `launch-logged-in`은 세션을 유지한 채 하단 내비까지 갑니다.

## 셀렉터를 고르는 기준

XML 화면은 `android:id`가 그대로 `id` 셀렉터가 됩니다. Compose 화면은 리소스 ID가 없어 보이는 텍스트로 잡습니다.

주의할 것이 셋 있습니다.

- **런타임에 바뀌는 텍스트는 `id`로 잡습니다.** 운동 기록 화면 하단 버튼은 XML에 `운동 기록하고 점수 얻기`로 적혀 있지만 `ExerciseMainFragment`가 점수에 따라 `운동 기록하고 N점 획득하기`나 `운동 기록하기`로 갈아끼웁니다
- **탭 라벨과 화면 제목이 같으면 텍스트 단언이 헛돕니다.** 피드 탭이 그렇습니다. 하단 내비 항목의 `selected: true`를 함께 겁니다
- **Compose 아이콘 버튼은 아직 못 잡습니다.** `contentDescription`이 전부 `null`이라 접근성 트리에 라벨이 안 올라갑니다. 필요해지는 순서대로 채워야 합니다

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
