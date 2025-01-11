package com.github.cleveard.scorpion.db

import android.os.Bundle
import android.os.Parcel
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import java.util.Objects

/**
 * State table entity
 *
 * This table keeps track of settings for each came, and common settings
 * for the app. Settings are kept by the qualified class name of the game,
 * or the qualified class name of the MainActivityViewModel for the common
 * settings.
 */
@Entity(tableName = StateDao.TABLE)
data class StateEntity(
    /** Current generation for the game */
    @ColumnInfo(name = StateDao.GENERATION) var generation: Long,
    /** The qualified class name of the game, or MainActivityViewModel */
    @PrimaryKey @ColumnInfo(name = StateDao.GAME) var game: String,
    /** A blob containing a Bundle that holds the setting */
    @ColumnInfo(name = StateDao.STATE) private var _state: ByteArray? = null
) {
    /**
     * Additional constructor that uses a bundle
     */
    constructor(generation: Long, game: String, bundle: Bundle): this(
        generation, game, bundleToBlob(bundle)
    )

    /** Read-only access to the blob */
    val state: ByteArray?
        get() = _state
    /** The bundle for the state */
    @Ignore val bundle: Bundle = bundleFromBlob(state)

    /**
     * Let the entity know that the bundle was updated
     *  and the blob needs to be regenerated
     */
    fun onBundleUpdated() {
        _state = bundleToBlob(bundle)
    }


    /**
     * Compare two StateEntities
     * @param other The other entity
     * @return True if the entities are equal
     * We ignore the bundle for the comparison and just
     * use the marshalled blob value
     */
    override fun equals(other: Any?): Boolean {
        if (other === this)
            return true
        val v = (other as? StateEntity)?: return false
        return generation == v.generation &&
            game == v.game &&
            state.contentEquals(v.state)
    }

    /**
     * Generate a hashCode for the StateEntity
     * The hash code is generated using the blob and not the bundle.
     */
    override fun hashCode(): Int {
        return Objects.hash(generation, game, state.contentHashCode())
    }

    companion object {
        /**
         * Convert a blob to a bundle
         * @param value The blob to convert
         * @return The Bundle
         */
        fun bundleFromBlob(value: ByteArray?): Bundle {
            return value?.let {
                // Unmarshall the blob into a parcel
                val parcel = Parcel.obtain()
                parcel.unmarshall(it, 0, it.size)
                // Read the bundle from the parcel
                parcel.setDataPosition(0)
                parcel.readBundle(StateEntity::class.java.classLoader).also {
                    // Recycle the parcel
                    parcel.recycle()
                }
            } ?: Bundle()   // null value is an empty bundle
        }

        /**
         * Convert a bundle to a blob
         * @param value The bundle
         * @return The blob
         */
        fun bundleToBlob(value: Bundle): ByteArray? {
            return if (value.keySet().isEmpty())
                null    // Return null if the bundle is empty
            else {
                // Write the bundle into the parcel
                val parcel = Parcel.obtain()
                parcel.writeBundle(value)
                // And marshall it into the blob
                parcel.marshall().also {
                    // Recycle the parcel
                    parcel.recycle()
                }
            }
        }
    }
}

@Dao
abstract class StateDao {
    /**
     * Insert state
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insert(state: StateEntity): Long

    /**
     * Get the state for a class name
     * @param name The qualified class name
     * @return The state or null if it isn't in the database
     */
    @Query("SELECT * FROM $TABLE WHERE $GAME = :name")
    abstract suspend fun get(name: String): StateEntity?

    /**
     * Update the generation for a game
     * @param name The qualified class name of the game
     * @param generation The new generation
     */
    @Transaction
    @Query("UPDATE OR ABORT $TABLE SET $GENERATION = :generation WHERE $GAME = :name ")
    abstract suspend fun update(name: String, generation: Long)

    /**
     * Update the blob for a game
     * @param name The qualified class name of the game
     * @param state The new blob value
     */
    @Transaction
    @Query("UPDATE OR ABORT $TABLE SET $STATE = :state WHERE $GAME = :name ")
    abstract suspend fun update(name: String, state: ByteArray?)

    companion object {
        const val TABLE = "state_table"
        const val GENERATION = "state_generation"
        const val GAME = "state_game"
        const val STATE = "state_state"
    }
}