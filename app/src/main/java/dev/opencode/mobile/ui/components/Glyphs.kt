package dev.opencode.mobile.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * One place that maps intent to a glyph.
 *
 * Most icons come from `material-icons-core`. The five the core set lacks are
 * drawn here on the same 24dp grid and 1.8dp stroke, so the toolbar reads as one
 * family rather than two.
 */
object Glyphs {
    val Back: ImageVector = Icons.AutoMirrored.Filled.ArrowBack
    val Close: ImageVector = Icons.Filled.Close
    val Add: ImageVector = Icons.Filled.Add
    val Send: ImageVector = Icons.Filled.Send
    val Refresh: ImageVector = Icons.Filled.Refresh
    val Settings: ImageVector = Icons.Filled.Settings
    val Check: ImageVector = Icons.Filled.Check
    val ChevronDown: ImageVector = Icons.Filled.ArrowDropDown
    val ChevronRight: ImageVector = Icons.Filled.KeyboardArrowRight
    val Alert: ImageVector = Icons.Filled.Warning

    /**
     * A back arrow with a full-length shaft.
     *
     * The material `ArrowBack` has a stubby tail that reads as a chevron at the
     * top of a screen; this one is drawn on the same grid as the rest of the
     * family so the toolbar matches the glyphs inside the app.
     */
    val ArrowLeft: ImageVector = drawn("arrow-left") {
        stroked {
            moveTo(10.5f, 19.5f); lineTo(3f, 12f); lineTo(10.5f, 4.5f)
        }
        stroked {
            moveTo(3f, 12f); lineTo(21f, 12f)
        }
    }

    /** A tick, for the one row that is chosen. Round caps, like the family. */
    val Tick: ImageVector = drawn("tick") {
        stroked {
            moveTo(4.5f, 12.75f); lineTo(10.5f, 18.75f); lineTo(19.5f, 5.25f)
        }
    }

    /**
     * A pushpin.
     *
     * Head, collar and needle in one family's stroke weight, so a pinned row reads
     * at 16dp without becoming a filled blob next to the outline icons.
     */
    val Pin: ImageVector = drawn("pin") {
        stroked {
            moveTo(9f, 3.5f); lineTo(15f, 3.5f)
            lineTo(15f, 9f); lineTo(17f, 12.5f)
            lineTo(7f, 12.5f); lineTo(9f, 9f); close()
        }
        stroked {
            moveTo(12f, 12.5f); lineTo(12f, 20.5f)
        }
    }

    val Stop: ImageVector = drawn("stop") {
        filled { addRect(7f, 7f, 17f, 17f) }
    }

    val Folder: ImageVector = drawn("folder") {
        stroked {
            moveTo(3f, 7f); lineTo(10f, 7f); lineTo(12f, 9f); lineTo(21f, 9f)
            lineTo(21f, 19f); lineTo(3f, 19f); close()
        }
    }

    val Terminal: ImageVector = drawn("terminal") {
        stroked {
            moveTo(4f, 6f); lineTo(20f, 6f); lineTo(20f, 18f); lineTo(4f, 18f); close()
        }
        stroked {
            moveTo(8f, 10f); lineTo(11f, 13f); lineTo(8f, 16f)
        }
        stroked {
            moveTo(13f, 16f); lineTo(16f, 16f)
        }
    }

    val Sparkle: ImageVector = drawn("sparkle") {
        stroked {
            moveTo(12f, 4f); lineTo(13.6f, 10.4f); lineTo(20f, 12f); lineTo(13.6f, 13.6f)
            lineTo(12f, 20f); lineTo(10.4f, 13.6f); lineTo(4f, 12f); lineTo(10.4f, 10.4f)
            close()
        }
    }

    val Power: ImageVector = drawn("power") {
        stroked {
            moveTo(12f, 4f); lineTo(12f, 12f)
        }
        stroked {
            moveTo(8f, 7f)
            arcTo(6f, 6f, 0f, isMoreThanHalf = true, isPositiveArc = false, x1 = 16f, y1 = 7f)
        }
    }

    /** Three finder squares plus a data mark: reads as a QR at 20dp. */
    val QrCode: ImageVector = drawn("qr") {
        stroked {
            moveTo(4f, 4f); lineTo(10f, 4f); lineTo(10f, 10f); lineTo(4f, 10f); close()
        }
        stroked {
            moveTo(14f, 4f); lineTo(20f, 4f); lineTo(20f, 10f); lineTo(14f, 10f); close()
        }
        stroked {
            moveTo(4f, 14f); lineTo(10f, 14f); lineTo(10f, 20f); lineTo(4f, 20f); close()
        }
        filled { addRect(14f, 14f, 17f, 17f) }
        filled { addRect(18f, 18f, 20f, 20f) }
    }

