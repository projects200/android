package com.project200.feature.exercise

import android.view.View
import androidx.core.content.ContextCompat.getColor
import androidx.core.view.isVisible
import com.kizitonwose.calendar.core.CalendarDay
import com.kizitonwose.calendar.core.DayPosition
import com.kizitonwose.calendar.view.MonthDayBinder
import com.kizitonwose.calendar.view.ViewContainer
import com.project200.presentation.base.BindingFragment
import com.project200.presentation.navigator.FragmentNavigator
import com.project200.undabang.feature.exercise.R
import com.project200.undabang.feature.exercise.databinding.CalendarDayLayoutBinding
import com.project200.undabang.feature.exercise.databinding.FragmentExerciseMainBinding
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

class ExerciseMainFragment :
    BindingFragment<FragmentExerciseMainBinding>(R.layout.fragment_exercise_main) {
    private var fragmentNavigator: FragmentNavigator? = null

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
        binding.root.setOnClickListener {
            fragmentNavigator?.navigateFromExerciseMainToExerciseList()
        }

        binding.exerciseCalendar.apply {
            dayBinder = object : MonthDayBinder<DayViewContainer> {
                override fun create(view: View): DayViewContainer {
                    return DayViewContainer(CalendarDayLayoutBinding.bind(view))
                }

                override fun bind(container: DayViewContainer, data: CalendarDay) {
                    container.day = data
                    val textView = container.binding.calendarDayTv
                    val todayView = container.binding.todayIv
                    val completeView = container.binding.exerciseCompleteIv

                    textView.text = data.date.dayOfMonth.toString()

                    // 한국 시간 기준 오늘 날짜
                    val today = LocalDate.now(ZoneId.of("Asia/Seoul"))

                    if (data.position == DayPosition.MonthDate) {
                        // 오늘 날짜일 때 todayIv 표시
                        todayView.isVisible = data.date == today
                        // 운동 완료 날짜일 때 completeIv 표시
                        completeView.isVisible = exerciseCompleteDates.contains(data.date)

                    } else {
                        // 이전/다음 달 날짜 색상 변경
                        textView.setTextColor(
                            getColor(requireContext(), com.project200.undabang.presentation.R.color.gray200)
                        )
                        todayView.isVisible = false
                        completeView.isVisible = false
                    }
                }
            }

            // 달력 범위 설정
            val currentMonth = YearMonth.now()
            val startMonth = currentMonth.minusMonths(100)
            val endMonth = currentMonth.plusMonths(100)
            val firstDayOfWeek = DayOfWeek.SUNDAY

            setup(startMonth, endMonth, firstDayOfWeek)
            scrollToMonth(currentMonth)
        }
    }

    inner class DayViewContainer(val binding: CalendarDayLayoutBinding) :
        ViewContainer(binding.root) {
        lateinit var day: CalendarDay

        init {
            binding.root.setOnClickListener {
                if (day.position == DayPosition.MonthDate) {
                    // 날짜 클릭 시 로직 (필요시 구현)
                }
            }
        }
    }

    override fun onDetach() {
        fragmentNavigator = null
        super.onDetach()
    }
}