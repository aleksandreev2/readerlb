package com.readerlb.app

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.readerlb.app.importer.ExportProgress
import com.readerlb.app.importer.ExportStage
import com.readerlb.app.importer.ImportRepository
import com.readerlb.app.importer.ParsedBook
import com.readerlb.app.importer.RanobeLibExporter
import com.readerlb.app.importer.ReaderBlock
import com.readerlb.app.importer.chapterNumberInRange
import com.readerlb.app.importer.compareChapterNumbers
import com.readerlb.app.storage.HistoryStore
import com.readerlb.app.storage.ImportHistoryItem
import com.readerlb.app.storage.LocalLibraryItem
import com.readerlb.app.storage.Preferences
import com.readerlb.app.storage.RanobeLibLibraryScanner
import com.readerlb.app.storage.decodeLocalLibraryCache
import com.readerlb.app.storage.encodeLocalLibraryCache
import com.readerlb.app.update.UpdateInfo
import com.readerlb.app.update.UpdateManager
import com.readerlb.app.ui.theme.Blue
import com.readerlb.app.ui.theme.Canvas
import com.readerlb.app.ui.theme.Cyan
import com.readerlb.app.ui.theme.Ink
import com.readerlb.app.ui.theme.Line
import com.readerlb.app.ui.theme.Muted
import com.readerlb.app.ui.theme.Navy
import com.readerlb.app.ui.theme.Navy2
import com.readerlb.app.ui.theme.ReaderLBTheme
import com.readerlb.app.ui.theme.Success
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

private data class IncomingImportRequest(
    val uri: Uri,
    val requestId: Long
)

