package com.github.cleveard.scorpion.db

import androidx.compose.runtime.MutableState
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.MapColumn
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RewriteQueriesToDropUnusedColumns
import androidx.room.Transaction
import com.github.cleveard.scorpion.ui.Game

@Entity(
    tableName = CardDao.TABLE,
    indices = [
        Index(CardDao.GENERATION, CardDao.VALUE, name = CardDao.INDEX, unique = true)
    ]
)
data class CardEntity(
    @ColumnInfo(name = CardDao.GENERATION) val generation: Long,
    @ColumnInfo(name = CardDao.VALUE) val value: Int,
    @ColumnInfo(name = CardDao.GROUP) val group: Int,
    @ColumnInfo(name = CardDao.POSITION) val position: Int,
    @ColumnInfo(name = CardDao.FLAGS) val flags: Int,
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = CardDao.ID, defaultValue = "NULL") var id: Long? = null,
) {

}

data class Card(
    @ColumnInfo(name = CardDao.GENERATION) val generation: Long,
    @ColumnInfo(name = CardDao.VALUE) val value: Int,
    @ColumnInfo(name = CardDao.GROUP) val group: Int,
    @ColumnInfo(name = CardDao.POSITION) val position: Int,
    @ColumnInfo(name = CardDao.FLAGS) private val _flags: MutableState<Int>
) {
    fun toEntity(
        generation: Long = this.generation,
        value: Int = this.value,
        group: Int = this.group,
        position: Int = this.position,
        highlight: Int = this.highlight,
        faceDown: Boolean = this.faceDown,
        spread: Boolean = this.spread
    ): CardEntity {
        return CardEntity(generation, value, group, position, calcFlags(highlight, faceDown, spread))
    }

    val flags: Int
        get() = _flags.value

    val faceDown: Boolean
        get() = (_flags.value and FACE_DOWN) != 0

    val faceUp: Boolean
        get() = !faceDown

    val highlight: Int
        get() = _flags.value and HIGHLIGHT_MASK

    val spread: Boolean
        get() = (_flags.value and SPREAD) != 0

    fun from(entity: CardEntity): Card {
        if (value != entity.value)
            throw IllegalArgumentException("Entity must be for the same card")
        return Card(entity.generation, entity.value, entity.group, entity.position, _flags).also {
            it._flags.value = entity.flags
        }
    }

    override fun toString(): String {
        val suit = when (value / Game.CARDS_PER_SUIT) {
            0 -> "Spade"
            1 -> "Hearts"
            2 -> "Clubs"
            3 -> "Diamonds"
            else -> "Unknown"
        }
        val card = when (value % Game.CARDS_PER_SUIT) {
            0 -> "Ace"
            1 -> "2"
            2 -> "3"
            3 -> "4"
            4 -> "5"
            5 -> "6"
            6 -> "7"
            7 -> "8"
            8 -> "9"
            9 -> "10"
            10 -> "Jack"
            11 -> "Queen"
            12 -> "King"
            else -> "Unknown"
        }
        val down = if (faceDown) "Face down" else "Face up"
        val spr = if (spread) "Spread" else "Stacked"
        return "$card of $suit, gen=$generation, pos=($group,$position), hlt=$highlight, $down, $spr"
    }

    companion object {
        const val HIGHLIGHT_MASK: Int = 0x07
        const val HIGHLIGHT_NONE: Int = 0
        const val FACE_DOWN: Int = 0x08
        const val SPREAD: Int = 0x10

        fun calcFlags(highlight: Int = HIGHLIGHT_NONE, faceDown: Boolean = false, spread: Boolean = false): Int {
            return (highlight and HIGHLIGHT_MASK) or
                (if (faceDown) FACE_DOWN else 0) or
                (if (spread) SPREAD else 0)
        }
    }
}

@Dao
abstract class CardDao {
    /**
     * Insert a card
     */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insert(card: List<CardEntity>)

    /**
     * Clear the table
     */
    @Transaction()
    @Query("DELETE FROM $TABLE")
    abstract suspend fun deleteAll()

    /**
     * Clear any redos past a generation
     * @param generation The redo to start clearing
     */
    @Transaction()
    @Query("DELETE FROM $TABLE WHERE $GENERATION >= :generation")
    abstract suspend fun clearRedo(generation: Long)

    /**
     * Add a list of card at a specific generation
     * @param list The list of cards
     */
    @Transaction()
    open suspend fun addAll(list: Collection<CardEntity>) {
        insert(list.map { it.copy(flags = it.flags and Card.HIGHLIGHT_MASK.inv())})
    }

    /**
     * Get the most recent generation of cards
     * @param generation The generation of the list
      */
    @RewriteQueriesToDropUnusedColumns
    @Query("SELECT * FROM $TABLE JOIN" +
        "(SELECT $VALUE t_value, MAX($GENERATION) t_gen FROM $TABLE WHERE $GENERATION <= :generation GROUP BY $VALUE ORDER BY $VALUE, $GENERATION)" +
        " WHERE $VALUE = t_value AND $GENERATION = t_gen ORDER BY $VALUE")
    abstract suspend fun getAllGeneration(generation: Long): List<Card>

    /**
     * Get the list of cards changed when a generation is undone
     * @param generation The generation to undo
     */
    @RewriteQueriesToDropUnusedColumns
    @Query("SELECT * FROM $TABLE JOIN " +
        "(SELECT $VALUE t_value, MAX($GENERATION) t_gen FROM $TABLE" +
        " WHERE $VALUE in (SELECT $VALUE FROM $TABLE WHERE $GENERATION = :generation) AND $GENERATION < :generation GROUP BY $VALUE ORDER BY $VALUE, $GENERATION)" +
        " WHERE $VALUE = t_value AND $GENERATION = t_gen ORDER BY $VALUE")
    abstract suspend fun undo(generation: Long): List<CardEntity>

    /**
     * Get the list of cards changed when a generation is redone
     * @param generation The generation to redo
     */
    @RewriteQueriesToDropUnusedColumns
    @Query("SELECT * FROM $TABLE WHERE $GENERATION = :generation")
    abstract suspend fun redo(generation: Long): List<CardEntity>

    companion object {
        const val ID = "card_id"
        const val TABLE = "card_table"
        const val GENERATION = "card_generation"
        const val VALUE = "card_value"
        const val GROUP = "card_group"
        const val POSITION = "card_position"
        const val FLAGS = "card_flags"
        const val INDEX = "card_index"
    }
}
