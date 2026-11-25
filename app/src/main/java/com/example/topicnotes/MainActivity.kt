package com.example.topicnotes

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

enum class TopicIconType(val label: String) {
    CAR("Машина"),
    GARAGE("Гараж"),
    HOME("Дом"),
    BUILD("Ремонт"),
    LIST("Список"),
    STAR("Важное");

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
    val createdAt: Long
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

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
    var topics by remember { mutableStateOf(loadTopics(storageFile)) }

    var selectedTopicId by remember {
        mutableStateOf(topics.firstOrNull()?.id ?: "")
    }
    val currentTopic = topics.find { it.id == selectedTopicId } ?: topics.firstOrNull()

    var selectedSubId by remember(currentTopic?.id) {
        mutableStateOf(currentTopic?.subcategories?.firstOrNull()?.id ?: "")
    }
    val currentSub = currentTopic?.subcategories?.find { it.id == selectedSubId }
        ?: currentTopic?.subcategories?.firstOrNull()

    var noteText by remember { mutableStateOf("") }
    var showAddTopicDialog by remember { mutableStateOf(false) }
    var showAddSubDialog by remember { mutableStateOf(false) }

    fun persist(updated: List<Topic>) {
        topics = updated
        saveTopics(storageFile, updated)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Тематический блокнот") },
                actions = {
                    IconButton(onClick = { showAddTopicDialog = true }) {
                        Icon(Icons.Default.CreateNewFolder, contentDescription = "Добавить тему")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Направления", style = MaterialTheme.typography.labelLarge)
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
                    TextButton(onClick = { showAddSubDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Подкатегория")
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
                OutlinedTextField(
                    value = noteText,
                    onValueChange = { noteText = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Заметка в «${currentTopic.title} / ${currentSub.name}»") },
                    minLines = 2,
                    maxLines = 4
                )

                Button(
                    onClick = {
                        val clean = noteText.trim()
                        if (clean.isNotEmpty()) {
                            val newNote = NoteItem(
                                id = UUID.randomUUID().toString(),
                                text = clean,
                                createdAt = System.currentTimeMillis()
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
                            noteText = ""
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Save, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Сохранить заметку")
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(currentSub.notes, key = { it.id }) { note ->
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Top
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(note.text, style = MaterialTheme.typography.bodyLarge)
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = formatTimestamp(note.createdAt),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        val updated = topics.map { t ->
                                            if (t.id != currentTopic.id) t
                                            else t.copy(
                                                subcategories = t.subcategories.map { s ->
                                                    if (s.id != currentSub.id) s
                                                    else s.copy(notes = s.notes.filterNot { it.id == note.id })
                                                }
                                            )
                                        }
                                        persist(updated)
                                    }
                                ) {
                                    Icon(Icons.Default.DeleteOutline, contentDescription = "Удалить")
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

    if (showAddTopicDialog) {
        AddTopicDialog(
            onDismiss = { showAddTopicDialog = false },
            onConfirm = { title, iconType ->
                val newSub = Subcategory(id = UUID.randomUUID().toString(), name = "Общее")
                val newTopic = Topic(
                    id = UUID.randomUUID().toString(),
                    title = title,
                    iconType = iconType,
                    subcategories = listOf(newSub)
                )
                val updated = topics + newTopic
                persist(updated)
                selectedTopicId = newTopic.id
                selectedSubId = newSub.id
                showAddTopicDialog = false
            }
        )
    }

    if (showAddSubDialog && currentTopic != null) {
        AddSubcategoryDialog(
            onDismiss = { showAddSubDialog = false },
            onConfirm = { subName ->
                val newSub = Subcategory(id = UUID.randomUUID().toString(), name = subName)
                val updated = topics.map { t ->
                    if (t.id != currentTopic.id) t
                    else t.copy(subcategories = t.subcategories + newSub)
                }
                persist(updated)
                selectedSubId = newSub.id
                showAddSubDialog = false
            }
        )
    }
}

@Composable
private fun AddTopicDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, TopicIconType) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var chosenIcon by remember { mutableStateOf(TopicIconType.LIST) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Новое направление") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    singleLine = true,
                    label = { Text("Название (например, Участок)") }
                )
                Text("Иконка:", fontWeight = FontWeight.Medium)
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
            TextButton(
                onClick = {
                    val clean = title.trim()
                    if (clean.isNotEmpty()) onConfirm(clean, chosenIcon)
                }
            ) { Text("Создать") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        }
    )
}

@Composable
private fun AddSubcategoryDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var name by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Новая подкатегория") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                label = { Text("Например: ТО, Регламент, Хотелки") }
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val clean = name.trim()
                    if (clean.isNotEmpty()) onConfirm(clean)
                }
            ) { Text("Добавить") }
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
                                        createdAt = nObj.optLong("createdAt", System.currentTimeMillis())
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
