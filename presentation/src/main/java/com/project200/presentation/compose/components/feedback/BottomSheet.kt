package com.project200.presentation.compose.components.feedback

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentManager
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.project200.presentation.compose.applyAppTheme
import com.project200.presentation.compose.theme.ColorBlack
import com.project200.presentation.compose.theme.ColorGray300
import com.project200.presentation.compose.theme.contentBold
import com.project200.presentation.compose.theme.contentRegular
import com.project200.undabang.presentation.R

/**
 * Compose 콘텐츠를 담는 공용 BottomSheetDialogFragment.
 *
 * 구체 콘텐츠는 [setContent] 또는 companion 팩토리(예: [showSelection])로 주입한다.
 * 호출 측은 직접 인스턴스를 만들기보다 팩토리를 사용하는 편이 안전하다.
 */
class UndabangBottomSheet : BottomSheetDialogFragment() {
    private var content: (@Composable (onDismiss: () -> Unit) -> Unit)? = null

    fun setContent(content: @Composable (onDismiss: () -> Unit) -> Unit) {
        this.content = content
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, R.style.CustomBottomSheetDialogTheme)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View =
        ComposeView(requireContext()).apply {
            applyAppTheme {
                content?.invoke { dismiss() }
            }
        }

    companion object {
        /**
         * 항목 리스트에서 하나를 선택하는 BottomSheet 표시.
         *
         * 기존 `SelectionBottomSheetDialog` 의 대체. 선택 시 [onItemSelected] 호출 후 자동 dismiss.
         */
        fun showSelection(
            fragmentManager: FragmentManager,
            items: List<String>,
            selectedItem: String? = null,
            onItemSelected: (String) -> Unit,
        ) {
            val sheet = UndabangBottomSheet()
            sheet.setContent { onDismiss ->
                SelectionBottomSheetContent(
                    items = items,
                    selectedItem = selectedItem,
                    onItemSelected = {
                        onItemSelected(it)
                        onDismiss()
                    },
                    onClose = onDismiss,
                )
            }
            sheet.show(fragmentManager, UndabangBottomSheet::class.java.name)
        }
    }
}

/**
 * 항목 리스트 + 선택 표시 + 닫기 버튼 형태의 BottomSheet 콘텐츠.
 *
 * 기존 XML `bottom_sheet_dialog_select` + `item_select` 디자인 그대로 이식.
 */
@Composable
fun SelectionBottomSheetContent(
    items: List<String>,
    selectedItem: String? = null,
    onItemSelected: (String) -> Unit,
    onClose: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        items.forEach { item ->
            val isSelected = item == selectedItem
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable { onItemSelected(item) }
                        .padding(horizontal = 24.dp, vertical = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = item,
                    modifier = Modifier.weight(1f),
                    style =
                        if (isSelected) {
                            MaterialTheme.typography.contentBold
                        } else {
                            MaterialTheme.typography.contentRegular
                        },
                    color = ColorBlack,
                )
                if (isSelected) {
                    Icon(
                        painter = painterResource(R.drawable.ic_select_check),
                        contentDescription = null,
                        tint = Color.Unspecified,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
        }
        HorizontalDivider(color = ColorGray300, thickness = 1.dp)
        Text(
            text = stringResource(R.string.close),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable { onClose() }
                    .padding(vertical = 20.dp),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.contentBold,
            color = ColorBlack,
        )
    }
}
