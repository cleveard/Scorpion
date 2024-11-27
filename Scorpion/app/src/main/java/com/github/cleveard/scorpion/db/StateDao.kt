package com.github.cleveard.scorpion.db

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.Objects

@Entity(
    tableName = StateDao.TABLE,
    indices = [
        Index(StateDao.GENERATION, StateDao.GAME, name = StateDao.INDEX, unique = true)
    ]
)
data class StateEntity(
    @PrimaryKey @ColumnInfo(name = StateDao.GENERATION) var generation: Long,
    @ColumnInfo(name = StateDao.GAME) var game: String,
    @ColumnInfo(name = StateDao.STATE) var state: ByteArray? = null,
    @ColumnInfo(name = StateDao.FLAGS) var flags: Int = 0,
) {
    var undone: Boolean
        get() = (flags and UNDONE) != 0
        set(value) {
            if (value)
                flags = flags or UNDONE
            else
                flags = flags and UNDONE.inv()
        }

    override fun equals(other: Any?): Boolean {
        if (other === this)
            return true
        val v = (other as? StateEntity)?: return false
        return generation == v.generation &&
            game == v.game &&
            state.contentEquals(v.state) &&
            flags == v.flags
    }

    override fun hashCode(): Int {
        return Objects.hash(generation, game, state.contentHashCode(), flags)
    }

    companion object {
        const val UNDONE: Int = 0x01
    }
}

@Dao
abstract class StateDao {

    companion object {
        const val TABLE = "state_table"
        const val GENERATION = "state_generation"
        const val GAME = "state_game"
        const val STATE = "state_state"
        const val FLAGS = "state_flags"
        const val INDEX = "state_index"
    }
}