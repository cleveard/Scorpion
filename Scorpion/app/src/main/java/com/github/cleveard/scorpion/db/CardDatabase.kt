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
import com.github.cleveard.scorpion.ui.Game
import kotlinx.coroutines.runBlocking
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
        StateEntity::class
    ],
    version = 1
)
@TypeConverters(Converters::class)
abstract class CardDatabase: RoomDatabase() {
    abstract fun getCardDao(): CardDao
    abstract fun getStateDao(): StateDao

    private var generation: Long = 0L
    private val changedCards: MutableList<Card> = mutableListOf()
    private var stateBlob: ByteArray? = null
    private var undoNesting: Int = 0

    private suspend fun getGeneration(): Long {
        return getStateDao().getAll().lastOrNull { it.undone }?.generation ?: 0
    }

    @Transaction
    open suspend fun loadGame(): Pair<State, List<Card>>? {
        return getStateDao().getAll().lastOrNull { !it.undone }?.let {state ->
            generation = state.generation
            getCardDao().getAllGeneration(generation).let {cards ->
                // Sanity check game
                if (cards.size == Game.CARD_COUNT && cards.allWithIndices {i, card -> i == card.value })
                    Pair(state, cards)
                else
                    null
            }
        }
    }
    @Transaction
    open suspend fun clearRedo(generation: Long) {
        getStateDao().clearRedo(generation)
        getCardDao().clearRedo(generation)
    }

    @Transaction
    open suspend fun undo(): Pair<State, List<Card>>? {
        return getStateDao().undo(generation)?.let {
            Pair(it, getCardDao().undo(generation--))
        }
    }

    @Transaction
    open suspend fun redo(): Pair<State, List<Card>>? {
        return getStateDao().redo(generation + 1)?.let {
            Pair(it, getCardDao().redo(++generation))
        }
    }

    @Transaction
    open suspend fun newGeneration(state: State, cards: List<Card>, newGeneration: Long = -1L) {
        val cardDao = getCardDao()
        val stateDao = getStateDao()

        if (newGeneration >= 0) {
            generation = newGeneration
            undoNesting = 0
        } else
            ++generation
        cardDao.clearRedo(generation)
        stateDao.clearRedo(generation)
        stateDao.insert(state, generation)
        cardDao.addAll(cards, generation)
    }

    fun cardChanged(card: Card) {
        changedCards.indexOfFirst { it.value == card.value }.let {
            if (it < 0)
                changedCards.add(card)
            else
                changedCards[it] = card
        }
    }

    fun stateChanged(stateBlob: ByteArray) {
        this.stateBlob = stateBlob
    }

    private suspend fun loadAndCheck(callback: (message: String?, generation: Long, state: State?, cards: List<Card>?) -> Unit) {
        val pair = loadGame()
        val error = run {
            if (pair == null)
                return@run "Can't reload the game"
            if (pair.first.generation != generation)
                return@run "Generation incorrect"
            if (pair.second.size != 52)
                return@run "Size incorrect"
            val list = pair.second.toMutableList()
            list.sortBy { it.value }
            for (i in list.indices) {
                if (i != list[i].value)
                    return@run "Card $i value incorrect"
            }
            list.sortWith(compareBy({ it.group }, { it.position }))
            var lastGroup = 0
            var lastPosition = -1
            for (c in list) {
                if (c.group < lastGroup)
                    return@run "Group invalid"
                if (
                    (if (c.group == lastGroup)
                        c.position != lastPosition + 1
                    else
                        c.position != 0)
                )
                    return@run "Position invalid"
                lastGroup = c.group
                lastPosition = c.position
            }
            null
        }

        if (error != null)
            callback(error, generation, pair?.first, pair?.second)
    }

    suspend fun <T> withUndo(name: String, callback: (message: String?, generation: Long, state: State?, cards: List<Card>?) -> Unit, action: suspend () -> T): T {
        var success = false
        ++undoNesting
        try {
            return action().also {
                success = true
            }
        } finally {
            if (undoNesting > 1) {
                --undoNesting
                success = true
            } else {
                undoNesting = 0
                try {
                    if (success) {
                        if (changedCards.isNotEmpty() || stateBlob != null) {
                            newGeneration(State(0L, name, stateBlob), changedCards)
                            loadAndCheck(callback)
                        }
                    }
                } finally {
                    stateBlob = null
                    changedCards.clear()
                }
            }
        }
    }

    companion object {
        private const val DATABASE_FILENAME = "SudokuPuzzles"
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
                runBlocking {
                    db.generation = db.getGeneration()
                }
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
