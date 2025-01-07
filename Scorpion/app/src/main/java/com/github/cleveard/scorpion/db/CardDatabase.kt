package com.github.cleveard.scorpion.db

import android.content.Context
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableIntStateOf
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.github.cleveard.scorpion.ui.games.Game
import java.io.IOException

class Converters {
    @TypeConverter
    fun intStateFromInt(value: Int): MutableState<Int> {
        return mutableIntStateOf(value)
    }

    @TypeConverter
    fun intStateToInt(state: MutableState<Int>): Int {
        return state.value
    }
}

@Database(
    entities = [
        CardEntity::class,
        StateEntity::class,
        HighlightEntity::class
    ],
    version = 1
)
@TypeConverters(Converters::class)
abstract class CardDatabase: RoomDatabase() {
    abstract fun getCardDao(): CardDao
    abstract fun getStateDao(): StateDao
    abstract fun getHighlightDao(): HighlightDao

    @Transaction
    open suspend fun loadGame(generation: Long): Pair<MutableList<Card>, List<HighlightEntity>>? {
        return getCardDao().getAllGeneration(generation).toMutableList().let {cards ->
            val highlight = db.getHighlightDao().get()
            // Sanity check game
            if (cards.size == Game.CARD_COUNT && cards.allWithIndices { i, card -> i == card.value })
                Pair(cards, highlight)
            else
                null
        }
    }

    @Transaction
    open suspend fun clearRedo(generation: Long) {
        getCardDao().clearRedo(generation)
    }

    @Transaction
    open suspend fun undo(game: String, generation: Long): List<CardEntity>? {
        if (generation <= 0)
            return null
        getStateDao().update(game, generation - 1)
        return getCardDao().undo(generation)
    }

    @Transaction
    open suspend fun redo(game: String, generation: Long): List<CardEntity>? {
        getStateDao().update(game, generation)
        return getCardDao().redo(generation)
    }

    @Transaction
    open suspend fun newGeneration(game: String, cards: Collection<CardEntity>, generation: Long) {
        val cardDao = getCardDao()
        val stateDao = getStateDao()

        if (cards.any { it.generation != generation })
            throw IllegalArgumentException("Cards do not have correct generation")
        clearRedo(generation)
        stateDao.update(game, generation)
        cardDao.addAll(cards)
    }

    companion object {
        private const val DATABASE_FILENAME = "Scorpion"
        private var deleteOnCloseName: String? = null

        lateinit var db: CardDatabase
            private set

        /**
         * Initialize the data base
         * @param context Application context
         * @param testing True to create a database for testing
         * @param name The name of the database. Use null for the default name or in-memory if testing is true
         */
        fun initialize(context: Context, testing: Boolean = false, name: String? = null) {
            if (!::db.isInitialized) {
                db = create(context, testing, name)
            }
        }

        /**
         * Create the data base
         * @param context Application context
         * @param testing True to create a database for testing
         * @param name The name of the database. Use null for the default name or in-memory if testing is true
         */
        private fun create(context: Context, testing: Boolean, name: String?): CardDatabase {
            val builder: Builder<CardDatabase>
            if (testing && name == null) {
                builder = Room.inMemoryDatabaseBuilder(context, CardDatabase::class.java)
            } else {
                if (testing)
                    context.deleteDatabase(name)
                builder = Room.databaseBuilder(
                    context, CardDatabase::class.java, name?: DATABASE_FILENAME
                )

                // If we have a prepopulate database use it.
                // This should only be used with a fresh debug install
                val assets = context.resources.assets
                try {
                    val asset = "database/${name?: DATABASE_FILENAME}"
                    val stream = assets.open(asset)
                    stream.close()
                    builder.createFromAsset(asset)
                } catch (ex: IOException) {
                    // No prepopulate asset, create empty database
                }

                // builder.addMigrations(*migrations)
            }

            // Build the database
            val db = builder.build()
            if (testing)
                deleteOnCloseName = name

            return db
        }

        private fun List<Card>.allWithIndices(predicate: (i: Int, card: Card) -> Boolean): Boolean {
            for (i in indices) {
                if (!predicate(i, this[i]))
                    return false
            }
            return true
        }
    }
}
