package dev.qtremors.arcile.feature.browser

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.qtremors.arcile.core.storage.domain.QuickAccessItem
import dev.qtremors.arcile.core.storage.domain.QuickAccessPreferencesStore
import dev.qtremors.arcile.core.storage.domain.QuickAccessType
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.launch

@HiltViewModel
internal class BrowserQuickAccessViewModel @Inject constructor(
    private val preferencesStore: QuickAccessPreferencesStore
) : ViewModel() {

    fun addCustomFolder(path: String, label: String) {
        viewModelScope.launch {
            preferencesStore.addItem(
                QuickAccessItem(
                    id = "custom_${UUID.randomUUID()}",
                    label = label,
                    path = path,
                    type = QuickAccessType.CUSTOM,
                    isPinned = true,
                    isEnabled = true
                )
            )
        }
    }
}