class MainActivity : ComponentActivity() {
    private var requestSequence = 0L
    private var incomingImport by mutableStateOf<
        IncomingImportRequest?
    >(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        acceptImportIntent(intent)

        setContent {
            ReaderLBTheme {
                ReaderLBRoot(
                    incomingImport = incomingImport,
                    onImportConsumed = {
                        incomingImport = null
                    }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        acceptImportIntent(intent)
    }

    private fun acceptImportIntent(intent: Intent?) {
        val uri = incomingImportUri(intent)
            ?: return

        requestSequence += 1
        incomingImport = IncomingImportRequest(
            uri = uri,
            requestId = requestSequence
        )
    }
}

private fun incomingImportUri(
    intent: Intent?
): Uri? {
    if (intent == null) return null

    return when (intent.action) {
        Intent.ACTION_VIEW -> intent.data
        Intent.ACTION_SEND -> {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(
                Intent.EXTRA_STREAM
            ) as? Uri
        }
        else -> null
    }
}

private enum class AppTab { HOME, IMPORT, LIBRARY, SETTINGS }

private val RANOBELIB_BOOK_INITIAL_URI: Uri = Uri.parse(
    "content://com.android.externalstorage.documents/document/" +
        "primary%3AAndroid%2Fdata%2Fru.libappc%2Ffiles%2Fbook"
)

private fun hasPersistedReadPermission(
    context: android.content.Context,
    uri: Uri
): Boolean =
    context.contentResolver
        .persistedUriPermissions
        .any { permission ->
            permission.uri == uri &&
                permission.isReadPermission
        }

private fun hasPersistedTreePermission(
    context: android.content.Context,
    uri: Uri
): Boolean =
    context.contentResolver
        .persistedUriPermissions
        .any { permission ->
            permission.uri == uri &&
                permission.isReadPermission &&
                permission.isWritePermission
        }

@Composable
private fun ReaderLBRoot(
    incomingImport: IncomingImportRequest?,
    onImportConsumed: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val preferences = remember { Preferences(context) }
    var onboardingDone by remember {
        mutableStateOf(preferences.onboardingDone)
    }

    if (!onboardingDone) {
        Onboarding(
            onDone = {
                preferences.onboardingDone = true
                onboardingDone = true
            }
        )
    } else {
        MainApp(
            incomingImport = incomingImport,
            onImportConsumed = onImportConsumed
        )
    }
}

@Composable
private fun Onboarding(onDone: () -> Unit) {
    var page by remember { mutableIntStateOf(0) }
    val pageCount = 3
    val uriHandler =
        androidx.compose.ui.platform.LocalUriHandler.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFF061B30),
                        Navy,
                        Color(0xFF03111F)
                    )
                )
            )
    ) {
        Box(
            Modifier
                .size(380.dp)
                .align(Alignment.Center)
                .background(
                    Brush.radialGradient(
                        listOf(
                            Color(0x3326CCFF),
                            Color.Transparent
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    horizontal = 18.dp,
                    vertical = 18.dp
                ),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            OnboardingArtwork(
                page = page,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            )

            Spacer(Modifier.height(14.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                repeat(pageCount) { index ->
                    Box(
                        Modifier
                            .size(
                                if (index == page) 10.dp
                                else 8.dp
                            )
                            .clip(
                                RoundedCornerShape(99.dp)
                            )
                            .background(
                                if (index == page) Cyan
                                else Color(0xFF52657A)
                            )
                    )
                }
            }

            Spacer(Modifier.height(18.dp))

            GradientButton(
                text = if (page == pageCount - 1) {
                    "Понятно"
                } else {
                    "Следующее  →"
                },
                onClick = {
                    if (page == pageCount - 1) {
                        onDone()
                    } else {
                        page++
                    }
                }
            )

            if (page < pageCount - 1) {
                Text(
                    "Пропустить",
                    color = Cyan,
                    modifier = Modifier
                        .padding(top = 14.dp)
                        .clickable(onClick = onDone),
                    fontSize = 15.sp
                )
            } else {
                Row(
                    modifier = Modifier.padding(top = 14.dp),
                    verticalAlignment =
                        Alignment.CenterVertically
                ) {
                    Text(
                        "Разработчик: dollar · ",
                        color = Color(0xFF8DA7BF),
                        fontSize = 12.sp
                    )
                    Text(
                        "Дом Некроманта",
                        color = Cyan,
                        fontSize = 12.sp,
                        fontWeight =
                            FontWeight.SemiBold,
                        modifier = Modifier.clickable {
                            uriHandler.openUri(
                                "https://t.me/domnekromanta"
                            )
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun OnboardingArtwork(
    page: Int,
    modifier: Modifier = Modifier
) {
    val context =
        androidx.compose.ui.platform.LocalContext.current

    val bitmap = remember(page) {
        runCatching {
            val assetName =
                "onboarding_${page + 1}.webp.b64"
            val encoded = context.assets
                .open(assetName)
                .bufferedReader()
                .use { it.readText() }
                .trim()

            val bytes = Base64.decode(
                encoded,
                Base64.DEFAULT
            )

            BitmapFactory.decodeByteArray(
                bytes,
                0,
                bytes.size
            ) ?: error(
                "Не удалось декодировать $assetName"
            )
        }.getOrNull()
    }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription =
                    "Экран знакомства ReaderLB ${page + 1}",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth()
                    .clip(
                        RoundedCornerShape(26.dp)
                    )
            )
        } else {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(520.dp),
                colors = CardDefaults.cardColors(
                    containerColor =
                        Color(0xFF0D2945)
                ),
                shape = RoundedCornerShape(28.dp),
                border =
                    androidx.compose.foundation.BorderStroke(
                        1.dp,
                        Color(0x334BC4FF)
                    )
            ) {
                Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment =
                            Alignment.CenterHorizontally
                    ) {
                        ReaderLogo(96.dp)
                        Spacer(Modifier.height(20.dp))
                        Text(
                            "ReaderLB",
                            color = Color.White,
                            fontSize = 28.sp,
                            fontWeight =
                                FontWeight.Bold
                        )
                        Text(
                            "Экран ${page + 1} / 3",
                            color = Cyan,
                            modifier =
                                Modifier.padding(top = 8.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MainApp(
    incomingImport: IncomingImportRequest?,
    onImportConsumed: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val preferences = remember { Preferences(context) }
    val historyStore = remember { HistoryStore(context) }
    val libraryScanner = remember {
        RanobeLibLibraryScanner(context)
    }
    val updateManager = remember { UpdateManager(context) }
    val scope = rememberCoroutineScope()
    var history by remember {
        mutableStateOf(historyStore.load())
    }
    var localLibrary by remember {
        mutableStateOf(
            if (
                preferences.localLibraryCacheTree ==
                preferences.ranobeLibBookTree
            ) {
                runCatching {
                    decodeLocalLibraryCache(
                        preferences
                            .localLibraryCacheJson
                    )
                }.getOrDefault(
                    emptyList()
                )
            } else {
                emptyList()
            }
        )
    }
    var libraryLoading by remember {
        mutableStateOf(false)
    }
    var librarySkipped by remember {
        mutableIntStateOf(0)
    }
    var libraryError by remember {
        mutableStateOf<String?>(null)
    }
    var libraryScanned by remember {
        mutableIntStateOf(0)
    }
    var libraryScanTotal by remember {
        mutableIntStateOf(0)
    }
    var libraryStatus by remember {
        mutableStateOf<String?>(null)
    }
    var libraryScanJob by remember {
        mutableStateOf<Job?>(null)
    }
    var libraryRefreshPending by remember {
        mutableStateOf(false)
    }
    var tab by rememberSaveable {
        mutableStateOf(AppTab.HOME)
    }

    LaunchedEffect(incomingImport?.requestId) {
        if (incomingImport != null) {
            tab = AppTab.IMPORT
        }
    }
    val savedFolderUri =
        remember {
            preferences.ranobeLibBookTree
        }
    var folderUri by remember {
        mutableStateOf(
            savedFolderUri?.takeIf {
                hasPersistedTreePermission(
                    context,
                    it
                )
            }
        )
    }

    LaunchedEffect(Unit) {
        if (
            savedFolderUri != null &&
            folderUri == null
        ) {
            preferences.ranobeLibBookTree = null
        }
    }
    var importHintsDone by remember {
        mutableStateOf(preferences.importHintsDone)
    }
    var latestUpdate by remember {
        mutableStateOf<UpdateInfo?>(null)
    }
    var autoUpdateChecks by remember {
        mutableStateOf(preferences.autoUpdateChecks)
    }
    var directImportEnabled by remember {
        mutableStateOf(
            preferences.directImportEnabled
        )
    }
    var updateBusy by remember { mutableStateOf(false) }
    var updateMessage by remember {
        mutableStateOf<String?>(null)
    }

    fun refreshLocalLibrary() {
        val tree = folderUri

        if (tree == null) {
            libraryScanJob?.cancel()
            libraryScanJob = null
            localLibrary = emptyList()
            librarySkipped = 0
            libraryError = null
            libraryStatus = null
            libraryScanned = 0
            libraryScanTotal = 0
            libraryLoading = false
            return
        }

        if (
            libraryScanJob?.isActive == true
        ) {
            libraryRefreshPending = true
            return
        }

        libraryScanJob = scope.launch {
            do {
                libraryRefreshPending = false
                val previous = localLibrary

                libraryLoading = true
                libraryError = null
                libraryStatus = null
                libraryScanned = 0
                libraryScanTotal = 0

                val result = try {
                    withTimeout(
                        120_000L
                    ) {
                        runInterruptible(
                            Dispatchers.IO
                        ) {
                            Result.success(
                                libraryScanner.scan(
                                    tree
                                ) {
                                        completed,
                                        total ->
                                    scope.launch {
                                        libraryScanned =
                                            maxOf(
                                                libraryScanned,
                                                completed
                                            )
                                        libraryScanTotal =
                                            maxOf(
                                                libraryScanTotal,
                                                total
                                            )
                                    }
                                }
                            )
                        }
                    }
                } catch (
                    timeout:
                    TimeoutCancellationException
                ) {
                    Result.failure(
                        IllegalStateException(
                            "RanobeLib слишком долго отвечает. " +
                                "Проверьте доступ к папке book " +
                                "и повторите сканирование."
                        )
                    )
                } catch (
                    cancelled:
                    CancellationException
                ) {
                    throw cancelled
                } catch (
                    throwable:
                    Throwable
                ) {
                    Result.failure(throwable)
                }

                result.onSuccess {
                        snapshot ->
                    localLibrary =
                        snapshot.items
                    preferences
                        .localLibraryCacheTree =
                        tree
                    preferences
                        .localLibraryCacheJson =
                        encodeLocalLibraryCache(
                            snapshot.items
                        )
                    librarySkipped =
                        snapshot.skippedTitles
                    libraryScanned =
                        snapshot.items.size +
                            snapshot.skippedTitles
                    libraryScanTotal =
                        maxOf(
                            libraryScanTotal,
                            libraryScanned
                        )
                    libraryStatus =
                        if (
                            previous ==
                            snapshot.items
                        ) {
                            "Локальные тайтлы уже актуальны"
                        } else {
                            "Обновлено: " +
                                snapshot.items.size +
                                " тайтлов"
                        }
                }.onFailure {
                    libraryError =
                        it.message
                            ?: "Не удалось прочитать библиотеку RanobeLib"
                }

                libraryLoading = false
            } while (
                libraryRefreshPending &&
                folderUri == tree
            )
        }
    }

    LaunchedEffect(folderUri) {
        libraryScanJob?.cancel()
        libraryScanJob = null
        libraryRefreshPending = false
        refreshLocalLibrary()
    }

    LaunchedEffect(Unit) {
        val now = System.currentTimeMillis()
        val due =
            now - preferences.lastUpdateCheckMillis >=
                24L * 60L * 60L * 1000L
        if (due && autoUpdateChecks) {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    updateManager.checkLatest()
                }
            }
            result.onSuccess { update ->
                latestUpdate = update
                preferences.lastUpdateCheckMillis = now
            }
        }
    }

    fun checkForUpdates() {
        if (updateBusy) return
        updateBusy = true
        updateMessage = null
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    updateManager.checkLatest()
                }
            }
            preferences.lastUpdateCheckMillis =
                System.currentTimeMillis()
            result.onSuccess { update ->
                latestUpdate = update
                updateMessage = if (update == null) {
                    "Установлена актуальная версия ReaderLB."
                } else {
                    "Доступна ReaderLB ${update.versionName}."
                }
            }.onFailure {
                updateMessage =
                    "Не удалось проверить обновления: " +
                        (it.message ?: "ошибка сети")
            }
            updateBusy = false
        }
    }

    fun installLatestUpdate() {
        val update = latestUpdate ?: return
        if (updateBusy) return

        if (!updateManager.canRequestInstallPackages()) {
            context.startActivity(
                updateManager.unknownSourcesSettingsIntent()
            )
            updateMessage =
                "Разрешите ReaderLB устанавливать обновления, " +
                    "затем нажмите «Обновить» ещё раз."
            return
        }

        updateBusy = true
        updateMessage = "Загрузка обновления…"
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    updateManager.download(update)
                }
            }.onSuccess { apk ->
                updateMessage =
                    "Обновление загружено. Передаю установку Android…"
                updateManager.requestInstall(apk)
            }.onFailure {
                updateMessage =
                    com.readerlb.app.update
                        .friendlyUpdateDownloadError(it)
            }
            updateBusy = false
        }
    }

    val folderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            val flags =
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION

            val persisted = runCatching {
                context.contentResolver
                    .takePersistableUriPermission(
                        uri,
                        flags
                    )
            }.isSuccess

            if (folderUri != uri) {
                localLibrary = emptyList()
                preferences
                    .localLibraryCacheTree =
                    null
                preferences
                    .localLibraryCacheJson =
                    null
            }

            folderUri = uri
            preferences.ranobeLibBookTree =
                if (persisted) {
                    uri
                } else {
                    null
                }
        }
    }

    Scaffold(
        containerColor = Canvas,
        bottomBar = {
            if (tab != AppTab.IMPORT) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                NavigationBarItem(
                    selected = tab == AppTab.HOME,
                    onClick = { tab = AppTab.HOME },
                    icon = { Icon(Icons.Default.Home, null) },
                    label = { Text("Главная") }
                )
                NavigationBarItem(
                    selected = tab == AppTab.IMPORT,
                    onClick = { tab = AppTab.IMPORT },
                    icon = { Icon(Icons.Default.Add, null) },
                    label = { Text("Импорт") }
                )
                NavigationBarItem(
                    selected = tab == AppTab.LIBRARY,
                    onClick = { tab = AppTab.LIBRARY },
                    icon = { Icon(Icons.Default.List, null) },
                    label = { Text("Библиотека") }
                )
                NavigationBarItem(
                    selected = tab == AppTab.SETTINGS,
                    onClick = { tab = AppTab.SETTINGS },
                    icon = { Icon(Icons.Default.Settings, null) },
                    label = { Text("Настройки") }
                )
                }
            }
        }
    ) { padding ->
        when (tab) {
            AppTab.HOME -> HomeScreen(
                modifier = Modifier.padding(padding),
                history = history,
                localLibrary = localLibrary,
                libraryConnected = folderUri != null,
                libraryLoading = libraryLoading,
                libraryScanned = libraryScanned,
                libraryScanTotal = libraryScanTotal,
                updateInfo = latestUpdate,
                updateBusy = updateBusy,
                updateMessage = updateMessage,
                onUpdate = ::installLatestUpdate,
                onAdd = { tab = AppTab.IMPORT },
                onOpenLibrary = { tab = AppTab.LIBRARY },
                onSettings = { tab = AppTab.SETTINGS }
            )
            AppTab.IMPORT -> ImportScreen(
                modifier = Modifier.padding(padding),
                folderUri = folderUri,
                directImportEnabled =
                    directImportEnabled,
                onDirectImportChanged = {
                    enabled ->
                    preferences.directImportEnabled =
                        enabled
                    directImportEnabled = enabled
                },
                initialImport = incomingImport,
                onInitialImportConsumed =
                    onImportConsumed,
                showHints = !importHintsDone,
                onHintsDone = {
                    preferences.importHintsDone = true
                    importHintsDone = true
                },
                onBack = { tab = AppTab.HOME },
                onPickFolder = {
                    folderPicker.launch(
                        RANOBELIB_BOOK_INITIAL_URI
                    )
                },
                onImported = {
                    historyStore.add(it)
                    history = historyStore.load()
                    refreshLocalLibrary()
                },
                onOpenLibrary = {
                    tab = AppTab.LIBRARY
                }
            )
            AppTab.LIBRARY -> LibraryScreen(
                modifier = Modifier.padding(padding),
                items = localLibrary,
                connected = folderUri != null,
                loading = libraryLoading,
                scanned = libraryScanned,
                scanTotal = libraryScanTotal,
                status = libraryStatus,
                skippedTitles = librarySkipped,
                error = libraryError,
                onRefresh = ::refreshLocalLibrary,
                onPickFolder = {
                    folderPicker.launch(
                        RANOBELIB_BOOK_INITIAL_URI
                    )
                },
                onAdd = {
                    tab = AppTab.IMPORT
                }
            )
            AppTab.SETTINGS -> SettingsScreen(
                modifier = Modifier.padding(padding),
                folderUri = folderUri,
                updateInfo = latestUpdate,
                autoUpdateChecks = autoUpdateChecks,
                updateBusy = updateBusy,
                updateMessage = updateMessage,
                onAutoUpdateChecksChanged = { enabled ->
                    preferences.autoUpdateChecks = enabled
                    autoUpdateChecks = enabled
                },
                onCheckUpdates = ::checkForUpdates,
                onInstallUpdate = ::installLatestUpdate,
                onRepeatHints = {
                    preferences.importHintsDone = false
                    importHintsDone = false
                    tab = AppTab.IMPORT
                },
                onPickFolder = {
                    folderPicker.launch(
                        RANOBELIB_BOOK_INITIAL_URI
                    )
                },
                onForgetFolder = {
                    preferences.ranobeLibBookTree = null
                    preferences.localLibraryCacheTree = null
                    preferences.localLibraryCacheJson = null
                    localLibrary = emptyList()
                    folderUri = null
                }
            )
        }
    }
}

