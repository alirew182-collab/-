package com.neuron.academy.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.neuron.academy.AppContainer
import com.neuron.academy.content.Curriculum
import com.neuron.academy.content.Subject
import com.neuron.academy.data.RestoreParser
import com.neuron.academy.data.RestorePlan
import com.neuron.academy.data.SubjectGradeRestore
import com.neuron.academy.ui.Routes
import com.neuron.academy.ui.components.ErrorBlock
import com.neuron.academy.ui.components.InfoCard
import com.neuron.academy.ui.components.LoadingBlock
import com.neuron.academy.ui.components.NeuronBubble
import com.neuron.academy.ui.containerViewModel
import com.neuron.academy.ui.theme.accent
import com.neuron.academy.ui.userMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Перенос прогресса из прошлой версии: описание словами, скриншоты или ручной выбор. */
class RestoreViewModel(private val c: AppContainer) : ViewModel() {
    /** Пройдено тем по паре предмет+класс. */
    val counts = mutableStateMapOf<Pair<Subject, Int>, Int>()
    val bosses = mutableStateMapOf<Pair<Subject, Int>, Boolean>()
    var level by mutableStateOf("")
    var crystals by mutableStateOf("")
    var streak by mutableStateOf("")
    var description by mutableStateOf("")
    var images by mutableStateOf<List<Uri>>(emptyList())
    var busy by mutableStateOf<String?>(null)
    var error by mutableStateOf<String?>(null)
    var notes by mutableStateOf<List<String>>(emptyList())
    var recognized by mutableStateOf(false)
    var done by mutableStateOf(false)

    val aiAvailable get() = c.router.isAvailable()
    val canReadScreenshots get() = c.tutor.canReadScreenshots

    init {
        RestorePlan.empty().items.forEach { counts[it.subject to it.grade] = 0; bosses[it.subject to it.grade] = false }
    }

    private fun fill(plan: RestorePlan) {
        plan.items.forEach { r ->
            val k = r.subject to r.grade
            // распознанное только добавляет: то, что ученик уже выставил вручную, не уменьшается
            counts[k] = maxOf(counts[k] ?: 0, r.completed)
            bosses[k] = (bosses[k] == true) || r.bossBeaten
        }
        plan.level?.let { level = it.toString() }
        plan.crystals?.let { crystals = it.toString() }
        plan.streak?.let { streak = it.toString() }
        notes = plan.notes
        recognized = true
    }

    fun recognizeText() {
        if (description.isBlank() || busy != null) return
        error = null; busy = "Нейрон читает описание…"
        viewModelScope.launch {
            try {
                fill(RestoreParser.parse(c.tutor.restoreFromText(description.trim())))
            } catch (e: Exception) {
                error = e.userMessage()
            } finally {
                busy = null
            }
        }
    }

    fun recognizeImages(context: Context) {
        if (images.isEmpty() || busy != null) return
        error = null; busy = "Нейрон рассматривает скриншоты… Это может занять минуту"
        viewModelScope.launch {
            val files = withContext(Dispatchers.IO) { prepareImages(context, images) }
            try {
                if (files.isEmpty()) throw IllegalStateException("Не удалось открыть скриншоты")
                fill(RestoreParser.parse(c.tutor.restoreFromScreenshots(files)))
            } catch (e: Exception) {
                error = e.userMessage()
            } finally {
                files.forEach { it.delete() }
                busy = null
            }
        }
    }

