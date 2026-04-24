# Compose ↔ XML 병행 운영 가이드

기존 Fragment + ViewBinding + XML 구조를 Jetpack Compose 로 점진 마이그레이션할 때의 규약과 함정 정리.

- 현재 프로젝트는 두 체계가 **한동안 공존**한다.
- 핵심 원칙: **한 레이어만 교체하고 나머지는 재사용**한다. Big bang 금지.

---

## 1. 두 방향의 Interop

### 방향 A — XML 안에 Compose 심기 (`ComposeView`)

기존 XML 레이아웃의 일부만 Compose 로 교체할 때 쓴다. 예를 들어 기존 Fragment 의 상단 헤더만 `UndabangTopBar` 로 바꾸기.

```xml
<LinearLayout ...>
    <androidx.compose.ui.platform.ComposeView
        android:id="@+id/top_bar_compose"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"/>
    <!-- 아래는 기존 XML 그대로 -->
    <androidx.recyclerview.widget.RecyclerView .../>
</LinearLayout>
```

```kotlin
override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    binding.topBarCompose.setContent {
        AppTheme {
            UndabangTopBar(
                title = "피드",
                onNavigationClick = { findNavController().popBackStack() },
            )
        }
    }
}
```

**반드시 지킬 것:**
- `AppTheme { ... }` 로 감싸기 — XML 테마는 ComposeView 로 상속되지 않는다. 빠뜨리면 `MaterialTheme.colorScheme.primary` 가 기본 보라색으로 나오고 "색이 왜 이상하지" 로 이어진다.
- **`ViewCompositionStrategy` 설정** — 기본값은 `DisposeOnDetachedFromWindow` 라 RecyclerView item 같이 반복 attach/detach 되는 곳에서 매번 컴포지션이 재생성된다. `DisposeOnViewTreeLifecycleDestroyed` 로 프래그먼트/뷰 라이프사이클에 맞추는 게 기본.

### 방향 B — Compose 안에 XML 심기 (`AndroidView`)

새로 만든 Compose 화면에서 이미 XML 로 구현된 컴포넌트 (KakaoMap, 복잡한 커스텀 뷰, 기존 차트 라이브러리) 를 재사용할 때.

```kotlin
@Composable
fun MapScreen() {
    AndroidView(
        factory = { context -> MapView(context).apply { /* 세팅 */ } },
        update = { view -> /* 상태 변화 반영 */ },
    )
}
```

`factory` 는 한 번만, `update` 는 recomposition 마다 호출된다. 상태 바인딩은 `update` 에서.

---

## 2. Fragment 전환 전략 (권장 순서)

### Phase 1 — Leaf 컴포넌트부터 교체
`PrimaryButton`, `UndabangTextField` 같은 **재사용 UI 를 기존 XML 화면 안에서 ComposeView 로 부분 교체**. 리스크 낮고, 공통 컴포넌트의 실전 검증에도 좋다.

### Phase 2 — 화면 전체를 Compose 로
특정 Fragment 의 `onCreateView` 에서 XML 대신 `ComposeView` 반환:

```kotlin
override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?,
): View = ComposeView(requireContext()).apply {
    setViewCompositionStrategy(
        ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed,
    )
    setContent {
        AppTheme {
            MyScreen(viewModel = hiltViewModel())
        }
    }
}
```

Navigation Graph (XML) / ViewModel / Hilt / Navigation args 는 그대로 재사용. **Fragment 껍데기만 남기고 내용물만 Compose 로** 채우는 방식. 설정·약관·공지처럼 상태 간섭이 적은 화면부터 시작.

### Phase 3 — Navigation-Compose 로 전환 (선택)
모든 Fragment 가 Compose 로 바뀐 뒤 고려. Compose 화면 비율이 70%+ 되기 전까지는 건드리지 않는 편이 안전.

---

## 3. 실전 함정

### 테마 경계
ComposeView 는 XML `AppTheme` 을 모른다. **ComposeView 안쪽에서 `AppTheme { }` 다시 감싸는 걸 빠뜨리지 말 것.** 이걸 잊으면 위에서 말한 보라색 지옥.

프로젝트 관례로 **`applyAppTheme` 같은 확장 함수를 만들어 강제**하는 편이 좋다:

```kotlin
fun ComposeView.applyAppTheme(
    content: @Composable () -> Unit,
) {
    setViewCompositionStrategy(
        ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed,
    )
    setContent {
        AppTheme { content() }
    }
}
```

이후 모든 ComposeView 진입점에서 `composeView.applyAppTheme { ... }` 만 호출하도록 규약화.

### RecyclerView item 안에 ComposeView
기본 전략 `DisposeOnDetachedFromWindow` 는 재활용하며 매번 컴포지션이 폐기·재생성되어 성능이 나쁘다. **`DisposeOnViewTreeLifecycleDestroyed` 또는 `DisposeOnLifecycleDestroyed(viewLifecycleOwner)` 로** 프래그먼트 라이프사이클에 맞출 것.

