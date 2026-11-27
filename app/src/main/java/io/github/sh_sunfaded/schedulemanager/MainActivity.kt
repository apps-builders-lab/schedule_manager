package io.github.sh_sunfaded.schedulemanager

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.sh_sunfaded.schedulemanager.notify.DailyRescheduleWorker
import io.github.sh_sunfaded.schedulemanager.ui.TaskListScreen
import io.github.sh_sunfaded.schedulemanager.ui.TaskViewModel
import io.github.sh_sunfaded.schedulemanager.ui.theme.Schedule_managerTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    /** 通知タップで渡された task id。画面が開いたら消費する。 */
    private val openTaskId = mutableStateOf<Long?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val graph = (application as ScheduleApp).graph
        if (savedInstanceState == null) readTaskId(intent)
        // 起動のたびに予約を整え、日次の保険を登録する(KEEP なので重複しない)。
        DailyRescheduleWorker.enqueue(this)
        lifecycleScope.launch { graph.rescheduler.reschedule() }
        setContent {
            Schedule_managerTheme {
                val viewModel: TaskViewModel = viewModel(factory = TaskViewModel.Factory(graph.repository))
                TaskListScreen(
                    viewModel = viewModel,
                    openTaskId = openTaskId.value,
                    onOpenConsumed = { openTaskId.value = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        readTaskId(intent)
    }

    private fun readTaskId(intent: Intent?) {
        val id = intent?.getLongExtra(EXTRA_TASK_ID, NO_TASK) ?: NO_TASK
        if (id != NO_TASK) openTaskId.value = id
    }

    companion object {
        const val EXTRA_TASK_ID = "task_id"
        private const val NO_TASK = -1L
    }
}
