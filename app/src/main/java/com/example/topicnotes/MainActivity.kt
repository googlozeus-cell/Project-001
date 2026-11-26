package com.example.topicnotes

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.AlarmClock
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID

const val REMINDER_CHANNEL_ID = "notes_reminders_channel"

enum class TopicIconType(val label: String, val accent: Color) {
    CAR("Машина", Color(0xFF1E88E5)),
    GARAGE("Гараж", Color(0xFF546E7A)),
    HOME("Дом", Color(0xFF43A047)),
    BUILD("Ремонт", Color(0xFFFB8C00)),
    LIST("Список", Color(0xFF8E24AA)),
    STAR("Важное", Color(0xFFE53935));

    fun toImageVector(): ImageVector = when (this) {
        CAR -> Icons.Default.DirectionsCar
        GARAGE -> Icons.Default.Build
        HOME -> Icons.Default.Home
        BUILD -> Icons.Default.Handyman
        LIST -> Icons.Default.FormatListBulleted
        STAR -> Icons.Default.Star
    }
}

data class NoteItem(
    val id: String,
    val text: String,
    val createdAt: Long,
    val isDone: Boolean = false,
    val isPinned: Boolean = false,
    val reminderAt: Long? = null
)

data class Subcategory(
    val id: String,
    val name: String,
    val notes: List<NoteItem> = emptyList()
)

data class Topic(
    val id: String,
    val title: String,
    val iconType: TopicIconType,
    val subcategories: List<Subcategory>
)

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val title = intent.getStringExtra("extra_title") ?: "Напоминание из «Моих заметок»"
        val body = intent.getStringExtra("extra_body") ?: ""
        val notifId = intent.getIntExtra("extra_notif_id", (System.currentTimeMillis() % Int.MAX_VALUE).toInt())

        ensureNotificationChannel(context)

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            notifId,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, REMINDER_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        val hasPermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

        if (hasPermission) {
            NotificationManagerCompat.from(context).notify(notifId, builder.build())
        }
    }
}

fun ensureNotificationChannel(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val channel = NotificationChannel(
            REMINDER_CHANNEL_ID,
            "Напоминания о заметках",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Уведомления по дате и времени из приложения «Мои заметки»"
        }
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(channel)
    }
}

fun scheduleNoteReminder(context: Context, note: NoteItem, topicTitle: String, subName: String) {
    val triggerAt = note.reminderAt ?: return
    if (triggerAt <= System.currentTimeMillis()) return

    ensureNotificationChannel(context)
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    val reqCode = note.id.hashCode()

    val intent = Intent(context, ReminderReceiver::class.java).apply {
        putExtra("extra_title", "$topicTitle · $subName")
        putExtra("extra_body", note.text)
        putExtra("extra_notif_id", reqCode)
    }

    val pendingIntent = PendingIntent.getBroadcast(
        context,
        reqCode,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
        } else {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
        }
    } catch (_: SecurityException) {
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
    }
}

