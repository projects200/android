package com.project200.presentation.base

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import androidx.constraintlayout.widget.ConstraintLayout
import com.project200.undabang.presentation.databinding.ViewBaseToolbarBinding

class BaseToolbar
    @JvmOverloads
    constructor(
        context: Context,
        attrs: AttributeSet? = null,
    ) : ConstraintLayout(context, attrs) {
        private val binding: ViewBaseToolbarBinding

        init {
            val inflater = LayoutInflater.from(context)
            binding = ViewBaseToolbarBinding.inflate(inflater, this, true)
        }

        fun setTitle(title: String) {
            binding.titleTv.text = title
        }

        fun showBackButton(
            show: Boolean,
            onClick: (() -> Unit)? = null,
        ) {
            binding.backBtn.apply {
                setImageResource(com.project200.undabang.presentation.R.drawable.ic_arrow_back)
                visibility = if (show) View.VISIBLE else View.INVISIBLE
                setOnClickListener { onClick?.invoke() }
            }
        }

        fun setLeftButton(
            iconRes: Int?,
            onClick: (() -> Unit)? = null,
        ) {
            binding.backBtn.apply {
                if (iconRes != null) {
                    setImageResource(iconRes)
                    visibility = View.VISIBLE
                    setOnClickListener { onClick?.invoke() }
                } else {
                    visibility = View.INVISIBLE
                }
            }
        }

        fun setSubButton(
            iconRes: Int?,
            onClick: ((View) -> Unit)? = null,
        ) {
            if (iconRes != null) {
                binding.subBtn.apply {
                    setImageResource(iconRes)
                    visibility = View.VISIBLE
                    setOnClickListener { clickedView -> onClick?.invoke(clickedView) }
                }
            } else {
                binding.subBtn.visibility = View.INVISIBLE
            }
        }

        fun setSubButton2(
            iconRes: Int?,
            onClick: ((View) -> Unit)? = null,
        ) {
            if (iconRes != null) {
                binding.subBtn2.apply {
                    setImageResource(iconRes)
                    visibility = View.VISIBLE
                    setOnClickListener { clickedView -> onClick?.invoke(clickedView) }
                }
            } else {
                binding.subBtn2.visibility = View.GONE
            }
        }
    }
