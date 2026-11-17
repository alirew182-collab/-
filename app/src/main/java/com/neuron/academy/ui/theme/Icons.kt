package com.neuron.academy.ui.theme

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Functions
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import com.neuron.academy.content.Subject

val Subject.icon: ImageVector
    get() = when (this) {
        Subject.ALGEBRA -> Icons.Rounded.Functions
        Subject.PHYSICS -> Icons.Rounded.Bolt
        Subject.CHEMISTRY -> Icons.Rounded.Science
    }

/** Цвет предмета для текущей темы (светлой или тёмной). */
val Subject.accent: Color
    @Composable @ReadOnlyComposable
    get() = tint(MaterialTheme.colorScheme.background.luminance() < 0.5f)

