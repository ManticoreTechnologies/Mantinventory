package com.manticore.mantinventory.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [BoxEntity::class, ItemEntity::class],
    version = 1,
    exportSchema = false
)
abstract class MantinventoryDatabase : RoomDatabase() {
    abstract fun inventoryDao(): InventoryDao

    companion object {
        @Volatile
        private var INSTANCE: MantinventoryDatabase? = null

        fun get(context: Context): MantinventoryDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    MantinventoryDatabase::class.java,
                    "mantinventory.db"
                ).fallbackToDestructiveMigration().build().also { INSTANCE = it }
            }
        }
    }
}
