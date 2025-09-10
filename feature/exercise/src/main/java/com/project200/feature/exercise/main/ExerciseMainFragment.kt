package com.project200.feature.exercise.main

import android.view.View
import android.widget.Toast
import androidx.core.content.ContextCompat.getColor
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.kizitonwose.calendar.core.CalendarDay
import com.kizitonwose.calendar.core.DayPosition
import com.kizitonwose.calendar.core.daysOfWeek
import com.kizitonwose.calendar.view.MonthDayBinder
import com.kizitonwose.calendar.view.ViewContainer
import com.project200.common.constants.RuleConstants.SCORE_HIGH_LEVEL
import com.project200.common.constants.RuleConstants.SCORE_MIDDLE_LEVEL
import com.project200.common.utils.CommonDateTimeFormatters.YYYY_M_KR
import com.project200.presentation.base.BindingFragment
import com.project200.undabang.feature.exercise.R
import com.project200.undabang.feature.exercise.databinding.CalendarDayLayoutBinding
import com.project200.undabang.feature.exercise.databinding.FragmentExerciseMainBinding
import dagger.hilt.android.AndroidEntryPoint
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

@AndroidEntryPoint
class ExerciseMainFragment : BindingFragment<FragmentExerciseMainBinding>(R.layout.fragment_exercise_main) {
    private val viewModel: ExerciseMainViewModel by viewModels()
    private var exerciseCompleteDates: Set<LocalDate> = emptySet()
    private val exerciseAdapter: ExerciseListAdapter by lazy {
        ExerciseListAdapter { recordId ->
            // 상세 화면으로 이동
            findNavController().navigate(
                ExerciseMainFragmentDirections.actionExerciseMainFragmentToExerciseDetailFragment(recordId),
            )
        }
    }

    override fun getViewBinding(view: View): FragmentExerciseMainBinding {
        return FragmentExerciseMainBinding.bind(view)
    }

    override fun setupViews() {
        super.setupViews()

        setupBtnListeners()
        setupCalendar()
        setupRecyclerView()

        // 예상 획득 점수 정보 불러오기
        viewModel.loadExpectedScoreInfo()
    }

    private fun setupCalendar() {
        binding.exerciseCalendar.apply {
            // 캘린더 범위, 요일, 초기 날짜 설정
            setup(
                YearMonth.now().minusMonths(100),
                YearMonth.now(),
                daysOfWeek(firstDayOfWeek = DayOfWeek.SUNDAY).first(),
            )

            // 캘린더 월 스크롤 리스너
            monthScrollListener = { calendarMonth ->
                if (calendarMonth.yearMonth != viewModel.selectedMonth.value) {
                    viewModel.onMonthChanged(calendarMonth.yearMonth)
                }
            }

            // 캘린더의 각 날짜(day)를 어떻게 그릴지 정의하는 부분
            dayBinder =
                object : MonthDayBinder<DayViewContainer> {
                    // DayViewContainer 인스턴스를 생성
                    override fun create(view: View) = DayViewContainer(CalendarDayLayoutBinding.bind(view))

                    // 생성된 View에 데이터를 바인딩
                    override fun bind(
                        container: DayViewContainer,
                        data: CalendarDay,
                    ) = with(container.binding) {
                        // DayViewContainer에 CalendarDay 데이터를 저장 (클릭 리스너에서 사용)
                        container.day = data
                        calendarDayTv.text = data.date.dayOfMonth.toString()

                        // 선택 효과, 오늘 날짜 표시, 운동 완료 점을 모두 초기 상태로 리셋합니다.
                        calendarDayTv.background = null
                        selectedIv.isVisible = false
                        exerciseCompleteIv.apply {
                            isVisible = false
                            animate().cancel() // 진행 중인 애니메이션이 있다면 취소
                            alpha = 1f
                        }

                        val today = LocalDate.now(ZoneId.of("Asia/Seoul"))

                        // 캘린더에 표시되는 현재 '월'에 해당하는 날짜일 경우에만 UI 로직을 적용
                        if (data.position == DayPosition.MonthDate) {
                            // 선택된 날짜 하이라이팅 처리
                            if (data.date == viewModel.selectedDate.value) {
                                selectedIv.isVisible = true
                            } else {
                                // 오늘 날짜 이후는 회색, 이전 및 당일은 검은색으로 처리
                                val textColorRes =
                                    if (data.date.isAfter(today)) {
                                        com.project200.undabang.presentation.R.color.gray200
                                    } else {
                                        com.project200.undabang.presentation.R.color.black
                                    }
                                calendarDayTv.setTextColor(getColor(requireContext(), textColorRes))
                            }

                            // 운동 기록이 있는 날 표시
                            if (exerciseCompleteDates.contains(data.date)) {
                                exerciseCompleteIv.apply {
                                    alpha = 0f
                                    isVisible = true
                                    animate().alpha(1f).setDuration(300).start()
                                }
                            }
                        } else {
                            // 이전/다음 달에서 넘어온 날짜들은 모두 회색으로 처리
                            calendarDayTv.setTextColor(getColor(requireContext(), com.project200.undabang.presentation.R.color.gray200))
                        }
                    }
                }
        }
    }

