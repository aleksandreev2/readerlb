package com.readerlb.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.readerlb.app.storage.LocalLibraryItem
import com.readerlb.app.storage.LocalMangaReader
import com.readerlb.app.storage.ShizukuAccess
import com.readerlb.app.importer.ReaderLbTransferManager
import com.readerlb.app.storage.MangaChapter
import com.readerlb.app.storage.MangaPage
import com.readerlb.app.export.ExportedLocalBookFile
import com.readerlb.app.export.ExportDestinationUnavailableException
import com.readerlb.app.export.LocalBookExportFormat
import com.readerlb.app.export.LocalExportProgress
import com.readerlb.app.export.LocalMangaExportManager
import com.readerlb.app.ui.theme.Blue
import com.readerlb.app.ui.theme.Ink
import com.readerlb.app.ui.theme.Line
import com.readerlb.app.ui.theme.Muted
import com.readerlb.app.ui.theme.Navy2
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext

@Composable
internal fun MangaTitleDetail(
    modifier: Modifier,
    item: LocalLibraryItem,
    onBack: () -> Unit,
    onDeleted: (String) -> Unit
) {
    val context = LocalContext.current
    val reader = remember(context) { LocalMangaReader(context) }
    val exporter = remember(context) { LocalMangaExportManager(context) }
    val transferManager = remember(context) { ReaderLbTransferManager(context) }
    val scope = rememberCoroutineScope()
    var chapters by remember(item.slugUrl) { mutableStateOf<List<MangaChapter>?>(null) }
    var selectedChapter by remember(item.slugUrl) { mutableStateOf<MangaChapter?>(null) }
    var pages by remember(item.slugUrl) { mutableStateOf<List<MangaPage>?>(null) }
    var pageIndex by remember(item.slugUrl) { mutableIntStateOf(0) }
    var bitmap by remember(item.slugUrl) { mutableStateOf<Bitmap?>(null) }
    var error by remember(item.slugUrl) { mutableStateOf<String?>(null) }
    var showExport by remember(item.slugUrl) { mutableStateOf(false) }
    var exportFormat by remember(item.slugUrl) { mutableStateOf(LocalBookExportFormat.EPUB) }
    var exportBusy by remember(item.slugUrl) { mutableStateOf(false) }
    var exportProgress by remember(item.slugUrl) { mutableStateOf<LocalExportProgress?>(null) }
    var exportResult by remember(item.slugUrl) { mutableStateOf<ExportedLocalBookFile?>(null) }
    var exportError by remember(item.slugUrl) { mutableStateOf<String?>(null) }
    var exportJob by remember(item.slugUrl) { mutableStateOf<Job?>(null) }
    var pendingDocumentExport by remember(item.slugUrl) {
        mutableStateOf<ExportDestinationUnavailableException?>(null)
    }
    var confirmDelete by remember(item.slugUrl) { mutableStateOf(false) }
    var deleteBusy by remember(item.slugUrl) { mutableStateOf(false) }

    LaunchedEffect(item.slugUrl) {
        runCatching { withContext(Dispatchers.IO) { reader.chapters(item) } }
            .onSuccess { chapters = it }
            .onFailure { error = it.message ?: "Не удалось прочитать главы" }
    }
    LaunchedEffect(selectedChapter) {
        val chapter = selectedChapter ?: return@LaunchedEffect
        pages = null
        bitmap = null
        pageIndex = 0
        runCatching { withContext(Dispatchers.IO) { reader.pages(item, chapter) } }
            .onSuccess { pages = it }
            .onFailure { error = it.message ?: "Не удалось прочитать ZIP главы" }
    }
    LaunchedEffect(pages, pageIndex) {
        val page = pages?.getOrNull(pageIndex) ?: return@LaunchedEffect
        bitmap = null
        runCatching { withContext(Dispatchers.IO) { decodeMangaPage(page) } }
            .onSuccess { bitmap = it }
            .onFailure { error = it.message ?: "Не удалось открыть страницу" }
    }

    fun startMangaExport(
        destinationUri: android.net.Uri? = null
    ) {
        if (exportBusy) {
            return
        }

        exportBusy = true
        exportProgress = null
        exportResult = null
        exportError = null

        exportJob = scope.launch {
            try {
                val result =
                    runInterruptible(
                        Dispatchers.IO
                    ) {
                        exporter.export(
                            item = item,
                            format = exportFormat,
                            onProgress = {
                                    progress ->
                                scope.launch {
                                    exportProgress =
                                        progress
                                }
                            },
                            destinationUri =
                                destinationUri
                        )
                    }
                exportResult = result
            } catch (
                failure:
                    ExportDestinationUnavailableException
            ) {
                if (destinationUri == null) {
                    exportError =
                        "Android не смог сохранить файл в Downloads. Выберите место сохранения вручную."
                    pendingDocumentExport =
                        failure
                } else {
                    exportError =
                        failure.message
                            ?: "Не удалось открыть выбранный файл для записи"
                }
            } catch (throwable: Throwable) {
                exportError =
                    throwable.message
                        ?: "Не удалось экспортировать мангу"
            } finally {
                exportBusy = false
                exportJob = null
            }
        }
    }

    val documentExportLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts
                .StartActivityForResult()
        ) { activityResult ->
            val pending =
                pendingDocumentExport
                    ?: return@rememberLauncherForActivityResult
            pendingDocumentExport = null

            val uri =
                activityResult
                    .data
                    ?.data

            if (
                activityResult.resultCode !=
                android.app.Activity.RESULT_OK ||
                uri == null
            ) {
                exportError =
                    "Сохранение в Downloads недоступно, а выбор другого места отменён."
            } else {
                startMangaExport(
                    destinationUri =
                        uri
                )
            }
        }

    LaunchedEffect(
        pendingDocumentExport
    ) {
        val pending =
            pendingDocumentExport
                ?: return@LaunchedEffect
        documentExportLauncher.launch(
            Intent(
                Intent.ACTION_CREATE_DOCUMENT
            )
                .addCategory(
                    Intent.CATEGORY_OPENABLE
                )
                .setType(
                    pending.mimeType
                )
                .putExtra(
                    Intent.EXTRA_TITLE,
                    pending.displayName
                )
        )
    }

    if (showExport) {
        MangaExportSheet(
            item = item,
            format = exportFormat,
            busy = exportBusy,
            progress = exportProgress,
            result = exportResult,
            error = exportError,
            onFormat = { exportFormat = it; exportError = null },
            onExport = {
                startMangaExport()
            },
            onCancel = { exportJob?.cancel() },
            onOpen = { result ->
                val intent = Intent(Intent.ACTION_VIEW)
                    .setDataAndType(result.uri, result.format.mimeType)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                runCatching { context.startActivity(intent) }
                    .onFailure { exportError = "На устройстве нет приложения для открытия этого формата" }
            },
            onShare = { result ->
                val intent = Intent(Intent.ACTION_SEND)
                    .setType(result.format.mimeType)
                    .putExtra(Intent.EXTRA_STREAM, result.uri)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                context.startActivity(Intent.createChooser(intent, "Поделиться мангой"))
            },
            onDismiss = { if (!exportBusy) showExport = false }
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { if (!deleteBusy) confirmDelete = false },
            title = { Text("Удалить мангу?") },
            text = { Text("ReaderLB удалит «${item.title}» из локальной папки MangaLib вместе со всеми главами. Это действие нельзя отменить.") },
            confirmButton = {
                TextButton(enabled = !deleteBusy, onClick = {
                    deleteBusy = true
                    error = null
                    scope.launch {
                        runCatching {
                            withContext(Dispatchers.IO) {
                                transferManager.deleteTitle(
                                    treeUri = ShizukuAccess.mangaTreeUri,
                                    folderName = item.folderName
                                )
                            }
                        }.onSuccess {
                            confirmDelete = false
                            onDeleted(localItemKey(item))
                            onBack()
                        }.onFailure { error = it.message ?: "Не удалось удалить мангу" }
                        deleteBusy = false
                    }
                }) { Text(if (deleteBusy) "Удаляю…" else "Удалить", color = Color(0xFFE4777D)) }
            },
            dismissButton = {
                TextButton(enabled = !deleteBusy, onClick = { confirmDelete = false }) { Text("Отмена") }
            }
        )
    }

    if (selectedChapter == null) {
        MangaTitleCard(
            modifier, item, chapters, error, onBack,
            onExport = { showExport = true },
            onDelete = { confirmDelete = true },
            onOpenChapter = { selectedChapter = it }
        )
        return
    }

    Column(modifier.fillMaxSize().padding(16.dp)) {
        TextButton(onClick = { selectedChapter = null }) { Text("Назад") }
        Text(item.title)
        error?.let { Text(it) }
        val currentPages = pages
        if (currentPages == null) {
            CircularProgressIndicator()
        } else {
            Text("Страница ${pageIndex + 1} / ${currentPages.size}")
            val image = bitmap
            if (image == null) CircularProgressIndicator()
            else Image(
                bitmap = image.asImageBitmap(),
                contentDescription = "Страница ${pageIndex + 1}",
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentScale = ContentScale.Fit
            )
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                TextButton(onClick = { pageIndex-- }, enabled = pageIndex > 0) { Text("Назад") }
                TextButton(onClick = { pageIndex++ }, enabled = pageIndex < currentPages.lastIndex) { Text("Далее") }
            }
        }
    }
}

