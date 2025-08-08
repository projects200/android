package com.project200.feature.exercise.main

import android.content.Context
import android.view.View
import android.widget.Toast
import androidx.core.content.ContextCompat.getColor
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import com.kizitonwose.calendar.core.CalendarDay
import com.kizitonwose.calendar.core.DayPosition
import com.kizitonwose.calendar.core.daysOfWeek
import com.kizitonwose.calendar.view.MonthDayBinder
import com.kizitonwose.calendar.view.ViewContainer
import com.project200.common.constants.RuleConstants.SCORE_HIGH_LEVEL
import com.project200.common.constants.RuleConstants.SCORE_MIDDLE_LEVEL
import com.project200.common.utils.CommonDateTimeFormatters.YYYY_M_KR
import com.project200.presentation.base.BindingFragment
import androidx.navigation.fragment.findNavController
import com.project200.presentation.navigator.FragmentNavigator
import com.project200.undabang.feature.exercise.R
import com.project200.undabang.feature.exercise.databinding.CalendarDayLayoutBinding
import com.project200.undabang.feature.exercise.databinding.FragmentExerciseMainBinding
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

@AndroidEntryPoint
class ExerciseMainFragment : BindingFragment<FragmentExerciseMainBinding>(R.layout.fragment_exercise_main) {
    private val viewModel: ExerciseMainViewModel by viewModels()
    private var exerciseCompleteDates: Set<LocalDate> = emptySet()


    override fun getViewBinding(view: View): FragmentExerciseMainBinding {
        return FragmentExerciseMainBinding.bind(view)
    }

    override fun setupViews() {
        super.setupViews()

        setupBtnListeners()
        setupCalendar()
    }

    private fun setupCalendar() {
        binding.exerciseCalendar.apply {
            // 캘린더 범위, 요일, 초기 날짜 설정
            setup(
                YearMonth.now().minusMonths(100),
                YearMonth.now(),
                daysOfWeek(firstDayOfWeek = DayOfWeek.SUNDAY).first()
            )

            monthScrollListener = { calendarMonth ->
                if (calendarMonth.yearMonth != viewModel.selectedMonth.value) {
                    viewModel.onMonthChanged(calendarMonth.yearMonth)
                }
            }

            dayBinder = object : MonthDayBinder<DayViewContainer> {
                override fun create(view: View) = DayViewContainer(CalendarDayLayoutBinding.bind(view))
                override fun bind(container: DayViewContainer, data: CalendarDay) = with(container.binding) {
                    // 초기값 설정
                    container.day = data
                    calendarDayTv.text = data.date.dayOfMonth.toString()
                    calendarDayTv.setTextColor(getColor(requireContext(), com.project200.undabang.presentation.R.color.gray200))
                    todayIv.isVisible = false
                    exerciseCompleteIv.apply {
                        isVisible = false
                        animate().cancel()
                        alpha = 1f
                    }

                    // 이번 달에 해당하는 날짜일 경우
                    if (data.position == DayPosition.MonthDate) {
                        val today = LocalDate.now(ZoneId.of("Asia/Seoul"))
                        todayIv.isVisible = data.date == today

                        // 오늘 날짜 이후는 회색, 이전 및 당일은 검은색으로 처리
                        calendarDayTv.setTextColor(getColor(requireContext(),
                            if (data.date.isAfter(today)) com.project200.undabang.presentation.R.color.gray200
                            else com.project200.undabang.presentation.R.color.black)
                        )

                        // 운동 기록이 있는 날 && 애니메이션 중복 방지
                        if (exerciseCompleteDates.contains(data.date) && !exerciseCompleteIv.isVisible) {
                            exerciseCompleteIv.apply {
                                alpha = 0f
                                isVisible = true
                                animate().alpha(1f).setDuration(300).start()
                            }
                        }
                    }
                }
            }
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
                 }
             )
        }

        viewModel.exerciseCount.observe(viewLifecycleOwner) { count ->
            binding.exerciseCntTv.text = getString(R.string.exercise_count_format, count)
        }
    }


    inner class DayViewContainer(val binding: CalendarDayLayoutBinding) :
        ViewContainer(binding.root) {
        lateinit var day: CalendarDay

        init {
            binding.root.setOnClickListener {
                if (day.position == DayPosition.MonthDate && !day.date.isAfter(LocalDate.now(ZoneId.of("Asia/Seoul")))) {
                    findNavController().navigate(ExerciseMainFragmentDirections.actionExerciseMainFragmentToExerciseListFragment(day.date))
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshData()
    }
}