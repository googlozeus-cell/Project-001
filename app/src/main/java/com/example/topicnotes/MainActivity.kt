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
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.AlarmClock
import android.provider.Settings
import android.widget.NumberPicker
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
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
    val reminderAt: Long? = null,
    val imageFileName: String? = null
)

data class Subcategory(
    val id: String,
    val name: String,
    val imageFileName: String? = null,
    val notes: List<NoteItem> = emptyList()
)

data class Topic(
    val id: String,
    val title: String,
    val iconType: TopicIconType,
    val imageFileName: String? = null,
    val subcategories: List<Subcategory>
)

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val title = intent.getStringExtra("extra_title") ?: "Напоминание из «Моих заметок»"
        val body = intent.getStringExtra("extra_body") ?: ""
        val notifId = intent.getIntExtra(
            "extra_notif_id",
            (System.currentTimeMillis() % Int.MAX_VALUE).toInt()
        )
        showNoteNotification(context, title, body, notifId)
    }
}

fun showNoteNotification(
    context: Context,
    title: String,
    body: String,
    notifId: Int = (System.currentTimeMillis() % Int.MAX_VALUE).toInt()
) {
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
        .setCategory(NotificationCompat.CATEGORY_ALARM)
        .setAutoCancel(true)
        .setContentIntent(pendingIntent)

    val hasPermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED

    if (hasPermission) {
        try {
            NotificationManagerCompat.from(context).notify(notifId, builder.build())
        } catch (_: SecurityException) {
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
            enableVibration(true)
        }
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(channel)
    }
}

fun openExactAlarmSettings(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        try {
            val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            return
        } catch (_: Exception) {
        }
    }
    openAppSettings(context)
}

