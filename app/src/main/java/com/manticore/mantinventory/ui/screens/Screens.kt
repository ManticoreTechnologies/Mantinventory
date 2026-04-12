package com.manticore.mantinventory.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.manticore.mantinventory.data.BoxEntity
import com.manticore.mantinventory.data.ItemEntity
import com.manticore.mantinventory.ui.AppViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

private const val ROUTE_HOME = "home"
private const val ROUTE_BOX_DETAIL = "box/{boxId}"
private const val ROUTE_BOX_DETAIL_ARGS = "box/%d"
private const val ROUTE_SCAN = "scan/{mode}"
private const val ROUTE_SCAN_ARGS = "scan/%s"
private const val ROUTE_NEW_BOX = "new_box"
private const val ROUTE_NEW_ITEM = "new_item/{boxId}"
private const val ROUTE_NEW_ITEM_ARGS = "new_item/%d"

@Composable
fun MantinventoryApp(viewModel: AppViewModel) {
    val navController = rememberNavController()
    val entry by navController.currentBackStackEntryAsState()
    val route = entry?.destination?.route ?: ROUTE_HOME
    val currentTitle = when {
        route.startsWith("box/") -> "Box Details"
        route.startsWith("scan/") -> "Scan"
        route == ROUTE_NEW_BOX -> "Create Box"
        route.startsWith("new_item/") -> "Add Item"
        else -> "Mantinventory"
    }

    val pendingDeepLinkCode by viewModel.pendingDeepLink.collectAsStateWithLifecycle()
    LaunchedEffect(pendingDeepLinkCode) {
        val code = pendingDeepLinkCode ?: return@LaunchedEffect
        val target = viewModel.resolveScannedCodeToBoxSuspending(code)
        if (target != null) {
            navController.navigate(ROUTE_BOX_DETAIL_ARGS.format(target))
        }
        viewModel.markDeepLinkHandled()
    }

    Scaffold(
        topBar = {
            TopBar(
                title = currentTitle,
                canGoBack = navController.previousBackStackEntry != null,
                onBack = { navController.popBackStack() },
                onScan = { navController.navigate(ROUTE_SCAN_ARGS.format("general")) }
            )
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = ROUTE_HOME,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(ROUTE_HOME) {
                HomeScreen(
                    viewModel = viewModel,
                    onOpenBox = { boxId ->
                        navController.navigate(ROUTE_BOX_DETAIL_ARGS.format(boxId))
                    },
                    onCreateBox = { navController.navigate(ROUTE_NEW_BOX) },
                    onScan = { navController.navigate(ROUTE_SCAN_ARGS.format("general")) }
                )
            }
            composable(
                route = ROUTE_BOX_DETAIL,
                arguments = listOf(navArgument("boxId") { type = NavType.LongType })
            ) { backStackEntry ->
                val boxId = backStackEntry.arguments?.getLong("boxId") ?: return@composable
                BoxDetailScreen(
                    viewModel = viewModel,
                    boxId = boxId,
                    onAddItem = {
                        navController.navigate(ROUTE_NEW_ITEM_ARGS.format(boxId))
                    }
                )
            }
            composable(
                route = ROUTE_SCAN,
                arguments = listOf(navArgument("mode") { type = NavType.StringType })
            ) { backStackEntry ->
                val mode = backStackEntry.arguments?.getString("mode").orEmpty()
                ScanScreen(
                    mode = mode,
                    viewModel = viewModel,
                    onBoxFound = { boxId ->
                        navController.navigate(ROUTE_BOX_DETAIL_ARGS.format(boxId))
                    },
                    onDone = { navController.popBackStack() }
                )
            }
            composable(ROUTE_NEW_BOX) {
                NewBoxScreen(
                    viewModel = viewModel,
                    onCreated = { boxId ->
                        navController.popBackStack()
                        navController.navigate(ROUTE_BOX_DETAIL_ARGS.format(boxId))
                    }
                )
            }
            composable(
                route = ROUTE_NEW_ITEM,
                arguments = listOf(navArgument("boxId") { type = NavType.LongType })
            ) { backStackEntry ->
                val boxId = backStackEntry.arguments?.getLong("boxId") ?: return@composable
                NewItemScreen(
                    boxId = boxId,
                    viewModel = viewModel,
                    onDone = { navController.popBackStack() },
                    onScan = { navController.navigate(ROUTE_SCAN_ARGS.format("item")) }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopBar(
    title: String,
    canGoBack: Boolean,
    onBack: () -> Unit,
    onScan: () -> Unit
) {
    TopAppBar(
        title = { Text(title) },
        navigationIcon = {
            if (canGoBack) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                }
            }
        },
        actions = {
            IconButton(onClick = onScan) {
                Icon(Icons.Default.QrCodeScanner, contentDescription = "Scan")
            }
        }
    )
}

@Composable
private fun HomeScreen(
    viewModel: AppViewModel,
    onOpenBox: (Long) -> Unit,
    onCreateBox: () -> Unit,
    onScan: () -> Unit
) {
    val boxes by viewModel.boxes.collectAsStateWithLifecycle()
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    var query by remember(ui.searchQuery) { mutableStateOf(ui.searchQuery) }
    val searchResults by viewModel.searchResults.collectAsStateWithLifecycle()

    LaunchedEffect(ui.lastScannedItemCode) {
        ui.lastScannedItemCode?.let {
            query = it
            viewModel.updateSearchQuery(it)
        }
    }

    Scaffold(
        floatingActionButton = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                FloatingActionButton(onClick = onScan) {
                    Icon(Icons.Default.CameraAlt, contentDescription = "Scan")
                }
                FloatingActionButton(onClick = onCreateBox) {
                    Icon(Icons.Default.Add, contentDescription = "Add box")
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = {
                        query = it
                        viewModel.updateSearchQuery(it)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Search items by name, description, or barcode") }
                )
            }
            if (ui.searchQuery.isNotBlank()) {
                item {
                    Text(
                        text = "Search results",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                items(searchResults, key = { it.id }) { result ->
                    SearchItemCard(result = result, onOpenBox = onOpenBox)
                }
            } else {
                item {
                    Text(
                        text = "Your boxes",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                items(boxes, key = { it.box.id }) { boxWithItems ->
                    BoxCard(
                        box = boxWithItems.box,
                        itemCount = boxWithItems.items.size,
                        onClick = { onOpenBox(boxWithItems.box.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun BoxCard(
    box: BoxEntity,
    itemCount: Int,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = box.name, style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = "Label: ${box.labelCode}", style = MaterialTheme.typography.bodyMedium)
            Text(text = "Location: ${box.location.ifBlank { "Not set" }}")
            Text(text = "Items: $itemCount")
        }
    }
}

@Composable
private fun SearchItemCard(
    result: ItemEntity,
    onOpenBox: (Long) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(text = result.name, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = result.description.ifBlank { "No description" })
            Text(text = "Barcode/QR: ${result.barcodeOrQr.ifBlank { "None" }}")
            TextButton(onClick = { onOpenBox(result.boxId) }) {
                Text("Open box")
            }
        }
    }
}

@Composable
private fun BoxDetailScreen(
    viewModel: AppViewModel,
    boxId: Long,
    onAddItem: () -> Unit
) {
    val boxStateFlow = remember(boxId) {
        viewModel.boxDetail(boxId).map { box ->
            if (box == null) {
                BoxDetailUiState.NotFound
            } else {
                BoxDetailUiState.Loaded(box)
            }
        }
    }
    val itemsFlow = remember(boxId) { viewModel.itemsForBox(boxId) }
    val boxState by boxStateFlow.collectAsStateWithLifecycle(initialValue = BoxDetailUiState.Loading)
    val items by itemsFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val box = when (val state = boxState) {
        BoxDetailUiState.Loading -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Loading box details…")
            }
            return
        }
        BoxDetailUiState.NotFound -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Box not found")
            }
            return
        }
        is BoxDetailUiState.Loaded -> state.box
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddItem) {
                Icon(Icons.Default.Add, contentDescription = "Add item")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(box.name, style = MaterialTheme.typography.headlineSmall)
            Text("Label code: ${box.labelCode}")
            Text("Location: ${box.location.ifBlank { "Not set" }}")
            Text("Description: ${box.description.ifBlank { "No description" }}")
            Text("Created: ${formatDate(box.createdAt)}")
            LabelImageCard(
                pngPath = box.labelPngPath,
                onShare = {
                    val shared = shareLabel(context, box.labelPngPath)
                    if (!shared) {
                        scope.launch { snackbar.showSnackbar("Label image not available.") }
                    }
                }
            )
            Text(
                text = "Items (${items.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            items.forEach { item ->
                ItemCard(item = item)
            }
        }
    }
}

private sealed interface BoxDetailUiState {
    data object Loading : BoxDetailUiState
    data object NotFound : BoxDetailUiState
    data class Loaded(val box: BoxEntity) : BoxDetailUiState
}

@Composable
private fun LabelImageCard(
    pngPath: String,
    onShare: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Box label (QR image)")
            val bitmap = remember(pngPath) {
                val file = File(pngPath)
                if (file.exists()) BitmapFactory.decodeFile(file.absolutePath) else null
            }
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "QR label",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .background(MaterialTheme.colorScheme.surface)
                )
            } else {
                Text("Label image not available yet.")
            }
            TextButton(onClick = onShare) {
                Text("Share / print label")
            }
        }
    }
}

