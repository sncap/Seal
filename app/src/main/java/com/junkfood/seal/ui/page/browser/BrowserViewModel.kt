package com.junkfood.seal.ui.page.browser

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

const val BROWSER_HOME_URL = "https://www.google.com"
const val DESKTOP_USER_AGENT =
    "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36"
const val MAX_TABS = 10

data class BrowserTab(
    val id: Int,
    val url: String = BROWSER_HOME_URL,
    val title: String = "",
)

data class BrowserBookmark(val title: String, val url: String)

data class BrowserUiState(
    val tabs: List<BrowserTab> = listOf(BrowserTab(id = 0)),
    val activeTabIndex: Int = 0,
    val detectedVideoUrl: String? = null,
    val bookmarks: List<BrowserBookmark> = emptyList(),
    val isDesktopMode: Boolean = false,
    val showBookmarksSheet: Boolean = false,
    val showTabsSheet: Boolean = false,
    val showMaxTabsSnackbar: Boolean = false,
) {
    val activeTab: BrowserTab
        get() = tabs.getOrElse(activeTabIndex) { tabs.first() }
}

class BrowserViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(BrowserUiState())
    val uiState = _uiState.asStateFlow()

    private var nextTabId = 1

    // Called from background thread via shouldInterceptRequest — StateFlow.update is thread-safe
    fun onVideoDetected(url: String) {
        _uiState.update { it.copy(detectedVideoUrl = url) }
    }

    fun clearDetectedVideo() {
        _uiState.update { it.copy(detectedVideoUrl = null) }
    }

    fun toggleDesktopMode() {
        _uiState.update { it.copy(isDesktopMode = !it.isDesktopMode) }
    }

    fun toggleBookmark(title: String, url: String) {
        _uiState.update { state ->
            val alreadyBookmarked = state.bookmarks.any { it.url == url }
            state.copy(
                bookmarks =
                    if (alreadyBookmarked) state.bookmarks.filter { it.url != url }
                    else state.bookmarks + BrowserBookmark(title.ifEmpty { url }, url),
            )
        }
    }

    fun showBookmarks() = _uiState.update { it.copy(showBookmarksSheet = true) }

    fun hideBookmarks() = _uiState.update { it.copy(showBookmarksSheet = false) }

    fun isBookmarked(url: String) = _uiState.value.bookmarks.any { it.url == url }

    fun showTabsSheet() = _uiState.update { it.copy(showTabsSheet = true) }

    fun hideTabsSheet() = _uiState.update { it.copy(showTabsSheet = false) }

    fun dismissMaxTabsSnackbar() = _uiState.update { it.copy(showMaxTabsSnackbar = false) }

    fun addTab(url: String = BROWSER_HOME_URL) {
        _uiState.update { state ->
            if (state.tabs.size >= MAX_TABS) {
                state.copy(showMaxTabsSnackbar = true)
            } else {
                val newTab = BrowserTab(id = nextTabId++, url = url)
                state.copy(
                    tabs = state.tabs + newTab,
                    activeTabIndex = state.tabs.size,
                    showTabsSheet = false,
                    showBookmarksSheet = false,
                )
            }
        }
    }

    fun closeTab(tabId: Int) {
        _uiState.update { state ->
            val tabIndex = state.tabs.indexOfFirst { it.id == tabId }
            if (tabIndex == -1) return@update state

            val newTabs = state.tabs.toMutableList().also { it.removeAt(tabIndex) }

            if (newTabs.isEmpty()) {
                val fallbackTab = BrowserTab(id = nextTabId++)
                return@update state.copy(
                    tabs = listOf(fallbackTab),
                    activeTabIndex = 0,
                    showBookmarksSheet = false,
                )
            }

            val newActiveIndex = when {
                state.activeTabIndex >= newTabs.size -> newTabs.size - 1
                tabIndex < state.activeTabIndex -> state.activeTabIndex - 1
                else -> state.activeTabIndex
            }
            state.copy(
                tabs = newTabs,
                activeTabIndex = newActiveIndex,
                showBookmarksSheet = false,
            )
        }
    }

    fun switchToTab(tabId: Int) {
        _uiState.update { state ->
            val index = state.tabs.indexOfFirst { it.id == tabId }
            if (index == -1) state
            else
                state.copy(
                    activeTabIndex = index,
                    showTabsSheet = false,
                    showBookmarksSheet = false,
                )
        }
    }

    fun updateTabUrl(tabId: Int, url: String) {
        _uiState.update { state ->
            state.copy(tabs = state.tabs.map { if (it.id == tabId) it.copy(url = url) else it })
        }
    }

    fun updateTabTitle(tabId: Int, title: String) {
        _uiState.update { state ->
            state.copy(
                tabs = state.tabs.map { if (it.id == tabId) it.copy(title = title) else it }
            )
        }
    }
}