@Composable
private fun HomeScreen(
    modifier: Modifier,
    history: List<ImportHistoryItem>,
    localLibrary: List<LocalLibraryItem>,
    libraryConnected: Boolean,
    libraryLoading: Boolean,
    libraryScanned: Int,
    libraryScanTotal: Int,
    updateInfo: UpdateInfo?,
    updateBusy: Boolean,
    updateMessage: String?,
    onUpdate: () -> Unit,
    onAdd: () -> Unit,
    onOpenLibrary: () -> Unit,
    onSettings: () -> Unit
) {
    val shownTitleCount =
        if (libraryConnected) {
            localLibrary.size
        } else {
            history.size
        }
    val totalChapters =
        if (libraryConnected) {
            localLibrary.sumOf {
                it.chapterCount
            }
        } else {
            history.sumOf { it.chapters }
        }
    val installedCount =
        if (libraryConnected) {
            localLibrary.size
        } else {
            history.count {
                it.installedDirectly
            }
        }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ReaderLogo(54.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "Импортер глав для",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Ink
                    )
                    Row {
                        Text("RanobeLib ", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Ink)
                        Text("APP", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Blue)
                    }
                }
                IconButton(onClick = onSettings) {
                    Icon(
                        Icons.Default.Settings,
                        contentDescription = "Настройки",
                        tint = Ink
                    )
                }
            }
        }

        item {
            GradientButton(
                text = "＋  Добавить новеллу",
                onClick = onAdd
            )
        }

        if (updateInfo != null) {
            item {
                UpdateAvailableCard(
                    info = updateInfo,
                    busy = updateBusy,
                    onUpdate = onUpdate
                )
            }
        }

        updateMessage?.let { message ->
            item {
                Text(
                    message,
                    color = Muted,
                    fontSize = 12.sp
                )
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatCard(
                    Modifier.weight(1f),
                    Icons.Default.List,
                    shownTitleCount.toString(),
                    "Новеллы"
                )
                StatCard(
                    Modifier.weight(1f),
                    Icons.Default.List,
                    totalChapters.toString(),
                    "Главы"
                )
                StatCard(
                    Modifier.weight(1f),
                    Icons.Default.Check,
                    installedCount.toString(),
                    "В RanobeLib"
                )
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (libraryConnected) {
                        "Локальная библиотека"
                    } else {
                        "Последние импорты"
                    },
                    modifier = Modifier.weight(1f),
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Все",
                    color = Blue,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable(
                        onClick = onOpenLibrary
                    )
                )
            }
        }

        when {
            libraryConnected &&
                libraryLoading &&
                localLibrary.isEmpty() -> {
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(18.dp),
                            verticalAlignment =
                                Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                modifier =
                                    Modifier.size(24.dp),
                                strokeWidth = 2.dp,
                                color = Blue
                            )
                            Text(
                                if (libraryScanTotal > 0) {
                                    "Читаю локальную библиотеку: " +
                                        libraryScanned +
                                        " из " +
                                        libraryScanTotal
                                } else if (
                                    libraryScanned > 0
                                ) {
                                    "Читаю локальную библиотеку: " +
                                        libraryScanned
                                } else {
                                    "Читаю локальную библиотеку RanobeLib…"
                                },
                                color = Muted,
                                fontSize = 13.sp,
                                modifier =
                                    Modifier.padding(
                                        start = 12.dp
                                    )
                            )
                        }
                    }
                }
            }

            libraryConnected &&
                localLibrary.isNotEmpty() -> {
                items(
                    localLibrary.take(6),
                    key = { it.slugUrl }
                ) { item ->
                    LocalLibraryCard(item)
                }
            }

            !libraryConnected &&
                history.isNotEmpty() -> {
                items(history.take(6)) { item ->
                    HistoryCard(item)
                }
            }

            else -> {
                item {
                    EmptyLibraryCard(onAdd)
                }
            }
        }
    }
}

