package com.github.cleveard.scorpion.db

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query

/**
 * The highlight table entity
 * Highlights aren't undoable, so there is a separate table for them
 */
@Entity(tableName = HighlightDao.TABLE)
data class HighlightEntity(
    /** The value of the card with the highlight */
    @PrimaryKey @ColumnInfo(name = HighlightDao.CARD) val card: Int,
    /** The highlight */
    @ColumnInfo(name = HighlightDao.HIGHLIGHT) val highlight: Int
)

@Dao
abstract class HighlightDao {
    /**
     * Insert a collection of highlights
     * @param list The collection of highlights
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insert(list: Collection<HighlightEntity>)

    /**
     * Delete the highlights for a list of cards
     * @param list The list of card values to delete
     */
    @Query("DELETE FROM $TABLE WHERE $CARD in ( :list )")
    abstract suspend fun delete(list: List<Int>)

    /**
     * Delete the highlights for a list of cards
     * @param list The list of card values to delete
     */
    @Query("DELETE FROM $TABLE WHERE $CARD >= 0")
    abstract suspend fun clear()

    /**
     * Get all of the highlights
     * @return A list of all highlights
     */
    @Query("SELECT * FROM $TABLE")
    abstract suspend fun get(): List<HighlightEntity>

    companion object {
        const val TABLE: String = "highlight_table"
        const val CARD: String = "highlight_card"
        const val HIGHLIGHT: String = "highlight_highlight"
    }
}
