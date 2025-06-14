package com.project200.feature.exercise

import android.content.Context
import android.view.View
import androidx.core.content.ContextCompat.getColor
import androidx.core.view.isVisible
import com.kizitonwose.calendar.core.CalendarDay
import com.kizitonwose.calendar.core.DayPosition
import com.kizitonwose.calendar.core.daysOfWeek
import com.kizitonwose.calendar.view.MonthDayBinder
import com.kizitonwose.calendar.view.ViewContainer
import com.project200.common.utils.CommonDateTimeFormatters.YYYY_M_KOR
import com.project200.presentation.base.BindingFragment
import com.project200.presentation.navigator.FragmentNavigator
import com.project200.undabang.feature.exercise.R
import com.project200.undabang.feature.exercise.databinding.CalendarDayLayoutBinding
import com.project200.undabang.feature.exercise.databinding.FragmentExerciseMainBinding
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class ExerciseMainFragment :
    BindingFragment<FragmentExerciseMainBinding>(R.layout.fragment_exercise_main) {

    private var fragmentNavigator: FragmentNavigator? = null

    // 현재 캘린더가 보여주는 달
    private var selectedMonth = YearMonth.now()

    // 임시 데이터
    private val exerciseCompleteDates = setOf(
        LocalDate.now().minusDays(2),
        LocalDate.now().minusDays(3),
        LocalDate.now().minusDays(4),
        LocalDate.now().plusDays(7),
    )
    override fun getViewBinding(view: View): FragmentExerciseMainBinding {
        return FragmentExerciseMainBinding.bind(view)
    }

    override fun setupViews() {
        super.setupViews()

        setupBtnListeners()

        binding.exerciseCalendar.apply {
            setup(
                YearMonth.now().minusMonths(100),
                YearMonth.now(),
                daysOfWeek(firstDayOfWeek = DayOfWeek.SUNDAY).first()
            )
            scrollToMonth(selectedMonth)

            dayBinder = object : MonthDayBinder<DayViewContainer> {
                override fun create(view: View) = DayViewContainer(CalendarDayLayoutBinding.bind(view))
                override fun bind(container: DayViewContainer, data: CalendarDay) {
                    container.day = data
                    val textView = container.binding.calendarDayTv
                    val todayView = container.binding.todayIv
                    val completeView = container.binding.exerciseCompleteIv
                    textView.text = data.date.dayOfMonth.toString()

                    val today = LocalDate.now(ZoneId.of("Asia/Seoul"))

                    if (data.position == DayPosition.MonthDate) {
                        todayView.isVisible = data.date == today
                        completeView.isVisible = exerciseCompleteDates.contains(data.date)
                    } else {
                        textView.setTextColor(getColor(requireContext(), com.project200.undabang.presentation.R.color.gray200))
                        todayView.isVisible = false
                        completeView.isVisible = false
                    }
                }
            }

            monthScrollListener = { calendarMonth ->
                selectedMonth = calendarMonth.yearMonth
                updateTitle()
                updateNextButtonState()
            }
        }
        updateTitle()
        updateNextButtonState()
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

        binding.settingBtn.setOnClickListener {
            fragmentNavigator?.navigateFromExerciseMainToSetting()
        }

        binding.exerciseCreateBtn.setOnClickListener {
            fragmentNavigator?.navigateFromExerciseMainToExerciseForm()
        }
    }

    private fun updateTitle() {
        binding.dateTv.text = selectedMonth.format(YYYY_M_KOR)
    }

    private fun updateNextButtonState() {
        val currentMonth = YearMonth.now()
        // 현재 선택된 달이 이번 달보다 이전이면 버튼 활성화, 아니면 비활성화
        val isVisible = selectedMonth.isBefore(currentMonth)
        binding.nextMonthBtn.visibility = if(isVisible) View.VISIBLE else View.GONE
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