@Composable
private fun ImportScreen(
    modifier: Modifier,
    folderUri: Uri?,
    directImportEnabled: Boolean,
    onDirectImportChanged: (Boolean) -> Unit,
    initialImport: IncomingImportRequest?,
    onInitialImportConsumed: () -> Unit,
    showHints: Boolean,
    onHintsDone: () -> Unit,
    onBack: () -> Unit,
    onPickFolder: () -> Unit,
    onImported: (com.readerlb.app.importer.ExportResult) -> Unit,
    onOpenLibrary: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember { ImportRepository(context) }
    val exporter = remember { RanobeLibExporter(context) }
    val importPreferences = remember {
        Preferences(context)
    }
    var recentDocument by remember {
        mutableStateOf(
            importPreferences.lastImportDocument
                ?.takeIf {
                    hasPersistedReadPermission(
                        context,
                        it
                    )
                }
        )
    }

    LaunchedEffect(Unit) {
        if (
            importPreferences.lastImportDocument != null &&
            recentDocument == null
        ) {
            importPreferences.lastImportDocument =
                null
        }
    }

    var parsed by remember {
        mutableStateOf<ParsedBook?>(null)
    }
    var selectedUri by rememberSaveable {
        mutableStateOf<String?>(null)
    }
    var parseGeneration by rememberSaveable {
        mutableIntStateOf(0)
    }
    var fileName by rememberSaveable {
        mutableStateOf("")
    }
    var title by rememberSaveable {
        mutableStateOf("")
    }
    var firstChapter by rememberSaveable {
        mutableStateOf("")
    }
    var lastChapter by rememberSaveable {
        mutableStateOf("")
    }
    var direct by rememberSaveable {
        mutableStateOf(
            directImportEnabled
        )
    }
    var busy by remember {
        mutableStateOf(false)
    }
    var exportProgress by remember {
        mutableStateOf<ExportProgress?>(null)
    }
    var error by rememberSaveable {
        mutableStateOf<String?>(null)
    }
    var success by rememberSaveable {
        mutableStateOf<String?>(null)
    }
    var warningsAcknowledged by rememberSaveable {
        mutableStateOf(false)
    }
    var rangeExpanded by rememberSaveable {
        mutableStateOf(false)
    }

    fun cleanupParsedAssets(
        book: ParsedBook? = parsed
    ) {
        book?.temporaryAssetDirectory
            ?.takeIf(String::isNotBlank)
            ?.let { path -> java.io.File(path) }
            ?.let { directory ->
                runCatching {
                    directory.deleteRecursively()
                }
            }
    }

    val exportBusyState =
        rememberUpdatedState(
            busy &&
                exportProgress != null
        )

    DisposableEffect(parsed) {
        val book = parsed
        onDispose {
            if (!exportBusyState.value) {
                cleanupParsedAssets(book)
            }
        }
    }

    fun queueFile(uri: Uri) {
        cleanupParsedAssets()
        val persisted = runCatching {
            context.contentResolver
                .takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
        }.isSuccess ||
            hasPersistedReadPermission(
                context,
                uri
            )

        if (persisted) {
            importPreferences.lastImportDocument =
                uri
            recentDocument = uri
        }

        selectedUri = uri.toString()
        fileName = repository.displayName(uri)
        parsed = null
        title = ""
        firstChapter = ""
        lastChapter = ""
        rangeExpanded = false
        error = null
        success = null
        warningsAcknowledged = false
        parseGeneration += 1
    }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            queueFile(uri)
        }
    }

    LaunchedEffect(initialImport?.requestId) {
        val request = initialImport
            ?: return@LaunchedEffect

        queueFile(request.uri)
        onInitialImportConsumed()
    }

    LaunchedEffect(
        selectedUri,
        parseGeneration
    ) {
        val rawUri = selectedUri
            ?: return@LaunchedEffect
        val generation = parseGeneration

        if (generation <= 0) {
            return@LaunchedEffect
        }

        val uri = Uri.parse(rawUri)
        parsed = null
        error = null
        success = null
        busy = true

        try {
            val book = withContext(Dispatchers.IO) {
                repository.parse(uri)
            }

            parsed = book
            if (title.isBlank()) {
                title = book.title
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (throwable: Throwable) {
            error =
                throwable.message
                    ?: "Не удалось разобрать файл"
        } finally {
            if (
                parseGeneration == generation
            ) {
                busy = false
            }
        }
    }

    fun cancelAnalysis() {
        if (
            busy &&
            parsed == null
        ) {
            selectedUri = null
            fileName = ""
            title = ""
            firstChapter = ""
            lastChapter = ""
            error = null
            success = null
            warningsAcknowledged = false
        }
    }

    val emptyImportState =
        parsed == null &&
            selectedUri == null &&
            !busy &&
            error == null

    if (emptyImportState) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(
                    horizontal = 18.dp
                )
        ) {
            ImportHeader(
                enabled = true,
                onBack = {
                    cleanupParsedAssets()
                    onBack()
                }
            )

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding =
                    PaddingValues(
                        top = 8.dp,
                        bottom = 112.dp
                    ),
                verticalArrangement =
                    Arrangement.spacedBy(
                        16.dp,
                        Alignment.CenterVertically
                    )
            ) {
                item {
                    FileDropCard(
                        fileName = fileName,
                        analyzing = false,
                        enabled = true,
                        onCancelAnalysis = {},
                        onPick = {
                            filePicker.launch(
                                arrayOf(
                                    "application/epub+zip",
                                    "application/zip",
                                    "text/plain",
                                    "application/octet-stream"
                                )
                            )
                        }
                    )
                }

                if (recentDocument != null) {
                    item {
                        OutlineAction(
                            "Открыть последний файл",
                            onClick = {
                                queueFile(
                                    requireNotNull(
                                        recentDocument
                                    )
                                )
                            }
                        )
                    }
                }

                if (showHints) {
                    item {
                        CoachHintCard(
                            title =
                                "Начните с файла",
                            text =
                                "Выберите EPUB или TXT. " +
                                    "ReaderLB сам найдёт главы, " +
                                    "обложку и иллюстрации — " +
                                    "ничего вручную заполнять " +
                                    "до анализа не нужно."
                        )
                    }
                }
            }
        }
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            horizontal = 18.dp,
            vertical = 18.dp
        ),
        verticalArrangement =
            Arrangement.spacedBy(16.dp)
    ) {
        item {
            ImportHeader(
                enabled = !busy,
                onBack = {
                    cleanupParsedAssets()
                    onBack()
                }
            )
        }

        item {
            FileDropCard(
                fileName = fileName,
                analyzing =
                    busy && parsed == null,
                enabled = !busy,
                onCancelAnalysis =
                    ::cancelAnalysis,
                onPick = {
                    filePicker.launch(
                        arrayOf(
                            "application/epub+zip",
                            "application/zip",
                            "text/plain",
                            "application/octet-stream"
                        )
                    )
                }
            )
        }

        if (
            selectedUri == null &&
            !busy &&
            recentDocument != null
        ) {
            item {
                OutlineAction(
                    "Открыть последний файл",
                    onClick = {
                        queueFile(
                            requireNotNull(
                                recentDocument
                            )
                        )
                    }
                )
            }
        }

        if (showHints && parsed == null && !busy) {
            item {
                CoachHintCard(
                    title = "Начните с файла",
                    text = "Выберите EPUB или TXT. ReaderLB сам найдёт " +
                        "главы, обложку и иллюстрации — ничего вручную " +
                        "заполнять до анализа не нужно."
                )
            }
        }

        parsed?.let { book ->
            item {
                ParsedPreview(book)
            }
            if (showHints) {
                item {
                    CoachHintCard(
                        title = "Файл уже проверен",
                        text = "Если количество глав и иллюстраций " +
                            "совпадает с ожиданием, оставьте «Все главы». " +
                            "Диапазон нужен только для частичного импорта.",
                        actionText = "Понятно",
                        onAction = onHintsDone
                    )
                }
            }
            val warningCount =
                book.issues.count {
                    it.severity ==
                        com.readerlb.app.importer
                            .ImportIssueSeverity.WARNING
                }

            if (warningCount > 0) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor =
                                if (warningsAcknowledged) {
                                    MaterialTheme.colorScheme.tertiaryContainer
                                } else {
                                    Color(0xFF3A2F16)
                                }
                        ),
                        shape = RoundedCornerShape(14.dp),
                        border =
                            androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (warningsAcknowledged) {
                                    Color(0xFF2F6A55)
                                } else {
                                    Color(0xFF7A6327)
                                }
                            )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement =
                                Arrangement.spacedBy(9.dp)
                        ) {
                            Text(
                                if (warningsAcknowledged) {
                                    "Предупреждения проверены"
                                } else {
                                    "Нужно проверить: " +
                                        warningCount
                                },
                                fontWeight = FontWeight.Bold,
                                color =
                                    if (warningsAcknowledged) {
                                        Color(0xFF82D9B7)
                                    } else {
                                        Color(0xFFE1B95B)
                                    }
                            )
                            Text(
                                if (warningsAcknowledged) {
                                    "ReaderLB разрешит импорт этого файла. " +
                                        "При выборе другого файла подтверждение " +
                                        "сбросится."
                                } else {
                                    "Посмотрите сообщения в карточке файла. " +
                                        "Импорт заблокирован, пока вы явно не " +
                                        "подтвердите, что результат выглядит верно."
                                },
                                color =
                                    if (warningsAcknowledged) {
                                        Color(0xFF82C7AA)
                                    } else {
                                        Color(0xFFD2A84F)
                                    },
                                fontSize = 12.sp,
                                lineHeight = 17.sp
                            )
                            if (!warningsAcknowledged) {
                                OutlineAction(
                                    "Я проверил — продолжить",
                                    onClick = {
                                        warningsAcknowledged =
                                            true
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        if (parsed != null) {
            item {
                Text(
                    "Название новеллы",
                    fontWeight = FontWeight.SemiBold,
                    color = Ink
                )
                Spacer(Modifier.height(7.dp))
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Введите название...") },
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )
            }
        }

        parsed?.let { book ->
            item {
                val first = book.chapters
                    .minWithOrNull(
                        Comparator { left, right ->
                            compareChapterNumbers(
                                left.number,
                                right.number
                            )
                        }
                    )
                    ?.number
                    .orEmpty()
                val last = book.chapters
                    .maxWithOrNull(
                        Comparator { left, right ->
                            compareChapterNumbers(
                                left.number,
                                right.number
                            )
                        }
                    )
                    ?.number
                    .orEmpty()

                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    shape = RoundedCornerShape(14.dp),
                    border =
                        androidx.compose.foundation.BorderStroke(
                            1.dp,
                            Line
                        )
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment =
                                Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "Главы",
                                    fontWeight = FontWeight.Bold,
                                    color = Ink
                                )
                                Text(
                                    if (
                                        firstChapter.isBlank() &&
                                        lastChapter.isBlank()
                                    ) {
                                        "Все ${book.chapters.size} · " +
                                            "$first–$last"
                                    } else {
                                        "Выбран диапазон"
                                    },
                                    color = Muted,
                                    fontSize = 12.sp,
                                    modifier =
                                        Modifier.padding(top = 3.dp)
                                )
                            }
                            Text(
                                if (rangeExpanded) {
                                    "Скрыть"
                                } else {
                                    "Изменить"
                                },
                                color = Blue,
                                fontWeight =
                                    FontWeight.SemiBold,
                                modifier = Modifier.clickable {
                                    rangeExpanded =
                                        !rangeExpanded
                                }
                            )
                        }

                        if (rangeExpanded) {
                            Spacer(Modifier.height(12.dp))
                            Column(
                                verticalArrangement =
                                    Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedTextField(
                                    value = firstChapter,
                                    onValueChange = {
                                        firstChapter =
                                            sanitizeChapterRangeInput(
                                                it
                                            )
                                    },
                                    modifier =
                                        Modifier.fillMaxWidth(),
                                    label = {
                                        Text("С главы")
                                    },
                                    placeholder = {
                                        Text(first)
                                    },
                                    keyboardOptions =
                                        KeyboardOptions(
                                            keyboardType =
                                                KeyboardType.Decimal
                                        ),
                                    singleLine = true,
                                    shape =
                                        RoundedCornerShape(12.dp)
                                )
                                OutlinedTextField(
                                    value = lastChapter,
                                    onValueChange = {
                                        lastChapter =
                                            sanitizeChapterRangeInput(
                                                it
                                            )
                                    },
                                    modifier =
                                        Modifier.fillMaxWidth(),
                                    label = {
                                        Text("По главу")
                                    },
                                    placeholder = {
                                        Text(last)
                                    },
                                    keyboardOptions =
                                        KeyboardOptions(
                                            keyboardType =
                                                KeyboardType.Decimal
                                        ),
                                    singleLine = true,
                                    shape =
                                        RoundedCornerShape(12.dp)
                                )
                            }
                            Text(
                                "Оставьте поля пустыми, чтобы " +
                                    "импортировать все главы.",
                                fontSize = 12.sp,
                                color = Muted,
                                modifier =
                                    Modifier.padding(top = 7.dp)
                            )
                        }
                    }
                }
            }
        }

        if (parsed != null) {
            item {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    border =
                        androidx.compose.foundation.BorderStroke(
                            1.dp,
                            Line
                        )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment =
                            Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "Добавить в RanobeLib",
                                fontWeight = FontWeight.Bold,
                                color = Ink
                            )
                            Text(
                                "Если тайтл уже существует, ReaderLB " +
                                    "добавит только отсутствующие главы.",
                                fontSize = 12.sp,
                                lineHeight = 17.sp,
                                color = Muted
                            )
                        }
                        Switch(
                            checked = direct,
                            onCheckedChange = {
                                enabled ->
                                direct = enabled
                                onDirectImportChanged(
                                    enabled
                                )
                            }
                        )
                    }
                }
            }
        }

        if (
            parsed != null &&
            direct &&
            folderUri == null
        ) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            "Нужно один раз указать папку book",
                            fontWeight = FontWeight.Bold,
                            color = Ink
                        )
                        Text(
                            "Выберите Android/data/ru.libappc/files/book. На Android 11+ системный проводник может запретить этот путь — тогда выключите прямое добавление, и ReaderLB сохранит готовый ZIP в Downloads/ReaderLB.",
                            color = Muted,
                            fontSize = 12.sp,
                            lineHeight = 18.sp,
                            modifier = Modifier.padding(top = 6.dp, bottom = 12.dp)
                        )
                        OutlineAction("Выбрать папку RanobeLib", onPickFolder)
                    }
                }
            }
        }

        if (
            busy &&
            parsed != null &&
            exportProgress != null
        ) {
            item {
                ImportProgressCard(
                    progress = requireNotNull(
                        exportProgress
                    ),
                    direct = direct
                )
            }
        }

        error?.let { message ->
            item { StatusCard(message, false) }

            if (
                parsed == null &&
                selectedUri != null &&
                !busy
            ) {
                item {
                    OutlineAction(
                        "Повторить анализ",
                        onClick = {
                            error = null
                            parseGeneration += 1
                        }
                    )
                }
            }
        }
        success?.let { message ->
            item {
                Column(
                    verticalArrangement =
                        Arrangement.spacedBy(10.dp)
                ) {
                    StatusCard(message, true)
                    OutlineAction(
                        "Открыть библиотеку",
                        onClick = {
                            cleanupParsedAssets()
                            onOpenLibrary()
                        }
                    )
                }
            }
        }

        parsed?.let { book ->
            item {
                val selectedCount =
                    book.chapters.count { chapter ->
                        chapterNumberInRange(
                            value = chapter.number,
                            first = firstChapter
                                .ifBlank { null },
                            last = lastChapter
                                .ifBlank { null }
                        )
                    }

                GradientButton(
                text = when {
                    busy ->
                        exportStageButtonText(
                            exportProgress,
                            direct
                        )
                    selectedCount == 0 ->
                        "Нет глав в диапазоне"
                    else ->
                        "Импортировать " +
                            chapterCountText(
                                selectedCount
                            )
                },
                enabled = !busy &&
                    selectedCount > 0 &&
                    parsed != null &&
                    (!direct || folderUri != null) &&
                    (parsed?.issues?.none {
                        it.severity == com.readerlb.app.importer.ImportIssueSeverity.WARNING
                    } != false || warningsAcknowledged),
                onClick = {
                    busy = true
                    exportProgress =
                        ExportProgress(
                            stage =
                                ExportStage.PREPARING
                        )
                    error = null
                    success = null
                    scope.launch {
                        runCatching {
                            withContext(Dispatchers.IO) {
                                exporter.export(
                                    book = book,
                                    titleOverride = title,
                                    firstChapter = firstChapter.ifBlank { null },
                                    lastChapter = lastChapter.ifBlank { null },
                                    ranobeLibBookTree =
                                        if (direct) {
                                            folderUri
                                        } else {
                                            null
                                        },
                                    onProgress = {
                                            progress ->
                                        scope.launch {
                                            exportProgress =
                                                progress
                                        }
                                    }
                                )
                            }
                        }.onSuccess { result ->
                            val baseSuccess = when {
                                !result.installedDirectly -> {
                                    "Готово: ZIP сохранён в Downloads/ReaderLB."
                                }
                                result.updatedExisting &&
                                    result.addedChapterCount == 0 -> {
                                    "Тайтл уже актуален: новых глав нет."
                                }
                                result.updatedExisting -> {
                                    "Обновлено: добавлено ${result.addedChapterCount} новых глав. " +
                                        "Всего в локальном тайтле: ${result.chapterCount}."
                                }
                                else -> {
                                    "Готово: ${result.chapterCount} глав добавлено в RanobeLib."
                                }
                            }
                            success = if (
                                book.domNekromantaEdition
                            ) {
                                baseSuccess +
                                    "\n\nПриятного чтения, товарищ! " +
                                    "(с) Некромант"
                            } else {
                                baseSuccess
                            }
                            onImported(result)
                        }.onFailure {
                            error = it.message ?: "Ошибка импорта"
                        }
                        exportProgress = null
                        busy = false
                    }
                }
                )
            }
        }
    }
}

