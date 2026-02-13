package com.project200.presentation.base

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import com.project200.undabang.presentation.databinding.ViewBaseToolbarBinding

class BaseToolbar
    @JvmOverloads
    constructor(
        context: Context,
        attrs: AttributeSet? = null,
    ) : LinearLayout(context, attrs) {
        private val binding: ViewBaseToolbarBinding

        init {
            val inflater = LayoutInflater.from(context)
            binding = ViewBaseToolbarBinding.inflate(inflater, this, true)
            orientation = HORIZONTAL
        }

        fun setTitle(title: String) {
            binding.titleTv.text = title
        }

        fun showBackButton(
            show: Boolean,
            onClick: (() -> Unit)? = null,
        ) {
            binding.backBtn.apply {
                visibility = if (show) View.VISIBLE else View.INVISIBLE
                setOnClickListener { onClick?.invoke() }
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

        fun setSecondarySubButton(
            iconRes: Int?,
            onClick: ((View) -> Unit)? = null,
        ) {
            if (iconRes != null) {
                binding.subBtnSecondary.apply {
                    setImageResource(iconRes)
                    visibility = View.VISIBLE
                    setOnClickListener { clickedView -> onClick?.invoke(clickedView) }
                }
            } else {
                binding.subBtnSecondary.visibility = View.GONE
            }
        }
    }
