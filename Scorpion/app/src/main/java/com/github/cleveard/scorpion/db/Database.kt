package com.github.cleveard.scorpion.db

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableIntStateOf
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters

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
        CardEntity::class
    ],
    version = 0
)
@TypeConverters(Converters::class)
abstract class Database: RoomDatabase() {
    abstract fun getCardDao(): CardDao
}
