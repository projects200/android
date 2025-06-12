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
import com.project200.common.utils.CommonDateTimeFormatters.YYYY_M
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

        binding.exerciseCalendar.apply {
            setup(
                YearMonth.now().minusMonths(100),
                YearMonth.now(),
                daysOfWeek(firstDayOfWeek = DayOfWeek.SUNDAY).first()
            )

            scrollToMonth(viewModel.selectedMonth.value ?: YearMonth.now())

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
                if (calendarMonth.yearMonth != viewModel.selectedMonth.value) {
                    viewModel.onMonthChanged(calendarMonth.yearMonth)
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
            binding.dateTv.text = month.format(YYYY_M)

            // 다음 달 버튼 활성화 여부
            binding.nextMonthBtn.isVisible = month.isBefore(YearMonth.now())

            // 캘린더 스크롤 위치 동기화
            if (binding.exerciseCalendar.findFirstVisibleMonth()?.yearMonth != month) {
                binding.exerciseCalendar.smoothScrollToMonth(month)
            }
        }

        viewModel.exerciseDates.observe(viewLifecycleOwner) { dates ->
            exerciseCompleteDates = dates
            binding.exerciseCalendar.notifyCalendarChanged()
        }

        viewModel.toastMessage.observe(viewLifecycleOwner) { message ->
            message?.let { Toast.makeText(requireContext(), it, Toast.LENGTH_SHORT).show() }
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