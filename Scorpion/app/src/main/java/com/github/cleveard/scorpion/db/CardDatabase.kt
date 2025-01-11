package com.github.cleveard.scorpion.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import com.github.cleveard.scorpion.ui.games.Game
import java.io.IOException

/**
 * The card database
 *
 * The database has three tables
 *    The card table holds cards and the generations when they were changed
 *    The state table holds settings for games and the application
 *    The highlight table hold highlights for cards. Highlights are separate
 *    because they are not undoable.
 */
@Database(
    entities = [
        Card::class,
        StateEntity::class,
        HighlightEntity::class
    ],
    version = 1
)
abstract class CardDatabase: RoomDatabase() {
    abstract fun getCardDao(): CardDao
    abstract fun getStateDao(): StateDao
    abstract fun getHighlightDao(): HighlightDao

    /**
     * Load all cards for a generation and all highlights
     * @param generation The card generation to load
     * @return Pair containing a list of cards and highlights
     */
    @Transaction
    open suspend fun loadGame(generation: Long): Pair<MutableList<Card>, List<HighlightEntity>>? {
        // Get the cards
        return getCardDao().getAllGeneration(generation).toMutableList().let {cards ->
            val highlight = db.getHighlightDao().get()
            // Sanity check - Got all cards in the correct order
            if (cards.size == Game.CARD_COUNT && cards.allWithIndices { i, card -> i == card.value })
                Pair(cards, highlight)
            else
                null
        }
    }

    /**
     * Clear any redo at or past a generation
     * @param generation The redo to start clearing
     * This can also clear the whole table using clearRedo(0L)
     */
    @Transaction
    open suspend fun clearRedo(generation: Long) {
        getCardDao().clearRedo(generation)
    }

    /**
     * Get the changes needed to undo
     * @param game The qualified class name for the game
     * @param generation The generation to undo should be the current generation
     * @return The list of cards to change
     */
    @Transaction
    open suspend fun undo(game: String, generation: Long): List<Card>? {
        // Can't undo before generation 0
        if (generation <= 0)
            return null
        // Update the generation in the state table
        getStateDao().update(game, generation - 1)
        // Get the changed cards
        return getCardDao().undo(generation)
    }

    /**
     * Get the cards to change to redo to a generation
     * @param game The qualified class name of the game
     * @param generation The generation, should be the current generation plus one
     */
    @Transaction
    open suspend fun redo(game: String, generation: Long): List<Card>? {
        // Update the generation in the state table
        getStateDao().update(game, generation)
        // Get the cards to redo
        return getCardDao().redo(generation)
    }

    /**
     * Add a generation to the data base
     * @param game The qualified class name of the game
     * @param cards The cards changed for the added generation
     * @param generation The added generation
     */
    @Transaction
    open suspend fun newGeneration(game: String, cards: Collection<Card>, generation: Long) {
        val cardDao = getCardDao()
        val stateDao = getStateDao()

        // Make sure all cards have the correct generation
        if (cards.any { it.generation != generation })
            throw IllegalArgumentException("Cards do not have correct generation")
        // Clear redo at or after generation
        clearRedo(generation)
        // Update the generation for the game
        stateDao.update(game, generation)
        // Add the changed cards to the card table
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

        /**
         * Test a predicate for all cards with index
         * @param predicate The predicate which uses the index and card
         * @return True if all cards satisfy the predicate
         */
        private fun List<Card>.allWithIndices(predicate: (i: Int, card: Card) -> Boolean): Boolean {
            for (i in indices) {
                if (!predicate(i, this[i]))
                    return false
            }
            return true
        }
    }
}
