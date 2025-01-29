package com.github.cleveard.scorpion.ui.widgets

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ColorFilter
import com.github.cleveard.scorpion.db.Card
import java.util.Objects.hash

class CardDrawable(
    var card: Card
) {
    private val _offset: MutableState<Offset> = mutableStateOf(Offset(0.0f, 0.0f))
    private val _size: MutableState<Size> = mutableStateOf(Size(0.0f, 0.0f))
    private val _visible: MutableState<Boolean> = mutableStateOf(false)
    private val _imagePath: MutableState<String?> = mutableStateOf(null)
    private val _colorFilter: MutableState<ColorFilter?> = mutableStateOf(null)

    var offset: Offset
        get() = _offset.value
        set(value) { _offset.value = value }

    var size: Size
        get() = _size.value
        set(value) { _size.value = value }

    var visible: Boolean
        get() = _visible.value
        set(value) { _visible.value = value }

    var imagePath: String?
        get() = _imagePath.value
        set(value) { _imagePath.value = value }

    var colorFilter: ColorFilter?
        get() = _colorFilter.value
        set(value) { _colorFilter.value = value }

    override fun equals(other: Any?): Boolean {
        return other === this ||
            (other as? CardDrawable)?.let {
                it.card == card && it.offset == offset && it.size == size && it.visible == visible &&
                    it.imagePath == imagePath && it.colorFilter == colorFilter
            }?: false
    }

    override fun hashCode(): Int {
        return hash(card, offset, size, visible, imagePath, colorFilter)
    }

    override fun toString(): String {
        return "Card=$card, Offset=$offset, Size=$size, visible=$visible, image=$imagePath, colorFilter=$colorFilter"
    }
}