    /**
     * A paperclip.
     *
     * Two nested hooks, drawn with explicit curves rather than arc flags: the
     * earlier version relied on arc sweeps that collapsed at 18dp and rendered as
     * a squiggle instead of a clip.
     */
    val Paperclip: ImageVector = drawn("paperclip") {
        stroked {
            // Outer hook: enters top-right, sweeps down and around to the left.
            moveTo(16.5f, 7.5f)
            lineTo(9f, 15f)
            curveTo(7.6f, 16.4f, 5.4f, 16.4f, 4.1f, 15f)
            curveTo(2.8f, 13.7f, 2.8f, 11.5f, 4.1f, 10.2f)
            lineTo(11.6f, 2.7f)
            curveTo(12.6f, 1.7f, 14.2f, 1.7f, 15.2f, 2.7f)
            curveTo(16.2f, 3.7f, 16.2f, 5.3f, 15.2f, 6.3f)
            lineTo(8.1f, 13.4f)
        }
    }

    /**
     * A plain down arrow — "jump to the newest message".
     *
     * Deliberately has no tray or underline: with one it reads as a download
     * action rather than a scroll affordance.
     */
    val ArrowDown: ImageVector = drawn("arrow-down") {
        stroked {
            moveTo(12f, 5f); lineTo(12f, 19f)
        }
        stroked {
            moveTo(6f, 13f); lineTo(12f, 19f); lineTo(18f, 13f)
        }
    }

    /** Sliders — the agent/model/effort sheet. */
    val Tune: ImageVector = drawn("tune") {
        stroked {
            moveTo(4f, 8f); lineTo(20f, 8f)
        }
        stroked {
            moveTo(4f, 16f); lineTo(20f, 16f)
        }
        filled { addCircle(9f, 8f, 2.4f) }
        filled { addCircle(15f, 16f, 2.4f) }
    }

    /** A check in a circle — a finished tool call. */
    val CheckCircle: ImageVector = drawn("check-circle") {
        stroked {
            moveTo(12f, 3.5f)
            curveTo(16.7f, 3.5f, 20.5f, 7.3f, 20.5f, 12f)
            curveTo(20.5f, 16.7f, 16.7f, 20.5f, 12f, 20.5f)
            curveTo(7.3f, 20.5f, 3.5f, 16.7f, 3.5f, 12f)
            curveTo(3.5f, 7.3f, 7.3f, 3.5f, 12f, 3.5f)
            close()
        }
        stroked {
            moveTo(8f, 12.2f); lineTo(10.8f, 15f); lineTo(16.2f, 9.2f)
        }
    }

    /** Magnifier — session search. */    val Search: ImageVector = drawn("search") {
        stroked {
            moveTo(11f, 4f)
            curveTo(14.9f, 4f, 18f, 7.1f, 18f, 11f)
            curveTo(18f, 14.9f, 14.9f, 18f, 11f, 18f)
            curveTo(7.1f, 18f, 4f, 14.9f, 4f, 11f)
            curveTo(4f, 7.1f, 7.1f, 4f, 11f, 4f)
            close()
        }
        stroked {
            moveTo(16.2f, 16.2f); lineTo(20f, 20f)
        }
    }

    private fun drawn(name: String, build: ImageVector.Builder.() -> Unit): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply(build).build()

    private fun ImageVector.Builder.stroked(build: PathBuilder.() -> Unit) {
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.8f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
            pathBuilder = build,
        )
    }

    private fun ImageVector.Builder.filled(build: PathBuilder.() -> Unit) {
        path(
            fill = SolidColor(Color.Black),
            pathBuilder = build,
        )
    }

    private fun PathBuilder.addRect(left: Float, top: Float, right: Float, bottom: Float) {
        moveTo(left, top)
        lineTo(right, top)
        lineTo(right, bottom)
        lineTo(left, bottom)
        close()
    }

    private fun PathBuilder.addCircle(cx: Float, cy: Float, r: Float) {
        moveTo(cx - r, cy)
        arcTo(r, r, 0f, isMoreThanHalf = true, isPositiveArc = true, x1 = cx + r, y1 = cy)
        arcTo(r, r, 0f, isMoreThanHalf = true, isPositiveArc = true, x1 = cx - r, y1 = cy)
        close()
    }
}
