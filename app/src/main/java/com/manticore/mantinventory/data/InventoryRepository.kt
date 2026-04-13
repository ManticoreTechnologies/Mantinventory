package com.manticore.mantinventory.data

import kotlinx.coroutines.flow.Flow

class InventoryRepository(
    private val inventoryDao: InventoryDao
) {
    fun observeBoxesWithItems(): Flow<List<BoxWithItems>> = inventoryDao.observeBoxesWithItems()

    fun observeBoxWithItems(boxId: Long): Flow<BoxWithItems?> = inventoryDao.observeBoxWithItems(boxId)

    fun observeItemsForBox(boxId: Long): Flow<List<ItemEntity>> = inventoryDao.observeItemsForBox(boxId)

    fun searchItems(query: String): Flow<List<ItemEntity>> = inventoryDao.searchItems(query)

    fun observeLowStockItems(): Flow<List<ItemEntity>> = inventoryDao.observeLowStockItems()

    suspend fun createBox(box: BoxEntity): Long = inventoryDao.insertBox(box)

    suspend fun addItem(item: ItemEntity): Long = inventoryDao.insertItem(item)

    suspend fun adjustItemQuantity(itemId: Long, delta: Int) {
        if (delta == 0) return
        inventoryDao.adjustItemQuantity(
            itemId = itemId,
            delta = delta,
            updatedAt = System.currentTimeMillis()
        )
    }

    suspend fun findBoxByLabelCode(labelCode: String): BoxEntity? = inventoryDao.getBoxByLabelCode(labelCode)

    suspend fun findItemByBarcodeOrQr(barcodeOrQr: String): ItemEntity? =
        inventoryDao.getItemByBarcodeOrQr(barcodeOrQr)
}
