# AGENT.md - Undabang Android Project

> AI Agent를 위한 프로젝트 컨텍스트 문서. 코드베이스 이해와 일관된 개발을 위해 이 파일을 먼저 참조할 것.

## 1. Project Overview

**운다방(Undabang)** - 운동 기록 및 매칭 Android 앱

- **패키지명**: `com.project200.undabang`
- **최소 SDK**: 26 (Android 8.0)
- **타겟 SDK**: 35
- **Kotlin 버전**: 1.9.23
- **JDK**: 17

## 2. Architecture

### 2.1 Clean Architecture + Multi-Module

```
app/                      # Application entry point, DI 통합, MainActivity
├── domain/              # Pure Kotlin (UseCase, Repository interfaces, Models)
├── data/                # Repository 구현체, API, DTO, Mapper, Room DB
├── presentation/        # 공통 UI 컴포넌트, Base 클래스, Utils
├── common/              # 공유 유틸, 상수, DI Qualifiers
├── core/
│   └── oauth/           # OAuth 인증 (Cognito/AppAuth)
└── feature/             # 피처 모듈 (화면 단위)
    ├── auth/            # 로그인, 회원가입
    ├── profile/         # 마이페이지, 설정
    ├── exercise/        # 운동 기록
    ├── timer/           # 타이머 기능
    ├── matching/        # 지도 기반 매칭
    ├── chatting/        # 채팅
    └── feed/            # 피드
```

### 2.2 Data Flow

```
View(Fragment/Activity) → ViewModel → UseCase → Repository(interface) → RepositoryImpl → ApiService/Room
```

### 2.3 Module Dependencies

```kotlin
// Feature 모듈 의존성 패턴
dependencies {
    implementation(projects.domain)      // 필수
    implementation(projects.common)      // 필수
    implementation(projects.presentation) // UI 관련
}

// Data 모듈
dependencies {
    implementation(projects.domain)
    implementation(projects.common)
}
```

## 3. Tech Stack

### Core
| Category | Technology |
|----------|-----------|
| DI | Hilt |
| Network | Retrofit2 + Moshi + OkHttp |
| Local DB | 없음 (Room은 의존성만 선언, 구현 코드 없음) |
| Preferences | EncryptedSharedPreferences (DataStore는 의존성만, 구현 코드 없음) |
| Async | Coroutines + Flow |

### UI
| Category | Technology |
|----------|-----------|
| View | **Compose 우선** + ViewBinding 잔존 (아래 4.4 참조) |
| Navigation | Navigation Component + SafeArgs |
| Image Loading | Glide (XML), Coil (Compose) |
| Map | Kakao Map SDK |

### Testing
| Category | Technology |
|----------|-----------|
| Unit Test | JUnit4 + MockK + Truth + Turbine |
| UI Test | Espresso |
| Coverage | JaCoCo |

### CI/CD
| Category | Technology |
|----------|-----------|
| Lint | Ktlint (org.jlleitschuh.gradle.ktlint) |
| CI | GitHub Actions |
| Distribution | Firebase App Distribution |

## 4. Conventions

### 4.1 Naming

| Type | Convention | Example |
|------|------------|---------|
| DTO | `{Action}{Entity}DTO` | `GetProfileDTO`, `PostLoginRequest` |
| UseCase | `{Action}{Entity}UseCase` | `LoginUseCase`, `GetExerciseRecordListUseCase` |
| Repository | `{Entity}Repository` | `AuthRepository`, `TimerRepository` |
| ViewModel | `{Screen}ViewModel` | `LoginViewModel`, `ExerciseFormViewModel` |
| Fragment | `{Screen}Fragment` | `RegisterFragment`, `ChattingRoomFragment` |

### 4.2 Package Structure (Feature Module)

```
feature/{feature-name}/
└── src/main/java/com/project200/feature/{feature-name}/
    ├── {screen}/
    │   ├── {Screen}Fragment.kt
    │   ├── {Screen}ViewModel.kt
    │   └── adapter/ (if needed)
    └── utils/ (if needed)
```

### 4.3 Code Style

- **Ktlint** 적용 (`.editorconfig` 참조)
- DTO 파일명에서 `ktlint_standard_filename` 비활성화
- ktlint는 `domain` 모듈에 적용되지 않음 (`convention.kotlin.jvm`만 적용). domain은 옆 파일 스타일을 따를 것

#### 주석

주석은 한국어로 쓴다. **종결어미가 붙는 문장은 존댓말로 통일한다** (`~ 합니다`, `~ 됩니다`). 반말(`~ 한다`, `~ 없다`)은 쓰지 않는다. KDoc이 이미 존댓말로 쌓여 있어 여기에 맞춘다

