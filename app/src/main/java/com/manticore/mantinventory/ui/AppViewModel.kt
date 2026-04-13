package com.manticore.mantinventory.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.manticore.mantinventory.data.BoxEntity
import com.manticore.mantinventory.data.BoxWithItems
import com.manticore.mantinventory.data.InventoryRepository
import com.manticore.mantinventory.data.ItemEntity
import com.manticore.mantinventory.data.LabelGenerator
import com.manticore.mantinventory.data.MantinventoryDatabase
import com.manticore.mantinventory.data.MarketValueEstimate
import com.manticore.mantinventory.data.MarketValueEstimator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

data class InventoryStats(
    val totalBoxes: Int = 0,
    val totalItems: Int = 0,
    val lowStockItems: Int = 0
)

data class AppUiState(
    val searchQuery: String = "",
    val lastScanMessage: String = "",
    val lastScannedItemCode: String? = null
)

data class ItemMarketValueUiState(
    val isLoading: Boolean = false,
    val estimate: MarketValueEstimate? = null,
    val errorMessage: String? = null
)

data class BoxDetailUiState(
    val isLoading: Boolean = true,
    val box: BoxEntity? = null,
    val wasResolved: Boolean = false
)

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: InventoryRepository
    private val marketValueEstimator = MarketValueEstimator()

    private val searchQueryState = MutableStateFlow("")
    private val lastScanMessageState = MutableStateFlow("")
    private val lastScannedItemCodeState = MutableStateFlow<String?>(null)
    private val selectedBoxIdState = MutableStateFlow<Long?>(null)

    private val pendingDeepLinkState = MutableStateFlow<String?>(null)
    val pendingDeepLink: StateFlow<String?> = pendingDeepLinkState
    private val itemMarketValuesState = MutableStateFlow<Map<Long, ItemMarketValueUiState>>(emptyMap())
    val itemMarketValues: StateFlow<Map<Long, ItemMarketValueUiState>> = itemMarketValuesState

    val boxes: StateFlow<List<BoxWithItems>>
    val searchResults: StateFlow<List<ItemEntity>>
    val searchQuery: StateFlow<String> = searchQueryState
    val stats: StateFlow<InventoryStats>

    val uiState: StateFlow<AppUiState>

    init {
        val db = MantinventoryDatabase.get(application)
        repository = InventoryRepository(db.inventoryDao())

        boxes = repository.observeBoxesWithItems()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

        searchResults = searchQueryState
            .flatMapLatest { query -> repository.searchItems(query.trim()) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

        stats = combine(
            boxes,
            repository.observeLowStockItems()
        ) { boxList, lowStock ->
            val itemCount = boxList.sumOf { it.items.size }
            InventoryStats(
                totalBoxes = boxList.size,
                totalItems = itemCount,
                lowStockItems = lowStock.size
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), InventoryStats())

        uiState = combine(
            searchQueryState,
            lastScanMessageState,
            lastScannedItemCodeState
        ) { query, message, itemCode ->
            AppUiState(
                searchQuery = query,
                lastScanMessage = message,
                lastScannedItemCode = itemCode
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppUiState())

        seedIfEmpty()
    }

    private fun nextBoxCode(): String {
        val stamp = DateTimeFormatter.ofPattern("yyyyMMddHHmmss").format(LocalDateTime.now())
        return "MANT-BOX-$stamp"
    }

    private fun seedIfEmpty() {
        viewModelScope.launch {
            val current = repository.observeBoxesWithItems().first()
            if (current.isNotEmpty()) return@launch

            val code = nextBoxCode()
            val deepLink = buildDeepLinkForCode(code)
            val pngPath = withContext(Dispatchers.IO) {
                LabelGenerator.saveLabelPng(
                    context = getApplication(),
                    labelCode = code,
                    deepLink = deepLink
                )
            }

            val boxId = repository.createBox(
                BoxEntity(
                    name = "Starter Box",
                    location = "Shelf A1",
                    description = "Sample box to get started",
                    labelCode = code,
                    labelPngPath = pngPath
                )
            )

            repository.addItem(
                ItemEntity(
                    boxId = boxId,
                    name = "USB-C Cable",
                    description = "1 meter braided cable",
                    quantity = 6,
                    minimumStock = 2,
                    barcodeOrQr = "111222333444"
                )
            )
        }
    }

    suspend fun createBoxSuspending(name: String, location: String, description: String): Long {
        val code = nextBoxCode()
        val deepLink = buildDeepLinkForCode(code)
        val labelPath = withContext(Dispatchers.IO) {
            LabelGenerator.saveLabelPng(
                context = getApplication(),
                labelCode = code,
                deepLink = deepLink
            )
        }
        val boxId = repository.createBox(
            BoxEntity(
                name = name,
                location = location,
                description = description,
                labelCode = code,
                labelPngPath = labelPath
            )
        )
        selectedBoxIdState.value = boxId
        lastScanMessageState.value = "Box created with label code $code"
        return boxId
    }

    fun addItem(
        boxId: Long,
        name: String,
        description: String,
        barcode: String,
        quantity: Int,
        condition: String = "Good",
        minimumStock: Int = 1
    ) {
        viewModelScope.launch {
            val decoratedDescription = if (condition.isBlank()) {
                description
            } else {
                "$description | Condition: $condition"
            }
            repository.addItem(
                ItemEntity(
                    boxId = boxId,
                    name = name,
                    description = decoratedDescription,
                    quantity = quantity.coerceAtLeast(1),
                    minimumStock = minimumStock.coerceAtLeast(0),
                    barcodeOrQr = barcode
                )
            )
            lastScanMessageState.value = "Item added to box"
        }
    }

    fun adjustItemQuantity(itemId: Long, delta: Int) {
        if (delta == 0) return
        viewModelScope.launch {
            repository.adjustItemQuantity(itemId = itemId, delta = delta)
        }
    }

    fun incrementItemQuantity(itemId: Long) = adjustItemQuantity(itemId = itemId, delta = 1)

    fun decrementItemQuantity(itemId: Long) = adjustItemQuantity(itemId = itemId, delta = -1)

    fun updateItem(
        itemId: Long,
        name: String,
        description: String,
        barcode: String,
        quantity: Int,
        minimumStock: Int
    ) {
        viewModelScope.launch {
            repository.updateItem(
                itemId = itemId,
                name = name.trim(),
                description = description.trim(),
                barcode = barcode.trim(),
                quantity = quantity.coerceAtLeast(1),
                minimumStock = minimumStock.coerceAtLeast(0)
            )
        }
    }

    fun deleteItem(itemId: Long) {
        viewModelScope.launch {
            repository.removeItemById(itemId)
            itemMarketValuesState.value = itemMarketValuesState.value - itemId
        }
    }

    fun marketEstimateForItem(itemId: Long): ItemMarketValueUiState? =
        itemMarketValuesState.value[itemId]

    fun refreshMarketValueForItem(item: ItemEntity) {
        val query = item.name.ifBlank { item.barcodeOrQr }.trim()
        if (query.isBlank()) {
            itemMarketValuesState.value = itemMarketValuesState.value + (
                item.id to ItemMarketValueUiState(errorMessage = "Item needs a name or barcode.")
            )
            return
        }
        viewModelScope.launch {
            val previousEstimate = itemMarketValuesState.value[item.id]?.estimate
            itemMarketValuesState.value = itemMarketValuesState.value + (
                item.id to ItemMarketValueUiState(
                    isLoading = true,
                    estimate = previousEstimate
                )
            )
            val estimate = runCatching {
                marketValueEstimator.estimateMarketValue(query)
            }
            itemMarketValuesState.value = itemMarketValuesState.value + (
                item.id to estimate.fold(
                    onSuccess = { value ->
                        if (value == null) {
                            ItemMarketValueUiState(errorMessage = "Not enough sold listings found.")
                        } else {
                            ItemMarketValueUiState(estimate = value)
                        }
                    },
                    onFailure = { error ->
                        ItemMarketValueUiState(
                            errorMessage = error.message ?: "Unable to fetch market value."
                        )
                    }
                )
            )
        }
    }

    fun boxDetail(boxId: Long): Flow<BoxEntity?> =
        repository.observeBoxWithItems(boxId).map { it?.box }

    fun boxDetailUiState(boxId: Long): Flow<BoxDetailUiState> =
        repository.observeBoxWithItems(boxId)
            .map { boxWithItems ->
                BoxDetailUiState(
                    isLoading = false,
                    box = boxWithItems?.box,
                    wasResolved = true
                )
            }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                BoxDetailUiState()
            )

    fun itemsForBox(boxId: Long): Flow<List<ItemEntity>> =
        repository.observeItemsForBox(boxId)

    fun updateSearchQuery(query: String) {
        searchQueryState.value = query
    }

    fun recordScannedItemCode(code: String) {
        lastScannedItemCodeState.value = code
        if (searchQueryState.value.isBlank()) {
            searchQueryState.value = code
        }
    }

    fun clearLastScannedItemCode() {
        lastScannedItemCodeState.value = null
    }

    suspend fun resolveScannedCodeToBoxSuspending(raw: String): Long? {
        val code = parseCodeFromRawScan(raw)?.trim().orEmpty()
        if (code.isBlank()) return null

        val direct = repository.findBoxByLabelCode(code)
        if (direct != null) {
            selectedBoxIdState.value = direct.id
            lastScanMessageState.value = "Opened box ${direct.name}"
            return direct.id
        }

        val item = repository.findItemByBarcodeOrQr(code)
        if (item != null) {
            selectedBoxIdState.value = item.boxId
            lastScanMessageState.value = "Found item ${item.name}, opening box"
            return item.boxId
        }
        lastScanMessageState.value = "Code not found; saved as search"
        return null
    }

    suspend fun findBoxByLabelSuspending(raw: String): BoxEntity? {
        val extracted = parseCodeFromRawScan(raw) ?: raw
        return repository.findBoxByLabelCode(extracted)
    }

    fun consumeDeepLink(uri: Uri?) {
        val code = uri?.getQueryParameter("code")
        if (!code.isNullOrBlank()) {
            pendingDeepLinkState.value = code
        }
    }

    fun markDeepLinkHandled() {
        pendingDeepLinkState.value = null
    }

    private fun buildDeepLinkForCode(code: String): String = "mantinventory://box/open?code=$code"

    fun parseCodeFromRawScan(raw: String): String? {
        val trimmed = raw.trim()
        return if (trimmed.startsWith("mantinventory://")) {
            Uri.parse(trimmed).getQueryParameter("code")
        } else {
            trimmed
        }
    }
}

class AppViewModelFactory(
    private val application: Application
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return AppViewModel(application) as T
    }
}
