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
import com.project200.feature.exercise.list.ExerciseYearMonthDialog
import com.project200.presentation.base.BindingFragment
import com.project200.presentation.navigator.FragmentNavigator
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
    private var fragmentNavigator: FragmentNavigator? = null
    private var exerciseCompleteDates: Set<LocalDate> = emptySet()


    override fun getViewBinding(view: View): FragmentExerciseMainBinding {
        return FragmentExerciseMainBinding.bind(view)
    }

    override fun setupViews() {
        super.setupViews()

        setupBtnListeners()
        setupScore()
        setupCalendar()
    }

    private fun setupScore() {

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

        binding.settingBtn.setOnClickListener {
            fragmentNavigator?.navigateFromExerciseMainToSetting()
        }

        binding.exerciseCreateBtn.setOnClickListener {
            fragmentNavigator?.navigateFromExerciseMainToExerciseForm()
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
            binding.scoreProgressBar.score = score
            binding.scoreTv.text = getString(R.string.exercise_score_format, score)
            binding.exerciseCntTv.text = getString(R.string.exercise_count_format, 0)

            binding.scoreLevelIv.setImageResource(
                 when {
                     score >= SCORE_HIGH_LEVEL -> R.drawable.ic_score_high_level
                     score >= SCORE_MIDDLE_LEVEL -> R.drawable.ic_score_middle_level
                     else -> R.drawable.ic_score_low_level
                 }
             )
        }
    }


    inner class DayViewContainer(val binding: CalendarDayLayoutBinding) :
        ViewContainer(binding.root) {
        lateinit var day: CalendarDay

        init {
            binding.root.setOnClickListener {
                if (day.position == DayPosition.MonthDate && !day.date.isAfter(LocalDate.now(ZoneId.of("Asia/Seoul")))) {
                    fragmentNavigator?.navigateFromExerciseMainToExerciseList(day.date)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshData()
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is FragmentNavigator) {
            fragmentNavigator = context
        } else {
            throw ClassCastException("$context must implement FragmentNavigator")
        }
    }

    override fun onDetach() {
        fragmentNavigator = null
        super.onDetach()
    }
}