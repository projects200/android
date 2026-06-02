package com.project200.presentation.base

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DatePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toDrawable
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import com.project200.common.constants.RuleConstants.MIN_YEAR
import com.project200.presentation.compose.applyAppTheme
import com.project200.presentation.compose.components.button.PrimaryButton
import com.project200.presentation.compose.components.button.SecondaryButton
import com.project200.presentation.compose.theme.ColorWhite300
import com.project200.undabang.presentation.R
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Locale

private val DatePickerButtonHeight = 45.dp

class DatePickerDialogFragment : DialogFragment() {
    private val initialDateString: String?
        get() = arguments?.getString(ARG_INITIAL_DATE)
    private val maxDateEpochDay: Long?
        get() =
            arguments
                ?.getLong(ARG_MAX_DATE_EPOCH_DAY, Long.MIN_VALUE)
                ?.takeIf { it != Long.MIN_VALUE }

    private var onDateSelectedListener: ((String) -> Unit)? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View =
        ComposeView(requireContext()).apply {
            applyAppTheme {
                UndabangDatePickerContent(
                    initialDateString = initialDateString,
                    maxDateEpochDay = maxDateEpochDay,
                    onConfirm = { dateStr ->
                        onDateSelectedListener?.invoke(dateStr)
                        dismiss()
                    },
                    onCancel = { dismiss() },
                )
            }
        }

    override fun onStart() {
        super.onStart()
        dialog?.window?.let { window ->
            val screenWidth = resources.displayMetrics.widthPixels
            window.setLayout((screenWidth * 0.85).toInt(), WindowManager.LayoutParams.WRAP_CONTENT)
            window.setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
        }
    }

    companion object {
        private const val ARG_INITIAL_DATE = "initial_date"
        private const val ARG_MAX_DATE_EPOCH_DAY = "max_date_epoch_day"

        fun show(
            fragmentManager: FragmentManager,
            initialDateString: String? = null,
            maxDate: LocalDate? = null,
            onDateSelected: (String) -> Unit,
        ) {
            DatePickerDialogFragment().apply {
                arguments =
                    Bundle().apply {
                        putString(ARG_INITIAL_DATE, initialDateString)
                        if (maxDate != null) putLong(ARG_MAX_DATE_EPOCH_DAY, maxDate.toEpochDay())
                    }
                onDateSelectedListener = onDateSelected
            }.show(fragmentManager, DatePickerDialogFragment::class.java.name)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UndabangDatePickerContent(
    initialDateString: String?,
    maxDateEpochDay: Long?,
    onConfirm: (String) -> Unit,
    onCancel: () -> Unit,
) {
    val initialDate =
        remember(initialDateString) {
            initialDateString?.takeIf { it.isNotBlank() }?.let { LocalDate.parse(it) }
                ?: LocalDate.of(2000, 1, 1)
        }
    val maxLocalDate =
        remember(maxDateEpochDay) {
            maxDateEpochDay?.let { LocalDate.ofEpochDay(it) } ?: LocalDate.now()
        }

    val initialMillis = remember(initialDate) { initialDate.toUtcMillis() }
    val minMillis = remember { LocalDate.of(MIN_YEAR, 8, 15).toUtcMillis() }
    val maxMillis = remember(maxLocalDate) { maxLocalDate.toUtcMillis() }

    val datePickerState =
        rememberDatePickerState(
            initialSelectedDateMillis = initialMillis,
            yearRange = MIN_YEAR..maxLocalDate.year,
            selectableDates =
                object : SelectableDates {
                    override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis in minMillis..maxMillis
                },
        )

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = ColorWhite300,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
        ) {
            DatePicker(
                state = datePickerState,
                title = null,
                headline = null,
                showModeToggle = false,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SecondaryButton(
                    text = stringResource(R.string.cancel),
                    onClick = onCancel,
                    modifier = Modifier.weight(1f),
                    height = DatePickerButtonHeight,
                )
                PrimaryButton(
                    text = stringResource(R.string.confirm),
                    onClick = {
                        val selectedMillis = datePickerState.selectedDateMillis ?: return@PrimaryButton
                        val selectedDate =
                            Instant
                                .ofEpochMilli(selectedMillis)
                                .atOffset(ZoneOffset.UTC)
                                .toLocalDate()
                        val formatted =
                            String.format(
                                Locale.KOREA,
                                "%04d-%02d-%02d",
                                selectedDate.year,
                                selectedDate.monthValue,
                                selectedDate.dayOfMonth,
                            )
                        onConfirm(formatted)
                    },
                    modifier = Modifier.weight(1f),
                    height = DatePickerButtonHeight,
                )
            }
        }
    }
}

private fun LocalDate.toUtcMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
