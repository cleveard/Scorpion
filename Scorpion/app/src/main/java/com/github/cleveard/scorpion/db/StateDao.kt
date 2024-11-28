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
import java.util.Objects

@Entity(
    tableName = StateDao.TABLE,
    indices = [
        Index(StateDao.GENERATION, StateDao.GAME, name = StateDao.INDEX, unique = true)
    ]
)
data class StateEntity(
    @ColumnInfo(name = StateDao.GENERATION) var generation: Long,
    @ColumnInfo(name = StateDao.GAME) var game: String,
    @ColumnInfo(name = StateDao.STATE) var state: ByteArray? = null,
    @ColumnInfo(name = StateDao.FLAGS) var flags: Int = 0,
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = StateDao.ID) var id: Long = 0
)

data class State(
    @ColumnInfo(name = StateDao.GENERATION) var generation: Long,
    @ColumnInfo(name = StateDao.GAME) var game: String,
    @ColumnInfo(name = StateDao.STATE) var state: ByteArray? = null,
    @ColumnInfo(name = StateDao.FLAGS) var flags: Int = 0,
) {
    fun toEnTity(): StateEntity {
        return StateEntity(
            generation,
            game,
            state,
            flags
        )
    }

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
        val v = (other as? State)?: return false
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
    /**
     * Insert state
     */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected suspend abstract fun insert(state: StateEntity): Long

    /**
     * Insert state
     */
    protected suspend fun insert(state: State): Long {
        return insert(state.toEnTity())
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
     * Add a state block at a generation
     * @param list The list of cards
     * @param generation The generation for the list
     */
    @Transaction()
    open suspend fun insert(state: State, generation: Long) {
        state.generation = generation
        state.undone = false
        insert(state)
    }

    @RewriteQueriesToDropUnusedColumns
    @Query("SELECT * FROM $TABLE ORDER BY $GENERATION")
    abstract suspend fun getAll(): List<State>

    /**
     * Get the state at a generation
     * @param generation The generation of the state
     */
    @RewriteQueriesToDropUnusedColumns
    @Query("SELECT * FROM $TABLE WHERE $GENERATION = :generation")
    abstract suspend fun getGeneration(generation: Long): State?

    /**
     * Mark state as undone
     */
    @Transaction
    @Query("UPDATE $TABLE SET $FLAGS = ($FLAGS & ~${State.UNDONE}) | (:undone & ${State.UNDONE}) WHERE $GENERATION = :generation")
    protected abstract suspend fun setUndone(generation: Long, undone: Int)

    /**
     * Get the state when a generation is undone
     * @param generation The generation to undo
     */
    @Transaction
    open suspend fun undo(generation: Long): State? {
        if (generation == 0L)
            return null
        setUndone(generation, State.UNDONE)
        return getGeneration(generation - 1)
    }

    /**
     * Get the state when a generation is redone
     * @param generation The generation to redo
     */
    @Transaction
    open suspend fun redo(generation: Long): State? {
        return getGeneration(generation)?.also {
            setUndone(generation, 0)
        }
    }

    companion object {
        const val ID = "state_id"
        const val TABLE = "state_table"
        const val GENERATION = "state_generation"
        const val GAME = "state_game"
        const val STATE = "state_state"
        const val FLAGS = "state_flags"
        const val INDEX = "state_index"
    }
}