    private fun setupRecyclerView() {
        binding.exerciseListRv.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = exerciseAdapter
        }
    }

    private fun setupBtnListeners() {
        binding.prevMonthBtn.setOnClickListener {
            binding.exerciseCalendar.findFirstVisibleMonth()?.let {
                val previousMonth = it.yearMonth.minusMonths(1)
                binding.exerciseCalendar.smoothScrollToMonth(previousMonth)
            }
        }

        binding.nextMonthBtn.setOnClickListener {
            binding.exerciseCalendar.findFirstVisibleMonth()?.let {
                val nextMonth = it.yearMonth.plusMonths(1)
                binding.exerciseCalendar.smoothScrollToMonth(nextMonth)
            }
        }

        binding.dateTv.setOnClickListener {
            val initialDate = viewModel.selectedMonth.value ?: YearMonth.now()
            // ExerciseYearMonthDialog를 생성하고 초기 날짜를 설정
            ExerciseYearMonthDialog.newInstance(initialDate).apply {
                onDateSelected = { selectedDate ->
                    viewModel.onMonthChanged(YearMonth.from(selectedDate))
                }
            }.show(childFragmentManager, "ExerciseYearMonthDialog")
        }

        binding.exerciseCreateBtn.setOnClickListener {
            findNavController().navigate(ExerciseMainFragmentDirections.actionExerciseMainFragmentToExerciseFormFragment())
        }

        binding.scoreCl.setOnClickListener {
            viewModel.scorePolicy.value?.let {
                ScorePolicyDialog().show(childFragmentManager, "ScorePolicyDialog")
            }
        }
    }

    override fun setupObservers() {
        viewModel.selectedMonth.observe(viewLifecycleOwner) { month ->
            // 날짜 헤더 업데이트
            binding.dateTv.text = month.format(YYYY_M_KR)

            // 다음 달 버튼 활성화 여부
            binding.nextMonthBtn.isVisible = month.isBefore(YearMonth.now())

            // 캘린더 스크롤 이동
            binding.exerciseCalendar.scrollToMonth(month)
        }

        viewModel.exerciseDates.observe(viewLifecycleOwner) { dates ->
            exerciseCompleteDates = dates
            binding.exerciseCalendar.notifyCalendarChanged()
        }

        viewModel.toastMessage.observe(viewLifecycleOwner) { message ->
            Toast.makeText(requireContext(), message ?: getText(R.string.data_error), Toast.LENGTH_SHORT).show()
        }

        viewModel.score.observe(viewLifecycleOwner) { score ->
            binding.scoreProgressBar.apply {
                maxScore = score.maxScore
                minScore = score.minScore
                this.score = score.score
            }
            binding.scoreTv.text = getString(R.string.exercise_score_format, score.score)

            // 점수 레벨 아이콘 설정
            val scoreRange = score.maxScore - score.minScore
            val highLevelScore = (score.minScore + scoreRange * SCORE_HIGH_LEVEL).toInt()
            val middleLevelScore = (score.minScore + scoreRange * SCORE_MIDDLE_LEVEL).toInt()

            binding.scoreLevelIv.setImageResource(
                when {
                    score.score >= highLevelScore -> R.drawable.ic_score_high_level
                    score.score >= middleLevelScore -> R.drawable.ic_score_middle_level
                    else -> R.drawable.ic_score_low_level
                },
            )

            viewModel.earnablePoints.observe(viewLifecycleOwner) { points ->
                binding.exerciseCreateBtn.text =
                    if (points > 0) {
                        // 점수가 0보다 크면, 획득 가능 텍스트를 보여줍니다.
                        getString(R.string.exercise_record_complete_with_points, points)
                    } else {
                        // 점수가 0이면, 기본 텍스트를 보여줍니다.
                        getString(R.string.record_exercise)
                    }
            }
        }

        viewModel.exerciseCount.observe(viewLifecycleOwner) { count ->
            binding.exerciseCntTv.text = getString(R.string.exercise_count_format, count)
        }

        // 선택된 날짜가 변경될 때 UI 업데이트
        var oldDate: LocalDate? = null // 이전 날짜를 저장하여 효율적으로 UI 갱신
        viewModel.selectedDate.observe(viewLifecycleOwner) { newDate ->
            // 새 선택 날짜만 갱신
            oldDate?.let { binding.exerciseCalendar.notifyDateChanged(it) }
            binding.exerciseCalendar.notifyDateChanged(newDate)
            oldDate = newDate
            // 캘린더를 최상단으로 스크롤
            binding.scrollView.smoothScrollTo(0, binding.exerciseCntScoreCl.height.plus(binding.topBarCl.height))
        }

        // 운동 목록이 변경될 때 RecyclerView 업데이트
        viewModel.exerciseList.observe(viewLifecycleOwner) { list ->
            exerciseAdapter.submitList(list)
            binding.exerciseListEmptyTv.isVisible = list.isNullOrEmpty()
        }
    }

    inner class DayViewContainer(val binding: CalendarDayLayoutBinding) :
        ViewContainer(binding.root) {
        lateinit var day: CalendarDay

        init {
            binding.root.setOnClickListener {
                if (day.position == DayPosition.MonthDate && !day.date.isAfter(LocalDate.now(ZoneId.of("Asia/Seoul")))) {
                    if (viewModel.selectedDate.value != day.date) {
                        viewModel.onDateSelected(day.date)
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshData()
    }
}
