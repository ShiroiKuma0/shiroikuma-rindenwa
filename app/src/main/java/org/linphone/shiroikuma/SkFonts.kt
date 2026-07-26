package org.linphone.shiroikuma

import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.util.TypedValue
import android.widget.TextView
import java.io.File
import org.linphone.R

/**
 * shiroikuma-rindenwa fork — external font support for the 白い熊 臨電話 UI.
 *
 * Imported fonts (ttf/otf, via SAF) are copied into `filesDir/sk_fonts/`. Each text slot stores
 * a font family (the filename, "" = system default, "@monospace" = monospace), a numeric weight
 * (0 = default) and a size in sp (0 = default). The picker renders every option **in its own
 * glyphs**, so a font is chosen by how it actually looks.
 */
object SkFonts {
    const val MONOSPACE_FONT = "@monospace"
    private val FONT_EXTENSIONS = setOf("ttf", "otf")
    private val typefaceCache = HashMap<String, Typeface>()

    data class FontOption(val displayName: String, val fileName: String)

    enum class WeightOption(val value: Int, val labelRes: Int) {
        DEFAULT(0, R.string.sk_weight_default),
        THIN(100, R.string.sk_weight_thin),
        LIGHT(300, R.string.sk_weight_light),
        REGULAR(400, R.string.sk_weight_regular),
        MEDIUM(500, R.string.sk_weight_medium),
        SEMIBOLD(600, R.string.sk_weight_semibold),
        BOLD(700, R.string.sk_weight_bold),
        BLACK(900, R.string.sk_weight_black),
        ;

        companion object {
            fun fromValue(value: Int): WeightOption =
                entries.firstOrNull { it.value == value } ?: DEFAULT
        }
    }

    /** Kept out of the SDK's own `filesDir` clutter by its own subdirectory name. */
    fun fontsDir(context: Context): File =
        File(context.filesDir, "sk_fonts").apply { if (!exists()) mkdirs() }

    /** Drop cached typefaces (font files may have been replaced by an import). */
    fun invalidateCache() {
        typefaceCache.clear()
    }

    fun availableFontOptions(context: Context): List<FontOption> {
        val options = mutableListOf(
            FontOption(context.getString(R.string.sk_font_system_default), ""),
            FontOption(context.getString(R.string.sk_font_monospace), MONOSPACE_FONT),
        )
        fontsDir(context).listFiles { _, name ->
            name.substringAfterLast('.', "").lowercase() in FONT_EXTENSIONS
        }?.sortedBy { it.name.lowercase() }?.forEach { file ->
            options.add(FontOption(file.nameWithoutExtension, file.name))
        }
        return options
    }

    fun fontDisplayName(context: Context, fileName: String): String = when {
        fileName.isEmpty() -> context.getString(R.string.sk_font_system_default)
        fileName == MONOSPACE_FONT -> context.getString(R.string.sk_font_monospace)
        else -> File(fileName).nameWithoutExtension
    }

    /** Copy a SAF-picked font into the fonts dir; returns the stored filename or null. */
    fun importFont(context: Context, uri: Uri): String? {
        val name = fontFileName(context, uri) ?: return null
        if (name.substringAfterLast('.', "").lowercase() !in FONT_EXTENSIONS) return null
        val bytes = try {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        } catch (e: Exception) {
            null
        } ?: return null
        return try {
            File(fontsDir(context), name).writeBytes(bytes)
            typefaceCache.remove(name)
            name
        } catch (e: Exception) {
            null
        }
    }

    private fun fontFileName(context: Context, uri: Uri): String? {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) {
                return cursor.getString(index)
            }
        }
        return uri.lastPathSegment
    }

    fun typeface(context: Context, fileName: String): Typeface = when {
        fileName.isEmpty() -> Typeface.DEFAULT
        fileName == MONOSPACE_FONT -> Typeface.MONOSPACE
        else -> typefaceCache.getOrPut(fileName) {
            try {
                Typeface.createFromFile(File(fontsDir(context), fileName))
            } catch (e: Exception) {
                Typeface.DEFAULT
            }
        }
    }

    /** Combine a family + numeric weight with a base style. */
    fun themeTypeface(
        context: Context,
        family: String,
        weight: Int,
        baseStyle: Int = Typeface.NORMAL,
    ): Typeface {
        val base = typeface(context, family)
        if (weight <= 0) {
            return if (family.isEmpty() && baseStyle == Typeface.NORMAL) {
                base
            } else {
                Typeface.create(base, baseStyle)
            }
        }
        val italic = baseStyle == Typeface.ITALIC || baseStyle == Typeface.BOLD_ITALIC
        val bold = baseStyle == Typeface.BOLD || baseStyle == Typeface.BOLD_ITALIC
        val effectiveWeight = if (bold) maxOf(weight, 700) else weight
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            Typeface.create(base, effectiveWeight, italic)
        } else {
            Typeface.create(base, if (effectiveWeight >= 600) Typeface.BOLD else Typeface.NORMAL)
        }
    }

    /** Apply a slot's font family/weight/size to a TextView (size only if user-set). */
    fun applyFont(view: TextView, slot: SkSlot, baseStyle: Int = Typeface.NORMAL) {
        val context = view.context
        view.typeface = themeTypeface(
            context,
            SkTheme.fontFamily(context, slot),
            SkTheme.fontWeight(context, slot),
            baseStyle,
        )
        val sizeSp = SkTheme.fontSize(context, slot)
        if (sizeSp > 0) {
            view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp.toFloat())
        }
    }

    /** Render the live sample line for the UI page. */
    fun showSample(view: TextView, slot: SkSlot) {
        val context = view.context
        view.text = context.getString(R.string.sk_font_sample)
        view.typeface = themeTypeface(
            context,
            SkTheme.fontFamily(context, slot),
            SkTheme.fontWeight(context, slot),
        )
        val sizeSp = SkTheme.fontSize(context, slot)
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, if (sizeSp > 0) sizeSp.toFloat() else 16f)
        view.setTextColor(SkTheme.color(context, slot))
    }
}
