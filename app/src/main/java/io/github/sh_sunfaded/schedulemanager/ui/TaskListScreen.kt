package io.github.sh_sunfaded.schedulemanager.ui

import android.Manifest
import android.app.AlarmManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import io.github.sh_sunfaded.schedulemanager.domain.needsNotificationPermissionRequest
import io.github.sh_sunfaded.schedulemanager.data.TaskEntity
import io.github.sh_sunfaded.schedulemanager.data.TaskStatus
import io.github.sh_sunfaded.schedulemanager.domain.JstTime

/** 画面状態の保持用。編集中の task は id で持ち、一覧から引き直す(回転後も復元できる)。 */
private const val NoTask = -1L

@Composable
fun TaskListScreen(
    viewModel: TaskViewModel,
    openTaskId: Long? = null,
    onOpenConsumed: () -> Unit = {},
) {
    val sections by viewModel.sections.collectAsState()
    val remindersByTask by viewModel.reminders.collectAsState()
    val context = LocalContext.current

    // 通知の許可。拒否されても一覧等は落とさない。
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (needsNotificationPermissionRequest(Build.VERSION.SDK_INT, granted)) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // exact alarm の許可(SCHEDULE_EXACT_ALARM)。無い間は案内を1行出す。
    var canExact by remember { mutableStateOf(true) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        canExact = context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
    }

    var adding by rememberSaveable { mutableStateOf(false) }
    var editingId by rememberSaveable { mutableLongStateOf(NoTask) }
    var deletingId by rememberSaveable { mutableLongStateOf(NoTask) }
    var doneExpanded by rememberSaveable { mutableStateOf(false) }

    val all = sections.active + sections.done
    val editing = all.firstOrNull { it.id == editingId }
    val deleting = all.firstOrNull { it.id == deletingId }

    // 通知タップで渡された todo を表示する(編集ダイアログで開く)。一覧に現れるまで待つ。
    LaunchedEffect(openTaskId, all) {
        if (openTaskId != null && all.any { it.id == openTaskId }) {
            editingId = openTaskId
            onOpenConsumed()
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = { adding = true }) { Text("追加") }
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = innerPadding.calculateTopPadding() + 8.dp,
                bottom = innerPadding.calculateBottomPadding() + 88.dp,
                start = 16.dp,
                end = 16.dp,
            ),
        ) {
            if (!canExact) {
                item(key = "exact-notice") {
                    Column(modifier = Modifier.padding(bottom = 8.dp)) {
                        Text("正確な時刻に通知するには、アラームとリマインダーの許可が必要です。")
                        TextButton(onClick = {
                            context.startActivity(
                                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                                    .setData(Uri.parse("package:" + context.packageName)),
                            )
                        }) { Text("許可の設定を開く") }
                    }
                }
            }
            if (sections.active.isEmpty() && sections.done.isEmpty()) {
                item { Text("todo はまだありません。") }
            }
            items(sections.active, key = { it.id }) { task ->
                TaskRow(
                    task = task,
                    onToggleDone = { viewModel.setDone(task.id, true) },
                    onEdit = { editingId = task.id },
                    onDelete = { deletingId = task.id },
                )
                HorizontalDivider()
            }
            if (sections.done.isNotEmpty()) {
                item(key = "done-header") {
                    Text(
                        text = (if (doneExpanded) "▼ " else "▶ ") + "完了(${sections.done.size})",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { doneExpanded = !doneExpanded }
                            .padding(vertical = 12.dp),
                    )
                }
                if (doneExpanded) {
                    items(sections.done, key = { it.id }) { task ->
                        TaskRow(
                            task = task,
                            onToggleDone = { viewModel.setDone(task.id, false) },
                            onEdit = { editingId = task.id },
                            onDelete = { deletingId = task.id },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }

    if (adding) {
        TaskEditorDialog(
            task = null,
            reminders = emptyList(),
            onAddReminder = { _, _ -> },
            onDeleteReminder = {},
            onDismiss = { adding = false },
            onSave = { title, note, dueAt ->
                viewModel.add(title, note, dueAt)
                adding = false
            },
        )
    }
    if (editing != null) {
        TaskEditorDialog(
            task = editing,
            reminders = remindersByTask[editing.id].orEmpty(),
            onAddReminder = { at, onResult -> viewModel.addReminder(editing.id, at, onResult) },
            onDeleteReminder = viewModel::deleteReminder,
            onDismiss = { editingId = NoTask },
            onSave = { title, note, dueAt ->
                viewModel.edit(editing.id, title, note, dueAt)
                editingId = NoTask
            },
        )
    }
    if (deleting != null) {
        AlertDialog(
            onDismissRequest = { deletingId = NoTask },
            title = { Text("削除") },
            text = { Text("「${deleting.title}」を削除します。元に戻せません。") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(deleting.id)
                    deletingId = NoTask
                }) { Text("削除する") }
            },
            dismissButton = { TextButton(onClick = { deletingId = NoTask }) { Text("キャンセル") } },
        )
    }
}

@Composable
private fun TaskRow(
    task: TaskEntity,
    onToggleDone: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val isDone = task.status == TaskStatus.DONE
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Checkbox(checked = isDone, onCheckedChange = { onToggleDone() })
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = task.title,
                textDecoration = if (isDone) TextDecoration.LineThrough else null,
            )
            task.dueAt?.let { Text("期限 " + JstTime.format(it), style = MaterialTheme.typography.bodySmall) }
            task.note?.let { Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 2) }
        }
        TextButton(onClick = onDelete) { Text("削除") }
    }
}