@Composable
private fun ItemCard(item: ItemEntity) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(item.name, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            Text(item.description.ifBlank { "No description" })
            Text("Barcode/QR: ${item.barcodeOrQr.ifBlank { "Not set" }}")
            Text("Qty: ${item.quantity}")
            Text("Low stock threshold: ${item.minimumStock}")
            Text("Updated: ${formatDate(item.updatedAt)}")
        }
    }
}

@Composable
private fun NewBoxScreen(
    viewModel: AppViewModel,
    onCreated: (Long) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    val canCreate = name.isNotBlank()
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Box name") }
        )
        OutlinedTextField(
            value = location,
            onValueChange = { location = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Location") }
        )
        OutlinedTextField(
            value = description,
            onValueChange = { description = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Description") }
        )
        Button(
            enabled = canCreate,
            onClick = {
                scope.launch {
                    val boxId = viewModel.createBoxSuspending(
                        name = name.trim(),
                        location = location.trim(),
                        description = description.trim()
                    )
                    onCreated(boxId)
                }
            }
        ) {
            Text("Create box and label")
        }
    }
}

@Composable
private fun NewItemScreen(
    boxId: Long,
    viewModel: AppViewModel,
    onDone: () -> Unit,
    onScan: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var barcode by remember { mutableStateOf("") }
    var quantityText by remember { mutableStateOf("1") }
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    val canSave = name.isNotBlank()

    LaunchedEffect(ui.lastScannedItemCode) {
        ui.lastScannedItemCode?.let { barcode = it }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Item name") }
        )
        OutlinedTextField(
            value = description,
            onValueChange = { description = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Description") }
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = barcode,
                onValueChange = { barcode = it },
                modifier = Modifier.weight(1f),
                label = { Text("Barcode/QR value") }
            )
            Button(
                onClick = onScan,
                modifier = Modifier.align(Alignment.CenterVertically)
            ) {
                Icon(Icons.Default.QrCodeScanner, contentDescription = null)
                Text("Scan")
            }
        }
        OutlinedTextField(
            value = quantityText,
            onValueChange = { quantityText = it.filter { ch -> ch.isDigit() } },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Quantity") }
        )
        Button(
            enabled = canSave,
            onClick = {
                val qty = quantityText.toIntOrNull() ?: 1
                viewModel.addItem(
                    boxId = boxId,
                    name = name.trim(),
                    description = description.trim(),
                    barcode = barcode.trim(),
                    quantity = qty
                )
                viewModel.clearLastScannedItemCode()
                onDone()
            }
        ) {
            Icon(Icons.Default.Inventory2, contentDescription = null)
            Text("Save item")
        }
    }
}

