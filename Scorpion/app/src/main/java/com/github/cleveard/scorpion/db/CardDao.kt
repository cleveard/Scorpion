package com.github.cleveard.scorpion.db

import androidx.compose.runtime.MutableState
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = CardDao.TABLE,
    indices = [
        Index(CardDao.GENERATION, CardDao.VALUE, name = CardDao.INDEX, unique = true)
    ]
)
data class CardEntity(
    @PrimaryKey @ColumnInfo(name = CardDao.GENERATION) var generation: Long,
    @ColumnInfo(name = CardDao.VALUE) val value: Int,
    @ColumnInfo(name = CardDao.GROUP) var group: Int,
    @ColumnInfo(name = CardDao.POSITION) var position: Int,
    @ColumnInfo(name = CardDao.FLAGS) val flags: MutableState<Int>
) {
    var faceDown: Boolean
        get() = (flags.value and FACE_DOWN) != 0
        set(value) {
            if (value)
                flags.value = flags.value or FACE_DOWN
            else
                flags.value = flags.value and FACE_DOWN.inv()
        }

    var faceUp: Boolean
        get() = !faceDown
        set(value) {
            faceDown = !value
        }

    var highlight: Int
        get() = flags.value and HIGHLIGHT_MASK
        set(value) {
            flags.value = (flags.value and HIGHLIGHT_MASK.inv()) or (value and HIGHLIGHT_MASK)
        }

    var spread: Boolean
        get() = (flags.value and SPREAD) != 0
        set(value) {
            if (value)
                flags.value = flags.value or SPREAD
            else
                flags.value = flags.value and SPREAD.inv()
        }

    companion object {
        const val HIGHLIGHT_MASK: Int = 0x07
        const val HIGHLIGHT_NONE: Int = 0
        const val FACE_DOWN: Int = 0x08
        const val SPREAD: Int = 0x10
    }
}

@Dao
abstract class CardDao {

    companion object {
        const val TABLE = "card_table"
        const val GENERATION = "card_generation"
        const val VALUE = "card_value"
        const val GROUP = "card_group"
        const val POSITION = "card_position"
        const val FLAGS = "card_flags"
        const val INDEX = "card_index"
    }
}
