package com.github.cleveard.scorpion.ui.widgets

import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.DpSize
import com.github.cleveard.scorpion.db.Card
import java.util.Objects.hash

/**
 * The drawable representation of a card
 * @param card The card the drawable is representing
 */
class CardDrawable(
    var card: Card
) {
    private val _offset: MutableState<DpOffset> = mutableStateOf(DpOffset.Zero)
    private val _size: MutableState<DpSize> = mutableStateOf(DpSize.Zero)
    private val _visible: MutableState<Boolean> = mutableStateOf(false)
    private val _imagePath: MutableState<String?> = mutableStateOf(null)
    private val _colorFilter: MutableState<ColorFilter?> = mutableStateOf(null)
    private val _offsetPos: MutableIntState = mutableIntStateOf(0)

    /** Offset of this card in card group */
    var offset: DpOffset
        get() = _offset.value
        set(value) { _offset.value = value }

    /** Size of this card */
    var size: DpSize
        get() = _size.value
        set(value) { _size.value = value }

    /** Flag of whether the card is drawn */
    var visible: Boolean
        get() = _visible.value
        set(value) { _visible.value = value }

    /** The asset path to the card image */
    var imagePath: String?
        get() = _imagePath.value
        set(value) { _imagePath.value = value }

    /** Color filter to apply to card */
    var colorFilter: ColorFilter?
        get() = _colorFilter.value
        set(value) { _colorFilter.value = value }

    /**
     * The number of visible cards and spacers before this card
     */
    var offsetPos: Int
        get() = _offsetPos.intValue
        set(value) { _offsetPos.intValue = value }

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