가능하면 item 레벨의 Compose 진입은 피하고, 리스트 자체를 Compose 로 가져갈 시점을 노리는 게 낫다.

### Dialog / BottomSheet
Compose `Dialog` 는 안드로이드 Dialog 위에 별도 Compose 윈도우를 띄운다. 기존 `DialogFragment` 와 혼용 시 dismiss/backpress/스택 관리가 꼬이기 쉽다. 새 다이얼로그는 Compose 쪽 (`UndabangAlertDialog`) 으로 통일, 기존 DialogFragment 는 화면째 Compose 로 넘어올 때 같이 정리.

### ViewModel 공유
Fragment 의 `viewModels()` · `hiltViewModel()` 둘 다 ComposeView 안에서 그대로 동작한다. Flow 는 `collectAsStateWithLifecycle()`, LiveData 는 `observeAsState()` 로 관찰:

```kotlin
val uiState by viewModel.uiState.collectAsStateWithLifecycle()
```

굳이 LiveData → StateFlow 전환을 먼저 하지 않아도 된다.

### Navigation Args
XML Navigation safe args 는 Fragment 가 받고, 그대로 Compose 에 전달. `ViewModel` 이 `SavedStateHandle` 로 받는 패턴이 가장 깔끔.

### Insets / SystemBar
XML 쪽은 `fitsSystemWindows`, Compose 쪽은 `Modifier.statusBarsPadding()` 계열. 혼용 시 패딩이 중복되기 쉽다. 화면 전체를 Compose 로 바꿀 때는 XML 쪽 `fitsSystemWindows` 를 빼고 Compose modifier 로 일원화.

### Transition / Animation
XML 의 `android:animateLayoutChanges`, `Transition` API 는 ComposeView 경계를 넘지 못한다. 전환 애니메이션이 중요한 화면은 한 체계로 통일해서 만든다.

### Preview vs Runtime 차이
Preview 에서 색이 잘 보이는데 실제 실행에서 다르다면 거의 항상 **ComposeView 안 AppTheme 감싸기 누락**.

---

## 4. 권장 마이그레이션 순서 (이 프로젝트 기준)

1. 공통 컴포넌트 확정 (`PrimaryButton`, `UndabangTextField`, `UndabangTopBar`, `UndabangAlertDialog`, `UndabangAvatar`, `UndabangBadge`, `UndabangCard` 등)
2. **간단한 화면 하나** (설정·약관·공지 등 상태 간섭 적은 화면) 를 Fragment + ComposeView 로 전체 전환해 감 익히기
3. `ComposeView.applyAppTheme` 확장 함수를 공통 모듈에 추가하고 규약화
4. 자주 쓰는 일회성 UI (AlertDialog, BottomSheetDialog) 부터 Compose 로 일괄 교체
5. 목록 화면 (RecyclerView) 은 마지막에. `LazyColumn` 으로 전환할 때는 스크롤 복원·paging 등 부수 작업이 따라오니 독립된 태스크로 다룬다
6. Compose 화면 비율이 70%+ 되면 Navigation-Compose 전환 검토

---

## 5. 버전 · 호환 체크리스트

| 항목 | 현재 값 | 비고 |
|---|---|---|
| Kotlin | 1.9.23 | Compose Compiler 1.5.13 과 짝 |
| Compose Compiler | 1.5.13 | `kotlinCompilerExtensionVersion` 으로 주입 |
| Compose BOM | 2024.02.02 | BOM 으로 Compose 라이브러리 버전 고정 |
| Activity | 1.9.0 | `ComponentActivity.setContent` 지원 |
| Fragment | 1.7.1 | `ComposeView` 안정 지원 |

**주의:**
- Kotlin 2.x 로 올릴 땐 `compose-compiler-gradle-plugin` 방식으로 전환해야 한다 (`kotlinCompilerExtensionVersion` 방식 폐지). 당장 업그레이드 계획은 없으나 라이브러리 업데이트 시 함께 확인.

---

## 6. 체크리스트

새 ComposeView 진입점을 추가할 때:

- [ ] `AppTheme { }` 으로 내용을 감쌌는가 (또는 `applyAppTheme` 확장을 사용했는가)
- [ ] `setViewCompositionStrategy` 를 `DisposeOnViewTreeLifecycleDestroyed` 로 설정했는가 (특히 RecyclerView item, Fragment 내부일 때)
- [ ] ViewModel 상태를 `collectAsStateWithLifecycle` / `observeAsState` 로 관찰하는가
- [ ] XML 쪽 `fitsSystemWindows` 와 Compose 의 `*Padding()` 이 중복되지 않는가
- [ ] Preview 의 색·폰트가 실 실행과 일치하는가 (불일치 = 테마 감싸기 누락 신호)
