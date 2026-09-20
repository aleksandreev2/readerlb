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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.readerlb.app.importer.ImportRepository
import com.readerlb.app.importer.ParsedBook
import com.readerlb.app.importer.RanobeLibExporter
import com.readerlb.app.importer.ReaderBlock
import com.readerlb.app.importer.compareChapterNumbers
import com.readerlb.app.storage.HistoryStore
import com.readerlb.app.storage.ImportHistoryItem
import com.readerlb.app.storage.Preferences
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ReaderLBTheme {
                ReaderLBRoot()
            }
        }
    }
}

private enum class AppTab { HOME, IMPORT, LIBRARY, SETTINGS }

private val RANOBELIB_BOOK_INITIAL_URI: Uri = Uri.parse(
    "content://com.android.externalstorage.documents/document/" +
        "primary%3AAndroid%2Fdata%2Fru.libappc%2Ffiles%2Fbook"
)

@Composable
private fun ReaderLBRoot() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val preferences = remember { Preferences(context) }
    var onboardingDone by remember { mutableStateOf(preferences.onboardingDone) }

    if (!onboardingDone) {
        Onboarding(
            onDone = {
                preferences.onboardingDone = true
                onboardingDone = true
            }
        )
    } else {
        MainApp()
    }
}

@Composable
private fun Onboarding(onDone: () -> Unit) {
    var page by remember { mutableIntStateOf(0) }
    val pageCount = 3

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
private fun MainApp() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val preferences = remember { Preferences(context) }
    val historyStore = remember { HistoryStore(context) }
    val updateManager = remember { UpdateManager(context) }
    val scope = rememberCoroutineScope()
    var history by remember { mutableStateOf(historyStore.load()) }
    var tab by remember { mutableStateOf(AppTab.HOME) }
    var folderUri by remember { mutableStateOf(preferences.ranobeLibBookTree) }
    var latestUpdate by remember {
        mutableStateOf<UpdateInfo?>(null)
    }
    var updateBusy by remember { mutableStateOf(false) }
    var updateMessage by remember {
        mutableStateOf<String?>(null)
    }

    LaunchedEffect(Unit) {
        val now = System.currentTimeMillis()
        val due =
            now - preferences.lastUpdateCheckMillis >=
                24L * 60L * 60L * 1000L
        if (due) {
            preferences.lastUpdateCheckMillis = now
            latestUpdate = withContext(Dispatchers.IO) {
                runCatching {
                    updateManager.checkLatest()
                }.getOrNull()
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
                    "Обновление загружено. Подтвердите установку Android."
                context.startActivity(
                    updateManager.installIntent(apk)
                )
            }.onFailure {
                updateMessage =
                    "Не удалось загрузить обновление: " +
                        (it.message ?: "ошибка сети")
            }
            updateBusy = false
        }
    }

    val folderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            runCatching { context.contentResolver.takePersistableUriPermission(uri, flags) }
            preferences.ranobeLibBookTree = uri
            folderUri = uri
        }
    }

    Scaffold(
        containerColor = Canvas,
        bottomBar = {
            NavigationBar(containerColor = Color.White) {
                NavigationBarItem(
                    selected = tab == AppTab.HOME,
                    onClick = { tab = AppTab.HOME },
                    icon = { Icon(Icons.Default.Home, null) },
                    label = { Text("Главная") }
                )
                NavigationBarItem(
                    selected = tab == AppTab.IMPORT,
                    onClick = { tab = AppTab.IMPORT },
                    icon = { Icon(Icons.Default.List, null) },
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
    ) { padding ->
        when (tab) {
            AppTab.HOME -> HomeScreen(
                modifier = Modifier.padding(padding),
                history = history,
                updateInfo = latestUpdate,
                updateBusy = updateBusy,
                updateMessage = updateMessage,
                onUpdate = ::installLatestUpdate,
                onAdd = { tab = AppTab.IMPORT },
                onSettings = { tab = AppTab.SETTINGS }
            )
            AppTab.IMPORT -> ImportScreen(
                modifier = Modifier.padding(padding),
                folderUri = folderUri,
                onBack = { tab = AppTab.HOME },
                onPickFolder = {
                    folderPicker.launch(
                        RANOBELIB_BOOK_INITIAL_URI
                    )
                },
                onImported = {
                    historyStore.add(it)
                    history = historyStore.load()
                },
                onOpenLibrary = {
                    tab = AppTab.LIBRARY
                }
            )
            AppTab.LIBRARY -> LibraryScreen(
                modifier = Modifier.padding(padding),
                history = history
            )
            AppTab.SETTINGS -> SettingsScreen(
                modifier = Modifier.padding(padding),
                folderUri = folderUri,
                updateInfo = latestUpdate,
                updateBusy = updateBusy,
                updateMessage = updateMessage,
                onCheckUpdates = ::checkForUpdates,
                onInstallUpdate = ::installLatestUpdate,
                onPickFolder = {
                    folderPicker.launch(
                        RANOBELIB_BOOK_INITIAL_URI
                    )
                },
                onForgetFolder = {
                    preferences.ranobeLibBookTree = null
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
    updateInfo: UpdateInfo?,
    updateBusy: Boolean,
    updateMessage: String?,
    onUpdate: () -> Unit,
    onAdd: () -> Unit,
    onSettings: () -> Unit
) {
    val totalChapters = history.sumOf { it.chapters }

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
                    Icon(Icons.Default.Settings, null, tint = Ink)
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
                    history.size.toString(),
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
                    history.count { it.installedDirectly }.toString(),
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
                    "Импортированные тайтлы",
                    modifier = Modifier.weight(1f),
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Все",
                    color = Blue,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        if (history.isEmpty()) {
            item {
                EmptyLibraryCard(onAdd)
            }
        } else {
            items(history.take(6)) { item ->
                HistoryCard(item)
            }
        }
    }
}

@Composable
private fun ImportScreen(
    modifier: Modifier,
    folderUri: Uri?,
    onBack: () -> Unit,
    onPickFolder: () -> Unit,
    onImported: (com.readerlb.app.importer.ExportResult) -> Unit,
    onOpenLibrary: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember { ImportRepository(context) }
    val exporter = remember { RanobeLibExporter(context) }

    var parsed by remember { mutableStateOf<ParsedBook?>(null) }
    var fileName by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var firstChapter by remember { mutableStateOf("") }
    var lastChapter by remember { mutableStateOf("") }
    var direct by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var success by remember { mutableStateOf<String?>(null) }
    var warningsAcknowledged by remember { mutableStateOf(false) }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            fileName = repository.displayName(uri)
            parsed = null
            error = null
            success = null
            warningsAcknowledged = false
            busy = true
            scope.launch {
                runCatching {
                    withContext(Dispatchers.IO) { repository.parse(uri) }
                }.onSuccess { book ->
                    parsed = book
                    title = book.title
                    firstChapter = ""
                    lastChapter = ""
                }.onFailure {
                    error = it.message ?: "Не удалось разобрать файл"
                }
                busy = false
            }
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                IconButton(
                    onClick = onBack,
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

        item {
            FileDropCard(
                fileName = fileName,
                busy = busy,
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

        parsed?.let { book ->
            item {
                ParsedPreview(book)
            }
            if (book.issues.any { it.severity == com.readerlb.app.importer.ImportIssueSeverity.WARNING }) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF7E6)),
                        shape = RoundedCornerShape(14.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF2C46D))
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "Проверьте предупреждения",
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF7A4E00)
                                )
                                Text(
                                    "ReaderLB не будет молча продолжать импорт при подозрении на пропущенные или конфликтующие главы.",
                                    color = Color(0xFF8A641B),
                                    fontSize = 12.sp,
                                    lineHeight = 17.sp,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                            Switch(
                                checked = warningsAcknowledged,
                                onCheckedChange = { warningsAcknowledged = it }
                            )
                        }
                    }
                }
            }
        }

        item {
            Text("Название новеллы", fontWeight = FontWeight.SemiBold, color = Ink)
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

        item {
            Text("Диапазон глав", fontWeight = FontWeight.SemiBold, color = Ink)
            Spacer(Modifier.height(7.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = firstChapter,
                    onValueChange = { firstChapter = sanitizeChapterRangeInput(it) },
                    modifier = Modifier.weight(1f),
                    placeholder = {
                        Text(
                            parsed
                                ?.chapters
                                ?.minWithOrNull(
                                    Comparator { left, right ->
                                        compareChapterNumbers(
                                            left.number,
                                            right.number
                                        )
                                    }
                                )
                                ?.number
                                ?: "1"
                        )
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )
                Text(" — ", color = Muted, modifier = Modifier.padding(horizontal = 8.dp))
                OutlinedTextField(
                    value = lastChapter,
                    onValueChange = { lastChapter = sanitizeChapterRangeInput(it) },
                    modifier = Modifier.weight(1f),
                    placeholder = {
                        Text(
                            parsed
                                ?.chapters
                                ?.maxWithOrNull(
                                    Comparator { left, right ->
                                        compareChapterNumbers(
                                            left.number,
                                            right.number
                                        )
                                    }
                                )
                                ?.number
                                ?: "100"
                        )
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )
            }
            Spacer(Modifier.height(7.dp))
            Text(
                "Оставьте пустым, чтобы импортировать все главы",
                fontSize = 12.sp,
                color = Muted
            )
        }

        item {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = androidx.compose.foundation.BorderStroke(1.dp, Line)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Добавить в RanobeLib", fontWeight = FontWeight.Bold, color = Ink)
                        Text(
                            "Если тайтл уже существует, ReaderLB добавит только отсутствующие главы. Старые главы и их ID не перезаписываются.",
                            fontSize = 12.sp,
                            lineHeight = 17.sp,
                            color = Muted
                        )
                    }
                    Switch(checked = direct, onCheckedChange = { direct = it })
                }
            }
        }

        if (direct && folderUri == null) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFEAF5FF)),
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

        error?.let { message ->
            item { StatusCard(message, false) }
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
                        onOpenLibrary
                    )
                }
            }
        }

        item {
            GradientButton(
                text = if (busy) "Подготовка..." else "↥  Импортировать",
                enabled = !busy &&
                    parsed != null &&
                    (!direct || folderUri != null) &&
                    (parsed?.issues?.none {
                        it.severity == com.readerlb.app.importer.ImportIssueSeverity.WARNING
                    } != false || warningsAcknowledged),
                onClick = {
                    val book = parsed ?: return@GradientButton
                    busy = true
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
                                    ranobeLibBookTree = if (direct) folderUri else null
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
                                    "\n\n☠ Некромант узнал своих. " +
                                    "Добро пожаловать домой."
                            } else {
                                baseSuccess
                            }
                            onImported(result)
                        }.onFailure {
                            error = it.message ?: "Ошибка импорта"
                        }
                        busy = false
                    }
                }
            )
        }
    }
}

