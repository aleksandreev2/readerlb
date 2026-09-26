package com.readerlb.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Base64
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.FirebaseMessaging
import com.readerlb.app.export.ExportedLocalBookFile
import com.readerlb.app.export.LocalBookExportFormat
import com.readerlb.app.export.LocalExportOptions
import com.readerlb.app.export.LocalExportProgress
import com.readerlb.app.export.LocalLibraryExportManager
import com.readerlb.app.importer.DEFAULT_EPUB_IMAGE_LIMIT_MB
import com.readerlb.app.importer.DEFAULT_EPUB_SOURCE_LIMIT_MB
import com.readerlb.app.importer.DEFAULT_EPUB_TOTAL_IMAGE_LIMIT_MB
import com.readerlb.app.importer.EpubImportLimits
import com.readerlb.app.importer.ExportProgress
import com.readerlb.app.importer.ExportStage
import com.readerlb.app.importer.ImportRepository
import com.readerlb.app.importer.ParsedBook
import com.readerlb.app.importer.PortableTitleInfo
import com.readerlb.app.importer.RanobeLibExporter
import com.readerlb.app.importer.ReaderLbTransferManager
import com.readerlb.app.importer.ReaderBlock
import com.readerlb.app.importer.chapterNumberInRange
import com.readerlb.app.importer.shouldInspectReaderLbTransferFile
import com.readerlb.app.importer.compareChapterNumbers
import com.readerlb.app.storage.HistoryStore
import com.readerlb.app.storage.ImportHistoryItem
import com.readerlb.app.storage.LocalLibraryItem
import com.readerlb.app.storage.Preferences
import com.readerlb.app.storage.RanobeLibAccessAssessment
import com.readerlb.app.storage.RanobeLibAccessCapability
import com.readerlb.app.storage.assessRanobeLibAccess
import com.readerlb.app.storage.RanobeLibLibraryScanner
import com.readerlb.app.storage.ShizukuRanobeLibLibraryScanner
import com.readerlb.app.shizuku.ShizukuRanobeLibBridge
import com.readerlb.app.shizuku.ShizukuRanobeLibState
import com.readerlb.app.shizuku.ShizukuRanobeLibStatus
import com.readerlb.app.storage.decodeLocalLibraryCache
import com.readerlb.app.storage.encodeLocalLibraryCache
import com.readerlb.app.update.UpdateDownloadProgress
import com.readerlb.app.update.UpdateDownloadStage
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
import java.io.File
import java.util.Locale

internal const val EXTRA_OPEN_UPDATES = "readerlb.open_updates"
private const val RELEASE_TOPIC = "readerlb_releases"

private data class IncomingImportRequest(
    val uri: Uri,
    val requestId: Long
)

class MainActivity : ComponentActivity() {
    private var requestSequence = 0L
    private var incomingImport by mutableStateOf<
        IncomingImportRequest?
    >(null)
    private var openUpdatesRequested by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        acceptIntent(intent)

