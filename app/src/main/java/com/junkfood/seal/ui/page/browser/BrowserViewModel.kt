package com.junkfood.seal.ui.page.browser

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

const val BROWSER_HOME_URL = "https://www.google.com"
const val DESKTOP_USER_AGENT =
    "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36"

data class BrowserBookmark(val title: String, val url: String)

data class BrowserUiState(
    val detectedVideoUrls: Set<String> = emptySet(),
    val bookmarks: List<BrowserBookmark> = emptyList(),
    val isDesktopMode: Boolean = false,
    val showBookmarksSheet: Boolean = false,
    val showVideoSheet: Boolean = false,
)

class BrowserViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(BrowserUiState())
    val uiState = _uiState.asStateFlow()

    // Called from background thread via shouldInterceptRequest — StateFlow.update is thread-safe
    fun onVideoDetected(url: String) {
        _uiState.update { it.copy(detectedVideoUrls = it.detectedVideoUrls + url) }
    }

    fun clearDetectedVideos() {
        _uiState.update { it.copy(detectedVideoUrls = emptySet(), showVideoSheet = false) }
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
                    else state.bookmarks + BrowserBookmark(title.ifEmpty { url }, url)
            )
        }
    }

    fun showBookmarks() = _uiState.update { it.copy(showBookmarksSheet = true) }

    fun hideBookmarks() = _uiState.update { it.copy(showBookmarksSheet = false) }

    fun showVideoSheet() = _uiState.update { it.copy(showVideoSheet = true) }

    fun hideVideoSheet() = _uiState.update { it.copy(showVideoSheet = false) }

    fun isBookmarked(url: String) = _uiState.value.bookmarks.any { it.url == url }
}
