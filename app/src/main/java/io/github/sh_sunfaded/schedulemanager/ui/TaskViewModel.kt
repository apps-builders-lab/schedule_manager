package io.github.sh_sunfaded.schedulemanager.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.github.sh_sunfaded.schedulemanager.data.ReminderEntity
import io.github.sh_sunfaded.schedulemanager.data.TaskRepository
import io.github.sh_sunfaded.schedulemanager.domain.TaskSections
import io.github.sh_sunfaded.schedulemanager.domain.buildSections
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TaskViewModel(private val repository: TaskRepository) : ViewModel() {

    val sections: StateFlow<TaskSections> = repository.observeAll()
        .map(::buildSections)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TaskSections(emptyList(), emptyList()))

    /** task_id ごとのリマインダ(編集ダイアログで表示する)。 */
    val reminders: StateFlow<Map<Long, List<ReminderEntity>>> = repository.observeReminders()
        .map { list -> list.groupBy { it.taskId } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    fun addReminder(taskId: Long, fireAt: Long, onResult: (Boolean) -> Unit) {
        viewModelScope.launch { onResult(repository.addReminder(taskId, fireAt)) }
    }

    fun deleteReminder(id: Long) {
        viewModelScope.launch { repository.deleteReminder(id) }
    }

    fun add(title: String, note: String?, dueAt: Long?) {
        viewModelScope.launch { repository.add(title, note, dueAt) }
    }

    fun edit(id: Long, title: String, note: String?, dueAt: Long?) {
        viewModelScope.launch { repository.edit(id, title, note, dueAt) }
    }

    fun setDone(id: Long, done: Boolean) {
        viewModelScope.launch { repository.setDone(id, done) }
    }

    fun delete(id: Long) {
        viewModelScope.launch { repository.delete(id) }
    }

    class Factory(private val repository: TaskRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = TaskViewModel(repository) as T
    }
}