        setContent {
            ReaderLBTheme {
                ReaderLBRoot(
                    incomingImport = incomingImport,
                    onImportConsumed = {
                        incomingImport = null
                    },
                    openUpdatesRequested = openUpdatesRequested,
                    onOpenUpdatesConsumed = {
                        openUpdatesRequested = false
                    }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        acceptIntent(intent)
    }

    private fun acceptIntent(intent: Intent?) {
        if (
            intent?.getBooleanExtra(
                EXTRA_OPEN_UPDATES,
                false
            ) == true
        ) {
            openUpdatesRequested = true
        }

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

private fun findPersistedRanobeLibTree(context: android.content.Context): Uri? =
    context.contentResolver.persistedUriPermissions
        .firstOrNull { permission ->
            permission.isReadPermission && permission.isWritePermission &&
                runCatching {
                    android.provider.DocumentsContract.getTreeDocumentId(permission.uri)
                        .replace('\\', '/')
                        .endsWith("Android/data/ru.libappc/files/book")
                }.getOrDefault(false)
        }
        ?.uri

@Composable
private fun ReaderLBRoot(
    incomingImport: IncomingImportRequest?,
    onImportConsumed: () -> Unit,
    openUpdatesRequested: Boolean,
    onOpenUpdatesConsumed: () -> Unit
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
            onImportConsumed = onImportConsumed,
            openUpdatesRequested = openUpdatesRequested,
            onOpenUpdatesConsumed = onOpenUpdatesConsumed
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RanobeLibAccessSetupSheet(
    assessment: RanobeLibAccessAssessment,
    shizukuStatus: ShizukuRanobeLibStatus?,
    onGrantLegacyAccess: () -> Unit,
    onShizukuAction: () -> Unit,
    onContinueWithoutDirectAccess: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState =
        rememberModalBottomSheetState(
            skipPartiallyExpanded = true
        )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor =
            MaterialTheme
                .colorScheme
                .background
    ) {
        LazyColumn(
            modifier =
                Modifier.fillMaxWidth(),
            contentPadding =
                PaddingValues(
                    start = 20.dp,
                    end = 20.dp,
                    bottom = 28.dp
                ),
            verticalArrangement =
                Arrangement.spacedBy(
                    14.dp
                )
        ) {
            item {
                Text(
                    "Доступ к RanobeLib",
                    color = Ink,
                    fontSize = 22.sp,
                    fontWeight =
                        FontWeight.Bold
                )
                Text(
                    assessment.androidLabel,
                    color = Blue,
                    fontSize = 13.sp,
                    fontWeight =
                        FontWeight.SemiBold,
                    modifier =
                        Modifier.padding(
                            top = 4.dp
                        )
                )
            }

            when (
                assessment.capability
            ) {
                RanobeLibAccessCapability
                    .CONNECTED -> {
                    item {
                        AccessSetupStatusCard(
                            icon =
                                Icons.Default.Check,
                            title =
                                "Доступ уже есть",
                            text =
                                "ReaderLB может читать локальную библиотеку RanobeLib и добавлять главы напрямую.",
                            success = true
                        )
                    }

                    item {
                        GradientButton(
                            text = "Готово",
                            onClick = onDismiss
                        )
                    }
                }

                RanobeLibAccessCapability
                    .USER_PICKER -> {
                    item {
                        AccessSetupStatusCard(
                            icon =
                                Icons.Default.Info,
                            title =
                                "Нужен доступ к папке book",
                            text =
                                "Android позволяет выдать его напрямую. ReaderLB откроет системный выбор папки сразу в каталоге RanobeLib.",
                            success = false
                        )
                    }

                    item {
                        Text(
                            "Выберите папку book и подтвердите доступ. ReaderLB сохранит системное разрешение, чтобы не спрашивать его при каждом запуске.",
                            color = Muted,
                            fontSize = 13.sp,
                            lineHeight = 19.sp
                        )
                    }

                    item {
                        GradientButton(
                            text =
                                "Дать доступ к RanobeLib",
                            onClick =
                                onGrantLegacyAccess
                        )
                    }

                    item {
                        Box(
                            modifier =
                                Modifier.fillMaxWidth(),
                            contentAlignment =
                                Alignment.Center
                        ) {
                            TextButton(
                                onClick =
                                    onContinueWithoutDirectAccess
                            ) {
                                Text(
                                    "Пока работать через Downloads"
                                )
                            }
                        }
                    }
                }

                RanobeLibAccessCapability
                    .SYSTEM_RESTRICTED -> {
                    val status =
                        shizukuStatus
                            ?: ShizukuRanobeLibStatus(
                                state =
                                    ShizukuRanobeLibState
                                        .CONNECTING,
                                message =
                                    "Проверяю способ полного доступа…"
                            )

                    item {
                        AccessSetupStatusCard(
                            icon =
                                if (
                                    status.ready
                                ) {
                                    Icons.Default.Check
                                } else {
                                    Icons.Default.Info
                                },
                            title =
                                when (
                                    status.state
                                ) {
                                    ShizukuRanobeLibState
                                        .NOT_INSTALLED ->
                                        "Нужен Shizuku"

                                    ShizukuRanobeLibState
                                        .NOT_RUNNING ->
                                        "Запустите Shizuku"

                                    ShizukuRanobeLibState
                                        .PERMISSION_REQUIRED ->
                                        "Один системный запрос"

                                    ShizukuRanobeLibState
                                        .PERMISSION_DENIED ->
                                        "Разрешение отклонено"

                                    ShizukuRanobeLibState
                                        .CONNECTING ->
                                        "Подключаю полный доступ"

                                    ShizukuRanobeLibState
                                        .READY ->
                                        "Полный доступ готов"

                                    ShizukuRanobeLibState
                                        .RANOBELIB_NOT_FOUND ->
                                        "Папка RanobeLib пока не найдена"

                                    ShizukuRanobeLibState
                                        .ERROR ->
                                        "Не удалось подключить Shizuku"
                                },
                            text =
                                status.message,
                            success =
                                status.ready
                        )
                    }

                    when (
                        status.state
                    ) {
                        ShizukuRanobeLibState
                            .NOT_INSTALLED -> {
                            item {
                                Text(
                                    "Android ${Build.VERSION.RELEASE} не даёт обычному приложению выбрать папку другого приложения в Android/data. ReaderLB может получить рабочий доступ через Shizuku без root.",
                                    color = Muted,
                                    fontSize = 13.sp,
                                    lineHeight = 19.sp
                                )
                            }

                            item {
                                Card(
                                    colors =
                                        CardDefaults.cardColors(
                                            containerColor =
                                                MaterialTheme
                                                    .colorScheme
                                                    .surface
                                        ),
                                    shape =
                                        RoundedCornerShape(
                                            14.dp
                                        ),
                                    border =
                                        androidx.compose.foundation
                                            .BorderStroke(
                                                1.dp,
                                                Line
                                            )
                                ) {
                                    Column(
                                        modifier =
                                            Modifier.padding(
                                                16.dp
                                            ),
                                        verticalArrangement =
                                            Arrangement.spacedBy(
                                                7.dp
                                            )
                                    ) {
                                        Text(
                                            "Как получить полный доступ",
                                            color = Ink,
                                            fontWeight =
                                                FontWeight.Bold
                                        )
                                        Text(
                                            "1. Установите Shizuku.\n2. Запустите его через «Беспроводную отладку».\n3. Вернитесь в ReaderLB и разрешите доступ.",
                                            color = Muted,
                                            fontSize = 13.sp,
                                            lineHeight = 19.sp
                                        )
                                    }
                                }
                            }

                            item {
                                GradientButton(
                                    text =
                                        "Установить Shizuku",
                                    onClick =
                                        onShizukuAction
                                )
                            }
                        }

                        ShizukuRanobeLibState
                            .NOT_RUNNING -> {
                            item {
                                Text(
                                    "Shizuku уже установлен. На Android 11+ его можно запустить прямо на телефоне через системную «Беспроводную отладку». После запуска просто вернитесь сюда — ReaderLB перепроверит доступ.",
                                    color = Muted,
                                    fontSize = 13.sp,
                                    lineHeight = 19.sp
                                )
                            }

                            item {
                                GradientButton(
                                    text =
                                        "Открыть Shizuku",
                                    onClick =
                                        onShizukuAction
                                )
                            }
                        }

                        ShizukuRanobeLibState
                            .PERMISSION_REQUIRED -> {
                            item {
                                Text(
                                    "Shizuku уже работает. Осталось один раз разрешить ReaderLB использовать его только для локальной папки книг RanobeLib.",
                                    color = Muted,
                                    fontSize = 13.sp,
                                    lineHeight = 19.sp
                                )
                            }

                            item {
                                GradientButton(
                                    text =
                                        "Разрешить ReaderLB",
                                    onClick =
                                        onShizukuAction
                                )
                            }
                        }

                        ShizukuRanobeLibState
                            .PERMISSION_DENIED -> {
                            item {
                                Text(
                                    "Откройте Shizuku → Приложения → ReaderLB и включите разрешение. Затем вернитесь — повторная проверка произойдёт автоматически.",
                                    color = Muted,
                                    fontSize = 13.sp,
                                    lineHeight = 19.sp
                                )
                            }

                            item {
                                GradientButton(
                                    text =
                                        "Открыть Shizuku",
                                    onClick =
                                        onShizukuAction
                                )
                            }
                        }

                        ShizukuRanobeLibState
                            .CONNECTING -> {
                            item {
                                LinearProgressIndicator(
                                    modifier =
                                        Modifier.fillMaxWidth(),
                                    color = Blue
                                )
                            }
                        }

                        ShizukuRanobeLibState
                            .READY -> {
                            item {
                                Text(
                                    "ReaderLB теперь может читать скачанную библиотеку, экспортировать тайтлы и добавлять главы напрямую. Повторно выбирать Android/data не нужно.",
                                    color = Muted,
                                    fontSize = 13.sp,
                                    lineHeight = 19.sp
                                )
                            }

                            item {
                                GradientButton(
                                    text = "Готово",
                                    onClick =
                                        onDismiss
                                )
                            }
                        }

                        ShizukuRanobeLibState
                            .RANOBELIB_NOT_FOUND -> {
                            item {
                                Text(
                                    "Откройте RanobeLib и скачайте хотя бы один тайтл локально, затем нажмите повторную проверку.",
                                    color = Muted,
                                    fontSize = 13.sp,
                                    lineHeight = 19.sp
                                )
                            }

                            item {
                                GradientButton(
                                    text =
                                        "Проверить ещё раз",
                                    onClick =
                                        onShizukuAction
                                )
                            }
                        }

                        ShizukuRanobeLibState
                            .ERROR -> {
                            item {
                                GradientButton(
                                    text =
                                        "Повторить проверку",
                                    onClick =
                                        onShizukuAction
                                )
                            }
                        }
                    }

                    if (
                        status.state !=
                        ShizukuRanobeLibState
                            .READY
                    ) {
                        item {
                            Box(
                                modifier =
                                    Modifier.fillMaxWidth(),
                                contentAlignment =
                                    Alignment.Center
                            ) {
                                TextButton(
                                    onClick =
                                        onContinueWithoutDirectAccess
                                ) {
                                    Text(
                                        "Пока работать через Downloads"
                                    )
                                }
                            }
                        }
                    }
                }
                }
            }
        }
    }
}

@Composable
private fun AccessSetupStatusCard(
    icon: ImageVector,
    title: String,
    text: String,
    success: Boolean
) {
    Card(
        colors =
            CardDefaults.cardColors(
                containerColor =
                    MaterialTheme
                        .colorScheme
                        .surface
            ),
        shape =
            RoundedCornerShape(
                14.dp
            ),
        border =
            androidx.compose.foundation
                .BorderStroke(
                    1.dp,
                    if (success) {
                        Success
                    } else {
                        Color(
                            0xFFE1B95B
                        )
                    }
                )
    ) {
        Row(
            modifier =
                Modifier.padding(
                    16.dp
                ),
            verticalAlignment =
                Alignment.Top
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint =
                    if (success) {
                        Success
                    } else {
                        Color(
                            0xFFE1B95B
                        )
                    },
                modifier =
                    Modifier.size(
                        24.dp
                    )
            )
            Column(
                modifier =
                    Modifier.padding(
                        start = 12.dp
                    )
            ) {
                Text(
                    title,
                    color = Ink,
                    fontWeight =
                        FontWeight.Bold
                )
                Text(
                    text,
                    color = Muted,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    modifier =
                        Modifier.padding(
                            top = 5.dp
                        )
                )
            }
        }
    }
}

@Composable
private fun RanobeLibAccessBanner(
    onClick: () -> Unit
) {
    Card(
        colors =
            CardDefaults.cardColors(
                containerColor =
                    MaterialTheme
                        .colorScheme
                        .surface
            ),
        shape =
            RoundedCornerShape(
                14.dp
            ),
        border =
            androidx.compose.foundation
                .BorderStroke(
                    1.dp,
                    Line
                )
    ) {
        Column(
            modifier =
                Modifier.padding(
                    16.dp
                ),
            verticalArrangement =
                Arrangement.spacedBy(
                    9.dp
                )
        ) {
            Row(
                verticalAlignment =
                    Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Info,
                    contentDescription = null,
                    tint = Blue,
                    modifier =
                        Modifier.size(
                            22.dp
                        )
                )
                Text(
                    "RanobeLib не подключена",
                    color = Ink,
                    fontWeight =
                        FontWeight.Bold,
                    modifier =
                        Modifier.padding(
                            start = 9.dp
                        )
                )
            }
            Text(
                if (
                    Build.VERSION.SDK_INT >=
                    Build.VERSION_CODES.R
                ) {
                    "ReaderLB определит ограничения этой версии Android и сразу покажет рабочий вариант."
                } else {
                    "ReaderLB может получить доступ к папке book через системный выбор папки."
                },
                color = Muted,
                fontSize = 12.sp,
                lineHeight = 17.sp
            )
            OutlineAction(
                text = "Проверить доступ",
                onClick = onClick
            )
        }
    }
}

@Composable
private fun Onboarding(onDone: () -> Unit) {
    var page by remember { mutableIntStateOf(0) }
    val pageCount = 3

    BackHandler(enabled = page > 0) {
        page -= 1
    }
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
                    "Следующее"
                },
                trailingIcon =
                    if (
                        page ==
                        pageCount - 1
                    ) {
                        null
                    } else {
                        Icons.Default
                            .ArrowForward
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
    onImportConsumed: () -> Unit,
    openUpdatesRequested: Boolean,
    onOpenUpdatesConsumed: () -> Unit
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
                ?.takeIf { hasPersistedTreePermission(context, it) }
                ?: findPersistedRanobeLibTree(context)
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

    var shizukuStatus by remember {
        mutableStateOf<
            ShizukuRanobeLibStatus?
        >(null)
    }
    val shizukuBridge = remember {
        ShizukuRanobeLibBridge(
            context
        ) {
                status ->
            shizukuStatus =
                status
        }
    }
    val shizukuLibraryScanner =
        remember(
            shizukuBridge
        ) {
            ShizukuRanobeLibLibraryScanner(
                context = context,
                bridge =
                    shizukuBridge
            )
        }

    DisposableEffect(
        shizukuBridge
    ) {
        shizukuBridge.start()
        onDispose {
            shizukuBridge.close()
        }
    }

    val shizukuReady =
        shizukuStatus?.ready ==
            true
    val libraryConnected =
        folderUri != null ||
            shizukuReady

    var showRanobeLibAccessSetup by rememberSaveable {
        mutableStateOf(
            folderUri == null &&
                if (
                    Build.VERSION.SDK_INT >=
                    Build.VERSION_CODES.R
                ) {
                    !preferences
                        .shizukuAccessIntroDone
                } else {
                    !preferences
                        .ranobeLibAccessIntroDone
                }
        )
    }

    val ranobeLibAccess =
        assessRanobeLibAccess(
            sdkInt =
                Build.VERSION.SDK_INT,
            androidRelease =
                Build.VERSION.RELEASE
                    .orEmpty(),
            connected =
                libraryConnected
        )

    LaunchedEffect(Unit) {
        preferences.ranobeLibBookTree = folderUri
    }

    LaunchedEffect(
        shizukuStatus?.state
    ) {
        if (shizukuReady) {
            preferences
                .shizukuAccessIntroDone =
                true
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
    var releaseNotificationsEnabled by remember {
        mutableStateOf(
            preferences.releaseNotificationsEnabled
        )
    }
    var notificationMessage by remember {
        mutableStateOf<String?>(null)
    }
    var directImportEnabled by remember {
        mutableStateOf(
            preferences.directImportEnabled
        )
    }
    var epubSourceLimitMb by remember {
        mutableStateOf(
            preferences.epubSourceLimitMb
        )
    }
    var epubImageLimitMb by remember {
        mutableStateOf(
            preferences.epubImageLimitMb
        )
    }
    var epubTotalImageLimitMb by remember {
        mutableStateOf(
            preferences.epubTotalImageLimitMb
        )
    }
    var updateBusy by remember { mutableStateOf(false) }
    var updateMessage by remember {
        mutableStateOf<String?>(null)
    }
    var updateDownloadProgress by remember {
        mutableStateOf<UpdateDownloadProgress?>(null)
    }
    var downloadedUpdateApk by remember {
        mutableStateOf<File?>(null)
    }
    var showUpdateDialog by rememberSaveable {
        mutableStateOf(false)
    }
    var updateJob by remember {
        mutableStateOf<Job?>(null)
    }

    fun subscribeReleaseNotifications() {
        val messaging =
            FirebaseMessaging.getInstance()
        messaging.isAutoInitEnabled = true
        notificationMessage =
            "Подключаю уведомления…"

        messaging
            .subscribeToTopic(
                RELEASE_TOPIC
            )
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    preferences
                        .releaseNotificationsEnabled =
                        true
                    releaseNotificationsEnabled =
                        true
                    notificationMessage =
                        "Уведомления о стабильных релизах включены."
                } else {
                    preferences
                        .releaseNotificationsEnabled =
                        false
                    releaseNotificationsEnabled =
                        false
                    messaging.isAutoInitEnabled =
                        false
                    notificationMessage =
                        "Не удалось подключить уведомления. Проверьте Google Play Services и интернет."
                }
            }
    }

    val notificationPermissionLauncher =
        rememberLauncherForActivityResult(
            contract =
                ActivityResultContracts
                    .RequestPermission()
        ) { granted ->
            if (granted) {
                subscribeReleaseNotifications()
            } else {
                preferences
                    .releaseNotificationsEnabled =
                    false
                releaseNotificationsEnabled =
                    false
                FirebaseMessaging
                    .getInstance()
                    .isAutoInitEnabled =
                    false
                notificationMessage =
                    "Android не разрешил уведомления."
            }
        }

    fun setReleaseNotifications(
        enabled: Boolean
    ) {
        if (!enabled) {
            preferences
                .releaseNotificationsEnabled =
                false
            releaseNotificationsEnabled =
                false
            notificationMessage =
                "Уведомления о релизах выключены."

            val messaging =
                FirebaseMessaging.getInstance()
            messaging
                .unsubscribeFromTopic(
                    RELEASE_TOPIC
                )
                .addOnCompleteListener {
                    messaging.deleteToken()
                    messaging.isAutoInitEnabled =
                        false
                }
            return
        }

        if (
            Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission
                    .POST_NOTIFICATIONS
            ) != PackageManager
                .PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher
                .launch(
                    Manifest.permission
                        .POST_NOTIFICATIONS
                )
        } else {
            subscribeReleaseNotifications()
        }
    }

    LaunchedEffect(Unit) {
        if (releaseNotificationsEnabled) {
            val messaging =
                FirebaseMessaging.getInstance()
            messaging.isAutoInitEnabled = true
            messaging.subscribeToTopic(
                RELEASE_TOPIC
            )
        }
    }

    fun removeLocalTitleFromUi(
        slugUrl: String
    ) {
        localLibrary =
            localLibrary.filterNot {
                it.slugUrl == slugUrl
            }
        preferences.localLibraryCacheJson =
            encodeLocalLibraryCache(
                localLibrary
            )
        libraryStatus =
            "Новелла удалена"
    }

