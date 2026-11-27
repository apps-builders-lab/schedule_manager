package io.github.sh_sunfaded.schedulemanager.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.sh_sunfaded.schedulemanager.data.ReminderEntity
import io.github.sh_sunfaded.schedulemanager.data.TaskEntity
import io.github.sh_sunfaded.schedulemanager.domain.JstTime
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset

/** 日付だけ選んだときに入れる時刻(仮定)。時刻ピッカーで分まで変えられる。 */
private val DefaultDueTime: LocalTime = LocalTime.of(9, 0)

private enum class Picker { None, Date, Time }

/** 追加(task == null)と編集の共用。title 必須、note と期限は任意(SPEC §3)。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskEditorDialog(
    task: TaskEntity?,
    reminders: List<ReminderEntity>,
    onAddReminder: (fireAt: Long, onResult: (Boolean) -> Unit) -> Unit,
    onDeleteReminder: (id: Long) -> Unit,
    onDismiss: () -> Unit,
    onSave: (title: String, note: String?, dueAt: Long?) -> Unit,
) {
    var title by rememberSaveable { mutableStateOf(task?.title.orEmpty()) }
    var note by rememberSaveable { mutableStateOf(task?.note.orEmpty()) }
    var dueMillis by rememberSaveable { mutableStateOf(task?.dueAt) }
    var picker by rememberSaveable { mutableStateOf(Picker.None) }
    var pickingReminder by rememberSaveable { mutableStateOf(false) }
    var reminderRejected by rememberSaveable { mutableStateOf(false) }

    val due: LocalDateTime? = dueMillis?.let(JstTime::toLocal)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (task == null) "追加" else "編集") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("title") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("note(任意)") },
                )
                Text("期限(任意): " + (dueMillis?.let(JstTime::format) ?: "なし"))
                Row {
                    TextButton(onClick = { picker = Picker.Date }) { Text("日付") }
                    TextButton(
                        onClick = { picker = Picker.Time },
                        enabled = due != null,
                    ) { Text("時刻") }
                    TextButton(
                        onClick = { dueMillis = null },
                        enabled = due != null,
                    ) { Text("期限を消す") }
                }
                if (task == null) {
                    Text("リマインダは追加したあと、編集で付けられます。")
                } else {
                    Text("リマインダ(各1回)")
                    reminders.forEach { r ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                JstTime.format(r.fireAt) + if (r.firedAt != null) "(済)" else "",
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = { onDeleteReminder(r.id) }) { Text("削除") }
                        }
                    }
                    TextButton(onClick = { reminderRejected = false; pickingReminder = true }) { Text("リマインダを追加") }
                    if (reminderRejected) Text("過去の時刻には付けられません。")
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(title, note.ifBlank { null }, dueMillis) },
                enabled = title.isNotBlank(),
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("キャンセル") } },
    )

    if (pickingReminder) {
        ReminderPicker(
            onPicked = { millis ->
                pickingReminder = false
                onAddReminder(millis) { ok -> reminderRejected = !ok }
            },
            onDismiss = { pickingReminder = false },
        )
    }

    if (picker == Picker.Date) {
        val state = rememberDatePickerState(
            // DatePicker は選択日の 00:00 UTC を返す。日付だけを取り出して JST の日付として扱う。
            initialSelectedDateMillis = due?.toLocalDate()?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { picker = Picker.None },
            confirmButton = {
                TextButton(
                    onClick = {
                        state.selectedDateMillis?.let { picked ->
                            val date = Instant.ofEpochMilli(picked).atZone(ZoneOffset.UTC).toLocalDate()
                            dueMillis = JstTime.toEpochMillis(date.atTime(due?.toLocalTime() ?: DefaultDueTime))
                        }
                        picker = Picker.None
                    },
                ) { Text("決定") }
            },
            dismissButton = { TextButton(onClick = { picker = Picker.None }) { Text("キャンセル") } },
        ) { DatePicker(state = state) }
    }

    if (picker == Picker.Time && due != null) {
        val state = rememberTimePickerState(
            initialHour = due.hour,
            initialMinute = due.minute,
            is24Hour = true,
        )
        AlertDialog(
            onDismissRequest = { picker = Picker.None },
            text = { TimePicker(state = state) },
            confirmButton = {
                TextButton(
                    onClick = {
                        val date: LocalDate = due.toLocalDate()
                        dueMillis = JstTime.toEpochMillis(date.atTime(state.hour, state.minute))
                        picker = Picker.None
                    },
                ) { Text("決定") }
            },
            dismissButton = { TextButton(onClick = { picker = Picker.None }) { Text("キャンセル") } },
        )
    }
}

/** リマインダの日時(分まで)を日付→時刻の順に選ぶ。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReminderPicker(onPicked: (Long) -> Unit, onDismiss: () -> Unit) {
    var date by rememberSaveable { mutableStateOf<Long?>(null) }
    if (date == null) {
        val state = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = onDismiss,
            confirmButton = {
                TextButton(onClick = { state.selectedDateMillis?.let { date = it } }) { Text("次へ") }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text("キャンセル") } },
        ) { DatePicker(state = state) }
    } else {
        val state = rememberTimePickerState(initialHour = DefaultDueTime.hour, initialMinute = 0, is24Hour = true)
        AlertDialog(
            onDismissRequest = onDismiss,
            text = { TimePicker(state = state) },
            confirmButton = {
                TextButton(onClick = {
                    val day = Instant.ofEpochMilli(date!!).atZone(ZoneOffset.UTC).toLocalDate()
                    onPicked(JstTime.toEpochMillis(day.atTime(state.hour, state.minute)))
                }) { Text("決定") }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text("キャンセル") } },
        )
    }
}