private fun exportStageButtonText(
    progress: ExportProgress?,
    direct: Boolean
): String =
    when (progress?.stage) {
        ExportStage.PREPARING ->
            "Подготовка пакета…"
        ExportStage.VERIFYING ->
            "Проверка глав…"
        ExportStage.WRITING ->
            if (direct) {
                "Запись в RanobeLib…"
            } else {
                "Сохранение ZIP…"
            }
        ExportStage.FINALIZING ->
            "Завершение…"
        null ->
            "Подготовка…"
    }

@Composable
private fun ImportProgressCard(
    progress: ExportProgress,
    direct: Boolean
) {
    val stage = progress.stage
    val title = when (stage) {
        ExportStage.PREPARING ->
            "Подготавливаю главы"
        ExportStage.VERIFYING ->
            "Проверяю пакет"
        ExportStage.WRITING ->
            if (direct) {
                "Записываю в RanobeLib"
            } else {
                "Сохраняю ZIP"
            }
        ExportStage.FINALIZING ->
            "Завершаю импорт"
    }

    val description = when (stage) {
        ExportStage.PREPARING ->
            "Собираю главы, иллюстрации и метаданные."
        ExportStage.VERIFYING ->
            "Проверяю структуру и файлы перед записью."
        ExportStage.WRITING ->
            "Не закрывайте ReaderLB до завершения записи."
        ExportStage.FINALIZING ->
            "Фиксирую результат и очищаю временные файлы."
    }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment =
                Alignment.CenterVertically
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(28.dp),
                strokeWidth = 2.dp,
                color = Blue
            )
            Column(
                modifier =
                    Modifier.padding(start = 12.dp)
            ) {
                Text(
                    title,
                    color = Ink,
                    fontWeight = FontWeight.Bold
                )
                if (
                    progress.hasCount &&
                    stage == ExportStage.PREPARING
                ) {
                    Text(
                        progress.completed.toString() +
                            " / " +
                            progress.total.toString(),
                        color = Blue,
                        fontSize = 12.sp,
                        fontWeight =
                            FontWeight.SemiBold,
                        modifier =
                            Modifier.padding(top = 3.dp)
                    )
                }
                Text(
                    description,
                    color = Muted,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    modifier =
                        Modifier.padding(top = 3.dp)
                )
            }
        }
    }
}

private fun chapterCountText(
    count: Int
): String {
    val mod100 = count % 100
    val mod10 = count % 10
    val word = when {
        mod100 in 11..14 -> "глав"
        mod10 == 1 -> "главу"
        mod10 in 2..4 -> "главы"
        else -> "глав"
    }

    return "$count $word"
}

@Composable
private fun ImportHeader(
    enabled: Boolean,
    onBack: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
    ) {
        IconButton(
            onClick = onBack,
            enabled = enabled,
            modifier = Modifier.align(
                Alignment.CenterStart
            )
        ) {
            Icon(
                Icons.Default.ArrowBack,
                contentDescription = "Назад",
                tint = Ink
            )
        }

        Text(
            "Импорт файла",
            modifier = Modifier.align(
                Alignment.Center
            ),
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = Ink,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun FileDropCard(
    fileName: String,
    analyzing: Boolean,
    enabled: Boolean,
    onCancelAnalysis: () -> Unit,
    onPick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(18.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Line)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 22.dp, vertical = 30.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (analyzing) {
                CircularProgressIndicator(
                    color = Blue,
                    modifier = Modifier.size(48.dp)
                )
            } else {
                Icon(
                    Icons.Default.Add,
                    null,
                    tint = Muted,
                    modifier = Modifier.size(58.dp)
                )
            }
            Spacer(Modifier.height(14.dp))
            Text(
                if (fileName.isBlank()) {
                    "Выберите EPUB или TXT"
                } else {
                    fileName
                },
                fontWeight = FontWeight.Bold,
                color = Ink,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(10.dp))
            if (analyzing) {
                Text(
                    "Отменить анализ",
                    color = Blue,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .padding(vertical = 12.dp)
                        .clickable(
                            onClick =
                                onCancelAnalysis
                        )
                )
            } else {
                Box(
                    modifier = Modifier
                        .clip(
                            RoundedCornerShape(12.dp)
                        )
                        .background(
                            if (enabled) {
                                Blue
                            } else {
                                Color(0xFF56616E)
                            }
                        )
                        .clickable(
                            enabled = enabled,
                            onClick = onPick
                        )
                        .padding(
                            horizontal = 42.dp,
                            vertical = 12.dp
                        )
                ) {
                    Text(
                        "Выбрать файл",
                        color = Color.White,
                        fontWeight =
                            FontWeight.Bold
                    )
                }
            }
            Text(
                "Поддерживаются: EPUB, TXT · ZIP с EPUB-структурой",
                color = Muted,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 12.dp)
            )
        }
    }
}

@Composable
private fun ParsedPreview(book: ParsedBook) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Line)
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val bitmap = remember(book.coverBytes) {
                    book.coverBytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
                }
                if (bitmap != null) {
                    androidx.compose.foundation.Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription =
                            "Обложка " + book.title,
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(10.dp))
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Brush.linearGradient(listOf(Navy2, Blue))),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.List, null, tint = Color.White)
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(book.title, fontWeight = FontWeight.Bold, color = Ink, maxLines = 2)
                    if (book.author.isNotBlank()) {
                        Text(book.author, color = Muted, fontSize = 12.sp, maxLines = 1)
                    }
                    val first = book.chapters
                        .minWithOrNull(
                            Comparator { left, right ->
                                compareChapterNumbers(
                                    left.number,
                                    right.number
                                )
                            }
                        )
                        ?.number
                        ?: "0"
                    val last = book.chapters
                        .maxWithOrNull(
                            Comparator { left, right ->
                                compareChapterNumbers(
                                    left.number,
                                    right.number
                                )
                            }
                        )
                        ?.number
                        ?: "0"
                    val range = if (first == last) "глава $first" else "главы $first–$last"
                    Text(
                        "${book.chapters.size} найдено · $range",
                        color = Success,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 5.dp)
                    )

                    val illustrationCount =
                        book.chapters.sumOf { chapter ->
                            chapter.blocks.count {
                                it is ReaderBlock.Image
                            }
                        }

                    if (illustrationCount > 0) {
                        Text(
                            "Иллюстраций к переносу: $illustrationCount",
                            color = Blue,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(top = 3.dp)
                        )
                    }
                }
            }

            if (book.issues.isNotEmpty()) {
                Divider(modifier = Modifier.padding(vertical = 12.dp))
                book.issues.take(4).forEach { issue ->
                    val warning = issue.severity == com.readerlb.app.importer.ImportIssueSeverity.WARNING
                    Text(
                        text = (if (warning) "⚠ " else "ℹ ") + issue.message,
                        color = if (warning) Color(0xFFE1B95B) else Muted,
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        modifier = Modifier.padding(vertical = 3.dp)
                    )
                }
                if (book.issues.size > 4) {
                    Text(
                        "Ещё ${book.issues.size - 4} предупреждений",
                        color = Muted,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(top = 3.dp)
                    )
                }
            }
        }
    }
}

private enum class LibrarySortMode {
    RECENT,
    TITLE,
    CHAPTERS
}

