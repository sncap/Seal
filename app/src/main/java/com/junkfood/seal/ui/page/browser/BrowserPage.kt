package com.junkfood.seal.ui.page.browser

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.Uri
import android.util.Patterns
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DesktopWindows
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.accompanist.web.AccompanistWebChromeClient
import com.google.accompanist.web.AccompanistWebViewClient
import com.google.accompanist.web.WebView
import com.google.accompanist.web.rememberWebViewNavigator
import com.google.accompanist.web.rememberWebViewState
import com.junkfood.seal.R
import org.koin.androidx.compose.koinViewModel

private val MEDIA_EXTENSIONS = setOf("mp4", "m3u8", "webm", "mkv", "ts", "mpd", "avi", "mov")
private val MEDIA_KEYWORDS = listOf("videoplayback", "mime=video", "manifest.m3u8", "/video/mp4")

private fun isMediaUrl(url: String): Boolean {
    val lower = url.lowercase()
    val path = lower.substringBefore("?")
    if (MEDIA_EXTENSIONS.any { path.endsWith(".$it") }) return true
    if (MEDIA_KEYWORDS.any { lower.contains(it) }) return true
    return false
}

private fun normalizeUrl(input: String): String {
    val trimmed = input.trim()
    return when {
        trimmed.startsWith("http://") || trimmed.startsWith("https://") -> trimmed
        Patterns.WEB_URL.matcher(trimmed).matches() -> "https://$trimmed"
        else -> "https://www.google.com/search?q=${Uri.encode(trimmed)}"
    }
}

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserPage(
    viewModel: BrowserViewModel = koinViewModel(),
    onDownloadUrl: (String) -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current

    var urlInput by rememberSaveable { mutableStateOf(BROWSER_HOME_URL) }
    var isEditingUrl by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }

    val webViewState = rememberWebViewState(BROWSER_HOME_URL)
    val navigator = rememberWebViewNavigator()

    // Track whether desktop mode changed after initial composition to avoid spurious reload
    var desktopModeInitialized by remember { mutableStateOf(false) }
    LaunchedEffect(uiState.isDesktopMode) {
        if (!desktopModeInitialized) {
            desktopModeInitialized = true
            return@LaunchedEffect
        }
        webViewState.webView?.settings?.userAgentString =
            if (uiState.isDesktopMode) DESKTOP_USER_AGENT else null
        navigator.reload()
    }

    // Sync URL bar text with actual page URL (only when not typing)
    LaunchedEffect(webViewState.lastLoadedUrl) {
        if (!isEditingUrl) {
            urlInput = webViewState.lastLoadedUrl ?: urlInput
        }
    }

    val webViewClient = remember(viewModel) {
        object : AccompanistWebViewClient() {
            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                viewModel.clearDetectedVideos()
            }

            override fun shouldInterceptRequest(
                view: WebView,
                request: WebResourceRequest,
            ): WebResourceResponse? {
                val url = request.url.toString()
                if (isMediaUrl(url)) viewModel.onVideoDetected(url)
                return super.shouldInterceptRequest(view, request)
            }
        }
    }
    val webViewChromeClient = remember { AccompanistWebChromeClient() }

    Scaffold(
        topBar = {
            BrowserTopBar(
                urlInput = urlInput,
                isLoading = webViewState.isLoading,
                isDesktopMode = uiState.isDesktopMode,
                isBookmarked = uiState.bookmarks.any { it.url == (webViewState.lastLoadedUrl ?: "") },
                canGoBack = navigator.canGoBack,
                canGoForward = navigator.canGoForward,
                showMenu = showMenu,
                onUrlChange = { urlInput = it },
                onUrlFocusChange = { isEditingUrl = it },
                onNavigate = { url ->
                    focusManager.clearFocus()
                    isEditingUrl = false
                    navigator.loadUrl(normalizeUrl(url))
                },
                onBack = { navigator.navigateBack() },
                onForward = { navigator.navigateForward() },
                onRefresh = { navigator.reload() },
                onHome = {
                    urlInput = BROWSER_HOME_URL
                    navigator.loadUrl(BROWSER_HOME_URL)
                },
                onToggleBookmark = {
                    viewModel.toggleBookmark(
                        title = webViewState.pageTitle ?: "",
                        url = webViewState.lastLoadedUrl ?: "",
                    )
                },
                onToggleDesktopMode = { viewModel.toggleDesktopMode() },
                onShowBookmarks = { viewModel.showBookmarks() },
                onMenuExpandChange = { showMenu = it },
            )
        },
        floatingActionButton = {
            if (uiState.detectedVideoUrls.isNotEmpty()) {
                FloatingActionButton(onClick = { viewModel.showVideoSheet() }) {
                    Icon(Icons.Outlined.VideoLibrary, stringResource(R.string.download))
                }
            }
        },
    ) { paddingValues ->
        WebView(
            state = webViewState,
            navigator = navigator,
            client = webViewClient,
            chromeClient = webViewChromeClient,
            modifier = Modifier.padding(paddingValues).fillMaxSize(),
            captureBackPresses = true,
            factory = { context ->
                WebView(context).apply {
                    settings.run {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        javaScriptCanOpenWindowsAutomatically = true
                        if (uiState.isDesktopMode) userAgentString = DESKTOP_USER_AGENT
                    }
                }
            },
        )
    }

    // Detected video URLs sheet
    if (uiState.showVideoSheet) {
        ModalBottomSheet(
            onDismissRequest = { viewModel.hideVideoSheet() },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            DetectedVideosSheet(
                urls = uiState.detectedVideoUrls.toList(),
                onDownload = { url ->
                    viewModel.hideVideoSheet()
                    onDownloadUrl(url)
                },
            )
        }
    }

    // Bookmarks sheet
    if (uiState.showBookmarksSheet) {
        ModalBottomSheet(
            onDismissRequest = { viewModel.hideBookmarks() },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            BookmarksSheet(
                bookmarks = uiState.bookmarks,
                onBookmarkClick = { url ->
                    viewModel.hideBookmarks()
                    urlInput = url
                    navigator.loadUrl(url)
                },
                onBookmarkDelete = { bookmark ->
                    viewModel.toggleBookmark(bookmark.title, bookmark.url)
                },
            )
        }
    }
}