- 라인 끝 마침표는 붙이지 않는다
- 짧은 라벨성 주석은 종결어미 없이 명사구로 끝내도 된다 (`// 운동 장소 삭제`, `// 유효성 검사`)
- 코드를 그대로 옮겨 적지 말고 왜 그렇게 했는지를 남긴다
- 리팩터링할 때 맥락이 맞는 기존 주석은 그대로 둔다. 테스트의 Given/When/Then 포함
- em-dash 남용, 따옴표 강조, 자기자랑 단어("안전하다", "최적화되어 있다")를 피한다

### 4.4 Compose와 ViewBinding 병행

현재 Compose로 전환 중. 두 방식이 함께 있으므로 **고칠 파일의 옆 파일을 먼저 볼 것.**

| | 현황 |
|---|---|
| `{Screen}Screen.kt` (Compose) | 22개 |
| `@Composable` 선언 | 186개 |
| `BindingFragment` 상속 | 10개 |

- **새 화면은 Compose로 만든다.** `AppTheme`으로 감싸고 `@Preview`를 붙인다
- Composable은 상태를 파라미터로 받고 이벤트를 콜백으로 올린다. ViewModel을 직접 참조하지 않는다
- 컨테이너는 Fragment. `ComposeView`에 붙인다
- **기존 XML 화면을 고칠 때는 `BindingFragment`를 그대로 쓴다.** 화면 하나를 고치면서 Compose로 갈아엎지 않는다

## 5. Key Patterns

### 5.1 BaseResult (Domain Layer)

```kotlin
sealed class BaseResult<out T> {
    data class Success<T>(val data: T) : BaseResult<T>()
    data class Error(
        val errorCode: String? = null,
        val message: String?,
        val cause: Throwable? = null
    ) : BaseResult<Nothing>()
}
```

### 5.2 UiState (Presentation Layer)

```kotlin
sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val failure: Failure) : UiState<Nothing>
}

sealed interface Failure {
    data object NetworkError : Failure
    data class ServerError(val code: String?, val message: String?) : Failure
    data object Unknown : Failure
}
```

### 5.3 UseCase Pattern

```kotlin
class LoginUseCase @Inject constructor(
    private val userRepository: AuthRepository
) {
    suspend operator fun invoke(): BaseResult<Unit> {
        return userRepository.login()
    }
}
```

### 5.4 Repository Implementation

```kotlin
class AuthRepositoryImpl @Inject constructor(
    private val apiService: ApiService,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : AuthRepository {

    override suspend fun login(): BaseResult<Unit> {
        return apiCallBuilder(
            ioDispatcher = ioDispatcher,
            apiCall = { apiService.postLogin(PostLoginRequest("ANDROID", "APP")) },
            mapper = { Unit },
        )
    }
}
```

### 5.5 BindingFragment Pattern

```kotlin
@AndroidEntryPoint
class SomeFragment : BindingFragment<FragmentSomeBinding>(R.layout.fragment_some) {
    private val viewModel: SomeViewModel by viewModels()

    override fun getViewBinding(view: View): FragmentSomeBinding {
        return FragmentSomeBinding.bind(view)
    }

    override fun setupViews() {
        // View 초기화, 클릭 리스너 설정
    }

    override fun setupObservers() {
        // LiveData observe
        viewModel.someData.observe(viewLifecycleOwner) { data ->
            // UI 업데이트
        }
    }
}
```

### 5.6 ViewModel Pattern

**StateFlow가 기본**(111곳). LiveData는 기존 XML 화면에 남아 있다(41곳). 새 ViewModel은 StateFlow로 쓴다.

```kotlin
@HiltViewModel
class SomeViewModel @Inject constructor(
    private val someUseCase: SomeUseCase
) : ViewModel() {

    private val _state = MutableStateFlow<UiState<SomeData>>(UiState.Loading)
    val state: StateFlow<UiState<SomeData>> = _state.asStateFlow()

    fun loadData() {
        viewModelScope.launch {
            _state.value = someUseCase().toUiState()
        }
    }
}
```

수집은 `repeatOnLifecycle(Lifecycle.State.STARTED)` 안에서 한다. `repeatOnLifecycle`은 재진입할 때 현재 값을 다시 흘리므로, 한 번만 일어나야 하는 일은 플래그나 `compareAndSet`으로 막는다.

### 5.7 Hilt Module Binding

```kotlin
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    @Singleton
    abstract fun bindAuthRepository(impl: AuthRepositoryImpl): AuthRepository
}
```

## 6. API Convention

### 6.1 BaseResponse

```kotlin
@JsonClass(generateAdapter = true)
data class BaseResponse<T>(
    val succeed: Boolean,
    val code: String,
    val message: String,
    val data: T? = null,
)
```

### 6.2 apiCallBuilder

모든 Repository API 호출은 `apiCallBuilder` 함수를 사용:

```kotlin
suspend fun <DTO, Domain> apiCallBuilder(
    ioDispatcher: CoroutineDispatcher,
    apiCall: suspend () -> BaseResponse<DTO>,
    mapper: (dto: DTO?) -> Domain,
): BaseResult<Domain>
```

