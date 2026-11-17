package com.neuron.academy.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.neuron.academy.data.ThemeMode
import com.neuron.academy.data.db.PlayerEntity
import com.neuron.academy.notifications.ReminderScheduler
import com.neuron.academy.ui.LocalContainer
import com.neuron.academy.ui.Routes
import com.neuron.academy.ui.components.InfoCard
import com.neuron.gamification.ShopCatalog
import com.neuron.gamification.ShopCategory
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(nav: NavHostController) {
    val c = LocalContainer.current
    val s = c.settings
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val themeMode by s.themeMode.collectAsState()
    val sound by s.sound.collectAsState()
    val vibration by s.vibration.collectAsState()
    val notif by s.notifications.collectAsState()
    val hour by s.notifyHour.collectAsState()
    val minute by s.notifyMinute.collectAsState()
    val threshold by s.gradeThreshold.collectAsState()
    val teacher by s.teacherMode.collectAsState()
    val keyMode by s.keyMode.collectAsState()
    val keys by c.keyStore.keys.collectAsState()
    val player by c.game.player.collectAsState(initial = PlayerEntity())

    var showTime by remember { mutableStateOf(false) }
    var pinDialog by remember { mutableStateOf(false) }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) { s.setNotifications(true); ReminderScheduler.schedule(ctx, hour, minute) }
    }

    Scaffold(topBar = {
        TopAppBar(title = { Text("Настройки") }, navigationIcon = { IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") } })
    }) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item {
                InfoCard("ИИ-репетитор и API-ключи", "🔑") {
                    Text("Ключей: ${keys.size} (активных: ${keys.count { it.enabled }}) · режим: ${keyMode.title}")
                    Text("Ключи хранятся зашифрованно (Android Keystore) и никуда, кроме выбранного провайдера, не отправляются.", style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(onClick = { nav.navigate(Routes.KEYS) }, modifier = Modifier.padding(top = 8.dp)) { Text("Управление ключами") }
                }
            }
            item {
                InfoCard("Оформление", "🎨") {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ThemeMode.entries.forEach { m -> FilterChip(themeMode == m, { s.setThemeMode(m) }, label = { Text(m.title) }) }
                    }
                    Text("Цветовая тема:", modifier = Modifier.padding(top = 8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ShopCatalog.items.filter { it.category == ShopCategory.THEME && it.id in player.owned }.forEach { t ->
                            FilterChip(player.theme == t.id, { scope.launch { c.game.equip(t.id) } }, label = { Text(t.emoji) })
                        }
                    }
                    TextButton(onClick = { nav.navigate(Routes.SHOP) }) { Text("Больше тем и скинов в магазине →") }
                }
            }
            item {
                InfoCard("Звук и вибрация", "🔊") {
                    SwitchRow("Звуки", sound) { s.setSound(it) }
                    SwitchRow("Вибрация при ответах", vibration) { s.setVibration(it) }
                }
            }
            item {
                InfoCard("Напоминания", "🔔") {
                    SwitchRow("Ежедневное напоминание", notif) { on ->
                        if (on) {
                            if (Build.VERSION.SDK_INT >= 33) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                            else { s.setNotifications(true); ReminderScheduler.schedule(ctx, hour, minute) }
                        } else { s.setNotifications(false); ReminderScheduler.cancel(ctx) }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Время: %02d:%02d".format(hour, minute), modifier = Modifier.weight(1f))
                        TextButton(onClick = { showTime = true }) { Text("Изменить") }
                    }
                    Text("Нейрон пишет дружелюбно и без давления 🙂", style = MaterialTheme.typography.bodySmall)
                }
            }
            item {
                InfoCard("Обучение", "🎓") {
                    Text("Порог завершения класса: ${(threshold * 100).toInt()}% тем по каждому предмету + итоговые экзамены")
                    Slider(threshold, { s.setGradeThreshold((it * 20).toInt() / 20f) }, valueRange = 0.5f..1f, steps = 9)
                    Text("Тема засчитывается при результате теста от 70%.", style = MaterialTheme.typography.bodySmall)
                    SwitchRow("👩‍🏫 Режим учителя (открыть всё)", teacher) { on ->
                        if (on) pinDialog = true else s.setTeacherMode(false)
                    }
                    Text("Открывает все классы и темы для повторения. Защищён PIN-кодом.", style = MaterialTheme.typography.bodySmall)
                }
            }
            item {
                InfoCard("О приложении", "🤖") {
                    Text("Нейрон: Академия 1.0 — алгебра, физика и химия 9–11 классов с ИИ-репетитором.")
                    Text("Формулы: KaTeX (офлайн). ИИ: Anthropic, OpenAI, Gemini, OpenRouter, DeepSeek и любые OpenAI-совместимые серверы.", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }

    if (showTime) {
        val state = rememberTimePickerState(hour, minute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { showTime = false },
            confirmButton = {
                TextButton(onClick = {
                    s.setNotifyTime(state.hour, state.minute)
                    if (s.notifications.value) ReminderScheduler.schedule(ctx, state.hour, state.minute)
                    showTime = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showTime = false }) { Text("Отмена") } },
            text = { TimePicker(state) },
        )
    }

    if (pinDialog) {
        val hasPin = c.keyStore.hasTeacherPin()
        var pin by remember { mutableStateOf("") }
        var pin2 by remember { mutableStateOf("") }
        var error by remember { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = { pinDialog = false },
            title = { Text(if (hasPin) "Введите PIN учителя" else "Придумайте PIN учителя") },
            text = {
                Column {
                    OutlinedTextField(pin, { pin = it.filter(Char::isDigit).take(8) }, label = { Text("PIN (4–8 цифр)") }, singleLine = true,
                        visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword))
                    if (!hasPin) OutlinedTextField(pin2, { pin2 = it.filter(Char::isDigit).take(8) }, label = { Text("Повторите PIN") }, singleLine = true,
                        visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword))
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    when {
                        pin.length < 4 -> error = "Минимум 4 цифры"
                        hasPin && !c.keyStore.checkTeacherPin(pin) -> error = "Неверный PIN"
                        !hasPin && pin != pin2 -> error = "PIN не совпадает"
                        else -> {
                            if (!hasPin) c.keyStore.setTeacherPin(pin)
                            s.setTeacherMode(true)
                            pinDialog = false
                        }
                    }
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { pinDialog = false }) { Text("Отмена") } },
        )
    }
}

@Composable
fun SwitchRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, modifier = Modifier.weight(1f))
        Switch(checked, onChange)
    }
}