@Composable
private fun LibraryScreen(
    modifier: Modifier,
    items: List<LocalLibraryItem>,
    connected: Boolean,
    loading: Boolean,
    scanned: Int,
    scanTotal: Int,
    status: String?,
    skippedTitles: Int,
    error: String?,
    onRefresh: () -> Unit,
    onPickFolder: () -> Unit,
    onAdd: () -> Unit
) {
    var query by rememberSaveable {
        mutableStateOf("")
    }
    var sortIndex by rememberSaveable {
        mutableIntStateOf(0)
    }
    var selectedSlug by rememberSaveable {
        mutableStateOf<String?>(null)
    }

    val selectedItem = items.firstOrNull {
        it.slugUrl == selectedSlug
    }

    if (selectedItem != null) {
        LibraryTitleDetail(
            modifier = modifier,
            item = selectedItem,
            onBack = {
                selectedSlug = null
            }
        )
        return
    }

    val sortMode =
        LibrarySortMode.entries[
            sortIndex.coerceIn(
                0,
                LibrarySortMode.entries.lastIndex
            )
        ]

    val visibleItems = remember(
        items,
        query,
        sortMode
    ) {
        val filtered = items.filter { item ->
            query.isBlank() ||
                item.title.contains(
                    query.trim(),
                    ignoreCase = true
                ) ||
                item.slugUrl.contains(
                    query.trim(),
                    ignoreCase = true
                )
        }

        when (sortMode) {
            LibrarySortMode.RECENT ->
                filtered.sortedWith(
                    compareByDescending<
                        LocalLibraryItem
                    > {
                        it.writeTime
                    }.thenBy {
                        it.title.lowercase()
                    }
                )

            LibrarySortMode.TITLE ->
                filtered.sortedBy {
                    it.title.lowercase()
                }

            LibrarySortMode.CHAPTERS ->
                filtered.sortedWith(
                    compareByDescending<
                        LocalLibraryItem
                    > {
                        it.chapterCount
                    }.thenBy {
                        it.title.lowercase()
                    }
                )
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement =
            Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment =
                    Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "Библиотека",
                        fontSize = 25.sp,
                        fontWeight = FontWeight.Bold,
                        color = Ink
                    )
                    Text(
                        if (connected) {
                            "Реальные локальные тайтлы RanobeLib."
                        } else {
                            "Подключите папку book, чтобы видеть " +
                                "то, что реально скачано в RanobeLib."
                        },
                        color = Muted,
                        modifier =
                            Modifier.padding(top = 4.dp)
                    )
                }

                if (connected) {
                    Text(
                        if (loading) {
                            "Обновление…"
                        } else {
                            "Обновить"
                        },
                        color = Blue,
                        fontWeight =
                            FontWeight.SemiBold,
                        modifier = Modifier.clickable(
                            enabled = !loading,
                            onClick = onRefresh
                        )
                    )
                }
            }
        }

        if (!connected) {
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    shape = RoundedCornerShape(16.dp),
                    border =
                        androidx.compose.foundation.BorderStroke(
                            1.dp,
                            Line
                        )
                ) {
                    Column(
                        Modifier.padding(18.dp),
                        verticalArrangement =
                            Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            "Нужен доступ к RanobeLib",
                            color = Ink,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "ReaderLB прочитает только info.json, " +
                                "chapters.json и обложки. Главы не " +
                                "изменяются при просмотре библиотеки.",
                            color = Muted,
                            fontSize = 13.sp,
                            lineHeight = 18.sp
                        )
                        OutlineAction(
                            "Выбрать папку book",
                            onPickFolder
                        )
                    }
                }
            }
        } else {
            if (loading && items.isEmpty()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            verticalAlignment =
                                Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                modifier =
                                    Modifier.size(26.dp),
                                strokeWidth = 2.dp,
                                color = Blue
                            )
                            Text(
                                if (scanTotal > 0) {
                                    "Сканирую: " +
                                        scanned +
                                        " из " +
                                        scanTotal
                                } else if (
                                    scanned > 0
                                ) {
                                    "Сканирую: " +
                                        scanned
                                } else {
                                    "Сканирую локальные тайтлы…"
                                },
                                color = Muted,
                                modifier =
                                    Modifier.padding(
                                        start = 12.dp
                                    )
                            )
                        }
                    }
                }
            }

            if (
                loading &&
                items.isNotEmpty()
            ) {
                item {
                    Text(
                        if (scanTotal > 0) {
                            "Обновление библиотеки: " +
                                scanned +
                                " / " +
                                scanTotal
                        } else if (
                            scanned > 0
                        ) {
                            "Обновление библиотеки: " +
                                scanned
                        } else {
                            "Обновление библиотеки…"
                        },
                        color = Muted,
                        fontSize = 12.sp
                    )
                }
            } else if (
                status != null
            ) {
                item {
                    Text(
                        status,
                        color = Success,
                        fontSize = 12.sp,
                        fontWeight =
                            FontWeight.SemiBold
                    )
                }
            }

            if (items.isNotEmpty()) {
                item {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier =
                            Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = {
                            Text("Поиск по библиотеке")
                        },
                        placeholder = {
                            Text(
                                "Название или slug"
                            )
                        },
                        shape =
                            RoundedCornerShape(12.dp)
                    )
                }

                item {
                    Row(
                        modifier =
                            Modifier.fillMaxWidth(),
                        horizontalArrangement =
                            Arrangement.spacedBy(8.dp)
                    ) {
                        LibrarySortPill(
                            modifier =
                                Modifier.weight(1f),
                            text = "Недавние",
                            selected =
                                sortMode ==
                                    LibrarySortMode.RECENT,
                            onClick = {
                                sortIndex =
                                    LibrarySortMode.RECENT
                                        .ordinal
                            }
                        )
                        LibrarySortPill(
                            modifier =
                                Modifier.weight(1f),
                            text = "А–Я",
                            selected =
                                sortMode ==
                                    LibrarySortMode.TITLE,
                            onClick = {
                                sortIndex =
                                    LibrarySortMode.TITLE
                                        .ordinal
                            }
                        )
                        LibrarySortPill(
                            modifier =
                                Modifier.weight(1f),
                            text = "По главам",
                            selected =
                                sortMode ==
                                    LibrarySortMode.CHAPTERS,
                            onClick = {
                                sortIndex =
                                    LibrarySortMode.CHAPTERS
                                        .ordinal
                            }
                        )
                    }
                }

                item {
                    Text(
                        if (query.isBlank()) {
                            "Тайтлов: " + items.size
                        } else {
                            "Найдено: " +
                                visibleItems.size +
                                " из " +
                                items.size
                        },
                        color = Muted,
                        fontSize = 12.sp
                    )
                }
            }

            error?.let { message ->
                item {
                    StatusCard(
                        "Не удалось обновить библиотеку: " +
                            message,
                        false
                    )
                }
                item {
                    OutlineAction(
                        "Повторить сканирование",
                        onClick = onRefresh
                    )
                }
            }

            if (skippedTitles > 0) {
                item {
                    Text(
                        "Не удалось прочитать папок: " +
                            skippedTitles +
                            ". Повреждённые или чужие папки " +
                            "пропущены.",
                        color = Color(0xFFE1B95B),
                        fontSize = 12.sp
                    )
                }
            }

            when {
                !loading &&
                    items.isEmpty() &&
                    error == null -> {
                    item {
                        EmptyLibraryCard(onAdd)
                    }
                }

                items.isNotEmpty() &&
                    visibleItems.isEmpty() -> {
                    item {
                        Card(
                            colors =
                                CardDefaults.cardColors(
                                    containerColor =
                                        MaterialTheme.colorScheme.surface
                                ),
                            shape =
                                RoundedCornerShape(
                                    16.dp
                                )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                horizontalAlignment =
                                    Alignment.CenterHorizontally
                            ) {
                                Text(
                                    "Ничего не найдено",
                                    color = Ink,
                                    fontWeight =
                                        FontWeight.Bold
                                )
                                Text(
                                    "Попробуйте другое название.",
                                    color = Muted,
                                    fontSize = 13.sp,
                                    modifier =
                                        Modifier.padding(
                                            top = 5.dp,
                                            bottom = 10.dp
                                        )
                                )
                                Text(
                                    "Сбросить поиск",
                                    color = Blue,
                                    fontWeight =
                                        FontWeight.SemiBold,
                                    modifier =
                                        Modifier.clickable {
                                            query = ""
                                        }
                                )
                            }
                        }
                    }
                }

                else -> {
                    items(
                        visibleItems,
                        key = { it.slugUrl }
                    ) { item ->
                        LocalLibraryCard(
                            item = item,
                            onClick = {
                                selectedSlug =
                                    item.slugUrl
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LibrarySortPill(
    modifier: Modifier,
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(
                RoundedCornerShape(99.dp)
            )
            .background(
                if (selected) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surface
                }
            )
            .border(
                width = 1.dp,
                color =
                    if (selected) {
                        Blue
                    } else {
                        Line
                    },
                shape =
                    RoundedCornerShape(99.dp)
            )
            .clickable(onClick = onClick)
            .padding(
                horizontal = 8.dp,
                vertical = 9.dp
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            color = if (selected) {
                Blue
            } else {
                Muted
            },
            fontSize = 12.sp,
            fontWeight =
                if (selected) {
                    FontWeight.SemiBold
                } else {
                    FontWeight.Normal
                },
            maxLines = 1
        )
    }
}

@Composable
private fun SettingsScreen(
    modifier: Modifier,
    folderUri: Uri?,
    updateInfo: UpdateInfo?,
    autoUpdateChecks: Boolean,
    updateBusy: Boolean,
    updateMessage: String?,
    onAutoUpdateChecksChanged: (Boolean) -> Unit,
    onCheckUpdates: () -> Unit,
    onInstallUpdate: () -> Unit,
    onRepeatHints: () -> Unit,
    onPickFolder: () -> Unit,
    onForgetFolder: () -> Unit
) {
    val uriHandler =
        androidx.compose.ui.platform.LocalUriHandler.current
    val context =
        androidx.compose.ui.platform.LocalContext.current
    var diagnosticsCopied by rememberSaveable {
        mutableStateOf(false)
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text("Настройки", fontSize = 25.sp, fontWeight = FontWeight.Bold, color = Ink)
        }
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Line)
            ) {
                Column(Modifier.padding(18.dp)) {
                    Text(
                        "Связь с RanobeLib",
                        fontWeight = FontWeight.Bold,
                        color = Ink
                    )
                    Text(
                        if (folderUri == null) {
                            "Не подключено. Без доступа ReaderLB " +
                                "сохраняет готовый ZIP в Downloads."
                        } else {
                            "Подключено. ReaderLB может читать локальную " +
                                "библиотеку и добавлять главы напрямую."
                        },
                        color = Muted,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        modifier =
                            Modifier.padding(
                                top = 6.dp,
                                bottom = 14.dp
                            )
                    )
                    OutlineAction(
                        if (folderUri == null) {
                            "Подключить RanobeLib"
                        } else {
                            "Изменить доступ"
                        },
                        onPickFolder
                    )
                    if (folderUri != null) {
                        Text(
                            "Отключить RanobeLib",
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier
                                .padding(top = 14.dp)
                                .clickable(
                                    onClick =
                                        onForgetFolder
                                )
                        )
                    }
                }
            }
        }
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                shape = RoundedCornerShape(16.dp),
                border =
                    androidx.compose.foundation.BorderStroke(
                        1.dp,
                        Line
                    )
            ) {
                Column(
                    Modifier.padding(18.dp),
                    verticalArrangement =
                        Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        "ReaderLB ${BuildConfig.VERSION_NAME}",
                        fontWeight = FontWeight.Bold,
                        color = Ink
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment =
                            Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "Автопроверка",
                                color = Ink,
                                fontWeight =
                                    FontWeight.SemiBold
                            )
                            Text(
                                "Проверять новые стабильные версии " +
                                    "не чаще одного раза в сутки. " +
                                    "Установка запускается только после " +
                                    "нажатия «Обновить».",
                                color = Muted,
                                fontSize = 12.sp,
                                lineHeight = 17.sp
                            )
                        }
                        Switch(
                            checked = autoUpdateChecks,
                            onCheckedChange =
                                onAutoUpdateChecksChanged
                        )
                    }
                    if (updateInfo != null) {
                        Text(
                            "Доступна версия " +
                                updateInfo.versionName,
                            color = Blue,
                            fontWeight = FontWeight.SemiBold
                        )
                        OutlineAction(
                            if (updateBusy) {
                                "Загрузка…"
                            } else {
                                "Обновить"
                            },
                            onInstallUpdate
                        )
                    } else {
                        OutlineAction(
                            if (updateBusy) {
                                "Проверка…"
                            } else {
                                "Проверить обновления"
                            },
                            onCheckUpdates
                        )
                    }
                    updateMessage?.let {
                        Text(
                            it,
                            color = Muted,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                shape = RoundedCornerShape(16.dp),
                border =
                    androidx.compose.foundation.BorderStroke(
                        1.dp,
                        Line
                    )
            ) {
                Column(
                    Modifier.padding(18.dp),
                    verticalArrangement =
                        Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "Помощь",
                        fontWeight = FontWeight.Bold,
                        color = Ink
                    )
                    Text(
                        "Контекстные подсказки объясняют импорт " +
                            "прямо на нужном экране.",
                        color = Muted,
                        fontSize = 13.sp,
                        lineHeight = 19.sp
                    )
                    OutlineAction(
                        "Повторить подсказки",
                        onRepeatHints
                    )
                    OutlineAction(
                        "Скопировать диагностику",
                        onClick = {
                            val clipboard =
                                context.getSystemService(
                                    android.content.Context
                                        .CLIPBOARD_SERVICE
                                ) as android.content
                                    .ClipboardManager
                            clipboard.setPrimaryClip(
                                android.content.ClipData
                                    .newPlainText(
                                        "ReaderLB diagnostics",
                                        buildReaderLbDiagnostics(
                                            folderConnected =
                                                folderUri != null,
                                            autoUpdateChecks =
                                                autoUpdateChecks
                                        )
                                    )
                            )
                            diagnosticsCopied = true
                        }
                    )
                    if (diagnosticsCopied) {
                        Text(
                            "Скопировано. Отчёт не содержит " +
                                "названий книг, путей к EPUB или " +
                                "содержимого библиотеки.",
                            color = Success,
                            fontSize = 12.sp,
                            lineHeight = 17.sp
                        )
                    }
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                shape = RoundedCornerShape(16.dp),
                border =
                    androidx.compose.foundation.BorderStroke(
                        1.dp,
                        Line
                    )
            ) {
                Column(
                    Modifier.padding(18.dp),
                    verticalArrangement =
                        Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "О приложении",
                        fontWeight = FontWeight.Bold,
                        color = Ink
                    )
                    Text(
                        "Разработчик: dollar",
                        color = Ink,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "Дом Некроманта · Telegram",
                        color = Blue,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clickable {
                            uriHandler.openUri(
                                "https://t.me/domnekromanta"
                            )
                        }
                    )
                    Text(
                        "GitHub · ReaderLB",
                        color = Blue,
                        modifier = Modifier.clickable {
                            uriHandler.openUri(
                                "https://github.com/" +
                                    "aleksandreev2/readerlb"
                            )
                        }
                    )
                }
            }
        }
    }
}

