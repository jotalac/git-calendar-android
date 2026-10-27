package dev.jotalac.ui.theme

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color
import dev.jotalac.R

/** The git-calendar red (web-client themes.css --git-base). */
val GitRed = Color(0xFFF03C2E)

/** Tag color palette, identical to web-client/src/colors.ts. Tags store the [id], not the hex value. */
enum class TagColor(val id: String, val color: Color, @StringRes val label: Int) {
    Blue("blue", Color(0xFF00579F), R.string.color_blue),
    Green("green", Color(0xFF0D7B00), R.string.color_green),
    Red("red", Color(0xFFAF0800), R.string.color_red),
    Purple("purple", Color(0xFF7A218F), R.string.color_purple),
    Yellow("yellow", Color(0xFFFDC700), R.string.color_yellow),
    Orange("orange", Color(0xFFE85D04), R.string.color_orange),
    Pink("pink", Color(0xFFFF5BA1), R.string.color_pink),
    Teal("teal", Color(0xFF00A8AD), R.string.color_teal),
    Brown("brown", Color(0xFF755020), R.string.color_brown),
    Gray("gray", Color(0xFF777777), R.string.color_gray);

    companion object {
        fun of(id: String?): TagColor = entries.firstOrNull { it.id == id } ?: Gray
    }
}

/** Readable text color on top of an event chip of [background]. */
fun contentColorOn(background: Color): Color {
    val luminance = 0.299f * background.red + 0.587f * background.green + 0.114f * background.blue
    return if (luminance > 0.6f) Color(0xFF1A1A1A) else Color.White
}

// Web "dark" theme tokens
internal val DarkBg = Color(0xFF1A1A1A)
internal val DarkSidebar = Color(0xFF242424)
internal val DarkBtn = Color(0xFF2B2B2B)
internal val DarkBtnStrong = Color(0xFF333333)
internal val DarkGrid = Color(0xFF303030)
internal val DarkText = Color(0xFFD9D9D9)
internal val DarkTextSoft = Color(0xFFC1C1C1)
internal val DarkGit = Color(0xFFD83629)

// Web "light" theme tokens
internal val LightBg = Color(0xFFF2F2F2)
internal val LightSidebar = Color(0xFFEAEAEA)
internal val LightBtn = Color(0xFFD5D5D5)
internal val LightBtnStrong = Color(0xFFDDDDDD)
internal val LightGrid = Color(0xFFBDBDBD)
internal val LightText = Color(0xFF101010)
internal val LightTextSoft = Color(0xFF1A1A1A)
internal val LightGit = Color(0xFFD8362A)