@Composable
private fun FileDropCard(
    fileName: String,
    busy: Boolean,
    onPick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFAFCFF)),
        shape = RoundedCornerShape(18.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFBED0E3))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 22.dp, vertical = 30.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (busy) {
                CircularProgressIndicator(color = Blue, modifier = Modifier.size(48.dp))
            } else {
                Icon(
                    Icons.Default.Add,
                    null,
                    tint = Color(0xFF7890AA),
                    modifier = Modifier.size(58.dp)
                )
            }
            Spacer(Modifier.height(14.dp))
            Text(
                if (fileName.isBlank()) "Перетащите файл сюда" else fileName,
                fontWeight = FontWeight.Bold,
                color = Ink,
                textAlign = TextAlign.Center
            )
            Text("или", color = Muted, modifier = Modifier.padding(vertical = 8.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(Blue)
                    .clickable(enabled = !busy, onClick = onPick)
                    .padding(horizontal = 42.dp, vertical = 12.dp)
            ) {
                Text("Выбрать файл", color = Color.White, fontWeight = FontWeight.Bold)
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
        colors = CardDefaults.cardColors(containerColor = Color.White),
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
                        contentDescription = null,
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
                        color = if (warning) Color(0xFF9A6700) else Muted,
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

@Composable
private fun LibraryScreen(
    modifier: Modifier,
    history: List<ImportHistoryItem>
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Библиотека", fontSize = 25.sp, fontWeight = FontWeight.Bold, color = Ink)
            Text(
                "Тайтлы, которые ReaderLB уже подготовил.",
                color = Muted,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
        if (history.isEmpty()) {
            item { EmptyLibraryCard {} }
        } else {
            items(history) { HistoryCard(it) }
        }
    }
}

@Composable
private fun SettingsScreen(
    modifier: Modifier,
    folderUri: Uri?,
    updateInfo: UpdateInfo?,
    updateBusy: Boolean,
    updateMessage: String?,
    onCheckUpdates: () -> Unit,
    onInstallUpdate: () -> Unit,
    onPickFolder: () -> Unit,
    onForgetFolder: () -> Unit
) {
    val uriHandler =
        androidx.compose.ui.platform.LocalUriHandler.current
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
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Line)
            ) {
                Column(Modifier.padding(18.dp)) {
                    Text("Папка RanobeLib", fontWeight = FontWeight.Bold, color = Ink)
                    Text(
                        folderUri?.toString() ?: "Не выбрана",
                        color = Muted,
                        fontSize = 12.sp,
                        maxLines = 3,
                        modifier = Modifier.padding(top = 6.dp, bottom = 14.dp)
                    )
                    OutlineAction(
                        if (folderUri == null) "Выбрать Android/data/.../book" else "Изменить папку",
                        onPickFolder
                    )
                    if (folderUri != null) {
                        Text(
                            "Забыть доступ",
                            color = Color(0xFFD64545),
                            modifier = Modifier
                                .padding(top = 14.dp)
                                .clickable(onClick = onForgetFolder)
                        )
                    }
                }
            }
        }
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = Color.White
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
                    Text(
                        "Обновления проверяются автоматически " +
                            "не чаще одного раза в сутки.",
                        color = Muted,
                        fontSize = 13.sp,
                        lineHeight = 19.sp
                    )
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
                    containerColor = Color.White
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
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Line)
            ) {
                Column(Modifier.padding(18.dp)) {
                    Text("Формат RanobeLib", fontWeight = FontWeight.Bold, color = Ink)
                    Text(
                        "ReaderLB создаёт info.json, chapters.json и отдельный ZIP с data.txt для каждой главы — тот же локальный формат, который уже проверен в штатной читалке RanobeLib.",
                        color = Muted,
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun HistoryCard(item: ImportHistoryItem) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
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
                            if (item.installedDirectly) Color(0xFFE4F8F0)
                            else Color(0xFFEAF3FF)
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
                            Color(0xFF168B67)
                        } else {
                            Blue
                        },
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            Icon(Icons.Default.MoreVert, null, tint = Muted)
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
        colors = CardDefaults.cardColors(containerColor = Color.White),
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
        colors = CardDefaults.cardColors(containerColor = Color.White),
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
                tint = Color(0xFF8AA2BB),
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
private fun UpdateAvailableCard(
    info: UpdateInfo,
    busy: Boolean,
    onUpdate: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFEAF5FF)
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
                "Обновление можно скачать прямо из приложения. " +
                    "Android попросит подтвердить установку.",
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
            containerColor = if (success) Color(0xFFE7F8F1) else Color(0xFFFFEEEE)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Text(
            message,
            color = if (success) Color(0xFF12785A) else Color(0xFFB13A3A),
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
        Color(0xFF9FB5C9),
        Color(0xFFB2C3D2)
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
private fun ReaderLogo(size: androidx.compose.ui.unit.Dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.2f))
            .background(Color(0xFF071827))
            .border(1.dp, Color(0xFF0BA7EE), RoundedCornerShape(size * 0.2f)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            "R",
            color = Color.White,
            fontWeight = FontWeight.ExtraBold,
            fontSize = (size.value * 0.48f).sp
        )
        Text(
            "⌜   ⌟",
            color = Cyan,
            fontWeight = FontWeight.Bold,
            fontSize = (size.value * 0.34f).sp,
            modifier = Modifier.align(Alignment.Center)
        )
    }
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
