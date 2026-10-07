package com.orihami.nagareyomi.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** The few player icons not included in material-icons-core (kept here to avoid the huge extended set). */
object ReaderIcons {
    private fun icon(name: String, block: ImageVector.Builder.() -> Unit): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply(block).build()

    val Pause: ImageVector = icon("Pause") {
        path(fill = SolidColor(Color.Black)) {
            moveTo(6f, 19f); horizontalLineToRelative(4f); verticalLineTo(5f); horizontalLineTo(6f); close()
            moveTo(14f, 5f); verticalLineToRelative(14f); horizontalLineToRelative(4f); verticalLineTo(5f); close()
        }
    }

    val SkipPrevious: ImageVector = icon("SkipPrevious") {
        path(fill = SolidColor(Color.Black)) {
            moveTo(6f, 6f); horizontalLineToRelative(2f); verticalLineToRelative(12f); horizontalLineTo(6f); close()
            moveTo(9.5f, 12f); lineToRelative(8.5f, 6f); verticalLineTo(6f); close()
        }
    }

    val SkipNext: ImageVector = icon("SkipNext") {
        path(fill = SolidColor(Color.Black)) {
            moveTo(6f, 18f); lineToRelative(8.5f, -6f); lineTo(6f, 6f); close()
            moveTo(16f, 6f); verticalLineToRelative(12f); horizontalLineToRelative(2f); verticalLineTo(6f); close()
        }
    }
}
