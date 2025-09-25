package com.project200.feature.matching.map

import android.view.View
import android.widget.Toast
import androidx.core.content.ContextCompat.getColor
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.bumptech.glide.Glide
import com.kizitonwose.calendar.core.CalendarDay
import com.kizitonwose.calendar.core.DayPosition
import com.kizitonwose.calendar.core.daysOfWeek
import com.kizitonwose.calendar.view.MonthDayBinder
import com.kizitonwose.calendar.view.ViewContainer
import com.project200.common.utils.CommonDateTimeFormatters.YYYY_M_KR
import com.project200.feature.matching.utils.GenderType
import com.project200.presentation.base.BindingFragment
import com.project200.undabang.feature.matching.R
import com.project200.undabang.feature.matching.databinding.CalendarDayLayoutBinding
import com.project200.undabang.feature.matching.databinding.FragmentMatchingProfileBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import kotlin.getValue

@AndroidEntryPoint
class MatchingProfileFragment: BindingFragment<FragmentMatchingProfileBinding> (R.layout.fragment_matching_profile) {
    private val viewModel: MatchingProfileViewModel by viewModels()
    private var exerciseCompleteDates: Set<LocalDate> = emptySet()
    private val args: MatchingProfileFragmentArgs by navArgs()

    override fun getViewBinding(view: View): FragmentMatchingProfileBinding {
        return FragmentMatchingProfileBinding.bind(view)
    }

    override fun setupViews() {
        binding.baseToolbar.apply {
            setTitle(getString(R.string.blank))
            showBackButton(true) { findNavController().navigateUp() }
        }
        initClickListener()
        viewModel.setMemberId(args.memberId)
        setupCalendar()
    }

    private fun initClickListener() {
        binding.prevMonthBtn.setOnClickListener {
            viewModel.onPreviousMonthClicked()
        }

        binding.nextMonthBtn.setOnClickListener {
            viewModel.onNextMonthClicked()
        }
    }

    override fun setupObservers() {
        viewModel.profile.observe(viewLifecycleOwner) { profile ->
            binding.apply {
                setupProfileImage(profile.profileThumbnailUrl, profile.profileImageUrl)

                nicknameTv.text = profile.nickname
                setGenderBirth(profile.gender, profile.birthDate)

                introductionTv.text =
                    if (profile.bio.isNullOrEmpty()) getString(R.string.empty_introduction) else profile.bio

                currentYearExerciseDaysTv.text = profile.yearlyExerciseDays.toString()
                recentExerciseCountsTv.text = profile.exerciseCountInLast30Days.toString()
                scoreTv.text = profile.exerciseScore.toString()
            }
        }

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

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.toast.collect { isVisible ->
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.error_failed_to_load),
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            }
        }
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
                        calendarDayTv.text = data.date.dayOfMonth.toString()

                        // 오늘 날짜 표시, 운동 완료 점을 모두 초기 상태로 리셋합니다.
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
                            if (data.date.isEqual(today)) {
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
                            calendarDayTv.setTextColor(
                                getColor(
                                    requireContext(),
                                    com.project200.undabang.presentation.R.color.gray200,
                                ),
                            )
                        }
                    }
                }
        }
    }

    private fun setupProfileImage(
        thumbnailUrl: String?,
        imageUrl: String?,
    ) {
        val imageRes = thumbnailUrl ?: imageUrl

        Glide.with(binding.profileIv)
            .load(imageRes)
            .placeholder(R.drawable.ic_profile_default)
            .error(R.drawable.ic_profile_default)
            .into(binding.profileIv)
    }

    private fun setGenderBirth(
        gender: String,
        birthDate: String,
    ) {
        val genderStr =
            when (gender) {
                GenderType.MALE.stringValue -> getString(R.string.mypage_male)
                GenderType.FEMALE.stringValue -> getString(R.string.mypage_female)
                else -> getString(R.string.unknown_gender)
            }
        binding.genderBirthTv.text =
            getString(R.string.gender_birth_format, genderStr, birthDate)
    }

    inner class DayViewContainer(val binding: CalendarDayLayoutBinding) : ViewContainer(binding.root)
}