@Composable
private fun ScanScreen(
    mode: String,
    viewModel: AppViewModel,
    onBoxFound: (Long) -> Unit,
    onDone: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
    }

    val scanner = remember { BarcodeScanning.getClient() }
    val analyzerExecutor: ExecutorService = remember { Executors.newSingleThreadExecutor() }
    var handled by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    DisposableEffect(Unit) {
        onDispose {
            analyzerExecutor.shutdown()
            scanner.close()
        }
    }

    LaunchedEffect(Unit) {
        if (!hasPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    if (!hasPermission) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Camera permission is required to scan codes.")
        }
        return
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = {
                val previewView = PreviewView(it)
                val cameraProviderFuture = ProcessCameraProvider.getInstance(it)
                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()
                    val preview = Preview.Builder().build().also { p ->
                        p.surfaceProvider = previewView.surfaceProvider
                    }
                    val analysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()

                    analysis.setAnalyzer(analyzerExecutor) { imageProxy ->
                        val mediaImage = imageProxy.image
                        if (mediaImage != null && !handled) {
                            val image = InputImage.fromMediaImage(
                                mediaImage,
                                imageProxy.imageInfo.rotationDegrees
                            )
                            scanner.process(image)
                                .addOnSuccessListener { barcodes ->
                                    val code = firstCode(barcodes)
                                    if (!code.isNullOrBlank() && !handled) {
                                        handled = true
                                        scope.launch {
                                            if (mode == "item") {
                                                val parsed = viewModel.parseCodeFromRawScan(code) ?: code
                                                viewModel.recordScannedItemCode(parsed)
                                                onDone()
                                            } else {
                                                val boxId = viewModel.resolveScannedCodeToBoxSuspending(code)
                                                if (boxId != null) {
                                                    onBoxFound(boxId)
                                                } else {
                                                    val parsed = viewModel.parseCodeFromRawScan(code) ?: code
                                                    viewModel.recordScannedItemCode(parsed)
                                                    onDone()
                                                }
                                            }
                                        }
                                    }
                                }
                                .addOnCompleteListener { imageProxy.close() }
                        } else {
                            imageProxy.close()
                        }
                    }
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        analysis
                    )
                }, ContextCompat.getMainExecutor(it))
                previewView
            },
            modifier = Modifier.fillMaxSize()
        )
        Text(
            text = "Point camera at a box label or item barcode",
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(16.dp)
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.8f))
                .padding(12.dp)
        )
    }
}

private fun firstCode(barcodes: List<Barcode>): String? {
    return barcodes.firstOrNull()?.rawValue
}

private fun shareLabel(context: Context, labelPath: String): Boolean {
    val file = File(labelPath)
    if (!file.exists()) return false
    val uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file
    )
    val share = Intent(Intent.ACTION_SEND).apply {
        putExtra(Intent.EXTRA_STREAM, uri)
        type = "image/png"
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(share, "Share label"))
    return true
}

private fun formatDate(epochMillis: Long): String {
    return SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(epochMillis))
}