### 6.3 Token Annotations

```kotlin
@IdTokenApi        // ID Token 필요 (인증 전)
@AccessTokenApi    // Access Token 필요 (인증 후)
@AccessTokenWithFcmApi  // Access Token + FCM Token 필요
```

## 7. Testing Patterns

### 7.1 UseCase Test

```kotlin
@ExperimentalCoroutinesApi
class GetExerciseRecordListUseCaseTest {
    @get:Rule
    val mockkRule = MockKRule(this)

    @MockK
    private lateinit var mockRepository: ExerciseRecordRepository

    private lateinit var useCase: GetExerciseRecordListUseCase

    @Before
    fun setUp() {
        useCase = GetExerciseRecordListUseCase(mockRepository)
    }

    @Test
    fun `invoke 호출 시 성공 결과 반환`() = runTest {
        // Given
        coEvery { mockRepository.getList(any()) } returns BaseResult.Success(listOf())
        
        // When
        val result = useCase.invoke(LocalDate.now())
        
        // Then
        assertThat(result).isInstanceOf(BaseResult.Success::class.java)
    }
}
```

### 7.2 ViewModel Test

```kotlin
@ExperimentalCoroutinesApi
class SomeViewModelTest {
    @get:Rule
    val mockkRule = MockKRule(this)

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }
}
```

## 8. Build Configuration

### 8.1 Convention Plugins

```kotlin
// Feature 모듈 build.gradle.kts
plugins {
    id("convention.android.library")
    id("convention.android.hilt")
    alias(libs.plugins.navigation.safeargs)
}
```

### 8.2 Available Convention Plugins

| Plugin | Description |
|--------|-------------|
| `convention.android.application` | App 모듈용 |
| `convention.android.library` | Library 모듈용 (ViewBinding, Timber, 테스트 의존성 포함) |
| `convention.android.hilt` | Hilt DI 설정 |
| `convention.kotlin.jvm` | Pure Kotlin 모듈 (domain) |
| `convention.ktlint` | Ktlint 설정 |
| `convention.android.jacoco` | Android JaCoCo 설정 |
| `convention.jvm.jacoco` | JVM JaCoCo 설정 |

## 9. Dispatcher Qualifiers

```kotlin
@IoDispatcher     // Dispatchers.IO
@MainDispatcher   // Dispatchers.Main
@DefaultDispatcher // Dispatchers.Default
```

## 10. Common Commands

```bash
# Lint
./gradlew lint

# Ktlint
./gradlew ktlintCheck
./gradlew ktlintFormat

# Unit Test
./gradlew testDebugUnitTest

# Build Debug
./gradlew assembleDebug

# Build Release
./gradlew assembleRelease

# JaCoCo Report
./gradlew jacocoFullReport
```

## 11. Important Files

| File | Purpose |
|------|---------|
| `gradle/libs.versions.toml` | 버전 카탈로그 (의존성 버전 관리) |
| `local.properties` | 로컬 비밀키 (KAKAO_NATIVE_APP_KEY 등) |
| `build-logic/convention/` | Convention Plugin 정의 |
| `.editorconfig` | Ktlint 규칙 설정 |

## 12. Git Workflow

- **main**: Production 브랜치 (CI/CD → Play Store)
- **dev**: Development 브랜치 (CI → Firebase App Distribution). 작업 브랜치는 여기서 딴다
- 브랜치 이름: `{타입}/{설명}-{이슈번호}` (예: `fix/fcm-token-sync-565`, `refactor/entry-state-562`)
  - 타입은 `feat` `fix` `refactor` `chore` `docs`
- PR Template: `.github/PULL_REQUEST_TEMPLATE.md` 참조. 이슈를 닫으려면 `Closes #번호`를 쓴다

## 13. DO's and DON'Ts

### DO
- `apiCallBuilder` 사용하여 API 호출
- `BaseResult`로 결과 래핑
- Convention Plugin 사용
- Repository interface를 domain에 정의
- UseCase는 single responsibility
- ViewModel에서 StateFlow로 상태 노출
- 새 화면은 Compose로 작성
- `by viewModels()` 위임 사용

### DON'T
- data 모듈에서 domain 구현체 직접 참조 금지
- Presentation/Feature에서 Retrofit/저장소 직접 사용 금지
- 기존 XML 화면을 고치면서 Compose로 갈아엎기 금지
- Room/DataStore가 이미 있다고 가정하고 코드 작성 금지 (의존성만 있고 구현이 없음)
- Compose Compiler 버전을 Kotlin 버전과 따로 올리기 금지
- CancellationException 삼키지 않기 (반드시 rethrow)
- Fragment/Activity에서 ViewModel의 StateFlow/LiveData에 `.value` 직접 접근 금지 (반드시 `collect` 또는 `observe` 사용)
