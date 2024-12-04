package com.github.cleveard.scorpion.db

import androidx.compose.runtime.MutableState
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RewriteQueriesToDropUnusedColumns
import androidx.room.Transaction

@Entity(
    tableName = CardDao.TABLE,
    indices = [
        Index(CardDao.GENERATION, CardDao.VALUE, name = CardDao.INDEX, unique = true)
    ]
)
data class CardEntity(
    @ColumnInfo(name = CardDao.GENERATION) var generation: Long,
    @ColumnInfo(name = CardDao.VALUE) val value: Int,
    @ColumnInfo(name = CardDao.GROUP) var group: Int,
    @ColumnInfo(name = CardDao.POSITION) var position: Int,
    @ColumnInfo(name = CardDao.FLAGS) val flags: MutableState<Int>,
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = CardDao.ID, defaultValue = "NULL") var id: Long? = null,
)

data class Card(
    @ColumnInfo(name = CardDao.GENERATION) var generation: Long,
    @ColumnInfo(name = CardDao.VALUE) val value: Int,
    @ColumnInfo(name = CardDao.GROUP) var group: Int,
    @ColumnInfo(name = CardDao.POSITION) var position: Int,
    @ColumnInfo(name = CardDao.FLAGS) val flags: MutableState<Int>
) {
    fun toEntity(): CardEntity {
        return CardEntity(
            generation,
            value,
            group,
            position,
            flags
        )
    }

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
    /**
     * Insert a card
     */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected suspend abstract fun insert(card: CardEntity): Long

    /**
     * Insert a card
     */
    protected suspend fun insert(card: Card): Long {
        return insert(card.toEntity().also { it.flags.value = it.flags.value and Card.HIGHLIGHT_MASK.inv()})
    }

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
     * @param generation The generation for the list
     */
    @Transaction()
    open suspend fun addAll(list: List<Card>, generation: Long) {
        for (card in list) {
            card.generation = generation
            insert(card)
        }
    }

    /**
     * Get the most recent generation of cards
     * @param generation The generation of the list
      */
    @RewriteQueriesToDropUnusedColumns
    @Query("SELECT * FROM $TABLE JOIN (SELECT $VALUE t_value, MAX($GENERATION) t_gen FROM $TABLE WHERE $GENERATION <= :generation GROUP BY $VALUE) WHERE $VALUE = t_value AND $GENERATION = t_gen ORDER BY $VALUE")
    abstract suspend fun getAllGeneration(generation: Long): List<Card>

    /**
     * Get the list of cards changed when a generation is undone
     * @param generation The generation to undo
     */
    @RewriteQueriesToDropUnusedColumns
    @Query("SELECT * FROM $TABLE JOIN " +
        "(SELECT $VALUE t_value, MAX($GENERATION) t_gen FROM $TABLE WHERE $VALUE in " +
        "(SELECT $VALUE FROM $TABLE WHERE $GENERATION = :generation) AND $GENERATION <= :generation - 1 GROUP BY $VALUE)" +
        " WHERE $VALUE = t_value AND $GENERATION = t_gen ORDER BY $VALUE")
    abstract suspend fun undo(generation: Long): List<Card>

    /**
     * Get the list of cards changed when a generation is redone
     * @param generation The generation to redo
     */
    @RewriteQueriesToDropUnusedColumns
    @Query("SELECT * FROM $TABLE WHERE $GENERATION = :generation")
    abstract suspend fun redo(generation: Long): List<Card>

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
