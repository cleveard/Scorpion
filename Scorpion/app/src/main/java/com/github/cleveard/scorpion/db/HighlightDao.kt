package com.github.cleveard.scorpion.db

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query

@Entity(tableName = HighlightDao.TABLE)
data class HighlightEntity(
    @PrimaryKey @ColumnInfo(name = HighlightDao.CARD) val card: Int,
    @ColumnInfo(name = HighlightDao.HIGHLIGHT) val highlight: Int
)

@Dao
abstract class HighlightDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insert(list: Collection<HighlightEntity>)

    @Query("DELETE FROM $TABLE WHERE $CARD in ( :list )")
    abstract suspend fun delete(list: List<Int>)

    @Query("SELECT * FROM $TABLE")
    abstract suspend fun get(): List<HighlightEntity>

    companion object {
        const val TABLE: String = "highlight_table"
        const val CARD: String = "highlight_card"
        const val HIGHLIGHT: String = "highlight_highlight"
    }
}