fun openAppSettings(context: Context) {
    try {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (_: Exception) {
        Toast.makeText(context, "Откройте настройки приложения вручную", Toast.LENGTH_SHORT).show()
    }
}

fun scheduleNoteReminder(
    context: Context,
    note: NoteItem,
    topicTitle: String,
    subName: String
): Boolean {
    val triggerAt = note.reminderAt ?: return false
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

    val safeTime = if (triggerAt <= System.currentTimeMillis()) {
        System.currentTimeMillis() + 2000L
    } else {
        triggerAt
    }

    return try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, safeTime, pendingIntent)
                true
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, safeTime, pendingIntent)
                false
            }
        } else {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, safeTime, pendingIntent)
            true
        }
    } catch (_: SecurityException) {
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, safeTime, pendingIntent)
        false
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
    var draftNoteImage by remember { mutableStateOf<String?>(null) }
    var showQipPanel by remember { mutableStateOf(false) }

    var showAddTopicDialog by remember { mutableStateOf(false) }
    var editingTopic by remember { mutableStateOf<Topic?>(null) }
    var topicToDelete by remember { mutableStateOf<Topic?>(null) }

    var showAddSubDialog by remember { mutableStateOf(false) }
    var editingSub by remember { mutableStateOf<Subcategory?>(null) }
    var subToDelete by remember { mutableStateOf<Subcategory?>(null) }

    var editingNote by remember { mutableStateOf<NoteItem?>(null) }
    var noteToDelete by remember { mutableStateOf<NoteItem?>(null) }
    var fullscreenImageName by remember { mutableStateOf<String?>(null) }

    var pickDateForNote by remember { mutableStateOf(false) }
    var pendingTimeDialogMillis by remember { mutableStateOf<Long?>(null) }
    var showExactAlarmPrompt by remember { mutableStateOf(false) }

    val draftPhotoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            val saved = LocalMediaStorage.copyAndCompressImage(context, uri, 1280)
            if (saved != null) {
                LocalMediaStorage.deleteMediaFile(context, draftNoteImage)
                draftNoteImage = saved
            }
        }
    }

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
                            Icon(Icons.Default.Edit, contentDescription = "Изменить тему")
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
            verticalArrangement = Arrangement.spacedBy(8.dp)
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
                    val topicBmp = remember(topic.imageFileName) {
                        LocalMediaStorage.loadImageBitmap(context, topic.imageFileName)
                    }
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            selectedTopicId = topic.id
                            selectedSubId = topic.subcategories.firstOrNull()?.id ?: ""
                        },
                        label = { Text(topic.title) },
                        leadingIcon = {
                            if (topicBmp != null) {
                                Image(
                                    bitmap = topicBmp,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(20.dp)
                                        .clip(CircleShape)
                                )
                            } else {
                                Icon(
                                    imageVector = topic.iconType.toImageVector(),
                                    contentDescription = null,
                                    tint = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer else topic.iconType.accent,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
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
                                Icon(Icons.Default.Edit, contentDescription = "Изменить подкатегорию", modifier = Modifier.size(18.dp))
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
                        val subBmp = remember(sub.imageFileName) {
                            LocalMediaStorage.loadImageBitmap(context, sub.imageFileName)
                        }
                        FilterChip(
                            selected = sub.id == currentSub?.id,
                            onClick = { selectedSubId = sub.id },
                            label = { Text("${sub.name} (${sub.notes.size})") },
                            leadingIcon = if (subBmp != null) {
                                {
                                    Image(
                                        bitmap = subBmp,
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(18.dp)
                                            .clip(CircleShape)
                                    )
                                }
                            } else null
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
                                Text("Например: заменить фильтр, приложить фото детали или схему...")
                            },
                            label = { Text("Новая заметка") },
                            minLines = 2,
                            maxLines = 4
                        )

                        // Панель инструментов заметки: Напоминание, Фото, QIP-смайлы
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AssistChip(
                                onClick = { pickDateForNote = true },
                                label = {
                                    Text(
                                        if (draftReminder != null) "🔔 ${formatTimestamp(draftReminder!!)}"
                                        else "Напомнить"
                                    )
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.Alarm, contentDescription = null, modifier = Modifier.size(18.dp))
                                }
                            )

                            AssistChip(
                                onClick = { draftPhotoPicker.launch("image/*") },
                                label = { Text(if (draftNoteImage != null) "Фото выбрано" else "Фото") },
                                leadingIcon = {
                                    Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                                }
                            )

                            AssistChip(
                                onClick = { showQipPanel = !showQipPanel },
                                label = { Text("😊 QIP") }
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
                                    label = { Text("Будильник") },
                                    leadingIcon = {
                                        Icon(Icons.Default.AccessAlarm, contentDescription = null, modifier = Modifier.size(18.dp))
                                    }
                                )
                                IconButton(
                                    onClick = { draftReminder = null },
                                    modifier = Modifier.size(30.dp)
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "Убрать напоминание", modifier = Modifier.size(16.dp))
                                }
                            }
                        }

                        // Панель смайлов в стиле старого QIP
                        if (showQipPanel) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState())
                                    .background(
                                        MaterialTheme.colorScheme.surfaceContainerHighest,
                                        RoundedCornerShape(8.dp)
                                    )
                                    .padding(horizontal = 6.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                QipSmilesCatalog.items.forEach { smile ->
                                    TextButton(
                                        onClick = { noteText += "${smile.emoji} " },
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                        modifier = Modifier.defaultMinSize(minWidth = 36.dp, minHeight = 36.dp)
                                    ) {
                                        Text(smile.emoji, fontSize = 20.sp)
                                    }
                                }
                            }
                        }

                        // Превью прикреплённого фото
                        if (draftNoteImage != null) {
                            val draftBmp = remember(draftNoteImage) {
                                LocalMediaStorage.loadImageBitmap(context, draftNoteImage)
                            }
                            if (draftBmp != null) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Image(
                                        bitmap = draftBmp,
                                        contentDescription = "Выбранное фото",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(56.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                    )
                                    TextButton(onClick = {
                                        LocalMediaStorage.deleteMediaFile(context, draftNoteImage)
                                        draftNoteImage = null
                                    }) {
                                        Text("Убрать фото")
                                    }
                                }
                            }
                        }

                        Button(
                            onClick = {
                                val clean = noteText.trim()
                                if (clean.isNotEmpty() || draftNoteImage != null) {
                                    val newNote = NoteItem(
                                        id = UUID.randomUUID().toString(),
                                        text = clean.ifEmpty { "Фото-заметка" },
                                        createdAt = System.currentTimeMillis(),
                                        reminderAt = draftReminder,
                                        imageFileName = draftNoteImage
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
                                        val exactOk = scheduleNoteReminder(
                                            context,
                                            newNote,
                                            currentTopic.title,
                                            currentSub.name
                                        )
                                        if (!exactOk) {
                                            showExactAlarmPrompt = true
                                        }
                                    }
                                    noteText = ""
                                    draftReminder = null
                                    draftNoteImage = null
                                    showQipPanel = false
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
                        val noteBmp = remember(note.imageFileName) {
                            LocalMediaStorage.loadImageBitmap(context, note.imageFileName)
                        }
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
                                        .heightIn(min = 80.dp)
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

                                    if (noteBmp != null && note.imageFileName != null) {
                                        Image(
                                            bitmap = noteBmp,
                                            contentDescription = "Фото заметки",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(140.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .clickable { fullscreenImageName = note.imageFileName }
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

    // Полноэкранный просмотр фото из заметки
    if (fullscreenImageName != null) {
        val fullBmp = remember(fullscreenImageName) {
            LocalMediaStorage.loadImageBitmap(context, fullscreenImageName)
        }
        AlertDialog(
            onDismissRequest = { fullscreenImageName = null },
            confirmButton = {
                TextButton(onClick = { fullscreenImageName = null }) { Text("Закрыть") }
            },
            text = {
                if (fullBmp != null) {
                    Image(
                        bitmap = fullBmp,
                        contentDescription = "Просмотр фото",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 420.dp)
                    )
                }
            }
        )
    }

    if (showExactAlarmPrompt) {
        AlertDialog(
            onDismissRequest = { showExactAlarmPrompt = false },
            title = { Text("Разрешение на точные напоминания") },
            text = {
                Text(
                    "Чтобы уведомления приходили точно по времени, разрешите «Будильники и напоминания» и «Уведомления» в настройках приложения."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showExactAlarmPrompt = false
                    openExactAlarmSettings(context)
                }) { Text("Настройки будильников") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showExactAlarmPrompt = false
                    openAppSettings(context)
                }) { Text("Все настройки") }
            }
        )
    }

    if (showAddTopicDialog) {
        TopicFormDialog(
            dialogTitle = "Новая тема",
            initialTitle = "",
            initialIcon = TopicIconType.LIST,
            initialImageName = null,
            placeholder = "Например: Машина, Гараж, Дом или Участок",
            confirmLabel = "Создать",
            onDismiss = { showAddTopicDialog = false },
            onConfirm = { title, icon, imgName ->
                val newSub = Subcategory(id = UUID.randomUUID().toString(), name = "Общее")
                val newTopic = Topic(
                    id = UUID.randomUUID().toString(),
                    title = title,
                    iconType = icon,
                    imageFileName = imgName,
                    subcategories = listOf(newSub)
                )
                persist(topics + newTopic)
                selectedTopicId = newTopic.id
                selectedSubId = newSub.id
                showAddTopicDialog = false
            }
        )
    }

    if (editingTopic != null) {
        val target = editingTopic!!
        TopicFormDialog(
            dialogTitle = "Изменить тему",
            initialTitle = target.title,
            initialIcon = target.iconType,
            initialImageName = target.imageFileName,
            placeholder = "Название темы",
            confirmLabel = "Сохранить",
            onDismiss = { editingTopic = null },
            onConfirm = { title, icon, imgName ->
                if (target.imageFileName != null && target.imageFileName != imgName) {
                    LocalMediaStorage.deleteMediaFile(context, target.imageFileName)
                }
                persist(topics.map {
                    if (it.id == target.id) it.copy(title = title, iconType = icon, imageFileName = imgName) else it
                })
                editingTopic = null
            }
        )
    }

    if (topicToDelete != null) {
        val target = topicToDelete!!
        AlertDialog(
            onDismissRequest = { topicToDelete = null },
            title = { Text("Удалить тему?") },
            text = { Text("Тема «${target.title}» и все её подкатегории и заметки будут удалены.") },
            confirmButton = {
                TextButton(onClick = {
                    LocalMediaStorage.deleteMediaFile(context, target.imageFileName)
                    target.subcategories.forEach { s ->
                        LocalMediaStorage.deleteMediaFile(context, s.imageFileName)
                        s.notes.forEach { n -> LocalMediaStorage.deleteMediaFile(context, n.imageFileName) }
                    }
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

    if (showAddSubDialog && currentTopic != null) {
        SubcategoryFormDialog(
            dialogTitle = "Новая подкатегория",
            initialName = "",
            initialImageName = null,
            placeholder = "Например: ТО, Регламент, Закупки или Дела",
            confirmLabel = "Добавить",
            onDismiss = { showAddSubDialog = false },
            onConfirm = { name, imgName ->
                val newSub = Subcategory(
                    id = UUID.randomUUID().toString(),
                    name = name,
                    imageFileName = imgName
                )
                persist(topics.map { t ->
                    if (t.id != currentTopic.id) t
                    else t.copy(subcategories = t.subcategories + newSub)
                })
                selectedSubId = newSub.id
                showAddSubDialog = false
            }
        )
    }

    if (editingSub != null && currentTopic != null) {
        val target = editingSub!!
        SubcategoryFormDialog(
            dialogTitle = "Изменить подкатегорию",
            initialName = target.name,
            initialImageName = target.imageFileName,
            placeholder = "Название подкатегории",
            confirmLabel = "Сохранить",
            onDismiss = { editingSub = null },
            onConfirm = { name, imgName ->
                if (target.imageFileName != null && target.imageFileName != imgName) {
                    LocalMediaStorage.deleteMediaFile(context, target.imageFileName)
                }
                persist(topics.map { t ->
                    if (t.id != currentTopic.id) t
                    else t.copy(
                        subcategories = t.subcategories.map { s ->
                            if (s.id == target.id) s.copy(name = name, imageFileName = imgName) else s
                        }
                    )
                })
                editingSub = null
            }
        )
    }

    if (subToDelete != null && currentTopic != null) {
        val target = subToDelete!!
        AlertDialog(
            onDismissRequest = { subToDelete = null },
            title = { Text("Удалить подкатегорию?") },
            text = { Text("Подкатегория «${target.name}» и её заметки будут удалены.") },
            confirmButton = {
                TextButton(onClick = {
                    LocalMediaStorage.deleteMediaFile(context, target.imageFileName)
                    target.notes.forEach { n -> LocalMediaStorage.deleteMediaFile(context, n.imageFileName) }
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

    if (editingNote != null && currentTopic != null && currentSub != null) {
        val target = editingNote!!
        var editText by remember(target.id) { mutableStateOf(target.text) }
        var editReminder by remember(target.id) { mutableStateOf(target.reminderAt) }
        var editImageName by remember(target.id) { mutableStateOf(target.imageFileName) }

        val editPhotoPicker = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.GetContent()
        ) { uri ->
            if (uri != null) {
                val saved = LocalMediaStorage.copyAndCompressImage(context, uri, 1280)
                if (saved != null) {
                    editImageName = saved
                }
            }
        }

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
                            onClick = { editPhotoPicker.launch("image/*") },
                            label = { Text(if (editImageName != null) "Заменить фото" else "Добавить фото") }
                        )
                        if (editImageName != null) {
                            TextButton(onClick = { editImageName = null }) { Text("Удалить фото") }
                        }
                    }
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
                    if (clean.isNotEmpty() || editImageName != null) {
                        if (target.imageFileName != null && target.imageFileName != editImageName) {
                            LocalMediaStorage.deleteMediaFile(context, target.imageFileName)
                        }
                        val updatedNote = target.copy(
                            text = clean.ifEmpty { "Фото-заметка" },
                            reminderAt = editReminder,
                            imageFileName = editImageName
                        )
                        updateNoteInState(target.id) { updatedNote }
                        if (updatedNote.reminderAt != null) {
                            val exactOk = scheduleNoteReminder(
                                context,
                                updatedNote,
                                currentTopic.title,
                                currentSub.name
                            )
                            if (!exactOk) showExactAlarmPrompt = true
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

    if (noteToDelete != null) {
        val target = noteToDelete!!
        AlertDialog(
            onDismissRequest = { noteToDelete = null },
            title = { Text("Удалить заметку?") },
            text = { Text(target.text) },
            confirmButton = {
                TextButton(onClick = {
                    cancelNoteReminder(context, target.id)
                    LocalMediaStorage.deleteMediaFile(context, target.imageFileName)
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

    if (pendingTimeDialogMillis != null) {
        val baseDateMillis = pendingTimeDialogMillis!!
        val initCal = Calendar.getInstance().apply {
            if (draftReminder != null) timeInMillis = draftReminder!!
        }
        var selectedHour by remember { mutableIntStateOf(initCal.get(Calendar.HOUR_OF_DAY)) }
        var selectedMinute by remember { mutableIntStateOf(initCal.get(Calendar.MINUTE)) }

        AlertDialog(
            onDismissRequest = { pendingTimeDialogMillis = null },
            title = { Text("Выберите время") },
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = String.format(Locale.getDefault(), "%02d:%02d", selectedHour, selectedMinute),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        WheelNumberPicker(
                            minVal = 0,
                            maxVal = 23,
                            value = selectedHour,
                            onValueChange = { selectedHour = it }
                        )
                        Text(
                            ":",
                            style = MaterialTheme.typography.headlineSmall,
                            modifier = Modifier.padding(horizontal = 12.dp)
                        )
                        WheelNumberPicker(
                            minVal = 0,
                            maxVal = 59,
                            value = selectedMinute,
                            onValueChange = { selectedMinute = it }
                        )
                    }
                    Text(
                        "Прокручивайте цифры вверх или вниз (минуты 00–59)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val dateCal = Calendar.getInstance().apply { timeInMillis = baseDateMillis }
                    val resultCal = Calendar.getInstance().apply {
                        set(Calendar.YEAR, dateCal.get(Calendar.YEAR))
                        set(Calendar.MONTH, dateCal.get(Calendar.MONTH))
                        set(Calendar.DAY_OF_MONTH, dateCal.get(Calendar.DAY_OF_MONTH))
                        set(Calendar.HOUR_OF_DAY, selectedHour)
                        set(Calendar.MINUTE, selectedMinute)
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
private fun WheelNumberPicker(
    minVal: Int,
    maxVal: Int,
    value: Int,
    onValueChange: (Int) -> Unit
) {
    AndroidView(
        factory = { ctx ->
            NumberPicker(ctx).apply {
                minValue = minVal
                maxValue = maxVal
                wrapSelectorWheel = true
                setFormatter { i -> String.format(Locale.getDefault(), "%02d", i) }
                setOnValueChangedListener { _, _, newVal -> onValueChange(newVal) }
            }
        },
        update = { picker ->
            picker.minValue = minVal
            picker.maxValue = maxVal
            if (picker.value != value) {
                picker.value = value
            }
        },
        modifier = Modifier.width(84.dp)
    )
}

@Composable
private fun TopicFormDialog(
    dialogTitle: String,
    initialTitle: String,
    initialIcon: TopicIconType,
    initialImageName: String?,
    placeholder: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (String, TopicIconType, String?) -> Unit
) {
    val context = LocalContext.current
    var title by remember { mutableStateOf(initialTitle) }
    var chosenIcon by remember { mutableStateOf(initialIcon) }
    var imageName by remember { mutableStateOf(initialImageName) }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            val saved = LocalMediaStorage.copyAndCompressImage(context, uri, 256)
            if (saved != null) imageName = saved
        }
    }

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
                Text("Стандартный значок:", fontWeight = FontWeight.Medium)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    TopicIconType.entries.forEach { icon ->
                        FilterChip(
                            selected = chosenIcon == icon && imageName == null,
                            onClick = {
                                chosenIcon = icon
                                imageName = null
                            },
                            label = { Text(icon.label) },
                            leadingIcon = {
                                Icon(icon.toImageVector(), contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                        )
                    }
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AssistChip(
                        onClick = { imagePicker.launch("image/*") },
                        label = { Text(if (imageName != null) "Сменить картинку" else "Своя картинка из галереи") },
                        leadingIcon = { Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    )
                    if (imageName != null) {
                        TextButton(onClick = { imageName = null }) { Text("Убрать") }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val clean = title.trim()
                if (clean.isNotEmpty()) onConfirm(clean, chosenIcon, imageName)
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
    initialImageName: String?,
    placeholder: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (String, String?) -> Unit
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf(initialName) }
    var imageName by remember { mutableStateOf(initialImageName) }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            val saved = LocalMediaStorage.copyAndCompressImage(context, uri, 256)
            if (saved != null) imageName = saved
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(dialogTitle) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    placeholder = { Text(placeholder) },
                    label = { Text("Название подкатегории") }
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AssistChip(
                        onClick = { imagePicker.launch("image/*") },
                        label = { Text(if (imageName != null) "Сменить иконку" else "Картинка из галереи") },
                        leadingIcon = { Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    )
                    if (imageName != null) {
                        TextButton(onClick = { imageName = null }) { Text("Убрать") }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val clean = name.trim()
                if (clean.isNotEmpty()) onConfirm(clean, imageName)
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
        if (t.imageFileName != null) tObj.put("imageFileName", t.imageFileName)

        val subsArr = JSONArray()
        t.subcategories.forEach { s ->
            val sObj = JSONObject()
            sObj.put("id", s.id)
            sObj.put("name", s.name)
            if (s.imageFileName != null) sObj.put("imageFileName", s.imageFileName)

            val notesArr = JSONArray()
            s.notes.forEach { n ->
                val nObj = JSONObject()
                nObj.put("id", n.id)
                nObj.put("text", n.text)
                nObj.put("createdAt", n.createdAt)
                nObj.put("isDone", n.isDone)
                nObj.put("isPinned", n.isPinned)
                if (n.reminderAt != null) nObj.put("reminderAt", n.reminderAt)
                if (n.imageFileName != null) nObj.put("imageFileName", n.imageFileName)
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
                val tImg = if (tObj.has("imageFileName") && !tObj.isNull("imageFileName"))
                    tObj.optString("imageFileName") else null

                val subsArr = tObj.optJSONArray("subcategories") ?: JSONArray()
                val subs = buildList {
                    for (j in 0 until subsArr.length()) {
                        val sObj = subsArr.getJSONObject(j)
                        val sImg = if (sObj.has("imageFileName") && !sObj.isNull("imageFileName"))
                            sObj.optString("imageFileName") else null
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
                                            nObj.optLong("reminderAt") else null,
                                        imageFileName = if (nObj.has("imageFileName") && !nObj.isNull("imageFileName"))
                                            nObj.optString("imageFileName") else null
                                    )
                                )
                            }
                        }
                        add(
                            Subcategory(
                                id = sObj.getString("id"),
                                name = sObj.getString("name"),
                                imageFileName = sImg,
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
                        imageFileName = tImg,
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