fun cancelNoteReminder(context: Context, noteId: String) {
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    val intent = Intent(context, ReminderReceiver::class.java)
    val pendingIntent = PendingIntent.getBroadcast(
        context,
        noteId.hashCode(),
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    alarmManager.cancel(pendingIntent)
}

fun openSystemAlarm(context: Context, timeMillis: Long?, message: String) {
    val cal = Calendar.getInstance().apply {
        if (timeMillis != null && timeMillis > 0L) timeInMillis = timeMillis
        else add(Calendar.HOUR_OF_DAY, 1)
    }
    val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
        putExtra(AlarmClock.EXTRA_HOUR, cal.get(Calendar.HOUR_OF_DAY))
        putExtra(AlarmClock.EXTRA_MINUTES, cal.get(Calendar.MINUTE))
        putExtra(AlarmClock.EXTRA_MESSAGE, message.take(80))
        putExtra(AlarmClock.EXTRA_SKIP_UI, false)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    try {
        context.startActivity(intent)
    } catch (_: Exception) {
        Toast.makeText(context, "Не удалось открыть приложение будильника", Toast.LENGTH_SHORT).show()
    }
}

class MainActivity : ComponentActivity() {

    private val requestNotifPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ensureNotificationChannel(this)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                requestNotifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        val storageFile = File(filesDir, "notes_topics_v1.json")

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    TopicNotesScreen(storageFile = storageFile)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopicNotesScreen(storageFile: File) {
    val context = LocalContext.current
    var topics by remember { mutableStateOf(loadTopics(storageFile)) }

    var selectedTopicId by remember { mutableStateOf(topics.firstOrNull()?.id ?: "") }
    val currentTopic = topics.find { it.id == selectedTopicId } ?: topics.firstOrNull()

    var selectedSubId by remember(currentTopic?.id) {
        mutableStateOf(currentTopic?.subcategories?.firstOrNull()?.id ?: "")
    }
    val currentSub = currentTopic?.subcategories?.find { it.id == selectedSubId }
        ?: currentTopic?.subcategories?.firstOrNull()

    var noteText by remember { mutableStateOf("") }
    var draftReminder by remember { mutableStateOf<Long?>(null) }

    var showAddTopicDialog by remember { mutableStateOf(false) }
    var editingTopic by remember { mutableStateOf<Topic?>(null) }
    var topicToDelete by remember { mutableStateOf<Topic?>(null) }

    var showAddSubDialog by remember { mutableStateOf(false) }
    var editingSub by remember { mutableStateOf<Subcategory?>(null) }
    var subToDelete by remember { mutableStateOf<Subcategory?>(null) }

    var editingNote by remember { mutableStateOf<NoteItem?>(null) }
    var noteToDelete by remember { mutableStateOf<NoteItem?>(null) }

    var pickDateForNote by remember { mutableStateOf(false) }
    var pendingTimeDialogMillis by remember { mutableStateOf<Long?>(null) }

    fun persist(updated: List<Topic>) {
        topics = updated
        saveTopics(storageFile, updated)
    }

    fun updateNoteInState(noteId: String, transform: (NoteItem) -> NoteItem) {
        if (currentTopic == null || currentSub == null) return
        val updated = topics.map { t ->
            if (t.id != currentTopic.id) t
            else t.copy(
                subcategories = t.subcategories.map { s ->
                    if (s.id != currentSub.id) s
                    else s.copy(
                        notes = s.notes.map { n ->
                            if (n.id == noteId) transform(n) else n
                        }
                    )
                }
            )
        }
        persist(updated)
    }

    val sortedNotes = remember(currentSub?.notes) {
        (currentSub?.notes ?: emptyList()).sortedWith(
            compareByDescending<NoteItem> { it.isPinned }
                .thenBy { it.isDone }
                .thenByDescending { it.createdAt }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Мои заметки", fontWeight = FontWeight.SemiBold)
                        if (currentTopic != null && currentSub != null) {
                            Text(
                                "${currentTopic.title} / ${currentSub.name}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    if (currentTopic != null) {
                        IconButton(onClick = { editingTopic = currentTopic }) {
                            Icon(Icons.Default.Edit, contentDescription = "Переименовать тему")
                        }
                        IconButton(onClick = { topicToDelete = currentTopic }) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = "Удалить тему")
                        }
                    }
                    IconButton(onClick = { showAddTopicDialog = true }) {
                        Icon(Icons.Default.CreateNewFolder, contentDescription = "Новая тема")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Темы", style = MaterialTheme.typography.labelLarge)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                topics.forEach { topic ->
                    val isSelected = topic.id == currentTopic?.id
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            selectedTopicId = topic.id
                            selectedSubId = topic.subcategories.firstOrNull()?.id ?: ""
                        },
                        label = { Text(topic.title) },
                        leadingIcon = {
                            Icon(
                                imageVector = topic.iconType.toImageVector(),
                                contentDescription = null,
                                tint = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer else topic.iconType.accent,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    )
                }
            }

            if (currentTopic != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Подкатегории", style = MaterialTheme.typography.labelLarge)
                    Row {
                        if (currentSub != null) {
                            IconButton(onClick = { editingSub = currentSub }) {
                                Icon(Icons.Default.Edit, contentDescription = "Переименовать подкатегорию", modifier = Modifier.size(18.dp))
                            }
                            IconButton(onClick = { subToDelete = currentSub }) {
                                Icon(Icons.Default.DeleteOutline, contentDescription = "Удалить подкатегорию", modifier = Modifier.size(18.dp))
                            }
                        }
                        TextButton(onClick = { showAddSubDialog = true }) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Подкатегория")
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    currentTopic.subcategories.forEach { sub ->
                        FilterChip(
                            selected = sub.id == currentSub?.id,
                            onClick = { selectedSubId = sub.id },
                            label = { Text("${sub.name} (${sub.notes.size})") }
                        )
                    }
                }
            }

            if (currentTopic != null && currentSub != null) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = noteText,
                            onValueChange = { noteText = it },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = {
                                Text("Например: заменить фильтр, купить свёрла или записать размер...")
                            },
                            label = { Text("Новая заметка") },
                            minLines = 2,
                            maxLines = 4
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AssistChip(
                                onClick = { pickDateForNote = true },
                                label = {
                                    Text(
                                        if (draftReminder != null) "🔔 ${formatTimestamp(draftReminder!!)}"
                                        else "Напомнить по дате и времени"
                                    )
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.Alarm, contentDescription = null, modifier = Modifier.size(18.dp))
                                }
                            )

                            if (draftReminder != null) {
                                AssistChip(
                                    onClick = {
                                        openSystemAlarm(
                                            context,
                                            draftReminder,
                                            noteText.ifBlank { "${currentTopic.title}: ${currentSub.name}" }
                                        )
                                    },
                                    label = { Text("В будильник") },
                                    leadingIcon = {
                                        Icon(Icons.Default.AccessAlarm, contentDescription = null, modifier = Modifier.size(18.dp))
                                    }
                                )
                                IconButton(
                                    onClick = { draftReminder = null },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "Убрать напоминание", modifier = Modifier.size(18.dp))
                                }
                            }
                        }

                        Button(
                            onClick = {
                                val clean = noteText.trim()
                                if (clean.isNotEmpty()) {
                                    val newNote = NoteItem(
                                        id = UUID.randomUUID().toString(),
                                        text = clean,
                                        createdAt = System.currentTimeMillis(),
                                        reminderAt = draftReminder
                                    )
                                    val updated = topics.map { t ->
                                        if (t.id != currentTopic.id) t
                                        else t.copy(
                                            subcategories = t.subcategories.map { s ->
                                                if (s.id != currentSub.id) s
                                                else s.copy(notes = listOf(newNote) + s.notes)
                                            }
                                        )
                                    }
                                    persist(updated)
                                    if (newNote.reminderAt != null) {
                                        scheduleNoteReminder(context, newNote, currentTopic.title, currentSub.name)
                                    }
                                    noteText = ""
                                    draftReminder = null
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Save, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Сохранить заметку")
                        }
                    }
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(sortedNotes, key = { it.id }) { note ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = if (note.isDone)
                                    MaterialTheme.colorScheme.surfaceContainerLowest
                                else
                                    MaterialTheme.colorScheme.surfaceContainerHigh
                            )
                        ) {
                            Row(modifier = Modifier.fillMaxWidth()) {
                                Box(
                                    modifier = Modifier
                                        .width(5.dp)
                                        .heightIn(min = 76.dp)
                                        .background(currentTopic.iconType.accent)
                                )
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.Top,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Checkbox(
                                            checked = note.isDone,
                                            onCheckedChange = { checked ->
                                                updateNoteInState(note.id) { it.copy(isDone = checked) }
                                            },
                                            modifier = Modifier.size(28.dp)
                                        )
                                        Text(
                                            text = note.text,
                                            style = MaterialTheme.typography.bodyLarge.copy(
                                                textDecoration = if (note.isDone) TextDecoration.LineThrough else TextDecoration.None
                                            ),
                                            color = if (note.isDone)
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            else
                                                MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                            Text(
                                                text = formatTimestamp(note.createdAt),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            if (note.reminderAt != null) {
                                                Text(
                                                    text = "🔔 ${formatTimestamp(note.reminderAt)}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    fontWeight = FontWeight.Medium
                                                )
                                            }
                                        }

                                        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                            IconButton(
                                                onClick = {
                                                    updateNoteInState(note.id) { it.copy(isPinned = !it.isPinned) }
                                                },
                                                modifier = Modifier.size(34.dp)
                                            ) {
                                                Icon(
                                                    imageVector = if (note.isPinned) Icons.Default.PushPin else Icons.Outlined.PushPin,
                                                    contentDescription = "Закрепить",
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                            IconButton(
                                                onClick = {
                                                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                    cm.setPrimaryClip(ClipData.newPlainText("Заметка", note.text))
                                                    Toast.makeText(context, "Текст скопирован", Toast.LENGTH_SHORT).show()
                                                },
                                                modifier = Modifier.size(34.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.ContentCopy,
                                                    contentDescription = "Копировать",
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                            IconButton(
                                                onClick = { editingNote = note },
                                                modifier = Modifier.size(34.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.Edit,
                                                    contentDescription = "Редактировать",
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                            IconButton(
                                                onClick = { noteToDelete = note },
                                                modifier = Modifier.size(34.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.DeleteOutline,
                                                    contentDescription = "Удалить",
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Создайте тему и подкатегорию для заметок")
                }
            }
        }
    }

    // Диалог новой темы
    if (showAddTopicDialog) {
        TopicFormDialog(
            dialogTitle = "Новая тема",
            initialTitle = "",
            initialIcon = TopicIconType.LIST,
            placeholder = "Например: Машина, Гараж, Дом или Участок",
            confirmLabel = "Создать",
            onDismiss = { showAddTopicDialog = false },
            onConfirm = { title, icon ->
                val newSub = Subcategory(id = UUID.randomUUID().toString(), name = "Общее")
                val newTopic = Topic(
                    id = UUID.randomUUID().toString(),
                    title = title,
                    iconType = icon,
                    subcategories = listOf(newSub)
                )
                persist(topics + newTopic)
                selectedTopicId = newTopic.id
                selectedSubId = newSub.id
                showAddTopicDialog = false
            }
        )
    }

    // Редактирование темы
    if (editingTopic != null) {
        val target = editingTopic!!
        TopicFormDialog(
            dialogTitle = "Изменить тему",
            initialTitle = target.title,
            initialIcon = target.iconType,
            placeholder = "Название темы",
            confirmLabel = "Сохранить",
            onDismiss = { editingTopic = null },
            onConfirm = { title, icon ->
                persist(topics.map { if (it.id == target.id) it.copy(title = title, iconType = icon) else it })
                editingTopic = null
            }
        )
    }

    // Удаление темы
    if (topicToDelete != null) {
        val target = topicToDelete!!
        AlertDialog(
            onDismissRequest = { topicToDelete = null },
            title = { Text("Удалить тему?") },
            text = { Text("Тема «${target.title}» и все её подкатегории и заметки будут удалены.") },
            confirmButton = {
                TextButton(onClick = {
                    val remaining = topics.filterNot { it.id == target.id }
                    persist(remaining)
                    if (selectedTopicId == target.id) {
                        selectedTopicId = remaining.firstOrNull()?.id ?: ""
                    }
                    topicToDelete = null
                }) { Text("Удалить") }
            },
            dismissButton = {
                TextButton(onClick = { topicToDelete = null }) { Text("Отмена") }
            }
        )
    }

    // Новая подкатегория
    if (showAddSubDialog && currentTopic != null) {
        SubcategoryFormDialog(
            dialogTitle = "Новая подкатегория",
            initialName = "",
            placeholder = "Например: ТО, Регламент, Закупки или Дела",
            confirmLabel = "Добавить",
            onDismiss = { showAddSubDialog = false },
            onConfirm = { name ->
                val newSub = Subcategory(id = UUID.randomUUID().toString(), name = name)
                persist(topics.map { t ->
                    if (t.id != currentTopic.id) t
                    else t.copy(subcategories = t.subcategories + newSub)
                })
                selectedSubId = newSub.id
                showAddSubDialog = false
            }
        )
    }

    // Редактирование подкатегории
    if (editingSub != null && currentTopic != null) {
        val target = editingSub!!
        SubcategoryFormDialog(
            dialogTitle = "Переименовать подкатегорию",
            initialName = target.name,
            placeholder = "Название подкатегории",
            confirmLabel = "Сохранить",
            onDismiss = { editingSub = null },
            onConfirm = { name ->
                persist(topics.map { t ->
                    if (t.id != currentTopic.id) t
                    else t.copy(
                        subcategories = t.subcategories.map { s ->
                            if (s.id == target.id) s.copy(name = name) else s
                        }
                    )
                })
                editingSub = null
            }
        )
    }

    // Удаление подкатегории
    if (subToDelete != null && currentTopic != null) {
        val target = subToDelete!!
        AlertDialog(
            onDismissRequest = { subToDelete = null },
            title = { Text("Удалить подкатегорию?") },
            text = { Text("Подкатегория «${target.name}» и её заметки будут удалены.") },
            confirmButton = {
                TextButton(onClick = {
                    val updated = topics.map { t ->
                        if (t.id != currentTopic.id) t
                        else t.copy(subcategories = t.subcategories.filterNot { it.id == target.id })
                    }
                    persist(updated)
                    if (selectedSubId == target.id) {
                        selectedSubId = updated.find { it.id == currentTopic.id }?.subcategories?.firstOrNull()?.id ?: ""
                    }
                    subToDelete = null
                }) { Text("Удалить") }
            },
            dismissButton = {
                TextButton(onClick = { subToDelete = null }) { Text("Отмена") }
            }
        )
    }

    // Редактирование заметки
    if (editingNote != null && currentTopic != null && currentSub != null) {
        val target = editingNote!!
        var editText by remember(target.id) { mutableStateOf(target.text) }
        var editReminder by remember(target.id) { mutableStateOf(target.reminderAt) }

        AlertDialog(
            onDismissRequest = { editingNote = null },
            title = { Text("Редактировать заметку") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = editText,
                        onValueChange = { editText = it },
                        label = { Text("Текст заметки") },
                        minLines = 2,
                        maxLines = 5
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AssistChip(
                            onClick = {
                                val nextHour = System.currentTimeMillis() + 3600_000L
                                editReminder = if (editReminder == null) nextHour else null
                            },
                            label = {
                                Text(
                                    if (editReminder != null) "🔔 ${formatTimestamp(editReminder!!)}"
                                    else "+1 час напоминание"
                                )
                            }
                        )
                        if (editReminder != null) {
                            TextButton(onClick = { editReminder = null }) { Text("Сбросить") }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val clean = editText.trim()
                    if (clean.isNotEmpty()) {
                        val updatedNote = target.copy(text = clean, reminderAt = editReminder)
                        updateNoteInState(target.id) { updatedNote }
                        if (updatedNote.reminderAt != null) {
                            scheduleNoteReminder(context, updatedNote, currentTopic.title, currentSub.name)
                        } else {
                            cancelNoteReminder(context, target.id)
                        }
                        editingNote = null
                    }
                }) { Text("Сохранить") }
            },
            dismissButton = {
                TextButton(onClick = { editingNote = null }) { Text("Отмена") }
            }
        )
    }

    // Подтверждение удаления заметки
    if (noteToDelete != null) {
        val target = noteToDelete!!
        AlertDialog(
            onDismissRequest = { noteToDelete = null },
            title = { Text("Удалить заметку?") },
            text = { Text(target.text) },
            confirmButton = {
                TextButton(onClick = {
                    cancelNoteReminder(context, target.id)
                    if (currentTopic != null && currentSub != null) {
                        val updated = topics.map { t ->
                            if (t.id != currentTopic.id) t
                            else t.copy(
                                subcategories = t.subcategories.map { s ->
                                    if (s.id != currentSub.id) s
                                    else s.copy(notes = s.notes.filterNot { it.id == target.id })
                                }
                            )
                        }
                        persist(updated)
                    }
                    noteToDelete = null
                }) { Text("Удалить") }
            },
            dismissButton = {
                TextButton(onClick = { noteToDelete = null }) { Text("Отмена") }
            }
        )
    }

    // Выбор даты напоминания
    if (pickDateForNote) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = draftReminder ?: System.currentTimeMillis()
        )
        DatePickerDialog(
            onDismissRequest = { pickDateForNote = false },
            confirmButton = {
                TextButton(onClick = {
                    val chosenDate = datePickerState.selectedDateMillis ?: System.currentTimeMillis()
                    pickDateForNote = false
                    pendingTimeDialogMillis = chosenDate
                }) { Text("Далее: время") }
            },
            dismissButton = {
                TextButton(onClick = { pickDateForNote = false }) { Text("Отмена") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    // Выбор времени напоминания
    if (pendingTimeDialogMillis != null) {
        val baseDateMillis = pendingTimeDialogMillis!!
        val calNow = Calendar.getInstance()
        val timePickerState = rememberTimePickerState(
            initialHour = calNow.get(Calendar.HOUR_OF_DAY),
            initialMinute = calNow.get(Calendar.MINUTE),
            is24Hour = true
        )

        AlertDialog(
            onDismissRequest = { pendingTimeDialogMillis = null },
            title = { Text("Выберите время напоминания") },
            text = { TimePicker(state = timePickerState) },
            confirmButton = {
                TextButton(onClick = {
                    val dateCal = Calendar.getInstance().apply { timeInMillis = baseDateMillis }
                    val resultCal = Calendar.getInstance().apply {
                        set(Calendar.YEAR, dateCal.get(Calendar.YEAR))
                        set(Calendar.MONTH, dateCal.get(Calendar.MONTH))
                        set(Calendar.DAY_OF_MONTH, dateCal.get(Calendar.DAY_OF_MONTH))
                        set(Calendar.HOUR_OF_DAY, timePickerState.hour)
                        set(Calendar.MINUTE, timePickerState.minute)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                    draftReminder = resultCal.timeInMillis
                    pendingTimeDialogMillis = null
                }) { Text("Ок") }
            },
            dismissButton = {
                TextButton(onClick = { pendingTimeDialogMillis = null }) { Text("Отмена") }
            }
        )
    }
}

@Composable
private fun TopicFormDialog(
    dialogTitle: String,
    initialTitle: String,
    initialIcon: TopicIconType,
    placeholder: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (String, TopicIconType) -> Unit
) {
    var title by remember { mutableStateOf(initialTitle) }
    var chosenIcon by remember { mutableStateOf(initialIcon) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(dialogTitle) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    singleLine = true,
                    placeholder = { Text(placeholder) },
                    label = { Text("Название темы") }
                )
                Text("Значок:", fontWeight = FontWeight.Medium)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    TopicIconType.entries.forEach { icon ->
                        FilterChip(
                            selected = chosenIcon == icon,
                            onClick = { chosenIcon = icon },
                            label = { Text(icon.label) },
                            leadingIcon = {
                                Icon(icon.toImageVector(), contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val clean = title.trim()
                if (clean.isNotEmpty()) onConfirm(clean, chosenIcon)
            }) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        }
    )
}

@Composable
private fun SubcategoryFormDialog(
    dialogTitle: String,
    initialName: String,
    placeholder: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(dialogTitle) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                placeholder = { Text(placeholder) },
                label = { Text("Название подкатегории") }
            )
        },
        confirmButton = {
            TextButton(onClick = {
                val clean = name.trim()
                if (clean.isNotEmpty()) onConfirm(clean)
            }) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        }
    )
}

private fun defaultTopics(): List<Topic> = listOf(
    Topic(
        id = "topic_car",
        title = "Машина",
        iconType = TopicIconType.CAR,
        subcategories = listOf(
            Subcategory("sub_car_to", "ТО"),
            Subcategory("sub_car_reg", "Регламент замены"),
            Subcategory("sub_car_wish", "Хотелки")
        )
    ),
    Topic(
        id = "topic_garage",
        title = "Гараж",
        iconType = TopicIconType.GARAGE,
        subcategories = listOf(
            Subcategory("sub_gar_tools", "Инструмент и расходники"),
            Subcategory("sub_gar_tasks", "Дела и доработки")
        )
    ),
    Topic(
        id = "topic_home",
        title = "Дом",
        iconType = TopicIconType.HOME,
        subcategories = listOf(
            Subcategory("sub_home_rep", "Быт и ремонт"),
            Subcategory("sub_home_buy", "Покупки")
        )
    )
)

private fun saveTopics(file: File, topics: List<Topic>) {
    val root = JSONArray()
    topics.forEach { t ->
        val tObj = JSONObject()
        tObj.put("id", t.id)
        tObj.put("title", t.title)
        tObj.put("iconType", t.iconType.name)

        val subsArr = JSONArray()
        t.subcategories.forEach { s ->
            val sObj = JSONObject()
            sObj.put("id", s.id)
            sObj.put("name", s.name)

            val notesArr = JSONArray()
            s.notes.forEach { n ->
                val nObj = JSONObject()
                nObj.put("id", n.id)
                nObj.put("text", n.text)
                nObj.put("createdAt", n.createdAt)
                nObj.put("isDone", n.isDone)
                nObj.put("isPinned", n.isPinned)
                if (n.reminderAt != null) nObj.put("reminderAt", n.reminderAt)
                notesArr.put(nObj)
            }
            sObj.put("notes", notesArr)
            subsArr.put(sObj)
        }
        tObj.put("subcategories", subsArr)
        root.put(tObj)
    }
    file.writeText(root.toString(), Charsets.UTF_8)
}

private fun loadTopics(file: File): List<Topic> {
    if (!file.exists()) return defaultTopics()
    return try {
        val raw = file.readText(Charsets.UTF_8)
        val arr = JSONArray(raw)
        if (arr.length() == 0) return defaultTopics()

        buildList {
            for (i in 0 until arr.length()) {
                val tObj = arr.getJSONObject(i)
                val iconName = tObj.optString("iconType", TopicIconType.LIST.name)
                val iconType = runCatching { TopicIconType.valueOf(iconName) }
                    .getOrDefault(TopicIconType.LIST)

                val subsArr = tObj.optJSONArray("subcategories") ?: JSONArray()
                val subs = buildList {
                    for (j in 0 until subsArr.length()) {
                        val sObj = subsArr.getJSONObject(j)
                        val notesArr = sObj.optJSONArray("notes") ?: JSONArray()
                        val notes = buildList {
                            for (k in 0 until notesArr.length()) {
                                val nObj = notesArr.getJSONObject(k)
                                add(
                                    NoteItem(
                                        id = nObj.getString("id"),
                                        text = nObj.getString("text"),
                                        createdAt = nObj.optLong("createdAt", System.currentTimeMillis()),
                                        isDone = nObj.optBoolean("isDone", false),
                                        isPinned = nObj.optBoolean("isPinned", false),
                                        reminderAt = if (nObj.has("reminderAt") && !nObj.isNull("reminderAt"))
                                            nObj.optLong("reminderAt") else null
                                    )
                                )
                            }
                        }
                        add(
                            Subcategory(
                                id = sObj.getString("id"),
                                name = sObj.getString("name"),
                                notes = notes
                            )
                        )
                    }
                }
                add(
                    Topic(
                        id = tObj.getString("id"),
                        title = tObj.getString("title"),
                        iconType = iconType,
                        subcategories = subs
                    )
                )
            }
        }
    } catch (_: Exception) {
        defaultTopics()
    }
}

private fun formatTimestamp(ts: Long): String {
    val sdf = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
    return sdf.format(Date(ts))
}