private fun buildReaderLbDiagnostics(
    folderConnected: Boolean,
    autoUpdateChecks: Boolean
): String =
    buildString {
        appendLine("ReaderLB diagnostics")
        append("Version: ")
        append(BuildConfig.VERSION_NAME)
        append(" (")
        append(BuildConfig.VERSION_CODE)
        appendLine(")")
        append("Android: ")
        append(android.os.Build.VERSION.RELEASE)
        append(" / API ")
        appendLine(
            android.os.Build.VERSION.SDK_INT
                .toString()
        )
        append("Device: ")
        append(
            android.os.Build.MANUFACTURER
                .ifBlank { "unknown" }
        )
        append(' ')
        appendLine(
            android.os.Build.MODEL
                .ifBlank { "unknown" }
        )
        append("RanobeLib access: ")
        appendLine(
            if (folderConnected) {
                "connected"
            } else {
                "not connected"
            }
        )
        append("Automatic update checks: ")
        appendLine(
            if (autoUpdateChecks) {
                "enabled"
            } else {
                "disabled"
            }
        )
    }

@Composable
private fun LibraryTitleDetail(
    modifier: Modifier,
    item: LocalLibraryItem,
    onBack: () -> Unit
) {
    val cover by rememberLibraryCover(
        item.coverUri
    )

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement =
            Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment =
                    Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.Default.ArrowBack,
                        contentDescription = "Назад",
                        tint = Ink
                    )
                }
                Text(
                    "О тайтле",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = Ink,
                    modifier =
                        Modifier.padding(start = 4.dp)
                )
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                shape = RoundedCornerShape(18.dp),
                border =
                    androidx.compose.foundation.BorderStroke(
                        1.dp,
                        Line
                    )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment =
                        Alignment.Top
                ) {
                    if (cover != null) {
                        Image(
                            bitmap =
                                requireNotNull(cover),
                            contentDescription =
                                "Обложка " + item.title,
                            contentScale =
                                ContentScale.Crop,
                            modifier = Modifier
                                .width(92.dp)
                                .height(132.dp)
                                .clip(
                                    RoundedCornerShape(
                                        12.dp
                                    )
                                )
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .width(92.dp)
                                .height(132.dp)
                                .clip(
                                    RoundedCornerShape(
                                        12.dp
                                    )
                                )
                                .background(
                                    Brush.linearGradient(
                                        listOf(
                                            Navy2,
                                            Blue
                                        )
                                    )
                                ),
                            contentAlignment =
                                Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.List,
                                null,
                                tint = Color.White,
                                modifier =
                                    Modifier.size(
                                        32.dp
                                    )
                            )
                        }
                    }

                    Column(
                        modifier =
                            Modifier.padding(
                                start = 14.dp
                            )
                    ) {
                        Text(
                            item.title,
                            color = Ink,
                            fontSize = 19.sp,
                            lineHeight = 24.sp,
                            fontWeight =
                                FontWeight.Bold
                        )

                        if (
                            item.createdByReaderLB
                        ) {
                            Box(
                                modifier = Modifier
                                    .padding(top = 8.dp)
                                    .clip(
                                        RoundedCornerShape(
                                            99.dp
                                        )
                                    )
                                    .background(
                                        Color(
                                            0xFFEAF5FF
                                        )
                                    )
                                    .padding(
                                        horizontal = 9.dp,
                                        vertical = 4.dp
                                    )
                            ) {
                                Text(
                                    "Импортировано ReaderLB",
                                    color = Blue,
                                    fontSize = 11.sp,
                                    fontWeight =
                                        FontWeight.SemiBold
                                )
                            }
                        }

                        Text(
                            chapterCountText(
                                item.chapterCount
                            ),
                            color = Success,
                            fontWeight =
                                FontWeight.SemiBold,
                            fontSize = 13.sp,
                            modifier =
                                Modifier.padding(
                                    top = 10.dp
                                )
                        )
                        Text(
                            localChapterRangeText(
                                item
                            ),
                            color = Muted,
                            fontSize = 13.sp,
                            modifier =
                                Modifier.padding(
                                    top = 4.dp
                                )
                        )
                    }
                }
            }
        }

        item {
            LibraryDetailRow(
                label = "Локально обновлено",
                value = formatLocalLibraryTime(
                    item.writeTime
                )
            )
        }

        item {
            LibraryDetailRow(
                label = "Источник",
                value =
                    if (item.createdByReaderLB) {
                        "ReaderLB"
                    } else {
                        "Локальная библиотека RanobeLib"
                    }
            )
        }

        item {
            LibraryDetailRow(
                label = "Локальный идентификатор",
                value = item.slugUrl
            )
        }

        item {
            Text(
                "ReaderLB показывает метаданные локальной копии. " +
                    "Этот экран ничего не изменяет и не удаляет.",
                color = Muted,
                fontSize = 12.sp,
                lineHeight = 17.sp
            )
        }
    }
}

@Composable
private fun LibraryDetailRow(
    label: String,
    value: String
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(14.dp),
        border =
            androidx.compose.foundation.BorderStroke(
                1.dp,
                Line
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(15.dp)
        ) {
            Text(
                label,
                color = Muted,
                fontSize = 11.sp
            )
            Text(
                value,
                color = Ink,
                fontSize = 14.sp,
                lineHeight = 19.sp,
                fontWeight =
                    FontWeight.SemiBold,
                modifier =
                    Modifier.padding(top = 4.dp)
            )
        }
    }
}

