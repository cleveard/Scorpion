package com.github.cleveard.scorpion.db

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
import com.github.cleveard.scorpion.ui.games.Game

/**
 * The entity for the Card Table in the data base
 *
 * This table is organized by value and generation of a card. When cards
 * are dealt, all of them are added as generation 0. As cards are changed,
 * each changed card is added with an incrementing generation. Undo and
 * redo extract the appropriate cards based on the generation.
 */
@Entity(
    tableName = CardDao.TABLE,
    indices = [
        Index(
            value = [CardDao.VALUE, CardDao.GENERATION],
            unique = true,
            orders = [Index.Order.ASC, Index.Order.ASC]
        )
    ]
)
data class Card(
    /** Generation this card was changed */
    @ColumnInfo(name = CardDao.GENERATION) val generation: Long,
    /**
     * The card value.
     *
     * Spades - ace through king is 0 to 12
     * Hearts - ace through king is 13 to 25
     * Clubs - ace through king is 26 to 38
     * Diamonds - ace through king is 39 to 51
     * */
    @ColumnInfo(name = CardDao.VALUE) val value: Int,
    /** The card group the card is in */
    @ColumnInfo(name = CardDao.GROUP) val group: Int,
    /** The card position within its group */
    @ColumnInfo(name = CardDao.POSITION) val position: Int,
    /** Flags */
    @ColumnInfo(name = CardDao.FLAGS) val flags: Int
) {
    /**
     * Primary key
     *
     * This key isn't included in copy or equals or hashCode. Since we always
     * copy the card to update it, this will be null when we insert a card into the database.
     * */
    @PrimaryKey @ColumnInfo(name = CardDao.ID, defaultValue = "NULL") var id: Long? = null

    /**
     * Copy function that copies values for the flags
     */
    fun copy(
        generation: Long = this.generation,
        value: Int = this.value,
        group: Int = this.group,
        position: Int = this.position,
        highlight: Int = this.highlight,
        faceDown: Boolean = this.faceDown,
        spread: Boolean = this.spread
    ): Card {
        return Card(generation, value, group, position, calcFlags(highlight, faceDown, spread))
    }

    /** Is the card face down */
    val faceDown: Boolean
        get() = (flags and FACE_DOWN) != 0

    /** Is the card face up */
    val faceUp: Boolean
        get() = !faceDown

    /** How is the card highlighted */
    val highlight: Int
        get() = flags and HIGHLIGHT_MASK

    /** Is this card not offset from the precious one in the group */
    val spread: Boolean
        get() = (flags and SPREAD) != 0

    /** Convert card to a readable string */
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
        /** Mask for highlight in the flags */
        const val HIGHLIGHT_MASK: Int = 0x07
        /** The value for no highlight */
        const val HIGHLIGHT_NONE: Int = 0
        /** Mask for face down card */
        const val FACE_DOWN: Int = 0x08
        /** Mask for offset card */
        const val SPREAD: Int = 0x10

        /**
         * Calculate the flags value from the separate fields
         * @param highlight Highlight for the flag value
         * @param faceDown True if the flag value is face down
         * @param spread True if the flag value is offset
         */
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
     * Insert a list os cards
     * @param cards The list of cards to insert
     */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insert(cards: List<Card>)

    /**
     * Clear any redo at or past a generation
     * @param generation The redo to start clearing
     * This can also clear the whole table using clearRedo(0L)
     */
    @Transaction
    @Query("DELETE FROM $TABLE WHERE $GENERATION >= :generation")
    abstract suspend fun clearRedo(generation: Long)

    /**
     * Clear any undo at a generation or before
     * @param generation The highest undo generation to clear
     * This SQL is a little more complex. For each card value we find the max generation
     * for each card through the generation one past generation. We then select the
     * id for each card/generation where the generation is less than the max generation
     * for the card and delete all of the cards with those ids.
     */
    @Transaction
    @Query("DELETE FROM $TABLE WHERE $ID in (SELECT $ID FROM $TABLE JOIN" +
        "(SELECT $VALUE t_value, MAX($GENERATION) t_gen FROM $TABLE WHERE $GENERATION <= :generation + 1 GROUP BY $VALUE)" +
        " WHERE $VALUE = t_value AND $GENERATION < t_gen)")
    abstract suspend fun clearUndo(generation: Long)

    /**
     * Add a list of card at a specific generation
     * @param list The list of cards
     * This just removes and highlight and inserts the cards.
     */
    @Transaction
    open suspend fun addAll(list: Collection<Card>) {
        insert(list.map { it.copy(flags = it.flags and Card.HIGHLIGHT_MASK.inv())})
    }

    /**
     * Get the most recent generation of cards
     * @param generation The generation of the list
     * @return List of all of the cards for a generation
     * This method gets each card whose generation is equal to the max generation
     * less than or equal to generation. All of the card are returned, unless you
     * request a generation less than the lowest available generation.
      */
    @RewriteQueriesToDropUnusedColumns
    @Query("SELECT * FROM $TABLE JOIN" +
        "(SELECT $VALUE t_value, MAX($GENERATION) t_gen FROM $TABLE WHERE $GENERATION <= :generation GROUP BY $VALUE)" +
        " WHERE $VALUE = t_value AND $GENERATION = t_gen ORDER BY $VALUE")
    abstract suspend fun getAllGeneration(generation: Long): List<Card>

    /**
     * Get the list of cards changed when a generation is undone
     * @param generation The generation to undo
     * @return The list of cards that need to be changed for the undo.
     * This is similar to getAllGeneration, except we filter the result
     * to only include the cards that were changed in the generation being undone.
     */
    @RewriteQueriesToDropUnusedColumns
    @Query("SELECT * FROM $TABLE JOIN " +
        "(SELECT $VALUE t_value, MAX($GENERATION) t_gen FROM $TABLE" +
        " WHERE $VALUE in (SELECT $VALUE FROM $TABLE WHERE $GENERATION = :generation) AND $GENERATION < :generation GROUP BY $VALUE)" +
        " WHERE $VALUE = t_value AND $GENERATION = t_gen ORDER BY $VALUE")
    abstract suspend fun undo(generation: Long): List<Card>

    /**
     * Get the list of cards changed when a generation is redone
     * @param generation The generation to redo
     * @return The list of cards to change for the redo
     * This one is just the cards that were added for a given generation
     */
    @RewriteQueriesToDropUnusedColumns
    @Query("SELECT * FROM $TABLE WHERE $GENERATION = :generation")
    abstract suspend fun redo(generation: Long): List<Card>

    /**
     * Get the minimum complete generation in the database
     * @return The minimum generation
     * This one is pretty simple, too. Find the min generation for each card
     * and then find the max of those generations. This is the smallest
     * generation where getAllGeneration will return all of the cards.
     */
    @Query("SELECT MAX(t_gen) FROM" +
        " (SELECT MIN($GENERATION) t_gen FROM $TABLE GROUP BY $VALUE)")
    abstract suspend fun minGeneration(): Long?

    /**
     * Get the max generation in the database
     * @return The max generation
     * Even easier - just the max generation in the database.
     */
    @Query("SELECT MAX($GENERATION) FROM $TABLE")
    abstract suspend fun maxGeneration(): Long?

    companion object {
        const val ID = "card_id"
        const val TABLE = "card_table"
        const val GENERATION = "card_generation"
        const val VALUE = "card_value"
        const val GROUP = "card_group"
        const val POSITION = "card_position"
        const val FLAGS = "card_flags"
    }
}
