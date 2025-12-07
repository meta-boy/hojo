package wtf.anurag.hojo.ui.viewmodels

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow
import wtf.anurag.hojo.data.repository.UserPreferencesRepository

@HiltViewModel
class MainViewModel
@Inject
constructor(private val userPreferencesRepository: UserPreferencesRepository) : ViewModel() {

    val isGridLayout: StateFlow<Boolean> = userPreferencesRepository.isGridLayout

    fun toggleLayout() {
        val current = isGridLayout.value
        userPreferencesRepository.setGridLayout(!current)
    }
}
