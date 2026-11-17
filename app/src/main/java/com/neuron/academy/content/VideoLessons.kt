package com.neuron.academy.content

import android.net.Uri

/**
 * Видеоуроки с YouTube-канала «Mektep Online · Математика» (@mektep_online_matematika).
 * Для каждой темы открывается поиск по каналу с названием темы. Если для темы известно
 * конкретное видео, его ссылку можно добавить в [exact] — тогда откроется сразу оно.
 */
object VideoLessons {
    const val CHANNEL_HANDLE = "@mektep_online_matematika"
    const val CHANNEL_URL = "https://www.youtube.com/$CHANNEL_HANDLE"

    /** Точные ссылки на видео по id темы (можно дополнять). */
    val exact: Map<String, String> = emptyMap()

    /** Поисковый запрос по каналу для темы. */
    fun query(topic: Topic): String = "${topic.title} ${topic.grade} класс"

    fun urlFor(topic: Topic): String =
        exact[topic.id] ?: "$CHANNEL_URL/search?query=${Uri.encode(query(topic))}"

    /** Общий поиск по YouTube — если на канале нет урока по этой теме. */
    fun youtubeSearch(topic: Topic): String =
        "https://www.youtube.com/results?search_query=${Uri.encode("${topic.subject.title} ${query(topic)} урок")}"
}
