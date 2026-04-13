package com.manticore.mantinventory.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface InventoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBox(box: BoxEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: ItemEntity): Long

    @Query(
        """
        UPDATE items
        SET quantity = CASE
            WHEN quantity + :delta < 1 THEN 1
            ELSE quantity + :delta
        END,
        updatedAt = :updatedAt
        WHERE id = :itemId
        """
    )
    suspend fun adjustItemQuantity(itemId: Long, delta: Int, updatedAt: Long)

    @Query(
        """
        UPDATE items
        SET name = :name,
            description = :description,
            barcodeOrQr = :barcodeOrQr,
            quantity = :quantity,
            minimumStock = :minimumStock,
            updatedAt = :updatedAt
        WHERE id = :itemId
        """
    )
    suspend fun updateItem(
        itemId: Long,
        name: String,
        description: String,
        barcodeOrQr: String,
        quantity: Int,
        minimumStock: Int,
        updatedAt: Long
    )

    @Query("DELETE FROM items WHERE id = :itemId")
    suspend fun deleteItem(itemId: Long)

    @Transaction
    @Query("SELECT * FROM boxes ORDER BY createdAt DESC")
    fun observeBoxesWithItems(): Flow<List<BoxWithItems>>

    @Transaction
    @Query("SELECT * FROM boxes WHERE id = :boxId LIMIT 1")
    fun observeBoxWithItems(boxId: Long): Flow<BoxWithItems?>

    @Query("SELECT * FROM boxes ORDER BY createdAt DESC")
    fun observeBoxes(): Flow<List<BoxEntity>>

    @Query("SELECT * FROM items WHERE boxId = :boxId ORDER BY name ASC")
    fun observeItemsForBox(boxId: Long): Flow<List<ItemEntity>>

    @Query(
        """
        SELECT * FROM items
        WHERE name LIKE '%' || :query || '%'
           OR description LIKE '%' || :query || '%'
           OR barcodeOrQr LIKE '%' || :query || '%'
        ORDER BY updatedAt DESC
        """
    )
    fun searchItems(query: String): Flow<List<ItemEntity>>

    @Query("SELECT * FROM items WHERE quantity <= minimumStock ORDER BY quantity ASC")
    fun observeLowStockItems(): Flow<List<ItemEntity>>

    @Query("SELECT * FROM boxes WHERE labelCode = :labelCode LIMIT 1")
    suspend fun getBoxByLabelCode(labelCode: String): BoxEntity?

    @Query("SELECT * FROM items WHERE barcodeOrQr = :barcodeOrQr LIMIT 1")
    suspend fun getItemByBarcodeOrQr(barcodeOrQr: String): ItemEntity?
}