    /** Уменьшает скриншоты до 1280 px по длинной стороне: модель читает их быстрее. */
    private fun prepareImages(context: Context, uris: List<Uri>): List<File> {
        val dir = File(context.cacheDir, "restore_images").apply { deleteRecursively(); mkdirs() }
        return uris.mapIndexedNotNull { i, uri ->
            runCatching {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                var sample = 1
                while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 1280) sample *= 2
                val bmp = context.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
                } ?: return@runCatching null
                val f = File(dir, "shot_$i.jpg")
                f.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
                bmp.recycle()
                f
            }.getOrNull()
        }
    }

    fun setCount(k: Pair<Subject, Int>, n: Int) {
        counts[k] = n
        if (n < Curriculum.topicsFor(k.first, k.second).size) bosses[k] = false
    }

    fun setBoss(k: Pair<Subject, Int>, on: Boolean) {
        bosses[k] = on
        if (on) counts[k] = Curriculum.topicsFor(k.first, k.second).size
    }

    val hasAnything get() = counts.values.any { it > 0 } || bosses.values.any { it } || level.isNotBlank() || crystals.isNotBlank() || streak.isNotBlank()

    fun apply() {
        if (busy != null) return
        error = null; busy = "Восстанавливаю…"
        viewModelScope.launch {
            try {
                val items = counts.map { (k, n) -> SubjectGradeRestore(k.first, k.second, n, bosses[k] == true) }
                val (g, b) = c.progress.restore(items)
                c.game.restorePlayer(level.toIntOrNull(), crystals.toIntOrNull(), streak.toIntOrNull(), g, b)
                done = true
            } catch (e: Exception) {
                error = e.userMessage()
            } finally {
                busy = null
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RestoreScreen(nav: NavHostController) {
    val vm = containerViewModel { RestoreViewModel(it) }
    val ctx = LocalContext.current
    var confirm by remember { mutableStateOf(false) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(6)) { uris ->
        if (uris.isNotEmpty()) vm.images = uris
    }

    Scaffold(topBar = {
        TopAppBar(title = { Text("Перенос прогресса") }, navigationIcon = { IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") } })
    }) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (vm.done) {
                item {
                    NeuronBubble("Прогресс восстановлен. Пройденные темы отмечены, следующие открыты", "skin_classic")
                }
                item {
                    Button(onClick = { nav.navigate(Routes.HOME) { popUpTo(Routes.HOME) { inclusive = true } } }, modifier = Modifier.fillMaxWidth()) { Text("На карту") }
                }
                return@LazyColumn
            }
            item {
                Text(
                    "Расскажи, где ты остановился в прошлой версии, или покажи скриншоты — Нейрон отметит пройденные темы. " +
                        "Перед восстановлением всё можно проверить и поправить вручную ниже.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            item {
                InfoCard("Описать словами") {
                    Column {
                        OutlinedTextField(
                            vm.description, { vm.description = it.take(2000) },
                            modifier = Modifier.fillMaxWidth(), minLines = 4,
                            placeholder = { Text("Например: алгебра 9 класс — дошёл до квадратных уравнений, физику 9 класса прошёл полностью, химия — до оксидов. Уровень 7, кристаллов 340, серия 5 дней") },
                        )
                        Spacer(Modifier.height(8.dp))
                        FilledTonalButton(onClick = vm::recognizeText, enabled = vm.description.isNotBlank() && vm.busy == null && vm.aiAvailable) { Text("Распознать описание") }
                        if (!vm.aiAvailable) {
                            Text("Для распознавания нужен ИИ: скачай встроенную модель в «Настройках ИИ» или заполни всё вручную ниже.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            item {
                InfoCard("Скриншоты") {
                    Column {
                        Text("Подойдут скриншоты карты каждого предмета и профиля из старой версии. До 6 штук.", style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            OutlinedButton(onClick = { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, enabled = vm.busy == null) {
                                Text(if (vm.images.isEmpty()) "Выбрать скриншоты" else "Выбрано: ${vm.images.size}")
                            }
                            FilledTonalButton(onClick = { vm.recognizeImages(ctx) }, enabled = vm.images.isNotEmpty() && vm.busy == null && vm.canReadScreenshots) { Text("Распознать") }
                        }
                        if (!vm.canReadScreenshots) {
                            Text(
                                "Скриншоты читает встроенная модель Gemma 4 («Нейрон» или «Нейрон Про» в «Настройках ИИ»). " +
                                    "Если её нет — опиши прогресс словами или заполни вручную.",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            vm.busy?.let { item { LoadingBlock(it) } }
            vm.error?.let { item { ErrorBlock(it) } }
            if (vm.notes.isNotEmpty()) item {
                ErrorBlock(vm.notes.joinToString("\n"))
            }
            if (vm.recognized) item {
                Text("Нейрон заполнил данные ниже. Проверь их перед восстановлением", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            }

            Curriculum.grades.forEach { g ->
                item(key = "grade_$g") {
                    InfoCard("$g класс") {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Subject.entries.forEach { s -> SubjectRestoreRow(vm, s, g) }
                        }
                    }
                }
            }

            item {
                InfoCard("Игрок") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Оставь пустым то, чего не помнишь", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        NumberField("Уровень", vm.level) { vm.level = it }
                        NumberField("Кристаллы", vm.crystals) { vm.crystals = it }
                        NumberField("Серия дней", vm.streak) { vm.streak = it }
                    }
                }
            }

            item {
                Button(onClick = { confirm = true }, enabled = vm.hasAnything && vm.busy == null, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Восстановить прогресс") }
                Spacer(Modifier.height(4.dp))
                Text("Уже пройденное в этой версии не пропадёт: прогресс только добавляется.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }

    if (confirm) {
        val topics = vm.counts.values.sum()
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Восстановить прогресс?") },
            text = { Text("Будет отмечено пройденными тем: $topics. Стражи пройденных разделов засчитаются, нужные классы откроются.") },
            confirmButton = { TextButton(onClick = { confirm = false; vm.apply() }) { Text("Восстановить") } },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Отмена") } },
        )
    }
}

@Composable
private fun SubjectRestoreRow(vm: RestoreViewModel, s: Subject, g: Int) {
    val k = s to g
    val topics = remember(s, g) { Curriculum.topicsFor(s, g) }
    val n = vm.counts[k] ?: 0
    val boss = vm.bosses[k] == true
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(s.title, style = MaterialTheme.typography.titleSmall, color = s.accent, modifier = Modifier.weight(1f))
            Text("$n из ${topics.size}", style = MaterialTheme.typography.labelLarge)
        }
        Slider(
            value = n.toFloat(), onValueChange = { vm.setCount(k, it.toInt()) },
            valueRange = 0f..topics.size.toFloat(), steps = (topics.size - 1).coerceAtLeast(0),
        )
        Text(
            if (n == 0) "Ничего не пройдено" else "Последняя пройденная: «${topics[n - 1].title}»",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(boss, { vm.setBoss(k, it) })
            Spacer(Modifier.width(4.dp))
            Text("Финальный босс побеждён", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun NumberField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value, { v -> onChange(v.filter { it.isDigit() }.take(7)) },
        label = { Text(label) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
    )
}
