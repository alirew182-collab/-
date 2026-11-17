package com.neuron.academy.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.neuron.academy.ai.AiFeature
import com.neuron.academy.ai.AiProviderType
import com.neuron.academy.ai.ApiKeyConfig
import com.neuron.academy.ai.KeyBackup
import com.neuron.academy.ai.KeySelectionMode
import com.neuron.academy.data.db.KeyStatEntity
import com.neuron.academy.ui.LocalContainer
import com.neuron.academy.ui.Routes
import com.neuron.academy.ui.components.EmptyHint
import com.neuron.academy.ui.components.InfoCard
import com.neuron.academy.ui.theme.GameColors
import com.neuron.academy.ui.userMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class BackupAction { EXPORT, IMPORT }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KeysScreen(nav: NavHostController) {
    val c = LocalContainer.current
    val ctx = LocalContext.current
    val keys by c.keyStore.keys.collectAsState()
    val statsFlow = remember { c.keyStats.observeAll() }
    val stats by statsFlow.collectAsState(initial = emptyList())
    val mode by c.settings.keyMode.collectAsState()
    val featureMap by c.settings.featureKeyMap.collectAsState()
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val testStatus = remember { mutableStateMapOf<String, String>() }

    var backupAction by remember { mutableStateOf<BackupAction?>(null) }
    var password by remember { mutableStateOf("") }
    var pendingPassword by remember { mutableStateOf("") }
    var deleteId by remember { mutableStateOf<String?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            val msg = try {
                val data = KeyBackup.export(c.keyStore.keys.value, pendingPassword)
                withContext(Dispatchers.IO) { ctx.contentResolver.openOutputStream(uri)?.use { it.write(data.toByteArray()) } }
                "Экспортировано ключей: ${c.keyStore.keys.value.size} 🔐"
            } catch (e: Exception) { "Ошибка экспорта: ${e.userMessage()}" }
            snackbar.showSnackbar(msg)
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            val msg = try {
                val text = withContext(Dispatchers.IO) { ctx.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) } } ?: ""
                val list = withContext(Dispatchers.Default) { KeyBackup.import(text, pendingPassword) }
                "Импортировано новых ключей: ${c.keyStore.merge(list)}"
            } catch (e: Exception) { "Ошибка импорта: ${e.userMessage()}" }
            snackbar.showSnackbar(msg)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = { TopAppBar(title = { Text("🔑 API-ключи") }, navigationIcon = { IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") } }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = { nav.navigate(Routes.keyEdit("new")) }, icon = { Icon(Icons.Filled.Add, null) }, text = { Text("Добавить ключ") })
        },
    ) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                InfoCard("Режим выбора ключа", "🔀") {
                    KeySelectionMode.entries.forEach { m ->
                        Row(Modifier.fillMaxWidth().selectable(mode == m) { c.settings.setKeyMode(m) }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(mode == m, { c.settings.setKeyMode(m) })
                            Column { Text(m.title, fontWeight = FontWeight.SemiBold); Text(m.description, style = MaterialTheme.typography.bodySmall) }
                        }
                    }
                    Text("При ошибке (429, 401, 5xx, таймаут) запрос автоматически уходит на следующий ключ.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
                }
            }
            if (mode == KeySelectionMode.MANUAL) item {
                InfoCard("Ключ для каждой функции", "🧩") {
                    AiFeature.entries.forEach { f ->
                        FeatureKeyRow(f, keys, featureMap[f]) { c.settings.setFeatureKey(f, it) }
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { backupAction = BackupAction.EXPORT; password = "" }, enabled = keys.isNotEmpty(), modifier = Modifier.weight(1f)) { Text("📤 Экспорт") }
                    OutlinedButton(onClick = { backupAction = BackupAction.IMPORT; password = "" }, modifier = Modifier.weight(1f)) { Text("📥 Импорт") }
                }
            }
            if (keys.isEmpty()) item {
                EmptyHint("🔑", "Пока нет ключей. Добавь ключ Claude, OpenAI, Gemini, OpenRouter, DeepSeek или свой OpenAI-совместимый сервер — и Нейрон оживёт!")
            }
            items(keys.sortedByDescending { it.priority }, key = { it.id }) { k ->
                KeyCard(
                    k, stats.firstOrNull { it.keyId == k.id }, testStatus[k.id],
                    onToggle = { c.keyStore.setEnabled(k.id, it) },
                    onTest = {
                        testStatus[k.id] = "⏳ Проверяю…"
                        scope.launch {
                            val r = c.router.testKey(k)
                            testStatus[k.id] = r.getOrElse { it.message ?: "Ошибка" }
                        }
                    },
                    onEdit = { nav.navigate(Routes.keyEdit(k.id)) },
                    onDelete = { deleteId = k.id },
                )
            }
        }
    }

    backupAction?.let { action ->
        AlertDialog(
            onDismissRequest = { backupAction = null },
            title = { Text(if (action == BackupAction.EXPORT) "Пароль для файла" else "Пароль от файла") },
            text = {
                Column {
                    Text(
                        if (action == BackupAction.EXPORT) "Файл будет зашифрован AES-256. Без пароля его не открыть — запомни пароль!"
                        else "Введи пароль, которым был зашифрован файл ключей.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    OutlinedTextField(password, { password = it }, label = { Text("Пароль (мин. 6 символов)") }, singleLine = true, visualTransformation = PasswordVisualTransformation())
                }
            },
            confirmButton = {
                TextButton(enabled = password.length >= 6, onClick = {
                    pendingPassword = password
                    backupAction = null
                    if (action == BackupAction.EXPORT) exportLauncher.launch("neuron-keys.json") else importLauncher.launch(arrayOf("application/json", "*/*"))
                }) { Text("Далее") }
            },
            dismissButton = { TextButton(onClick = { backupAction = null }) { Text("Отмена") } },
        )
    }
    deleteId?.let { id ->
        AlertDialog(
            onDismissRequest = { deleteId = null },
            title = { Text("Удалить ключ?") },
            text = { Text("Ключ будет удалён с устройства без возможности восстановления (если нет экспорта).") },
            confirmButton = { TextButton(onClick = { c.keyStore.delete(id); deleteId = null }) { Text("Удалить", color = GameColors.Error) } },
            dismissButton = { TextButton(onClick = { deleteId = null }) { Text("Отмена") } },
        )
    }
}

