package com.tvman.app

import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView

/** Large, DPAD-operable on-screen keyboard shared by the two independent TV search screens. */
class TvRemoteKeyboardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : LinearLayout(context, attrs) {

    private var target: EditText? = null
    private var searchAction: (() -> Unit)? = null
    private val keys = mutableListOf<View>()
    private val rows = listOf(
        listOf("A", "B", "C", "D", "E", "F", "G", "H"),
        listOf("I", "J", "K", "L", "M", "N", "O", "P"),
        listOf("Q", "R", "S", "T", "U", "V", "W", "X"),
        listOf("Y", "Z", "0", "1", "2", "3", "4", "5"),
        listOf("6", "7", "8", "9", "ESPACIO", "⌫", "BUSCAR")
    )

    init {
        orientation = VERTICAL
        clipChildren = false
        clipToPadding = false
        buildKeyboard()
    }

    fun bindInput(input: EditText, onSearch: () -> Unit) {
        target = input
        searchAction = onSearch
        input.showSoftInputOnFocus = false
    }

    fun focusFirstKey() {
        keys.firstOrNull()?.requestFocus()
    }

    private fun buildKeyboard() {
        rows.forEachIndexed { rowIndex, rowKeys ->
            val row = LinearLayout(context).apply {
                orientation = HORIZONTAL
                gravity = Gravity.CENTER
                clipChildren = false
                clipToPadding = false
                layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, dp(54)).apply {
                    if (rowIndex > 0) topMargin = dp(5)
                }
            }
            rowKeys.forEachIndexed { index, label ->
                val isWide = label == "ESPACIO" || label == "BUSCAR"
                val key = TextView(context).apply {
                    text = when (label) {
                        "ESPACIO" -> "Espacio"
                        "BUSCAR" -> "Buscar"
                        else -> label
                    }
                    contentDescription = when (label) {
                        "ESPACIO" -> "Insertar espacio"
                        "⌫" -> "Borrar carácter"
                        "BUSCAR" -> "Buscar"
                        else -> label
                    }
                    gravity = Gravity.CENTER
                    textSize = if (isWide) 13f else 16f
                    setTextColor(0xFFF2F5FA.toInt())
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                    setBackgroundResource(R.drawable.bg_tv_keyboard_key)
                    foreground = androidx.core.content.ContextCompat.getDrawable(context, R.drawable.tv_focus_ring)
                    isFocusable = true
                    isFocusableInTouchMode = true
                    isClickable = true
                    layoutParams = LayoutParams(0, LayoutParams.MATCH_PARENT, if (isWide) 1.55f else 1f).apply {
                        if (index > 0) marginStart = dp(5)
                    }
                    setOnClickListener { activate(label) }
                }
                row.addView(key)
                keys.add(key)
            }
            addView(row)
        }
    }

    private fun activate(key: String) {
        if (key == "BUSCAR") {
            searchAction?.invoke()
            return
        }
        val edit = target ?: return
        val start = edit.selectionStart.coerceAtLeast(0)
        val end = edit.selectionEnd.coerceAtLeast(start)
        val text = edit.text
        when (key) {
            "⌫" -> {
                if (end > start) text.delete(start, end)
                else if (start > 0) text.delete(start - 1, start)
                edit.setSelection((if (end > start) start else start - 1).coerceAtLeast(0))
            }
            "ESPACIO" -> {
                text.replace(start, end.coerceAtLeast(start), " ")
                edit.setSelection(start + 1)
            }
            else -> {
                text.replace(start, end.coerceAtLeast(start), key)
                edit.setSelection(start + key.length)
            }
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density + 0.5f).toInt()
}
