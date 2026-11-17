package com.neuron.academy.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.neuron.academy.content.Topic
import com.neuron.academy.content.VideoLessons
import com.neuron.academy.ui.components.Eyebrow
import com.neuron.academy.ui.components.IconBadge
import com.neuron.academy.ui.components.Panel

/** Карточка «Видеоурок»: открывает урок на канале Mektep Online в YouTube. */
@Composable
fun VideoLessonCard(topic: Topic) {
    val ctx = LocalContext.current
    fun open(url: String) {
        runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(Icons.Rounded.PlayCircle, Color(0xFFD32F2F), 44.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Eyebrow("Видеоурок · Mektep Online")
                Text(topic.title, style = MaterialTheme.typography.titleMedium)
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "Посмотри объяснение на YouTube-канале ${VideoLessons.CHANNEL_HANDLE}, а потом возвращайся к примерам и контрольной.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Button(onClick = { open(VideoLessons.urlFor(topic)) }, modifier = Modifier.fillMaxWidth()) { Text("Смотреть урок на канале") }
            OutlinedButton(onClick = { open(VideoLessons.CHANNEL_URL) }, modifier = Modifier.fillMaxWidth()) { Text("Открыть канал") }
            OutlinedButton(onClick = { open(VideoLessons.youtubeSearch(topic)) }, modifier = Modifier.fillMaxWidth()) { Text("Найти другие видео по теме") }
        }
    }
}
