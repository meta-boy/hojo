package wtf.anurag.hojo.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import wtf.anurag.hojo.data.TaskRepository
import wtf.anurag.hojo.data.model.TaskItem
import wtf.anurag.hojo.data.model.TaskStatus

@HiltViewModel
class TasksViewModel @Inject constructor(private val taskRepository: TaskRepository) : ViewModel() {

    val ongoingTasks: StateFlow<List<TaskItem>> =
            taskRepository
                    .tasks
                    .map { list ->
                        list
                                .filter {
                                    it.status == TaskStatus.QUEUED ||
                                            it.status == TaskStatus.UPLOADING
                                }
                                .sortedBy { it.timestamp }
                    }
                    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val historyTasks: StateFlow<List<TaskItem>> =
            taskRepository
                    .tasks
                    .map { list ->
                        list
                                .filter {
                                    it.status == TaskStatus.COMPLETED ||
                                            it.status == TaskStatus.FAILED ||
                                            it.status == TaskStatus.CANCELLED
                                }
                                .sortedByDescending { it.timestamp }
                    }
                    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun cancelTask(taskId: String) {
        taskRepository.cancelTask(taskId)
    }

    fun clearHistory() {
        taskRepository.clearHistory()
    }

    fun retryTask(taskId: String) {
        taskRepository.retryTask(taskId)
    }
}