private fun localChapterRangeText(
    item: LocalLibraryItem
): String =
    if (
        item.firstChapter ==
        item.lastChapter
    ) {
        "Глава " + item.firstChapter
    } else {
        "Главы " +
            item.firstChapter +
            "–" +
            item.lastChapter
    }

private fun formatLocalLibraryTime(
    millis: Long
): String {
    if (millis <= 0L) {
        return "Неизвестно"
    }

    return runCatching {
        java.text.DateFormat
            .getDateTimeInstance(
                java.text.DateFormat.MEDIUM,
                java.text.DateFormat.SHORT
            )
            .format(
                java.util.Date(millis)
            )
    }.getOrDefault("Неизвестно")
}

@Composable
private fun LocalLibraryCard(
    item: LocalLibraryItem,
    onClick: (() -> Unit)? = null
) {
    val cover by rememberLibraryCover(item.coverUri)
    val cardModifier =
        if (onClick == null) {
            Modifier.fillMaxWidth()
        } else {
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
        }

    Card(
        modifier = cardModifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(15.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            Line
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment =
                Alignment.CenterVertically
        ) {
            if (cover != null) {
                Image(
                    bitmap = requireNotNull(cover),
                    contentDescription =
                        "Обложка " + item.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(62.dp)
                        .clip(
                            RoundedCornerShape(10.dp)
                        )
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(62.dp)
                        .clip(
                            RoundedCornerShape(10.dp)
                        )
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    Color(0xFF183753),
                                    Color(0xFF1996E6)
                                )
                            )
                        ),
                    contentAlignment =
                        Alignment.Center
                ) {
                    Icon(
                        Icons.Default.List,
                        null,
                        tint = Color.White
                    )
                }
            }

            Spacer(Modifier.width(12.dp))

            Column(Modifier.weight(1f)) {
                Text(
                    item.title,
                    fontWeight = FontWeight.Bold,
                    color = Ink,
                    maxLines = 2
                )
                Text(
                    if (
                        item.firstChapter ==
                        item.lastChapter
                    ) {
                        "Глава ${item.firstChapter}"
                    } else {
                        "Главы ${item.firstChapter}–" +
                            item.lastChapter
                    },
                    color = Muted,
                    fontSize = 13.sp,
                    modifier =
                        Modifier.padding(top = 3.dp)
                )
                Row(
                    modifier =
                        Modifier.padding(top = 5.dp),
                    verticalAlignment =
                        Alignment.CenterVertically,
                    horizontalArrangement =
                        Arrangement.spacedBy(7.dp)
                ) {
                    Text(
                        chapterCountText(
                            item.chapterCount
                        ),
                        color = Success,
                        fontSize = 11.sp,
                        fontWeight =
                            FontWeight.SemiBold
                    )

                    if (item.createdByReaderLB) {
                        Box(
                            modifier = Modifier
                                .clip(
                                    RoundedCornerShape(
                                        99.dp
                                    )
                                )
                                .background(
                                    MaterialTheme.colorScheme.primaryContainer
                                )
                                .padding(
                                    horizontal = 7.dp,
                                    vertical = 2.dp
                                )
                        ) {
                            Text(
                                "ReaderLB",
                                color = Blue,
                                fontSize = 10.sp,
                                fontWeight =
                                    FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun rememberLibraryCover(
    uri: Uri?
): androidx.compose.runtime.State<
    androidx.compose.ui.graphics.ImageBitmap?
> {
    val context =
        androidx.compose.ui.platform.LocalContext.current
    val state = remember(uri) {
        mutableStateOf<
            androidx.compose.ui.graphics.ImageBitmap?
        >(null)
    }

    LaunchedEffect(uri) {
        state.value = if (uri == null) {
            null
        } else {
            withContext(Dispatchers.IO) {
                decodeLibraryCover(
                    context = context,
                    uri = uri
                )?.asImageBitmap()
            }
        }
    }

    return state
}

private fun decodeLibraryCover(
    context: android.content.Context,
    uri: Uri
): android.graphics.Bitmap? =
    runCatching {
        val bounds = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }

        context.contentResolver
            .openInputStream(uri)
            ?.use {
                BitmapFactory.decodeStream(
                    it,
                    null,
                    bounds
                )
            }

        var sample = 1
        while (
            bounds.outWidth / sample > 256 ||
            bounds.outHeight / sample > 384
        ) {
            sample *= 2
        }

        val options =
            BitmapFactory.Options().apply {
                inSampleSize = sample
            }

        context.contentResolver
            .openInputStream(uri)
            ?.use {
                BitmapFactory.decodeStream(
                    it,
                    null,
                    options
                )
            }
    }.getOrNull()

@Composable
private fun HistoryCard(item: ImportHistoryItem) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(15.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Line)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(62.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xFF183753), Color(0xFF1996E6))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.List, null, tint = Color.White)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    item.title,
                    fontWeight = FontWeight.Bold,
                    color = Ink,
                    maxLines = 2
                )
                Text(
                    if (item.firstChapter == item.lastChapter) "Глава ${item.firstChapter}" else "Главы ${item.firstChapter}–${item.lastChapter}",
                    color = Muted,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 3.dp)
                )
                Box(
                    modifier = Modifier
                        .padding(top = 7.dp)
                        .clip(RoundedCornerShape(99.dp))
                        .background(
                            if (item.installedDirectly) MaterialTheme.colorScheme.tertiaryContainer
                            else MaterialTheme.colorScheme.primaryContainer
                        )
                        .padding(horizontal = 9.dp, vertical = 4.dp)
                ) {
                    Text(
                        when {
                            item.updatedExisting &&
                                item.addedChapterCount > 0 -> {
                                "✓ Обновлено +${item.addedChapterCount}"
                            }
                            item.updatedExisting -> {
                                "✓ Уже актуально"
                            }
                            item.installedDirectly -> {
                                "✓ В RanobeLib"
                            }
                            else -> {
                                "ZIP подготовлен"
                            }
                        },
                        color = if (item.installedDirectly) {
                            MaterialTheme.colorScheme.onTertiaryContainer
                        } else {
                            Blue
                        },
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun StatCard(
    modifier: Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    value: String,
    label: String
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(Modifier.padding(13.dp)) {
            Icon(icon, null, tint = Blue, modifier = Modifier.size(23.dp))
            Text(
                value,
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
                color = Ink,
                modifier = Modifier.padding(top = 8.dp)
            )
            Text(label, color = Muted, fontSize = 11.sp)
        }
    }
}

@Composable
private fun EmptyLibraryCard(onAdd: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Default.List,
                null,
                tint = Muted,
                modifier = Modifier.size(52.dp)
            )
            Text(
                "Пока пусто",
                fontWeight = FontWeight.Bold,
                color = Ink,
                modifier = Modifier.padding(top = 10.dp)
            )
            Text(
                "Добавьте EPUB — ReaderLB подготовит его для локальной библиотеки.",
                textAlign = TextAlign.Center,
                color = Muted,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 6.dp, bottom = 12.dp)
            )
            Text(
                "Добавить новеллу",
                color = Blue,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable(onClick = onAdd)
            )
        }
    }
}

@Composable
private fun CoachHintCard(
    title: String,
    text: String,
    actionText: String? = null,
    onAction: (() -> Unit)? = null
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        shape = RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            Line
        )
    ) {
        Column(
            Modifier.padding(15.dp),
            verticalArrangement =
                Arrangement.spacedBy(6.dp)
        ) {
            Text(
                title,
                fontWeight = FontWeight.Bold,
                color = Ink
            )
            Text(
                text,
                color = Muted,
                fontSize = 12.sp,
                lineHeight = 17.sp
            )
            if (
                actionText != null &&
                onAction != null
            ) {
                Text(
                    actionText,
                    color = Blue,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .clickable(onClick = onAction)
                )
            }
        }
    }
}

@Composable
private fun UpdateAvailableCard(
    info: UpdateInfo,
    busy: Boolean,
    onUpdate: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement =
                Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "Доступна ReaderLB ${info.versionName}",
                fontWeight = FontWeight.Bold,
                color = Ink
            )
            Text(
                "ReaderLB сам скачает APK и передаст обновление Android. " +
                    "На некоторых версиях системы понадобится подтверждение.",
                color = Muted,
                fontSize = 12.sp,
                lineHeight = 17.sp
            )
            OutlineAction(
                if (busy) "Загрузка…" else "Обновить",
                onUpdate
            )
        }
    }
}

@Composable
private fun StatusCard(message: String, success: Boolean) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (success) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.errorContainer
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Text(
            message,
            color = if (success) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.padding(14.dp),
            fontSize = 13.sp
        )
    }
}

@Composable
private fun OutlineAction(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Blue, RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = Blue, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun GradientButton(
    text: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val colors = if (enabled) listOf(Blue, Color(0xFF09A8F2)) else listOf(
        Color(0xFF3C4652),
        Color(0xFF46515E)
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp)
            .clip(RoundedCornerShape(15.dp))
            .background(Brush.horizontalGradient(colors))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 17.sp
        )
    }
}

@Composable
private fun ReaderLogo(
    size: androidx.compose.ui.unit.Dp
) {
    Image(
        painter = painterResource(
            id = R.drawable.readerlb_logo
        ),
        contentDescription = "ReaderLB",
        contentScale = ContentScale.Fit,
        modifier = Modifier
            .size(size)
            .clip(
                RoundedCornerShape(
                    size * 0.2f
                )
            )
    )
}

private fun sanitizeChapterRangeInput(
    value: String
): String {
    val normalized = value
        .replace(',', '.')
        .filter { character ->
            character.isDigit() || character == '.'
        }

    val dot = normalized.indexOf('.')
    return if (dot < 0) {
        normalized
    } else {
        normalized.substring(0, dot + 1) +
            normalized
                .substring(dot + 1)
                .replace(".", "")
    }
}