    fun refreshLocalLibrary() {
        val tree = folderUri
        val useShizuku =
            tree == null &&
                shizukuReady

        if (
            tree == null &&
            !useShizuku
        ) {
            libraryScanJob?.cancel()
            libraryScanJob = null
            localLibrary =
                emptyList()
            librarySkipped = 0
            libraryError = null
            libraryStatus = null
            libraryScanned = 0
            libraryScanTotal = 0
            libraryLoading = false
            return
        }

        if (
            libraryScanJob?.isActive ==
            true
        ) {
            libraryRefreshPending =
                true
            return
        }

        libraryScanJob =
            scope.launch {
                do {
                    libraryRefreshPending =
                        false
                    val previous =
                        localLibrary

                    libraryLoading =
                        true
                    libraryError =
                        null
                    libraryStatus =
                        null
                    libraryScanned = 0
                    libraryScanTotal = 0

                    val result =
                        try {
                            withTimeout(
                                120_000L
                            ) {
                                runInterruptible(
                                    Dispatchers.IO
                                ) {
                                    val progress:
                                        (
                                            Int,
                                            Int
                                        ) -> Unit =
                                        {
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

                                    Result.success(
                                        if (
                                            tree !=
                                            null
                                        ) {
                                            libraryScanner
                                                .scan(
                                                    tree,
                                                    progress
                                                )
                                        } else {
                                            shizukuLibraryScanner
                                                .scan(
                                                    progress
                                                )
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
                                    "RanobeLib слишком долго отвечает. Проверьте доступ и повторите сканирование."
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
                            Result.failure(
                                throwable
                            )
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
                            snapshot
                                .skippedTitles
                        libraryScanned =
                            snapshot.items
                                .size +
                                snapshot
                                    .skippedTitles
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
                                    snapshot.items
                                        .size +
                                    " тайтлов"
                            }
                    }.onFailure {
                            throwable ->
                        libraryError =
                            throwable.message
                                ?: "Не удалось прочитать библиотеку RanobeLib"

                        if (
                            tree != null &&
                            (
                                throwable is
                                    SecurityException ||
                                    !hasPersistedTreePermission(
                                        context,
                                        tree
                                    )
                                )
                        ) {
                            preferences
                                .ranobeLibBookTree =
                                null
                            preferences
                                .localLibraryCacheTree =
                                null
                            preferences
                                .localLibraryCacheJson =
                                null
                            localLibrary =
                                emptyList()
                            folderUri = null
                            libraryError =
                                "Доступ к RanobeLib потерян. ReaderLB проверит другой рабочий способ."
                            showRanobeLibAccessSetup =
                                true
                        } else if (
                            useShizuku
                        ) {
                            shizukuBridge.refresh()
                            libraryError =
                                "Shizuku потерял доступ к RanobeLib. ReaderLB перепроверяет подключение."
                        }
                    }

                    libraryLoading =
                        false
                } while (
                    libraryRefreshPending &&
                    (
                        folderUri ==
                            tree ||
                            (
                                tree ==
                                    null &&
                                    shizukuReady
                                )
                        )
                )
            }
    }

    LaunchedEffect(
        folderUri,
        shizukuStatus?.state
    ) {
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
            updateBusy = false
            result.onSuccess { update ->
                if (
                    update?.versionName !=
                    latestUpdate?.versionName
                ) {
                    downloadedUpdateApk = null
                    updateDownloadProgress = null
                }

                latestUpdate = update
                updateMessage = if (update == null) {
                    "Установлена актуальная версия ReaderLB."
                } else {
                    "Доступна ReaderLB ${update.versionName}."
                }

                if (update != null) {
                    showUpdateDialog = true
                }
            }.onFailure {
                updateMessage =
                    "Не удалось проверить обновления: " +
                        (it.message ?: "ошибка сети")
            }
        }
    }

    LaunchedEffect(openUpdatesRequested) {
        if (openUpdatesRequested) {
            tab = AppTab.SETTINGS
            checkForUpdates()
            onOpenUpdatesConsumed()
        }
    }

    fun openLatestUpdate() {
        if (latestUpdate != null) {
            showUpdateDialog = true
        }
    }

    fun downloadLatestUpdate() {
        val update = latestUpdate ?: return
        if (updateBusy) return

        downloadedUpdateApk = null
        updateDownloadProgress =
            UpdateDownloadProgress(
                stage =
                    UpdateDownloadStage.DOWNLOADING,
                downloadedBytes = 0L,
                totalBytes =
                    update.sizeBytes
            )
        updateBusy = true
        updateMessage = null

        updateJob = scope.launch {
            try {
                val apk = runInterruptible(
                    Dispatchers.IO
                ) {
                    updateManager.download(
                        info = update,
                        onProgress = {
                                progress ->
                            updateDownloadProgress =
                                progress
                        }
                    )
                }

                downloadedUpdateApk = apk
                updateMessage = null
            } catch (
                cancelled:
                    CancellationException
            ) {
                downloadedUpdateApk = null
                updateDownloadProgress = null
                updateMessage =
                    "Загрузка обновления отменена."
            } catch (throwable: Throwable) {
                downloadedUpdateApk = null
                updateDownloadProgress = null
                updateMessage =
                    com.readerlb.app.update
                        .friendlyUpdateDownloadError(
                            throwable
                        )
            } finally {
                updateBusy = false
                updateJob = null
            }
        }
    }

    fun cancelUpdateDownload() {
        updateJob?.cancel()
    }

    fun installDownloadedUpdate() {
        val apk =
            downloadedUpdateApk
                ?: return

        if (
            !updateManager
                .canRequestInstallPackages()
        ) {
            context.startActivity(
                updateManager
                    .unknownSourcesSettingsIntent()
            )
            updateMessage =
                "Разрешите ReaderLB устанавливать приложения из этого источника, затем вернитесь и нажмите «Установить»."
            return
        }

        updateMessage = null
        showUpdateDialog = false
        updateManager.requestInstall(apk)
    }

    BackHandler(enabled = tab != AppTab.HOME) {
        tab = AppTab.HOME
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
            if (
                persisted &&
                hasPersistedTreePermission(
                    context,
                    uri
                )
            ) {
                preferences
                    .ranobeLibAccessIntroDone =
                    true
                showRanobeLibAccessSetup =
                    false
            }
        }
    }

    fun openRanobeLibAccessSetup() {
        showRanobeLibAccessSetup = true
    }

    if (showRanobeLibAccessSetup) {
        RanobeLibAccessSetupSheet(
            assessment =
                ranobeLibAccess,
            shizukuStatus =
                shizukuStatus,
            onGrantLegacyAccess = {
                folderPicker.launch(
                    RANOBELIB_BOOK_INITIAL_URI
                )
            },
            onShizukuAction = {
                shizukuBridge
                    .requestAccess()
            },
            onContinueWithoutDirectAccess = {
                if (
                    Build.VERSION.SDK_INT >=
                    Build.VERSION_CODES.R
                ) {
                    preferences
                        .shizukuAccessIntroDone =
                        true
                } else {
                    preferences
                        .ranobeLibAccessIntroDone =
                        true
                }
                showRanobeLibAccessSetup =
                    false
            },
            onDismiss = {
                showRanobeLibAccessSetup =
                    false
            }
        )
    }

    if (
        showUpdateDialog &&
        latestUpdate != null
    ) {
        ReaderLbUpdateDialog(
            info = requireNotNull(
                latestUpdate
            ),
            busy = updateBusy,
            progress =
                updateDownloadProgress,
            readyToInstall =
                downloadedUpdateApk != null,
            message = updateMessage,
            onDownload =
                ::downloadLatestUpdate,
            onInstall =
                ::installDownloadedUpdate,
            onCancel =
                ::cancelUpdateDownload,
            onDismiss = {
                if (!updateBusy) {
                    showUpdateDialog = false
                }
            }
        )
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
                libraryConnected =
                    libraryConnected,
                libraryLoading = libraryLoading,
                libraryScanned = libraryScanned,
                libraryScanTotal = libraryScanTotal,
                treeUri = folderUri,
                shizukuBridge =
                    if (
                        shizukuReady
                    ) {
                        shizukuBridge
                    } else {
                        null
                    },
                updateInfo = latestUpdate,
                updateBusy = updateBusy,
                updateMessage = updateMessage,
                onUpdate = ::openLatestUpdate,
                onAdd = { tab = AppTab.IMPORT },
                onOpenLibrary = { tab = AppTab.LIBRARY },
                onSettings = { tab = AppTab.SETTINGS },
                onConfigureRanobeLibAccess =
                    ::openRanobeLibAccessSetup,
                onTitleDeleted = ::removeLocalTitleFromUi
            )
            AppTab.IMPORT -> ImportScreen(
                modifier = Modifier.padding(padding),
                folderUri = folderUri,
                shizukuBridge =
                    shizukuBridge,
                directAccessAvailable =
                    libraryConnected,
                directImportEnabled =
                    directImportEnabled,
                epubLimits =
                    EpubImportLimits
                        .fromMegabytes(
                            sourceMb =
                                epubSourceLimitMb,
                            singleImageMb =
                                epubImageLimitMb,
                            totalImageMb =
                                epubTotalImageLimitMb
                        ),
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
                onNeedRanobeLibAccess =
                    ::openRanobeLibAccessSetup,
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
                connected =
                    libraryConnected,
                loading = libraryLoading,
                scanned = libraryScanned,
                scanTotal = libraryScanTotal,
                status = libraryStatus,
                skippedTitles = librarySkipped,
                error = libraryError,
                treeUri = folderUri,
                shizukuBridge =
                    if (
                        shizukuReady
                    ) {
                        shizukuBridge
                    } else {
                        null
                    },
                onRefresh = ::refreshLocalLibrary,
                onPickFolder =
                    ::openRanobeLibAccessSetup,
                onAdd = {
                    tab = AppTab.IMPORT
                },
                onTitleDeleted = ::removeLocalTitleFromUi
            )
            AppTab.SETTINGS -> SettingsScreen(
                modifier = Modifier.padding(padding),
                folderUri = folderUri,
                updateInfo = latestUpdate,
                autoUpdateChecks = autoUpdateChecks,
                releaseNotificationsEnabled =
                    releaseNotificationsEnabled,
                notificationMessage =
                    notificationMessage,
                updateBusy = updateBusy,
                updateMessage = updateMessage,
                epubSourceLimitMb =
                    epubSourceLimitMb,
                epubImageLimitMb =
                    epubImageLimitMb,
                epubTotalImageLimitMb =
                    epubTotalImageLimitMb,
                onEpubSourceLimitChanged = {
                    value ->
                    preferences.epubSourceLimitMb =
                        value
                    epubSourceLimitMb = value
                },
                onEpubImageLimitChanged = {
                    value ->
                    preferences.epubImageLimitMb =
                        value
                    epubImageLimitMb = value
                },
                onEpubTotalImageLimitChanged = {
                    value ->
                    preferences
                        .epubTotalImageLimitMb =
                        value
                    epubTotalImageLimitMb =
                        value
                },
                onResetEpubLimits = {
                    preferences.epubSourceLimitMb =
                        DEFAULT_EPUB_SOURCE_LIMIT_MB
                    preferences.epubImageLimitMb =
                        DEFAULT_EPUB_IMAGE_LIMIT_MB
                    preferences
                        .epubTotalImageLimitMb =
                        DEFAULT_EPUB_TOTAL_IMAGE_LIMIT_MB
                    epubSourceLimitMb =
                        DEFAULT_EPUB_SOURCE_LIMIT_MB
                    epubImageLimitMb =
                        DEFAULT_EPUB_IMAGE_LIMIT_MB
                    epubTotalImageLimitMb =
                        DEFAULT_EPUB_TOTAL_IMAGE_LIMIT_MB
                },
                onAutoUpdateChecksChanged = { enabled ->
                    preferences.autoUpdateChecks = enabled
                    autoUpdateChecks = enabled
                },
                onReleaseNotificationsChanged =
                    ::setReleaseNotifications,
                onCheckUpdates = ::checkForUpdates,
                onInstallUpdate = ::openLatestUpdate,
                onRepeatHints = {
                    preferences.importHintsDone = false
                    importHintsDone = false
                    tab = AppTab.IMPORT
                },
                onPickFolder = {
                    if (
                        Build.VERSION.SDK_INT <
                        Build.VERSION_CODES.R &&
                        folderUri != null
                    ) {
                        folderPicker.launch(
                            RANOBELIB_BOOK_INITIAL_URI
                        )
                    } else {
                        openRanobeLibAccessSetup()
                    }
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
    treeUri: Uri?,
    shizukuBridge:
        ShizukuRanobeLibBridge?,
    updateInfo: UpdateInfo?,
    updateBusy: Boolean,
    updateMessage: String?,
    onUpdate: () -> Unit,
    onAdd: () -> Unit,
    onOpenLibrary: () -> Unit,
    onSettings: () -> Unit,
    onConfigureRanobeLibAccess: () -> Unit,
    onTitleDeleted: (String) -> Unit
) {
    var selectedSlug by rememberSaveable {
        mutableStateOf<String?>(null)
    }
    val selectedItem =
        localLibrary.firstOrNull {
            it.slugUrl == selectedSlug
        }

    BackHandler(enabled = selectedItem != null) {
        selectedSlug = null
    }

    if (selectedItem != null) {
        LibraryTitleDetail(
            modifier = modifier,
            item = selectedItem,
            treeUri = treeUri,
            shizukuBridge =
                shizukuBridge,
            onBack = {
                selectedSlug = null
            },
            onDeleted = onTitleDeleted
        )
        return
    }

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
                text = "Добавить новеллу",
                leadingIcon =
                    Icons.Default.Add,
                onClick = onAdd
            )
        }

        if (!libraryConnected) {
            item {
                RanobeLibAccessBanner(
                    onClick =
                        onConfigureRanobeLibAccess
                )
            }
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
                    LocalLibraryCard(
                        item = item,
                        onClick = {
                            selectedSlug =
                                item.slugUrl
                        }
                    )
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
    shizukuBridge:
        ShizukuRanobeLibBridge,
    directAccessAvailable: Boolean,
    directImportEnabled: Boolean,
    epubLimits: EpubImportLimits,
    onDirectImportChanged: (Boolean) -> Unit,
    initialImport: IncomingImportRequest?,
    onInitialImportConsumed: () -> Unit,
    showHints: Boolean,
    onHintsDone: () -> Unit,
    onBack: () -> Unit,
    onPickFolder: () -> Unit,
    onNeedRanobeLibAccess: () -> Unit,
    onImported: (com.readerlb.app.importer.ExportResult) -> Unit,
    onOpenLibrary: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember { ImportRepository(context) }
    val exporter = remember { RanobeLibExporter(context) }
    val transferManager = remember {
        ReaderLbTransferManager(context)
    }
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
    var portablePackage by remember {
        mutableStateOf<PortableTitleInfo?>(null)
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
            directImportEnabled &&
                directAccessAvailable
        )
    }
    LaunchedEffect(
        directAccessAvailable
    ) {
        if (
            !directAccessAvailable
        ) {
            direct = false
        }
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
        portablePackage = null
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
        portablePackage = null
        error = null
        success = null
        busy = true

        try {
            val looksLikeReaderLbPackage =
                fileName.endsWith(
                    ".readerlb.zip",
                    ignoreCase = true
                ) ||
                    fileName.endsWith(
                        "_readerlb.zip",
                        ignoreCase = true
                    )
            val transfer =
                if (
                    shouldInspectReaderLbTransferFile(
                        fileName
                    )
                ) {
                    withContext(
                        Dispatchers.IO
                    ) {
                        transferManager
                            .inspect(uri)
                            ?: if (
                                looksLikeReaderLbPackage
                            ) {
                                error(
                                    "Пакет ReaderLB повреждён или имеет неизвестную версию"
                                )
                            } else {
                                null
                            }
                    }
                } else {
                    null
                }

            if (transfer != null) {
                portablePackage =
                    transfer
                title = transfer.title
            } else {
                val book =
                    runInterruptible(
                        Dispatchers.IO
                    ) {
                        repository.parse(
                            uri,
                            epubLimits
                        )
                    }

                parsed = book
                if (title.isBlank()) {
                    title = book.title
                }
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
            portablePackage = null
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
            portablePackage == null &&
            selectedUri == null &&
            !busy &&
            error == null

    if (emptyImportState) {
        Box(
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

            FileDropCard(
                fileName = fileName,
                analyzing = false,
                enabled = true,
                recentFileAvailable =
                    recentDocument != null,
                onOpenRecent = {
                    queueFile(
                        requireNotNull(
                            recentDocument
                        )
                    )
                },
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
                },
                modifier = Modifier
                    .align(Alignment.Center)
                    .testTag(
                        "import-file-hero"
                    )
            )
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
                    busy &&
                        parsed == null &&
                        portablePackage == null,
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

        if (
            showHints &&
            parsed == null &&
            portablePackage == null &&
            !busy
        ) {
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
                            enabled = !busy,
                            onCheckedChange = {
                                enabled ->
                                if (
                                    enabled &&
                                    folderUri == null
                                ) {
                                    direct = false
                                    onNeedRanobeLibAccess()
                                } else {
                                    direct = enabled
                                    onDirectImportChanged(
                                        enabled
                                    )
                                }
                            }
                        )
                    }
                }
            }
        }

        if (
            parsed != null &&
            direct &&
            folderUri == null &&
            Build.VERSION.SDK_INT < Build.VERSION_CODES.R
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
                            "Выберите папку book в системном окне Android.",
                            color = Muted,
                            fontSize = 12.sp,
                            lineHeight = 18.sp,
                            modifier = Modifier.padding(top = 6.dp, bottom = 12.dp)
                        )
                        OutlineAction(
                            "Настроить доступ",
                            onNeedRanobeLibAccess
                        )
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

        portablePackage?.let {
                transfer ->
            item {
                GradientButton(
                    text =
                        if (busy) {
                            "Устанавливаю пакет…"
                        } else {
                            "Установить в RanobeLib"
                        },
                    enabled =
                        !busy &&
                            directAccessAvailable &&
                            selectedUri != null,
                    onClick = {
                        val rawUri =
                            selectedUri
                                ?: return@GradientButton
                        busy = true
                        error = null
                        success = null

                        scope.launch {
                            runCatching {
                                withContext(
                                    Dispatchers.IO
                                ) {
                                    if (
                                        folderUri !=
                                        null
                                    ) {
                                        transferManager
                                            .install(
                                                uri =
                                                    Uri.parse(
                                                        rawUri
                                                    ),
                                                treeUri =
                                                    folderUri
                                            )
                                    } else {
                                        transferManager
                                            .install(
                                                uri =
                                                    Uri.parse(
                                                        rawUri
                                                    ),
                                                bridge =
                                                    shizukuBridge
                                            )
                                    }
                                }
                            }.onSuccess {
                                    result ->
                                success =
                                    "Готово: " +
                                        transfer.title +
                                        " установлен в RanobeLib."
                                onImported(
                                    result
                                )
                            }.onFailure {
                                error =
                                    it.message
                                        ?: "Не удалось установить пакет ReaderLB"
                            }
                            busy = false
                        }
                    }
                )
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
                    (!direct ||
                        directAccessAvailable) &&
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
                                        if (
                                            direct
                                        ) {
                                            folderUri
                                        } else {
                                            null
                                        },
                                    shizukuBridge =
                                        if (
                                            direct &&
                                            folderUri ==
                                                null
                                        ) {
                                            shizukuBridge
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
    onPick: () -> Unit,
    recentFileAvailable: Boolean = false,
    onOpenRecent: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
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

                if (recentFileAvailable) {
                    Text(
                        "Открыть последний файл",
                        color = Blue,
                        fontWeight =
                            FontWeight.SemiBold,
                        modifier = Modifier
                            .padding(top = 14.dp)
                            .clickable(
                                onClick =
                                    onOpenRecent
                            )
                    )
                }
            }
            Text(
                "Поддерживаются: EPUB, TXT, пакеты ReaderLB · ZIP с EPUB-структурой",
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
                    val warning =
                        issue.severity ==
                            com.readerlb.app.importer
                                .ImportIssueSeverity
                                .WARNING
                    Row(
                        modifier =
                            Modifier.padding(
                                vertical = 3.dp
                            ),
                        verticalAlignment =
                            Alignment.Top
                    ) {
                        Icon(
                            imageVector =
                                if (warning) {
                                    Icons.Default.Warning
                                } else {
                                    Icons.Default.Info
                                },
                            contentDescription =
                                null,
                            tint =
                                if (warning) {
                                    Color(
                                        0xFFE1B95B
                                    )
                                } else {
                                    Muted
                                },
                            modifier =
                                Modifier.size(
                                    18.dp
                                )
                        )
                        Text(
                            text = issue.message,
                            color =
                                if (warning) {
                                    Color(
                                        0xFFE1B95B
                                    )
                                } else {
                                    Muted
                                },
                            fontSize = 12.sp,
                            lineHeight = 17.sp,
                            modifier =
                                Modifier.padding(
                                    start = 7.dp
                                )
                        )
                    }
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

private enum class LibrarySourceFilter {
    ALL,
    READERLB,
    RANOBELIB
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
    treeUri: Uri?,
    shizukuBridge:
        ShizukuRanobeLibBridge?,
    onRefresh: () -> Unit,
    onPickFolder: () -> Unit,
    onAdd: () -> Unit,
    onTitleDeleted: (String) -> Unit
) {
    var query by rememberSaveable {
        mutableStateOf("")
    }
    var sortIndex by rememberSaveable {
        mutableIntStateOf(0)
    }
    var sourceFilterIndex by rememberSaveable {
        mutableIntStateOf(0)
    }
    var selectedSlug by rememberSaveable {
        mutableStateOf<String?>(null)
    }

    val selectedItem = items.firstOrNull {
        it.slugUrl == selectedSlug
    }

    BackHandler(enabled = selectedItem != null) {
        selectedSlug = null
    }

    if (selectedItem != null) {
        LibraryTitleDetail(
            modifier = modifier,
            item = selectedItem,
            treeUri = treeUri,
            shizukuBridge =
                shizukuBridge,
            onBack = {
                selectedSlug = null
            },
            onDeleted = onTitleDeleted
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

    val sourceFilter =
        LibrarySourceFilter.entries[
            sourceFilterIndex.coerceIn(
                0,
                LibrarySourceFilter
                    .entries
                    .lastIndex
            )
        ]

    val visibleItems = remember(
        items,
        query,
        sortMode,
        sourceFilter
    ) {
        val filtered = items.filter { item ->
            val matchesQuery =
                query.isBlank() ||
                    item.title.contains(
                        query.trim(),
                        ignoreCase = true
                    ) ||
                    item.slugUrl.contains(
                        query.trim(),
                        ignoreCase = true
                    )
            val matchesSource =
                when (
                    sourceFilter
                ) {
                    LibrarySourceFilter
                        .ALL -> true
                    LibrarySourceFilter
                        .READERLB ->
                        item.createdByReaderLB
                    LibrarySourceFilter
                        .RANOBELIB ->
                        !item.createdByReaderLB
                }

            matchesQuery &&
                matchesSource
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
                            if (
                                Build.VERSION.SDK_INT >=
                                    Build.VERSION_CODES.R
                            ) {
                                "ReaderLB проверит доступ и сразу объяснит, " +
                                    "что доступно на этой версии Android."
                            } else {
                                "ReaderLB прочитает только info.json, " +
                                    "chapters.json и обложки. Главы не " +
                                    "изменяются при просмотре библиотеки."
                            },
                            color = Muted,
                            fontSize = 13.sp,
                            lineHeight = 18.sp
                        )
                        OutlineAction(
                            "Настроить доступ",
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
                    Row(
                        modifier =
                            Modifier.fillMaxWidth(),
                        horizontalArrangement =
                            Arrangement.spacedBy(
                                8.dp
                            )
                    ) {
                        LibrarySortPill(
                            modifier =
                                Modifier.weight(
                                    1f
                                ),
                            text = "Все",
                            selected =
                                sourceFilter ==
                                    LibrarySourceFilter
                                        .ALL,
                            onClick = {
                                sourceFilterIndex =
                                    LibrarySourceFilter
                                        .ALL
                                        .ordinal
                            }
                        )
                        LibrarySortPill(
                            modifier =
                                Modifier.weight(
                                    1f
                                ),
                            text = "ReaderLB",
                            selected =
                                sourceFilter ==
                                    LibrarySourceFilter
                                        .READERLB,
                            onClick = {
                                sourceFilterIndex =
                                    LibrarySourceFilter
                                        .READERLB
                                        .ordinal
                            }
                        )
                        LibrarySortPill(
                            modifier =
                                Modifier.weight(
                                    1f
                                ),
                            text = "RanobeLib",
                            selected =
                                sourceFilter ==
                                    LibrarySourceFilter
                                        .RANOBELIB,
                            onClick = {
                                sourceFilterIndex =
                                    LibrarySourceFilter
                                        .RANOBELIB
                                        .ordinal
                            }
                        )
                    }
                }

                item {
                    Text(
                        if (
                            query.isBlank() &&
                            sourceFilter ==
                                LibrarySourceFilter
                                    .ALL
                        ) {
                            "Тайтлов: " +
                                items.size
                        } else {
                            "Показано: " +
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
private fun EpubLimitField(
    label: String,
    value: Long,
    onValueChanged: (Long) -> Unit
) {
    var text by rememberSaveable(value) {
        mutableStateOf(value.toString())
    }

    OutlinedTextField(
        value = text,
        onValueChange = { raw ->
            val filtered =
                raw.filter(Char::isDigit)
                    .take(12)

            text = filtered

            filtered
                .toLongOrNull()
                ?.let(onValueChanged)
        },
        modifier = Modifier.fillMaxWidth(),
        label = {
            Text(label)
        },
        singleLine = true,
        keyboardOptions =
            KeyboardOptions(
                keyboardType =
                    KeyboardType.Number
            )
    )
}

@Composable
private fun SettingsScreen(
    modifier: Modifier,
    folderUri: Uri?,
    updateInfo: UpdateInfo?,
    autoUpdateChecks: Boolean,
    releaseNotificationsEnabled: Boolean,
    notificationMessage: String?,
    updateBusy: Boolean,
    updateMessage: String?,
    epubSourceLimitMb: Long,
    epubImageLimitMb: Long,
    epubTotalImageLimitMb: Long,
    onEpubSourceLimitChanged: (Long) -> Unit,
    onEpubImageLimitChanged: (Long) -> Unit,
    onEpubTotalImageLimitChanged: (Long) -> Unit,
    onResetEpubLimits: () -> Unit,
    onAutoUpdateChecksChanged: (Boolean) -> Unit,
    onReleaseNotificationsChanged: (Boolean) -> Unit,
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
                        when {
                            folderUri == null ->
                                "Настроить доступ"

                            Build.VERSION.SDK_INT <
                                Build.VERSION_CODES.R ->
                                "Изменить папку"

                            else ->
                                "Проверить доступ"
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
                colors =
                    CardDefaults.cardColors(
                        containerColor =
                            MaterialTheme
                                .colorScheme
                                .surface
                    ),
                shape =
                    RoundedCornerShape(16.dp),
                border =
                    androidx.compose.foundation
                        .BorderStroke(
                            1.dp,
                            Line
                        )
            ) {
                Column(
                    modifier =
                        Modifier.padding(18.dp),
                    verticalArrangement =
                        Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        "Лимиты EPUB",
                        color = Ink,
                        fontWeight =
                            FontWeight.Bold
                    )
                    Text(
                        "Значения задаются в МБ. " +
                            "0 отключает соответствующий лимит. " +
                            "Слишком высокие значения или режим без лимита " +
                            "могут увеличить расход памяти и места на устройстве.",
                        color = Muted,
                        fontSize = 12.sp,
                        lineHeight = 17.sp
                    )
                    EpubLimitField(
                        label =
                            "Максимальный размер EPUB, МБ",
                        value =
                            epubSourceLimitMb,
                        onValueChanged =
                            onEpubSourceLimitChanged
                    )
                    EpubLimitField(
                        label =
                            "Одна иллюстрация, МБ",
                        value =
                            epubImageLimitMb,
                        onValueChanged =
                            onEpubImageLimitChanged
                    )
                    EpubLimitField(
                        label =
                            "Все иллюстрации, МБ",
                        value =
                            epubTotalImageLimitMb,
                        onValueChanged =
                            onEpubTotalImageLimitChanged
                    )
                    Text(
                        "Сбросить: 256 / 24 / 256 МБ",
                        color = Blue,
                        fontSize = 13.sp,
                        fontWeight =
                            FontWeight.SemiBold,
                        modifier =
                            Modifier.clickable(
                                onClick =
                                    onResetEpubLimits
                            )
                    )
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
                                    "Скачивание и установка запускаются " +
                                    "только после вашего подтверждения.",
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

                    Divider()

                    Row(
                        modifier =
                            Modifier.fillMaxWidth(),
                        verticalAlignment =
                            Alignment.CenterVertically
                    ) {
                        Column(
                            Modifier.weight(1f)
                        ) {
                            Text(
                                "Push о новых релизах",
                                color = Ink,
                                fontWeight =
                                    FontWeight.SemiBold
                            )
                            Text(
                                "Получать уведомление только при публикации стабильной версии ReaderLB. Функция выключена по умолчанию.",
                                color = Muted,
                                fontSize = 12.sp,
                                lineHeight = 17.sp
                            )
                        }
                        Switch(
                            checked =
                                releaseNotificationsEnabled,
                            onCheckedChange =
                                onReleaseNotificationsChanged
                        )
                    }

                    notificationMessage?.let {
                        Text(
                            it,
                            color = Muted,
                            fontSize = 12.sp,
                            lineHeight = 17.sp
                        )
                    }

                    if (updateInfo != null) {
                        Text(
                            "Доступна версия " +
                                updateInfo.versionName,
                            color = Blue,
                            fontWeight =
                                FontWeight.SemiBold
                        )
                        Text(
                            "Откройте карточку обновления, чтобы посмотреть изменения, скачать APK и проверить его перед установкой.",
                            color = Muted,
                            fontSize = 12.sp,
                            lineHeight = 17.sp
                        )
                        OutlineAction(
                            "Посмотреть обновление",
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
    treeUri: Uri?,
    shizukuBridge:
        ShizukuRanobeLibBridge?,
    onBack: () -> Unit,
    onDeleted: (String) -> Unit
) {
    val context =
        androidx.compose.ui.platform
            .LocalContext.current
    val scope = rememberCoroutineScope()
    val transferManager = remember {
        ReaderLbTransferManager(context)
    }
    val exportManager = remember {
        LocalLibraryExportManager(
            context
        )
    }
    val cover by rememberLibraryCover(
        item.coverUri
    )
    var busy by remember {
        mutableStateOf(false)
    }
    var error by remember {
        mutableStateOf<String?>(null)
    }
    var confirmDelete by remember {
        mutableStateOf(false)
    }
    var showExport by remember {
        mutableStateOf(false)
    }
    var exportFormatIndex by rememberSaveable {
        mutableIntStateOf(0)
    }
    var exportUseRange by rememberSaveable {
        mutableStateOf(false)
    }
    var exportFirstChapter by rememberSaveable(
        item.slugUrl
    ) {
        mutableStateOf(
            item.firstChapter
        )
    }
    var exportLastChapter by rememberSaveable(
        item.slugUrl
    ) {
        mutableStateOf(
            item.lastChapter
        )
    }
    var exportIncludeCover by rememberSaveable {
        mutableStateOf(true)
    }
    var exportIncludeImages by rememberSaveable {
        mutableStateOf(true)
    }
    var exportBusy by remember {
        mutableStateOf(false)
    }
    var exportProgress by remember {
        mutableStateOf<LocalExportProgress?>(
            null
        )
    }
    var exportResult by remember {
        mutableStateOf<ExportedLocalBookFile?>(
            null
        )
    }
    var exportError by remember {
        mutableStateOf<String?>(null)
    }
    var exportJob by remember {
        mutableStateOf<Job?>(null)
    }

    val exportFormat =
        LocalBookExportFormat.entries[
            exportFormatIndex.coerceIn(
                0,
                LocalBookExportFormat
                    .entries
                    .lastIndex
            )
        ]

    fun startExport() {
        if (
            treeUri == null &&
            shizukuBridge == null
        ) {
            exportError =
                "ReaderLB пока не имеет доступа к локальной библиотеке RanobeLib."
            return
        }

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
                        val exportOptions =
                            LocalExportOptions(
                                firstChapter =
                                    if (
                                        exportUseRange
                                    ) {
                                        exportFirstChapter
                                    } else {
                                        null
                                    },
                                lastChapter =
                                    if (
                                        exportUseRange
                                    ) {
                                        exportLastChapter
                                    } else {
                                        null
                                    },
                                includeCover =
                                    exportIncludeCover,
                                includeImages =
                                    exportIncludeImages
                            )

                        val progressCallback:
                            (
                                LocalExportProgress
                            ) -> Unit = {
                                progress ->
                            scope.launch {
                                exportProgress =
                                    progress
                            }
                        }

                        if (
                            treeUri != null
                        ) {
                            exportManager.export(
                                format =
                                    exportFormat,
                                treeUri =
                                    treeUri,
                                item = item,
                                options =
                                    exportOptions,
                                onProgress =
                                    progressCallback
                            )
                        } else {
                            exportManager.export(
                                format =
                                    exportFormat,
                                privilegedFiles =
                                    requireNotNull(
                                        shizukuBridge
                                    ),
                                item = item,
                                options =
                                    exportOptions,
                                onProgress =
                                    progressCallback
                            )
                        }
                    }

                exportResult =
                    result
            } catch (
                cancelled:
                    CancellationException
            ) {
                exportError =
                    "Экспорт отменён."
            } catch (throwable: Throwable) {
                exportError =
                    throwable.message
                        ?: "Не удалось экспортировать книгу"
            } finally {
                exportBusy = false
                exportJob = null
            }
        }
    }

    fun openExportedFile(
        result: ExportedLocalBookFile
    ) {
        val intent =
            Intent(
                Intent.ACTION_VIEW
            )
                .setDataAndType(
                    result.uri,
                    result.format
                        .mimeType
                )
                .addFlags(
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )

        runCatching {
            context.startActivity(
                intent
            )
        }.onFailure {
            exportError =
                "На устройстве нет приложения для открытия этого формата."
        }
    }

    fun shareExportedFile(
        result: ExportedLocalBookFile
    ) {
        val intent =
            Intent(
                Intent.ACTION_SEND
            )
                .setType(
                    result.format
                        .mimeType
                )
                .putExtra(
                    Intent.EXTRA_STREAM,
                    result.uri
                )
                .addFlags(
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )

        context.startActivity(
            Intent.createChooser(
                intent,
                "Поделиться книгой"
            )
        )
    }

    if (showExport) {
        LocalBookExportDialog(
            item = item,
            selectedFormat =
                exportFormat,
            onFormatSelected = {
                exportFormatIndex =
                    it.ordinal
                exportResult = null
                exportError = null
            },
            useRange =
                exportUseRange,
            onUseRangeChanged = {
                exportUseRange = it
            },
            firstChapter =
                exportFirstChapter,
            onFirstChapterChanged = {
                exportFirstChapter =
                    sanitizeChapterRangeInput(
                        it
                    )
            },
            lastChapter =
                exportLastChapter,
            onLastChapterChanged = {
                exportLastChapter =
                    sanitizeChapterRangeInput(
                        it
                    )
            },
            includeCover =
                exportIncludeCover,
            onIncludeCoverChanged = {
                exportIncludeCover = it
            },
            includeImages =
                exportIncludeImages,
            onIncludeImagesChanged = {
                exportIncludeImages = it
            },
            busy = exportBusy,
            progress =
                exportProgress,
            result = exportResult,
            error = exportError,
            onExport = ::startExport,
            onCancel = {
                exportJob?.cancel()
            },
            onOpen = ::openExportedFile,
            onShare =
                ::shareExportedFile,
            onDismiss = {
                if (!exportBusy) {
                    showExport = false
                    exportResult = null
                    exportError = null
                }
            }
        )
    }


    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = {
                if (!busy) {
                    confirmDelete = false
                }
            },
            title = {
                Text("Удалить новеллу?")
            },
            text = {
                Text(
                    "ReaderLB удалит локальную копию «" +
                        item.title +
                        "» из папки RanobeLib вместе со всеми главами и иллюстрациями. Это действие нельзя отменить."
                )
            },
            confirmButton = {
                TextButton(
                    enabled = !busy,
                    onClick = {
                        if (
                            treeUri == null &&
                            shizukuBridge ==
                                null
                        ) {
                            error =
                                "Доступ к RanobeLib потерян."
                            return@TextButton
                        }

                        busy = true
                        error = null

                        scope.launch {
                            runCatching {
                                withContext(
                                    Dispatchers.IO
                                ) {
                                    if (
                                        treeUri !=
                                        null
                                    ) {
                                        transferManager
                                            .deleteTitle(
                                                treeUri =
                                                    treeUri,
                                                folderName =
                                                    item.folderName
                                            )
                                    } else {
                                        transferManager
                                            .deleteTitle(
                                                bridge =
                                                    requireNotNull(
                                                        shizukuBridge
                                                    ),
                                                folderName =
                                                    item.folderName
                                            )
                                    }
                                }
                            }.onSuccess {
                                confirmDelete =
                                    false
                                onDeleted(
                                    item.slugUrl
                                )
                            }.onFailure {
                                error =
                                    it.message
                                        ?: "Не удалось удалить новеллу"
                            }
                            busy = false
                        }
                    }
                ) {
                    Text(
                        if (busy) {
                            "Удаляю…"
                        } else {
                            "Удалить"
                        },
                        color =
                            Color(
                                0xFFE4777D
                            )
                    )
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !busy,
                    onClick = {
                        confirmDelete = false
                    }
                ) {
                    Text("Отмена")
                }
            }
        )
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding =
            PaddingValues(
                bottom = 28.dp
            ),
        verticalArrangement =
            Arrangement.spacedBy(
                14.dp
            )
    ) {
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(365.dp)
                    .background(
                        MaterialTheme
                            .colorScheme
                            .surface
                    )
            ) {
                if (cover != null) {
                    Image(
                        bitmap =
                            requireNotNull(
                                cover
                            ),
                        contentDescription = null,
                        contentScale =
                            ContentScale.Crop,
                        alpha = 0.28f,
                        modifier =
                            Modifier.fillMaxSize()
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color(
                                        0x44101216
                                    ),
                                    Color(
                                        0xCC101216
                                    ),
                                    Color(
                                        0xFF101216
                                    )
                                )
                            )
                        )
                )

                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .align(
                            Alignment.TopStart
                        )
                        .padding(8.dp)
                ) {
                    Icon(
                        Icons.Default.ArrowBack,
                        contentDescription =
                            "Назад",
                        tint = Ink
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(
                            horizontal = 20.dp,
                            vertical = 18.dp
                        ),
                    horizontalAlignment =
                        Alignment.CenterHorizontally,
                    verticalArrangement =
                        Arrangement.Bottom
                ) {
                    if (cover != null) {
                        Image(
                            bitmap =
                                requireNotNull(
                                    cover
                                ),
                            contentDescription =
                                "Обложка " +
                                    item.title,
                            contentScale =
                                ContentScale.Crop,
                            modifier = Modifier
                                .width(132.dp)
                                .height(186.dp)
                                .clip(
                                    RoundedCornerShape(
                                        10.dp
                                    )
                                )
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .width(132.dp)
                                .height(186.dp)
                                .clip(
                                    RoundedCornerShape(
                                        10.dp
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
                                contentDescription =
                                    null,
                                tint = Color.White,
                                modifier =
                                    Modifier.size(
                                        42.dp
                                    )
                            )
                        }
                    }

                    Text(
                        item.title,
                        color = Ink,
                        fontSize = 21.sp,
                        lineHeight = 26.sp,
                        fontWeight =
                            FontWeight.Bold,
                        textAlign =
                            TextAlign.Center,
                        maxLines = 2,
                        modifier =
                            Modifier.padding(
                                top = 14.dp
                            )
                    )

                    Text(
                        if (
                            item.createdByReaderLB
                        ) {
                            "Импорт ReaderLB"
                        } else {
                            "Скачано в RanobeLib"
                        },
                        color = Muted,
                        fontSize = 13.sp,
                        modifier =
                            Modifier.padding(
                                top = 5.dp
                            )
                    )
                }
            }
        }

        item {
            Card(
                modifier = Modifier
                    .padding(
                        horizontal = 18.dp
                    ),
                colors =
                    CardDefaults.cardColors(
                        containerColor =
                            MaterialTheme
                                .colorScheme
                                .surface
                    ),
                shape =
                    RoundedCornerShape(
                        16.dp
                    ),
                border =
                    androidx.compose.foundation
                        .BorderStroke(
                            1.dp,
                            Line
                        )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement =
                        Arrangement.spacedBy(
                            14.dp
                        )
                ) {
                    Text(
                        "Информация",
                        color = Ink,
                        fontSize = 17.sp,
                        fontWeight =
                            FontWeight.Bold
                    )

                    TitleInfoRow(
                        label = "Главы",
                        value =
                            item.chapterCount
                                .toString()
                    )
                    TitleInfoRow(
                        label = "Диапазон",
                        value =
                            localChapterRangeText(
                                item
                            )
                    )
                    TitleInfoRow(
                        label = "Источник",
                        value =
                            if (
                                item.createdByReaderLB
                            ) {
                                "Импорт ReaderLB"
                            } else {
                                "Скачано в RanobeLib"
                            }
                    )
                    TitleInfoRow(
                        label = "Обновлено",
                        value =
                            formatLocalLibraryTime(
                                item.writeTime
                            )
                    )

                    Text(
                        "ID: " + item.slugUrl,
                        color = Muted,
                        fontSize = 10.sp
                    )
                }
            }
        }

        item {
            Column(
                modifier = Modifier
                    .padding(
                        horizontal = 18.dp
                    ),
                verticalArrangement =
                    Arrangement.spacedBy(
                        10.dp
                    )
            ) {
                OutlineAction(
                    if (exportBusy) {
                        "Экспортирую…"
                    } else {
                        "Экспортировать"
                    },
                    onClick = {
                        if (
                            !busy &&
                            !exportBusy
                        ) {
                            exportResult = null
                            exportError = null
                            exportProgress = null
                            showExport = true
                        }
                    }
                )

                OutlineAction(
                    if (busy) {
                        "Подготавливаю пакет…"
                    } else {
                        "Поделиться новеллой"
                    },
                    onClick = {
                        if (
                            treeUri == null &&
                            shizukuBridge ==
                                null
                        ) {
                            error =
                                "ReaderLB пока не имеет доступа к RanobeLib."
                            return@OutlineAction
                        }
                        if (
                            busy ||
                            exportBusy
                        ) {
                            return@OutlineAction
                        }

                        busy = true
                        error = null
                        scope.launch {
                            runCatching {
                                withContext(
                                    Dispatchers.IO
                                ) {
                                    if (
                                        treeUri !=
                                        null
                                    ) {
                                        transferManager
                                            .createShareUri(
                                                treeUri =
                                                    treeUri,
                                                item =
                                                    item
                                            )
                                    } else {
                                        transferManager
                                            .createShareUri(
                                                bridge =
                                                    requireNotNull(
                                                        shizukuBridge
                                                    ),
                                                item =
                                                    item
                                            )
                                    }
                                }
                            }.onSuccess {
                                    shareUri ->
                                val share =
                                    transferManager
                                        .shareIntent(
                                            uri =
                                                shareUri,
                                            title =
                                                item.title
                                        )
                                context.startActivity(
                                    Intent.createChooser(
                                        share,
                                        "Поделиться новеллой"
                                    )
                                )
                            }.onFailure {
                                error =
                                    it.message
                                        ?: "Не удалось подготовить пакет ReaderLB"
                            }
                            busy = false
                        }
                    }
                )

                DangerAction(
                    text =
                        "Удалить новеллу",
                    enabled =
                        !busy &&
                            !exportBusy &&
                            (
                                treeUri !=
                                    null ||
                                    shizukuBridge !=
                                    null
                                ),
                    onClick = {
                        confirmDelete = true
                    }
                )

                error?.let {
                    StatusCard(
                        message = it,
                        success = false
                    )
                }

                Text(
                    "При отправке ReaderLB создаёт переносимый .readerlb.zip с локальными главами, обложкой и иллюстрациями. На другом телефоне его можно открыть прямо через ReaderLB.",
                    color = Muted,
                    fontSize = 11.sp,
                    lineHeight = 16.sp
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LocalBookExportDialog(
    item: LocalLibraryItem,
    selectedFormat: LocalBookExportFormat,
    onFormatSelected: (
        LocalBookExportFormat
    ) -> Unit,
    useRange: Boolean,
    onUseRangeChanged: (
        Boolean
    ) -> Unit,
    firstChapter: String,
    onFirstChapterChanged: (
        String
    ) -> Unit,
    lastChapter: String,
    onLastChapterChanged: (
        String
    ) -> Unit,
    includeCover: Boolean,
    onIncludeCoverChanged: (
        Boolean
    ) -> Unit,
    includeImages: Boolean,
    onIncludeImagesChanged: (
        Boolean
    ) -> Unit,
    busy: Boolean,
    progress: LocalExportProgress?,
    result: ExportedLocalBookFile?,
    error: String?,
    onExport: () -> Unit,
    onCancel: () -> Unit,
    onOpen: (
        ExportedLocalBookFile
    ) -> Unit,
    onShare: (
        ExportedLocalBookFile
    ) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState =
        rememberModalBottomSheetState(
            skipPartiallyExpanded = true
        )
    val progressFraction =
        progress?.let {
            if (
                it.totalChapters > 0
            ) {
                (
                    it.completedChapters
                        .toFloat() /
                        it.totalChapters
                            .toFloat()
                    )
                    .coerceIn(
                        0f,
                        1f
                    )
            } else {
                null
            }
        }

    ModalBottomSheet(
        onDismissRequest = {
            if (!busy) {
                onDismiss()
            }
        },
        sheetState = sheetState,
        containerColor =
            MaterialTheme
                .colorScheme
                .background
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(
                    max = 650.dp
                ),
            contentPadding =
                PaddingValues(
                    start = 20.dp,
                    end = 20.dp,
                    bottom = 28.dp
                ),
            verticalArrangement =
                Arrangement.spacedBy(
                    12.dp
                )
        ) {
            item {
                Text(
                    if (
                        result == null
                    ) {
                        "Экспортировать книгу"
                    } else {
                        "Книга сохранена"
                    },
                    color = Ink,
                    fontSize = 21.sp,
                    fontWeight =
                        FontWeight.Bold
                )
            }

            if (
                result == null
            ) {
                item {
                    Text(
                        item.title,
                        color = Ink,
                        fontWeight =
                            FontWeight.SemiBold,
                        maxLines = 2
                    )
                    Text(
                        item.chapterCount
                            .toString() +
                            " глав · " +
                            localChapterRangeText(
                                item
                            ),
                        color = Muted,
                        fontSize = 12.sp,
                        modifier =
                            Modifier.padding(
                                top = 4.dp
                            )
                    )
                }

                item {
                    Text(
                        "Формат",
                        color = Ink,
                        fontWeight =
                            FontWeight.SemiBold
                    )
                }

                item {
                    Row(
                        modifier =
                            Modifier.fillMaxWidth(),
                        horizontalArrangement =
                            Arrangement.spacedBy(
                                8.dp
                            )
                    ) {
                        ExportFormatChoice(
                            modifier =
                                Modifier.weight(
                                    1f
                                ),
                            format =
                                LocalBookExportFormat
                                    .EPUB,
                            selected =
                                selectedFormat ==
                                    LocalBookExportFormat
                                        .EPUB,
                            subtitle =
                                "Рекомендуется",
                            enabled = !busy,
                            onClick =
                                onFormatSelected
                        )
                        ExportFormatChoice(
                            modifier =
                                Modifier.weight(
                                    1f
                                ),
                            format =
                                LocalBookExportFormat
                                    .PDF,
                            selected =
                                selectedFormat ==
                                    LocalBookExportFormat
                                        .PDF,
                            subtitle =
                                "Для чтения",
                            enabled = !busy,
                            onClick =
                                onFormatSelected
                        )
                    }
                }

                item {
                    Row(
                        modifier =
                            Modifier.fillMaxWidth(),
                        horizontalArrangement =
                            Arrangement.spacedBy(
                                8.dp
                            )
                    ) {
                        ExportFormatChoice(
                            modifier =
                                Modifier.weight(
                                    1f
                                ),
                            format =
                                LocalBookExportFormat
                                    .FB2,
                            selected =
                                selectedFormat ==
                                    LocalBookExportFormat
                                        .FB2,
                            subtitle =
                                "Для читалок",
                            enabled = !busy,
                            onClick =
                                onFormatSelected
                        )
                        ExportFormatChoice(
                            modifier =
                                Modifier.weight(
                                    1f
                                ),
                            format =
                                LocalBookExportFormat
                                    .TXT,
                            selected =
                                selectedFormat ==
                                    LocalBookExportFormat
                                        .TXT,
                            subtitle =
                                "Простой текст",
                            enabled = !busy,
                            onClick =
                                onFormatSelected
                        )
                    }
                }

                item {
                    Divider()
                }

                item {
                    Row(
                        modifier =
                            Modifier.fillMaxWidth(),
                        verticalAlignment =
                            Alignment.CenterVertically
                    ) {
                        Column(
                            Modifier.weight(
                                1f
                            )
                        ) {
                            Text(
                                "Диапазон глав",
                                color = Ink,
                                fontWeight =
                                    FontWeight.SemiBold
                            )
                            Text(
                                if (
                                    useRange
                                ) {
                                    "Экспортировать только выбранные главы."
                                } else {
                                    "Экспортировать все локальные главы."
                                },
                                color = Muted,
                                fontSize = 12.sp,
                                lineHeight = 17.sp
                            )
                        }
                        Switch(
                            checked =
                                useRange,
                            enabled = !busy,
                            onCheckedChange =
                                onUseRangeChanged
                        )
                    }
                }

                if (useRange) {
                    item {
                        OutlinedTextField(
                            value =
                                firstChapter,
                            onValueChange =
                                onFirstChapterChanged,
                            enabled = !busy,
                            label = {
                                Text(
                                    "С главы"
                                )
                            },
                            singleLine = true,
                            keyboardOptions =
                                KeyboardOptions(
                                    keyboardType =
                                        KeyboardType
                                            .Decimal
                                ),
                            modifier =
                                Modifier.fillMaxWidth()
                        )
                    }

                    item {
                        OutlinedTextField(
                            value =
                                lastChapter,
                            onValueChange =
                                onLastChapterChanged,
                            enabled = !busy,
                            label = {
                                Text(
                                    "По главу"
                                )
                            },
                            singleLine = true,
                            keyboardOptions =
                                KeyboardOptions(
                                    keyboardType =
                                        KeyboardType
                                            .Decimal
                                ),
                            modifier =
                                Modifier.fillMaxWidth()
                        )
                    }
                }

                item {
                    Divider()
                }

                item {
                    ExportOptionSwitch(
                        title = "Обложка",
                        description =
                            if (
                                selectedFormat ==
                                    LocalBookExportFormat
                                        .TXT
                            ) {
                                "TXT не хранит обложку."
                            } else {
                                "Добавить обложку в книгу."
                            },
                        checked =
                            includeCover &&
                                selectedFormat !=
                                    LocalBookExportFormat
                                        .TXT,
                        enabled =
                            !busy &&
                                selectedFormat !=
                                    LocalBookExportFormat
                                        .TXT,
                        onCheckedChange =
                            onIncludeCoverChanged
                    )
                }

                item {
                    ExportOptionSwitch(
                        title = "Иллюстрации",
                        description =
                            if (
                                selectedFormat ==
                                    LocalBookExportFormat
                                        .TXT
                            ) {
                                "В TXT изображения отмечаются текстовыми подписями."
                            } else {
                                "Сохранить встроенные иллюстрации книги."
                            },
                        checked =
                            includeImages,
                        enabled = !busy,
                        onCheckedChange =
                            onIncludeImagesChanged
                    )
                }

                item {
                    Card(
                        colors =
                            CardDefaults.cardColors(
                                containerColor =
                                    MaterialTheme
                                        .colorScheme
                                        .surface
                            ),
                        shape =
                            RoundedCornerShape(
                                12.dp
                            ),
                        border =
                            androidx.compose.foundation
                                .BorderStroke(
                                    1.dp,
                                    Line
                                )
                    ) {
                        Text(
                            (
                                if (
                                    useRange
                                ) {
                                    "Главы " +
                                        firstChapter +
                                        "–" +
                                        lastChapter
                                } else {
                                    item.chapterCount
                                        .toString() +
                                        " глав"
                                }
                                ) +
                                " · " +
                                selectedFormat
                                    .displayName +
                                " · " +
                                if (
                                    includeImages
                                ) {
                                    "с иллюстрациями"
                                } else {
                                    "без иллюстраций"
                                },
                            color = Ink,
                            fontSize = 12.sp,
                            lineHeight = 17.sp,
                            modifier =
                                Modifier.padding(
                                    horizontal = 12.dp,
                                    vertical = 10.dp
                                )
                        )
                    }
                }

                if (busy) {
                    item {
                        Column(
                            verticalArrangement =
                                Arrangement.spacedBy(
                                    7.dp
                                )
                        ) {
                            Text(
                                "Экспортирую " +
                                    selectedFormat
                                        .displayName,
                                color = Ink,
                                fontWeight =
                                    FontWeight.SemiBold
                            )

                            if (
                                progressFraction !=
                                null
                            ) {
                                LinearProgressIndicator(
                                    progress = {
                                        progressFraction
                                    },
                                    modifier =
                                        Modifier.fillMaxWidth(),
                                    color = Blue
                                )
                            } else {
                                LinearProgressIndicator(
                                    modifier =
                                        Modifier.fillMaxWidth(),
                                    color = Blue
                                )
                            }

                            if (
                                progress !=
                                null
                            ) {
                                Text(
                                    "Глава " +
                                        progress
                                            .completedChapters +
                                        " из " +
                                        progress
                                            .totalChapters,
                                    color = Muted,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }

                error?.let {
                    item {
                        StatusCard(
                            message = it,
                            success = false
                        )
                    }
                }

                item {
                    if (busy) {
                        OutlineAction(
                            text = "Отменить",
                            onClick = onCancel
                        )
                    } else {
                        GradientButton(
                            text =
                                "Сохранить " +
                                    selectedFormat
                                        .displayName,
                            onClick = onExport
                        )
                    }
                }

                if (!busy) {
                    item {
                        Box(
                            modifier =
                                Modifier.fillMaxWidth(),
                            contentAlignment =
                                Alignment.Center
                        ) {
                            TextButton(
                                onClick =
                                    onDismiss
                            ) {
                                Text("Закрыть")
                            }
                        }
                    }
                }
            } else {
                item {
                    Row(
                        verticalAlignment =
                            Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription =
                                null,
                            tint = Success,
                            modifier =
                                Modifier.size(
                                    26.dp
                                )
                        )
                        Column(
                            modifier =
                                Modifier.padding(
                                    start = 10.dp
                                )
                        ) {
                            Text(
                                result.displayName,
                                color = Ink,
                                fontWeight =
                                    FontWeight.SemiBold
                            )
                            Text(
                                result.chapterCount
                                    .toString() +
                                    " глав · " +
                                    result.format
                                        .displayName +
                                    " · Downloads/ReaderLB",
                                color = Muted,
                                fontSize = 12.sp,
                                lineHeight = 17.sp
                            )
                        }
                    }
                }

                if (
                    result.warningCount > 0
                ) {
                    item {
                        Card(
                            colors =
                                CardDefaults.cardColors(
                                    containerColor =
                                        MaterialTheme
                                            .colorScheme
                                            .surface
                                ),
                            shape =
                                RoundedCornerShape(
                                    12.dp
                                ),
                            border =
                                androidx.compose.foundation
                                    .BorderStroke(
                                        1.dp,
                                        Color(
                                            0xFFE1B95B
                                        )
                                    )
                        ) {
                            Column(
                                modifier =
                                    Modifier.padding(
                                        12.dp
                                    ),
                                verticalArrangement =
                                    Arrangement.spacedBy(
                                        5.dp
                                    )
                            ) {
                                Text(
                                    "Экспорт завершён с предупреждениями: " +
                                        result.warningCount,
                                    color =
                                        Color(
                                            0xFFE1B95B
                                        ),
                                    fontWeight =
                                        FontWeight.SemiBold,
                                    fontSize = 12.sp
                                )
                                result.warnings
                                    .take(4)
                                    .forEach {
                                            warning ->
                                        Text(
                                            "• " +
                                                warning,
                                            color = Muted,
                                            fontSize =
                                                11.sp,
                                            lineHeight =
                                                15.sp
                                        )
                                    }
                                if (
                                    result.warningCount >
                                    result.warnings
                                        .take(4)
                                        .size
                                ) {
                                    Text(
                                        "Показаны первые предупреждения.",
                                        color = Muted,
                                        fontSize = 10.sp
                                    )
                                }
                            }
                        }
                    }
                }

                item {
                    OutlineAction(
                        text = "Открыть",
                        onClick = {
                            onOpen(
                                result
                            )
                        }
                    )
                }

                item {
                    OutlineAction(
                        text = "Поделиться",
                        onClick = {
                            onShare(
                                result
                            )
                        }
                    )
                }

                error?.let {
                    item {
                        StatusCard(
                            message = it,
                            success = false
                        )
                    }
                }

                item {
                    GradientButton(
                        text = "Готово",
                        onClick = onDismiss
                    )
                }
            }
        }
    }
}

@Composable
private fun ExportFormatChoice(
    modifier: Modifier,
    format: LocalBookExportFormat,
    selected: Boolean,
    subtitle: String,
    enabled: Boolean,
    onClick: (
        LocalBookExportFormat
    ) -> Unit
) {
    Card(
        modifier = modifier
            .clip(
                RoundedCornerShape(
                    12.dp
                )
            )
            .clickable(
                enabled = enabled,
                onClick = {
                    onClick(format)
                }
            ),
        colors =
            CardDefaults.cardColors(
                containerColor =
                    if (selected) {
                        MaterialTheme
                            .colorScheme
                            .primaryContainer
                    } else {
                        MaterialTheme
                            .colorScheme
                            .surface
                    }
            ),
        border =
            androidx.compose.foundation
                .BorderStroke(
                    1.dp,
                    if (selected) {
                        Blue
                    } else {
                        Line
                    }
                ),
        shape =
            RoundedCornerShape(
                12.dp
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = 12.dp,
                    vertical = 11.dp
                )
        ) {
            Text(
                format.displayName,
                color =
                    if (selected) {
                        Blue
                    } else {
                        Ink
                    },
                fontWeight =
                    FontWeight.Bold
            )
            Text(
                subtitle,
                color = Muted,
                fontSize = 10.sp,
                modifier =
                    Modifier.padding(
                        top = 2.dp
                    )
            )
        }
    }
}

@Composable
private fun ExportOptionSwitch(
    title: String,
    description: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (
        Boolean
    ) -> Unit
) {
    Row(
        modifier =
            Modifier.fillMaxWidth(),
        verticalAlignment =
            Alignment.CenterVertically
    ) {
        Column(
            Modifier.weight(
                1f
            )
        ) {
            Text(
                title,
                color = Ink,
                fontWeight =
                    FontWeight.SemiBold
            )
            Text(
                description,
                color = Muted,
                fontSize = 12.sp,
                lineHeight = 17.sp
            )
        }
        Switch(
            checked = checked,
            enabled = enabled,
            onCheckedChange =
                onCheckedChange
        )
    }
}

@Composable
private fun TitleInfoRow(
    label: String,
    value: String
) {
    Row(
        modifier =
            Modifier.fillMaxWidth(),
        verticalAlignment =
            Alignment.CenterVertically
    ) {
        Text(
            label,
            color = Muted,
            fontSize = 12.sp,
            modifier =
                Modifier.weight(1f)
        )
        Text(
            value,
            color = Ink,
            fontSize = 13.sp,
            fontWeight =
                FontWeight.SemiBold,
            textAlign =
                TextAlign.End
        )
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

                    Box(
                        modifier = Modifier
                            .clip(
                                RoundedCornerShape(
                                    99.dp
                                )
                            )
                            .background(
                                MaterialTheme
                                    .colorScheme
                                    .primaryContainer
                            )
                            .padding(
                                horizontal = 7.dp,
                                vertical = 2.dp
                            )
                    ) {
                        Text(
                            if (
                                item.createdByReaderLB
                            ) {
                                "Импорт ReaderLB"
                            } else {
                                "Скачано в RanobeLib"
                            },
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
                        .clip(
                            RoundedCornerShape(
                                99.dp
                            )
                        )
                        .background(
                            if (
                                item.installedDirectly
                            ) {
                                MaterialTheme
                                    .colorScheme
                                    .tertiaryContainer
                            } else {
                                MaterialTheme
                                    .colorScheme
                                    .primaryContainer
                            }
                        )
                        .padding(
                            horizontal = 9.dp,
                            vertical = 4.dp
                        )
                ) {
                    val statusText =
                        when {
                            item.updatedExisting &&
                                item.addedChapterCount > 0 ->
                                "Обновлено +${item.addedChapterCount}"

                            item.updatedExisting ->
                                "Уже актуально"

                            item.installedDirectly ->
                                "В RanobeLib"

                            else ->
                                "ZIP подготовлен"
                        }

                    Row(
                        verticalAlignment =
                            Alignment.CenterVertically
                    ) {
                        if (
                            item.installedDirectly
                        ) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription =
                                    null,
                                tint =
                                    MaterialTheme
                                        .colorScheme
                                        .onTertiaryContainer,
                                modifier =
                                    Modifier.size(
                                        14.dp
                                    )
                            )
                            Spacer(
                                Modifier.width(
                                    4.dp
                                )
                            )
                        }

                        Text(
                            statusText,
                            color =
                                if (
                                    item.installedDirectly
                                ) {
                                    MaterialTheme
                                        .colorScheme
                                        .onTertiaryContainer
                                } else {
                                    Blue
                                },
                            fontSize = 11.sp,
                            fontWeight =
                                FontWeight.SemiBold
                        )
                    }
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
            containerColor =
                MaterialTheme
                    .colorScheme
                    .primaryContainer
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
                "Новая стабильная версия готова. " +
                    "ReaderLB скачает APK, проверит его " +
                    "целостность и подпись перед установкой.",
                color = Muted,
                fontSize = 12.sp,
                lineHeight = 17.sp
            )
            OutlineAction(
                if (busy) {
                    "Обновление открыто"
                } else {
                    "Посмотреть обновление"
                },
                onUpdate
            )
        }
    }
}

@Composable
private fun ReaderLbUpdateDialog(
    info: UpdateInfo,
    busy: Boolean,
    progress: UpdateDownloadProgress?,
    readyToInstall: Boolean,
    message: String?,
    onDownload: () -> Unit,
    onInstall: () -> Unit,
    onCancel: () -> Unit,
    onDismiss: () -> Unit
) {
    val notes =
        updateNotesPreview(
            info.notes
        )
    val sizeText =
        formatUpdateFileSize(
            info.sizeBytes
        )
    val verifying =
        progress?.stage ==
            UpdateDownloadStage.VERIFYING
    val fraction =
        progress?.fraction

    AlertDialog(
        onDismissRequest = {
            if (!busy) {
                onDismiss()
            }
        },
        title = {
            Row(
                verticalAlignment =
                    Alignment.CenterVertically
            ) {
                ReaderLogo(52.dp)
                Spacer(
                    Modifier.width(12.dp)
                )
                Column {
                    Text(
                        "Обновление ReaderLB",
                        color = Ink,
                        fontWeight =
                            FontWeight.Bold
                    )
                    Text(
                        "Доступна версия " +
                            info.versionName,
                        color = Blue,
                        fontSize = 13.sp,
                        fontWeight =
                            FontWeight.SemiBold
                    )
                }
            }
        },
        text = {
            Column(
                verticalArrangement =
                    Arrangement.spacedBy(
                        12.dp
                    )
            ) {
                Card(
                    colors =
                        CardDefaults.cardColors(
                            containerColor =
                                MaterialTheme
                                    .colorScheme
                                    .surface
                        ),
                    shape =
                        RoundedCornerShape(
                            14.dp
                        ),
                    border =
                        androidx.compose
                            .foundation
                            .BorderStroke(
                                1.dp,
                                Line
                            )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement =
                            Arrangement.spacedBy(
                                6.dp
                            )
                    ) {
                        Text(
                            "Установлена версия " +
                                BuildConfig.VERSION_NAME,
                            color = Muted,
                            fontSize = 12.sp
                        )
                        Text(
                            "Новая версия " +
                                info.versionName,
                            color = Ink,
                            fontWeight =
                                FontWeight.SemiBold
                        )
                        if (
                            sizeText != null
                        ) {
                            Text(
                                "Размер APK " +
                                    sizeText,
                                color = Muted,
                                fontSize = 12.sp
                            )
                        }
                    }
                }

                if (
                    notes.isNotBlank() &&
                    !busy
                ) {
                    Text(
                        "Что нового",
                        color = Ink,
                        fontWeight =
                            FontWeight.SemiBold
                    )
                    Text(
                        notes,
                        color = Muted,
                        fontSize = 12.sp,
                        lineHeight = 18.sp
                    )
                }

                if (busy) {
                    Text(
                        if (verifying) {
                            "Проверяю обновление"
                        } else {
                            "Скачиваю обновление"
                        },
                        color = Ink,
                        fontWeight =
                            FontWeight.SemiBold
                    )

                    if (
                        !verifying &&
                        fraction != null
                    ) {
                        LinearProgressIndicator(
                            progress = {
                                fraction
                            },
                            modifier =
                                Modifier.fillMaxWidth(),
                            color = Blue
                        )
                        Text(
                            "Загружено " +
                                ((fraction * 100f)
                                    .toInt()
                                    .coerceIn(
                                        0,
                                        100
                                    )) +
                                "%",
                            color = Muted,
                            fontSize = 12.sp
                        )
                    } else {
                        LinearProgressIndicator(
                            modifier =
                                Modifier.fillMaxWidth(),
                            color = Blue
                        )
                    }

                    if (verifying) {
                        Text(
                            "Проверяю SHA-256, пакет ReaderLB и сертификат подписи.",
                            color = Muted,
                            fontSize = 12.sp,
                            lineHeight = 17.sp
                        )
                    }
                }

                if (
                    readyToInstall &&
                    !busy
                ) {
                    Row(
                        verticalAlignment =
                            Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription =
                                null,
                            tint = Success,
                            modifier =
                                Modifier.size(
                                    20.dp
                                )
                        )
                        Text(
                            "APK скачан и проверен. Можно устанавливать.",
                            color = Ink,
                            fontSize = 12.sp,
                            lineHeight = 17.sp,
                            modifier =
                                Modifier.padding(
                                    start = 8.dp
                                )
                        )
                    }
                }

                message?.let {
                    Text(
                        it,
                        color =
                            if (
                                readyToInstall
                            ) {
                                Muted
                            } else {
                                MaterialTheme
                                    .colorScheme
                                    .error
                            },
                        fontSize = 12.sp,
                        lineHeight = 17.sp
                    )
                }
            }
        },
        confirmButton = {
            when {
                busy -> {
                    TextButton(
                        onClick = onCancel
                    ) {
                        Text("Отменить")
                    }
                }

                readyToInstall -> {
                    TextButton(
                        onClick = onInstall
                    ) {
                        Text("Установить")
                    }
                }

                else -> {
                    TextButton(
                        onClick = onDownload
                    ) {
                        Text(
                            "Скачать обновление"
                        )
                    }
                }
            }
        },
        dismissButton = {
            if (!busy) {
                TextButton(
                    onClick = onDismiss
                ) {
                    Text("Позже")
                }
            }
        }
    )
}

private fun formatUpdateFileSize(
    bytes: Long?
): String? {
    val value =
        bytes?.takeIf { it > 0L }
            ?: return null
    val megabyte =
        1024.0 * 1024.0

    return if (
        value >= megabyte
    ) {
        String.format(
            Locale.getDefault(),
            "%.1f МБ",
            value / megabyte
        )
    } else {
        val kilobyte = 1024.0
        String.format(
            Locale.getDefault(),
            "%.0f КБ",
            value / kilobyte
        )
    }
}

private fun updateNotesPreview(
    raw: String
): String =
    raw.lineSequence()
        .map(String::trim)
        .filter(String::isNotBlank)
        .map { line ->
            line
                .replace(
                    Regex(
                        """^#{1,6}\s*"""
                    ),
                    ""
                )
                .replace(
                    Regex(
                        """^[-*]\s+"""
                    ),
                    ""
                )
                .replace(
                    Regex(
                        """\[([^]]+)]\([^)]+\)"""
                    ),
                    "$1"
                )
                .trim()
        }
        .filter {
            it.isNotBlank() &&
                !it.startsWith(
                    "Full Changelog",
                    ignoreCase = true
                )
        }
        .take(4)
        .joinToString("\n")

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
private fun DangerAction(
    text: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                1.dp,
                Color(0xFFD8656D),
                RoundedCornerShape(
                    12.dp
                )
            )
            .clip(
                RoundedCornerShape(
                    12.dp
                )
            )
            .clickable(
                enabled = enabled,
                onClick = onClick
            )
            .padding(
                vertical = 12.dp
            ),
        contentAlignment =
            Alignment.Center
    ) {
        Text(
            text,
            color =
                if (enabled) {
                    Color(
                        0xFFE4777D
                    )
                } else {
                    Muted
                },
            fontWeight =
                FontWeight.Bold
        )
    }
}

@Composable
private fun GradientButton(
    text: String,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null,
    trailingIcon: ImageVector? = null,
    onClick: () -> Unit
) {
    val colors =
        if (enabled) {
            listOf(
                Blue,
                Color(
                    0xFF09A8F2
                )
            )
        } else {
            listOf(
                Color(
                    0xFF3C4652
                ),
                Color(
                    0xFF46515E
                )
            )
        }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp)
            .clip(
                RoundedCornerShape(
                    15.dp
                )
            )
            .background(
                Brush.horizontalGradient(
                    colors
                )
            )
            .clickable(
                enabled = enabled,
                onClick = onClick
            ),
        contentAlignment =
            Alignment.Center
    ) {
        Row(
            verticalAlignment =
                Alignment.CenterVertically,
            horizontalArrangement =
                Arrangement.spacedBy(
                    8.dp
                )
        ) {
            if (leadingIcon != null) {
                Icon(
                    imageVector =
                        leadingIcon,
                    contentDescription =
                        null,
                    tint = Color.White,
                    modifier =
                        Modifier.size(
                            20.dp
                        )
                )
            }

            Text(
                text,
                color = Color.White,
                fontWeight =
                    FontWeight.Bold,
                fontSize = 17.sp
            )

            if (trailingIcon != null) {
                Icon(
                    imageVector =
                        trailingIcon,
                    contentDescription =
                        null,
                    tint = Color.White,
                    modifier =
                        Modifier.size(
                            20.dp
                        )
                )
            }
        }
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