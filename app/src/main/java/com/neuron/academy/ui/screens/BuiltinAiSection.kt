package com.neuron.academy.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.neuron.academy.ai.local.LocalModel
import com.neuron.academy.ai.local.ModelStatus
import com.neuron.academy.ui.LocalContainer
import com.neuron.academy.ui.components.Eyebrow
import com.neuron.academy.ui.components.MascotMood
import com.neuron.academy.ui.components.NeuronMascot
import com.neuron.academy.ui.components.Panel
import com.neuron.academy.ui.components.ThinProgress
import com.neuron.academy.ui.theme.GameColors

/** Установка и выбор встроенной модели, которая работает прямо на телефоне. */
@Composable
fun BuiltinAiSection() {
    val c = LocalContainer.current
    val llm = c.localLlm
    val statuses by llm.status.collectAsState()
    val active by llm.active.collectAsState()
    val mobile by c.settings.allowMobileDownload.collectAsState()
    val ram = remember { llm.deviceRamGb() }
    var confirmDelete by remember { mutableStateOf<LocalModel?>(null) }

    Column {
    Panel(color = MaterialTheme.colorScheme.primaryContainer, border = MaterialTheme.colorScheme.primaryContainer) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            NeuronMascot("skin_classic", 56.dp, if (llm.isActiveReady) MascotMood.HAPPY else MascotMood.NORMAL)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Eyebrow("Встроенный ИИ")
                Text(if (llm.isActiveReady) "Установлен и готов" else "Не установлен", style = MaterialTheme.typography.titleLarge)
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "Модель работает прямо на телефоне: не нужны ключи и интернет, переписка никуда не отправляется. " +
                "Скачивается один раз. Ответ занимает от нескольких секунд до минуты — зависит от телефона.",
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.height(4.dp))
        Text("Память телефона: %.1f ГБ ОЗУ".format(ram), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }

    Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        LocalModel.entries.forEach { m ->
            val st = statuses[m] ?: ModelStatus.NotDownloaded
            val isActive = m == active
            val weak = ram + 0.5 < m.minRamGb
            Panel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(m.title, style = MaterialTheme.typography.titleMedium)
                        Text("${m.sizeLabel} · от ${m.minRamGb} ГБ ОЗУ", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (st is ModelStatus.Ready && isActive) Text("Используется", style = MaterialTheme.typography.labelLarge, color = GameColors.Success)
                }
                Spacer(Modifier.height(4.dp))
                Text(m.description, style = MaterialTheme.typography.bodySmall)
                if (weak) Text("Для этого телефона может быть тяжеловата — лучше выбрать Лайт.", style = MaterialTheme.typography.bodySmall, color = GameColors.Fire)
                Spacer(Modifier.height(10.dp))
                when (st) {
                    is ModelStatus.NotDownloaded, is ModelStatus.Failed -> {
                        if (st is ModelStatus.Failed) Text(st.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        Button(onClick = { llm.setActive(m); llm.download(m, mobile) }) { Text("Скачать ${m.sizeLabel}") }
                    }
                    is ModelStatus.Downloading -> {
                        ThinProgress(st.progress, MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "%d%% · %.2f из %.1f ГБ".format((st.progress * 100).toInt(), st.downloadedBytes / 1e9, m.sizeBytes / 1e9),
                                style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = { llm.cancel(m) }) { Text("Отмена") }
                        }
                        if (!mobile) Text("Скачивание идёт только по Wi-Fi.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    is ModelStatus.Ready -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (!isActive) Button(onClick = { llm.setActive(m) }) { Text("Использовать") }
                        OutlinedButton(onClick = { confirmDelete = m }) { Text("Удалить") }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Разрешить скачивание по мобильной сети", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            Switch(mobile, { c.settings.setAllowMobileDownload(it) })
        }
    }
    }

    confirmDelete?.let { m ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Удалить модель?") },
            text = { Text("Освободится ${m.sizeLabel}. Чтобы снова пользоваться встроенным ИИ, модель придётся скачать заново.") },
            confirmButton = { TextButton(onClick = { llm.delete(m); confirmDelete = null }) { Text("Удалить") } },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Отмена") } },
        )
    }
}
