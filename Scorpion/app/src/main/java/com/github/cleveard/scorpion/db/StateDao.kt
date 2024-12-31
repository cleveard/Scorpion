package com.github.cleveard.scorpion.db

import android.os.Bundle
import android.os.Parcel
import androidx.lifecycle.LiveData
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

@Entity(tableName = StateDao.TABLE)
data class StateEntity(
    @ColumnInfo(name = StateDao.GENERATION) var generation: Long,
    @PrimaryKey @ColumnInfo(name = StateDao.GAME) var game: String,
    @ColumnInfo(name = StateDao.STATE) private var _state: ByteArray? = null
) {
    constructor(generation: Long, game: String, bundle: Bundle): this(
        generation, game, bundleToBlob(bundle)
    )

    val state: ByteArray?
        get() = _state
    @Ignore val bundle: Bundle = bundleFromBlob(state)

    fun onBundleUpdated() {
        _state = bundleToBlob(bundle)
    }


    override fun equals(other: Any?): Boolean {
        if (other === this)
            return true
        val v = (other as? StateEntity)?: return false
        return generation == v.generation &&
            game == v.game &&
            state.contentEquals(v.state)
    }

    override fun hashCode(): Int {
        return Objects.hash(generation, game, state.contentHashCode())
    }

    companion object {
        fun bundleFromBlob(value: ByteArray?): Bundle {
            return value?.let { it ->
                val parcel = Parcel.obtain()
                parcel.unmarshall(it, 0, it.size)
                parcel.setDataPosition(0)
                parcel.readBundle(Converters::class.java.classLoader).also {
                    parcel.recycle()
                }
            } ?: Bundle()
        }

        fun bundleToBlob(value: Bundle): ByteArray? {
            return if (value.keySet().isEmpty())
                null
            else {
                val parcel = Parcel.obtain()
                parcel.writeBundle(value)
                parcel.marshall().also {
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

    @Query("SELECT * FROM $TABLE WHERE $GAME = :name")
    abstract suspend fun get(name: String): StateEntity?

    @Query("SELECT * FROM $TABLE where $GAME = :name")
    abstract fun getGeneration(name: String): LiveData<StateEntity?>

    @Transaction
    @Query("UPDATE OR ABORT $TABLE SET $GENERATION = :generation WHERE $GAME = :name ")
    abstract suspend fun update(name: String, generation: Long)

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