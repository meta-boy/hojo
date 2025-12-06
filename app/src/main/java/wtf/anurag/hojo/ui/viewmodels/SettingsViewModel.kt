package wtf.anurag.hojo.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import wtf.anurag.hojo.data.repository.ThemeMode
import wtf.anurag.hojo.data.repository.ThemeRepository

@HiltViewModel
class SettingsViewModel @Inject constructor(private val themeRepository: ThemeRepository) :
        ViewModel() {

    val themeMode: StateFlow<ThemeMode> =
            themeRepository.themeMode.stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5000),
                    initialValue = ThemeMode.SYSTEM
            )

    fun setTheme(mode: ThemeMode) {
        themeRepository.setThemeMode(mode)
    }
}