@Composable
private fun BrowserTopBar(
    urlInput: String,
    isLoading: Boolean,
    isDesktopMode: Boolean,
    isBookmarked: Boolean,
    canGoBack: Boolean,
    canGoForward: Boolean,
    showMenu: Boolean,
    onUrlChange: (String) -> Unit,
    onUrlFocusChange: (Boolean) -> Unit,
    onNavigate: (String) -> Unit,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onRefresh: () -> Unit,
    onHome: () -> Unit,
    onToggleBookmark: () -> Unit,
    onToggleDesktopMode: () -> Unit,
    onShowBookmarks: () -> Unit,
    onMenuExpandChange: (Boolean) -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
    ) {
        Column(modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars)) {
            // URL bar row
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack, enabled = canGoBack) {
                    Icon(
                        Icons.AutoMirrored.Outlined.ArrowBack,
                        stringResource(R.string.back),
                        tint =
                            if (canGoBack) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                    )
                }
                IconButton(onClick = onForward, enabled = canGoForward) {
                    Icon(
                        Icons.AutoMirrored.Outlined.ArrowForward,
                        contentDescription = null,
                        tint =
                            if (canGoForward) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                    )
                }
                TextField(
                    value = urlInput,
                    onValueChange = onUrlChange,
                    modifier =
                        Modifier.weight(1f).onFocusChanged { onUrlFocusChange(it.isFocused) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { onNavigate(urlInput) }),
                    textStyle = MaterialTheme.typography.bodySmall,
                    colors =
                        TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                        ),
                    shape = MaterialTheme.shapes.medium,
                )
                IconButton(onClick = onToggleBookmark) {
                    Icon(
                        if (isBookmarked) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                        contentDescription = null,
                        tint =
                            if (isBookmarked) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface,
                    )
                }
                Box {
                    IconButton(onClick = { onMenuExpandChange(true) }) {
                        Icon(Icons.Outlined.MoreVert, contentDescription = null)
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { onMenuExpandChange(false) },
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.browser_refresh)) },
                            leadingIcon = { Icon(Icons.Outlined.Refresh, null) },
                            onClick = {
                                onMenuExpandChange(false)
                                onRefresh()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.browser_home)) },
                            leadingIcon = { Icon(Icons.Outlined.Home, null) },
                            onClick = {
                                onMenuExpandChange(false)
                                onHome()
                            },
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = {
                                Text(
                                    stringResource(
                                        if (isDesktopMode) R.string.browser_mobile_mode
                                        else R.string.browser_desktop_mode
                                    )
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    if (isDesktopMode) Icons.Outlined.PhoneAndroid
                                    else Icons.Outlined.DesktopWindows,
                                    null,
                                )
                            },
                            onClick = {
                                onMenuExpandChange(false)
                                onToggleDesktopMode()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.bookmarks)) },
                            leadingIcon = { Icon(Icons.Outlined.Bookmark, null) },
                            onClick = {
                                onMenuExpandChange(false)
                                onShowBookmarks()
                            },
                        )
                    }
                }
            }
            // Loading indicator
            if (isLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(2.dp))
            } else {
                Spacer(modifier = Modifier.height(2.dp))
            }
        }
    }
}

@Composable
private fun DetectedVideosSheet(urls: List<String>, onDownload: (String) -> Unit) {
    Column(modifier = Modifier.padding(bottom = 16.dp)) {
        Text(
            text = stringResource(R.string.browser_detected_videos),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )
        HorizontalDivider()
        LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
            items(urls) { url ->
                ListItem(
                    headlineContent = {
                        Text(
                            url.substringAfterLast("/").substringBefore("?").ifEmpty { url },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    supportingContent = {
                        Text(url, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    },
                    trailingContent = {
                        TextButton(onClick = { onDownload(url) }) {
                            Icon(
                                Icons.Outlined.Download,
                                null,
                                modifier = Modifier.size(18.dp),
                            )
                            Text(stringResource(R.string.download), modifier = Modifier.padding(start = 4.dp))
                        }
                    },
                    modifier = Modifier.clickable { onDownload(url) },
                )
            }
        }
    }
}

@Composable
private fun BookmarksSheet(
    bookmarks: List<BrowserBookmark>,
    onBookmarkClick: (String) -> Unit,
    onBookmarkDelete: (BrowserBookmark) -> Unit,
) {
    Column(modifier = Modifier.padding(bottom = 16.dp)) {
        Text(
            text = stringResource(R.string.bookmarks),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )
        HorizontalDivider()
        if (bookmarks.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().height(120.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    stringResource(R.string.browser_no_bookmarks),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
                items(bookmarks) { bookmark ->
                    ListItem(
                        headlineContent = {
                            Text(bookmark.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        },
                        supportingContent = {
                            Text(
                                bookmark.url,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        trailingContent = {
                            IconButton(onClick = { onBookmarkDelete(bookmark) }) {
                                Icon(
                                    Icons.Outlined.Delete,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                )
                            }
                        },
                        modifier = Modifier.clickable { onBookmarkClick(bookmark.url) },
                    )
                }
            }
        }
    }
}
