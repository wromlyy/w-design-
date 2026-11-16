package com.wdesign.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class ElementType { TEXT, SHAPE, IMAGE, GLASS_PANEL, WIDGET_REF, ICON_GRID, CLOCK, DATE }

/**
 * One element on an editor canvas. Geometry is expressed as fractions of the canvas so the same
 * design reflows across screen sizes. Free-form styling lives in [props] (see [Props] keys).
 */
@Serializable
data class CanvasElement(
    val id: String,
    val type: ElementType,
    val x: Float,
    val y: Float,
    val w: Float,
    val h: Float,
    val rotation: Float = 0f,
    val z: Int = 0,
    val opacity: Float = 1f,
    val locked: Boolean = false,
    val hidden: Boolean = false,
    val props: Map<String, String> = emptyMap(),
) {
    fun prop(key: String): String? = props[key]
    fun withProp(key: String, value: String?): CanvasElement =
        copy(props = if (value == null) props - key else props + (key to value))

    fun color(key: String, fallback: ArgbColor): ArgbColor = props[key]?.let(ArgbColor::parse) ?: fallback
    fun float(key: String, fallback: Float): Float = props[key]?.toFloatOrNull() ?: fallback
    fun assetPath(): String? = if (type == ElementType.IMAGE) props[Props.ASSET] else null

    val centerX: Float get() = x + w / 2f
    val centerY: Float get() = y + h / 2f

    /** True when this element's rectangle intersects the circle (cx, cy, r) in normalized space. */
    fun overlapsCircle(cx: Float, cy: Float, r: Float, aspect: Float): Boolean {
        // aspect = height / width; compare in width units.
        val nx = cx.coerceIn(x, x + w)
        val ny = cy.coerceIn(y, y + h)
        val dx = nx - cx
        val dy = (ny - cy) * aspect
        return dx * dx + dy * dy <= r * r
    }

    object Props {
        const val TEXT = "text"
        const val COLOR = "color"
        const val FILL = "fill"
        const val STROKE = "stroke"
        const val CORNER = "corner"          // dp-ish, relative to 360dp width
        const val SHAPE = "shape"            // rect | circle | pill | line
        const val ASSET = "asset"
        const val BLUR = "blur"
        const val TINT_ALPHA = "tintAlpha"
        const val FONT = "font"              // SystemFont name
        const val FONT_SIZE = "fontSize"     // sp
        const val WEIGHT = "weight"
        const val ALIGN = "align"
        const val WIDGET_ID = "widgetId"
        const val LABEL = "label"
    }
}