@Composable
private fun FeatureKeyRow(feature: AiFeature, keys: List<ApiKeyConfig>, selectedId: String?, onSelect: (String?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val selected = keys.firstOrNull { it.id == selectedId }
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(feature.title, modifier = Modifier.weight(1f))
        Box {
            TextButton(onClick = { open = true }) { Text(selected?.name ?: "Авто") }
            DropdownMenu(open, { open = false }) {
                DropdownMenuItem(text = { Text("Авто (по приоритету)") }, onClick = { onSelect(null); open = false })
                keys.filter { it.enabled }.forEach { k ->
                    DropdownMenuItem(text = { Text("${k.name} · ${k.model}") }, onClick = { onSelect(k.id); open = false })
                }
            }
        }
    }
}

private val dateFmt = SimpleDateFormat("dd.MM HH:mm", Locale("ru"))

@Composable
private fun KeyCard(
    k: ApiKeyConfig, stat: KeyStatEntity?, status: String?,
    onToggle: (Boolean) -> Unit, onTest: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit,
) {
    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(k.name, style = MaterialTheme.typography.titleMedium)
                    Text("${k.provider.title} · ${k.model}", style = MaterialTheme.typography.bodySmall)
                    Text("${k.maskedKey} · приоритет ${k.priority}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (k.baseUrl.isNotBlank()) Text(k.baseUrl, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
                Switch(k.enabled, onToggle)
            }
            Spacer(Modifier.height(6.dp))
            val s = stat ?: KeyStatEntity(k.id)
            Text(
                "📊 Запросов: ${s.requests} · ошибок: ${s.errors}" +
                    (if (s.lastUsed > 0) " · последний: ${dateFmt.format(Date(s.lastUsed))}" else "") +
                    (if (s.lastLatencyMs > 0) " · ${s.lastLatencyMs} мс" else ""),
                style = MaterialTheme.typography.bodySmall,
            )
            if (s.lastSuccess == false && s.lastError != null) Text("Последняя ошибка: ${s.lastError}", style = MaterialTheme.typography.bodySmall, color = GameColors.Error, maxLines = 2)
            status?.let { Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp)) }
            Row {
                TextButton(onClick = onTest) { Text("🧪 Проверить ключ") }
                TextButton(onClick = onEdit) { Text("Изменить") }
                TextButton(onClick = onDelete) { Text("Удалить", color = GameColors.Error) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KeyEditScreen(nav: NavHostController, keyId: String) {
    val c = LocalContainer.current
    val existing = remember(keyId) { c.keyStore.keys.value.firstOrNull { it.id == keyId } }
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var provider by remember { mutableStateOf(existing?.provider ?: AiProviderType.ANTHROPIC) }
    var model by remember { mutableStateOf(existing?.model ?: AiProviderType.ANTHROPIC.models.first()) }
    var baseUrl by remember { mutableStateOf(existing?.baseUrl ?: "") }
    var apiKey by remember { mutableStateOf(existing?.apiKey ?: "") }
    var enabled by remember { mutableStateOf(existing?.enabled ?: true) }
    var priority by remember { mutableStateOf((existing?.priority ?: (10 - c.keyStore.keys.value.size).coerceAtLeast(0)).toString()) }
    var showKey by remember { mutableStateOf(false) }
    var providerMenu by remember { mutableStateOf(false) }
    var modelMenu by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun build() = ApiKeyConfig(
        id = existing?.id ?: c.keyStore.newId(),
        name = name.ifBlank { provider.title },
        provider = provider, model = model.trim(), baseUrl = baseUrl.trim(), apiKey = apiKey.trim(),
        enabled = enabled, priority = priority.toIntOrNull() ?: 0,
    )
    val valid = model.isNotBlank() && (apiKey.isNotBlank() || provider == AiProviderType.CUSTOM) && (provider != AiProviderType.CUSTOM || baseUrl.isNotBlank())

    Scaffold(topBar = {
        TopAppBar(title = { Text(if (existing == null) "Новый ключ" else "Изменить ключ") }, navigationIcon = { IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") } })
    }) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(name, { name = it }, label = { Text("Название (например, «Мой Claude»)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Box {
                OutlinedButton(onClick = { providerMenu = true }, modifier = Modifier.fillMaxWidth()) { Text("Провайдер: ${provider.title}") }
                DropdownMenu(providerMenu, { providerMenu = false }) {
                    AiProviderType.entries.forEach { p ->
                        DropdownMenuItem(text = { Text(p.title) }, onClick = {
                            provider = p; providerMenu = false
                            model = p.models.firstOrNull() ?: ""
                        })
                    }
                }
            }
            Box {
                OutlinedTextField(
                    model, { model = it }, label = { Text("Модель (выбери или впиши вручную)") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    trailingIcon = { if (provider.models.isNotEmpty()) TextButton(onClick = { modelMenu = true }) { Text("Список") } },
                )
                DropdownMenu(modelMenu, { modelMenu = false }) {
                    provider.models.forEach { m -> DropdownMenuItem(text = { Text(m) }, onClick = { model = m; modelMenu = false }) }
                }
            }
            OutlinedTextField(
                baseUrl, { baseUrl = it },
                label = { Text(if (provider == AiProviderType.CUSTOM) "Base URL (обязательно)" else "Base URL (необязательно)") },
                placeholder = { Text(provider.defaultBaseUrl.ifBlank { "https://my-server.com/v1" }) },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            )
            OutlinedTextField(
                apiKey, { apiKey = it }, label = { Text("API-ключ") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = { IconButton(onClick = { showKey = !showKey }) { Icon(if (showKey) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, null) } },
            )
            OutlinedTextField(
                priority, { priority = it.filter { ch -> ch.isDigit() }.take(3) }, label = { Text("Приоритет (больше — раньше)") }, singleLine = true,
                modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Ключ включён", modifier = Modifier.weight(1f)); Switch(enabled, { enabled = it })
            }
            Text("🔒 Ключ сохраняется только на этом устройстве в зашифрованном виде.", style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(enabled = valid, onClick = {
                    testResult = "⏳ Проверяю…"
                    scope.launch { testResult = c.router.testKey(build()).fold({ it }, { it.message ?: "Ошибка" }) }
                }, modifier = Modifier.weight(1f)) { Text("🧪 Проверить") }
                Button(enabled = valid, onClick = { c.keyStore.upsert(build()); nav.popBackStack() }, modifier = Modifier.weight(1f)) { Text("Сохранить") }
            }
            testResult?.let { Text(it) }
            Spacer(Modifier.width(1.dp))
        }
    }
}
