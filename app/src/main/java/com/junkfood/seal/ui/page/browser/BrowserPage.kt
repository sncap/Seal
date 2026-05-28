package com.junkfood.seal.ui.page.browser

import android.annotation.SuppressLint
import android.net.Uri
import android.util.Patterns
import android.webkit.WebView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DesktopWindows
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
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
import androidx.compose.ui.text.font.FontWeight
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

private fun normalizeUrl(input: String): String {
    val trimmed = input.trim()
    return when {
        trimmed.startsWith("http://") || trimmed.startsWith("https://") -> trimmed
        Patterns.WEB_URL.matcher(trimmed).matches() -> "https://$trimmed"
        else -> "https://www.google.com/search?q=${Uri.encode(trimmed)}"
    }
}

@Composable
fun BrowserPage(
    viewModel: BrowserViewModel = koinViewModel(),
    onDownloadUrl: (String) -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val maxTabsMessage = stringResource(R.string.browser_max_tabs, MAX_TABS)

    LaunchedEffect(uiState.showMaxTabsSnackbar) {
        if (uiState.showMaxTabsSnackbar) {
            snackbarHostState.showSnackbar(maxTabsMessage)
            viewModel.dismissMaxTabsSnackbar()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Keyed by active tab ID — destroys and recreates WebView state on tab switch
        key(uiState.activeTab.id) {
            BrowserTabContent(
                viewModel = viewModel,
                uiState = uiState,
                snackbarHostState = snackbarHostState,
                onDownloadUrl = onDownloadUrl,
            )
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }

    // Tabs sheet is outside the key so it persists while open
    if (uiState.showTabsSheet) {
        TabsSheet(
            tabs = uiState.tabs,
            activeTabId = uiState.activeTab.id,
            onTabClick = { viewModel.switchToTab(it) },
            onTabClose = { viewModel.closeTab(it) },
            onNewTab = { viewModel.addTab() },
            onDismiss = { viewModel.hideTabsSheet() },
        )
    }
}

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BrowserTabContent(
    viewModel: BrowserViewModel,
    uiState: BrowserUiState,
    snackbarHostState: SnackbarHostState,
    onDownloadUrl: (String) -> Unit,
) {
    val focusManager = LocalFocusManager.current
    val activeTab = uiState.activeTab

    var urlInput by rememberSaveable { mutableStateOf(activeTab.url) }
    var isEditingUrl by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }

    val webViewState = rememberWebViewState(activeTab.url)
    val navigator = rememberWebViewNavigator()
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    var desktopModeInitialized by remember { mutableStateOf(false) }
    LaunchedEffect(uiState.isDesktopMode) {
        if (!desktopModeInitialized) {
            desktopModeInitialized = true
            return@LaunchedEffect
        }
        webViewRef?.settings?.userAgentString =
            if (uiState.isDesktopMode) DESKTOP_USER_AGENT else null
        navigator.reload()
    }

    LaunchedEffect(webViewState.lastLoadedUrl) {
        if (!isEditingUrl) {
            urlInput = webViewState.lastLoadedUrl ?: urlInput
        }
        webViewState.lastLoadedUrl?.let { viewModel.updateTabUrl(activeTab.id, it) }
    }

    LaunchedEffect(webViewState.pageTitle) {
        webViewState.pageTitle?.let { viewModel.updateTabTitle(activeTab.id, it) }
    }

    val webViewClient = remember { AccompanistWebViewClient() }
    val webViewChromeClient = remember { AccompanistWebChromeClient() }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            BrowserTopBar(
                urlInput = urlInput,
                isLoading = webViewState.isLoading,
                isDesktopMode = uiState.isDesktopMode,
                isBookmarked =
                    uiState.bookmarks.any { it.url == (webViewState.lastLoadedUrl ?: "") },
                canGoBack = navigator.canGoBack,
                canGoForward = navigator.canGoForward,
                tabCount = uiState.tabs.size,
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
                onShowTabs = { viewModel.showTabsSheet() },
                onMenuExpandChange = { showMenu = it },
            )
        },
        floatingActionButton = {
            val currentUrl = webViewState.lastLoadedUrl ?: activeTab.url
            if (currentUrl.isNotEmpty() && currentUrl != BROWSER_HOME_URL) {
                FloatingActionButton(onClick = { onDownloadUrl(currentUrl) }) {
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
                    webViewRef = this
                }
            },
        )
    }

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
    tabCount: Int,
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
    onShowTabs: () -> Unit,
    onMenuExpandChange: (Boolean) -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
    ) {
        Column(modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars)) {
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
                // Tab count button — rounded square with count, like Chrome mobile
                IconButton(onClick = onShowTabs) {
                    Box(
                        modifier =
                            Modifier.size(24.dp).border(
                                width = 2.dp,
                                color = MaterialTheme.colorScheme.onSurface,
                                shape = RoundedCornerShape(4.dp),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = if (tabCount > 9) "9+" else tabCount.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
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
            if (isLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(2.dp))
            } else {
                Spacer(modifier = Modifier.height(2.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TabsSheet(
    tabs: List<BrowserTab>,
    activeTabId: Int,
    onTabClick: (Int) -> Unit,
    onTabClose: (Int) -> Unit,
    onNewTab: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false),
    ) {
        Column(modifier = Modifier.padding(bottom = 16.dp)) {
            Row(
                modifier =
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.browser_tabs, tabs.size),
                    style = MaterialTheme.typography.titleMedium,
                )
                FilledTonalButton(
                    onClick = onNewTab,
                    enabled = tabs.size < MAX_TABS,
                ) {
                    Icon(Icons.Outlined.Add, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.browser_new_tab))
                }
            }
            HorizontalDivider()
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(tabs, key = { it.id }) { tab ->
                    TabCard(
                        tab = tab,
                        isActive = tab.id == activeTabId,
                        onTabClick = { onTabClick(tab.id) },
                        onTabClose = { onTabClose(tab.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun TabCard(
    tab: BrowserTab,
    isActive: Boolean,
    onTabClick: () -> Unit,
    onTabClose: () -> Unit,
) {
    val borderColor =
        if (isActive) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.outlineVariant
    val borderWidth = if (isActive) 2.dp else 1.dp

    Card(
        modifier =
            Modifier.fillMaxWidth()
                .height(80.dp)
                .border(borderWidth, borderColor, MaterialTheme.shapes.medium)
                .clickable(onClick = onTabClick),
        shape = MaterialTheme.shapes.medium,
        colors =
            CardDefaults.cardColors(
                containerColor =
                    if (isActive) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant,
            ),
    ) {
        Box(modifier = Modifier.fillMaxSize().padding(8.dp)) {
            Column(modifier = Modifier.fillMaxWidth().padding(end = 24.dp)) {
                Text(
                    text = tab.title.ifEmpty { tab.url },
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text =
                        tab.url
                            .removePrefix("https://")
                            .removePrefix("http://")
                            .substringBefore("/"),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(
                onClick = onTabClose,
                modifier = Modifier.size(24.dp).align(Alignment.TopEnd),
            ) {
                Icon(
                    Icons.Default.Close,
                    stringResource(R.string.browser_close_tab),
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
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
