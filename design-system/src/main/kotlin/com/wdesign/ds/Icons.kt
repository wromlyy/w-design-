package com.wdesign.ds

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * W Design's own line icons (24-unit grid, 1.75 stroke, round caps). Drawn for this project, so no
 * third-party icon licence applies. Tinted by Icon(tint = …).
 */
object WIcons {
    private fun icon(name: String, vararg paths: String): ImageVector {
        val b = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
        for (d in paths) b.addPath(
            pathData = addPathNodes(d), stroke = SolidColor(Color.Black), strokeLineWidth = 1.75f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
        )
        return b.build()
    }

    private const val CIRCLE = "M12 3a9 9 0 1 0 0 18a9 9 0 1 0 0-18z"

    val Home = icon("home", "M3 10.5 12 3l9 7.5V20a1 1 0 0 1-1 1h-5v-6h-6v6H4a1 1 0 0 1-1-1z")
    val Compass = icon("compass", CIRCLE, "M15.5 8.5 13.5 13.5 8.5 15.5 10.5 10.5z")
    val Plus = icon("plus", "M12 5v14M5 12h14")
    val Layers = icon("layers", "M12 3 2 8l10 5 10-5z", "M2 13l10 5 10-5", "M2 18l10 5 10-5")
    val Settings = icon("settings", "M12 9a3 3 0 1 0 0 6a3 3 0 1 0 0-6z", "M12 2.5v3M12 18.5v3M2.5 12h3M18.5 12h3M5.3 5.3l2.1 2.1M16.6 16.6l2.1 2.1M5.3 18.7l2.1-2.1M16.6 7.4l2.1-2.1")
    val Lock = icon("lock", "M6 11h12v10H6z", "M8 11V7a4 4 0 0 1 8 0v4")
    val Phone = icon("smartphone", "M7 2h10a1 1 0 0 1 1 1v18a1 1 0 0 1-1 1H7a1 1 0 0 1-1-1V3a1 1 0 0 1 1-1z", "M11 18h2")
    val Package = icon("package", "M3 7.5 12 3l9 4.5v9L12 21l-9-4.5z", "M3 7.5 12 12l9-4.5", "M12 12v9")
    val Download = icon("download", "M12 3v12", "M7 10l5 5 5-5", "M4 20h16")
    val Upload = icon("upload", "M12 15V3", "M7 8l5-5 5 5", "M4 20h16")
    val Check = icon("check", "M5 12.5 10 17 19 7")
    val Close = icon("x", "M6 6l12 12M18 6 6 18")
    val Info = icon("info", CIRCLE, "M12 11v6", "M12 7.5v.5")
    val Alert = icon("alert", "M12 3.5 2.5 20h19z", "M12 10v4.5", "M12 17.5v.5")
    val Image = icon("image", "M4 4h16v16H4z", "M4 16l5-5 4 4 3-3 4 4", "M15 7.5a1.5 1.5 0 1 0 0 3a1.5 1.5 0 1 0 0-3z")
    val Heart = icon("heart", "M12 20s-8-4.6-8-10.5A4.5 4.5 0 0 1 12 6a4.5 4.5 0 0 1 8 3.5C20 15.4 12 20 12 20z")
    val Trash = icon("trash", "M4 7h16", "M9 7V4h6v3", "M6 7l1 13h10l1-13")
    val Copy = icon("copy", "M8 8h12v12H8z", "M4 16V4h12")
    val Search = icon("search", "M10.5 4a6.5 6.5 0 1 0 0 13a6.5 6.5 0 1 0 0-13z", "M15.5 15.5 20 20")
    val ChevronRight = icon("chevron-right", "M9 5l7 7-7 7")
    val Back = icon("back", "M15 5l-7 7 7 7")
    val Folder = icon("folder", "M3 6h6l2 2h10v11H3z")
    val Cpu = icon("cpu", "M7 7h10v10H7z", "M10 3v4M14 3v4M10 17v4M14 17v4M3 10h4M3 14h4M17 10h4M17 14h4")
    val Sparkles = icon("sparkles", "M12 3l1.8 5.2L19 10l-5.2 1.8L12 17l-1.8-5.2L5 10l5.2-1.8z", "M19 16v4M17 18h4")
    val Shield = icon("shield-check", "M12 3 4 6v6c0 5 3.5 8 8 9 4.5-1 8-4 8-9V6z", "M8.5 12l2.5 2.5 4.5-5")
    val Clock = icon("clock", CIRCLE, "M12 7v5l3 2")
    val Share = icon("share", "M12 3v12", "M8 7l4-4 4 4", "M5 12v8h14v-8")
    val Edit = icon("edit", "M4 20h4L19 9l-4-4L4 16z", "M13.5 6.5l4 4")
    val Wand = icon("wand", "M4 20 15 9", "M15 4v2M19 8h2M18 5l1.5-1.5M13 3.5V5", "M17.5 11.5l1 1")
    val Grid = icon("grid", "M4 4h7v7H4z", "M13 4h7v7h-7z", "M4 13h7v7H4z", "M13 13h7v7h-7z")
}