@Composable
private fun MangaTitleCard(
    modifier: Modifier,
    item: LocalLibraryItem,
    chapters: List<MangaChapter>?,
    error: String?,
    onBack: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit,
    onOpenChapter: (MangaChapter) -> Unit
) {
    val cover by rememberLibraryCover(item.coverUri)
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Box(
                modifier = Modifier.fillMaxWidth().height(365.dp)
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                cover?.let {
                    Image(
                        bitmap = it,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        alpha = 0.28f,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                Box(
                    modifier = Modifier.fillMaxSize().background(
                        Brush.verticalGradient(
                            listOf(Color(0x44101216), Color(0xCC101216), Color(0xFF101216))
                        )
                    )
                )
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.align(Alignment.TopStart).padding(8.dp)
                ) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Назад", tint = Ink)
                }
                Column(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom
                ) {
                    if (cover != null) {
                        Image(
                            bitmap = requireNotNull(cover),
                            contentDescription = "Обложка ${item.title}",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.width(132.dp).height(186.dp)
                                .clip(RoundedCornerShape(10.dp))
                        )
                    } else {
                        Box(
                            modifier = Modifier.width(132.dp).height(186.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Brush.linearGradient(listOf(Navy2, Blue))),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.List,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(42.dp)
                            )
                        }
                    }
                    Text(
                        item.title,
                        color = Ink,
                        fontSize = 21.sp,
                        lineHeight = 26.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 14.dp)
                    )
                    Text(
                        "Скачано в MangaLib",
                        color = Muted,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(top = 5.dp)
                    )
                }
            }
        }

        item {
            Card(
                modifier = Modifier.padding(horizontal = 18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Line)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text("Информация", color = Ink, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    TitleInfoRow("Главы", (chapters?.size ?: item.chapterCount).toString())
                    TitleInfoRow(
                        "Диапазон",
                        if (item.firstChapter == item.lastChapter) "Глава ${item.firstChapter}"
                        else "Главы ${item.firstChapter}–${item.lastChapter}"
                    )
                    TitleInfoRow("Источник", "Скачано в MangaLib")
                    TitleInfoRow("Обновлено", formatLocalLibraryTime(item.writeTime))
                    Text("ID: ${item.slugUrl}", color = Muted, fontSize = 10.sp)
                }
            }
        }

        item {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp)) {
                OutlineAction("Экспортировать", onExport)
                TextButton(onClick = onDelete) {
                    Text("Удалить мангу", color = Color(0xFFE4777D))
                }
            }
        }

        item {
            Text(
                "Главы",
                modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = 4.dp),
                color = Ink,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        }
        if (error != null) {
            item {
                Text(error, modifier = Modifier.padding(horizontal = 18.dp), color = Color(0xFFE4777D))
            }
        } else if (chapters == null) {
            item { CircularProgressIndicator(modifier = Modifier.padding(start = 18.dp)) }
        } else {
            items(chapters, key = { it.archiveName }) { chapter ->
                Card(
                    modifier = Modifier.padding(horizontal = 18.dp)
                        .fillMaxWidth().clickable { onOpenChapter(chapter) },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(16.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Line)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Том ${chapter.volume} · Глава ${chapter.number}",
                                color = Ink,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            if (chapter.name.isNotBlank()) {
                                Text(
                                    chapter.name,
                                    color = Muted,
                                    fontSize = 12.sp,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                        }
                        Icon(Icons.Default.ArrowForward, contentDescription = null, tint = Blue)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MangaExportSheet(
    item: LocalLibraryItem,
    format: LocalBookExportFormat,
    busy: Boolean,
    progress: LocalExportProgress?,
    result: ExportedLocalBookFile?,
    error: String?,
    onFormat: (LocalBookExportFormat) -> Unit,
    onExport: () -> Unit,
    onCancel: () -> Unit,
    onOpen: (ExportedLocalBookFile) -> Unit,
    onShare: (ExportedLocalBookFile) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = { if (!busy) onDismiss() },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                if (result == null) "Экспортировать мангу" else "Манга сохранена",
                color = Ink, fontSize = 21.sp, fontWeight = FontWeight.Bold
            )
            Text(item.title, color = Ink, fontWeight = FontWeight.SemiBold, maxLines = 2)
            if (result == null) {
                Text("Формат", color = Ink, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = format == LocalBookExportFormat.EPUB,
                        onClick = { if (!busy) onFormat(LocalBookExportFormat.EPUB) },
                        label = { Text("EPUB") }, enabled = !busy
                    )
                    FilterChip(
                        selected = format == LocalBookExportFormat.PDF,
                        onClick = { if (!busy) onFormat(LocalBookExportFormat.PDF) },
                        label = { Text("PDF") }, enabled = !busy
                    )
                }
                Text("Все скачанные главы и страницы", color = Muted, fontSize = 12.sp)
                if (busy) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Text(
                        progress?.let { "Глава ${it.completedChapters} из ${it.totalChapters}" }
                            ?: "Подготавливаю экспорт…",
                        color = Muted, fontSize = 12.sp
                    )
                    TextButton(onClick = onCancel) { Text("Отменить") }
                } else {
                    OutlineAction("Сохранить в Загрузки/ReaderLB", onExport)
                }
            } else {
                Text(result.displayName, color = Muted, fontSize = 12.sp)
                Text("Сохранено глав: ${result.chapterCount} · Загрузки/ReaderLB", color = Muted)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TextButton(onClick = { onOpen(result) }) { Text("Открыть") }
                    TextButton(onClick = { onShare(result) }) { Text("Поделиться") }
                }
            }
            error?.let { Text(it, color = Color(0xFFE4777D), fontSize = 12.sp) }
            if (!busy) TextButton(onClick = onDismiss) { Text("Закрыть") }
        }
    }
}

private fun decodeMangaPage(page: MangaPage): Bitmap =
    if (page.format == "avif") {
        com.radzivon.bartoshyk.avif.coder.HeifCoder().decode(page.bytes)
    } else {
        requireNotNull(BitmapFactory.decodeByteArray(page.bytes, 0, page.bytes.size)) {
            "Unsupported image"
        }
    }
