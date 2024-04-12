package org.jeudego.pairgoth.util

import com.diogonunes.jcolor.Ansi
import com.diogonunes.jcolor.AnsiFormat
import com.diogonunes.jcolor.Attribute
import java.util.*

private val blue = AnsiFormat(Attribute.BRIGHT_BLUE_TEXT())
private val green = AnsiFormat(Attribute.BRIGHT_GREEN_TEXT())
private val red = AnsiFormat(Attribute.BRIGHT_RED_TEXT())
private val bold = AnsiFormat(Attribute.BOLD())

object Colorizer {

    val colorize = System.getProperty("os.name").lowercase(Locale.ROOT).contains(Regex("nix|nux|aix"))

    fun blue(str: String) = if (colorize) Ansi.colorize(str, blue) else str
    fun green(str: String) = if (colorize) Ansi.colorize(str, green) else str
    fun red(str: String) = if (colorize) Ansi.colorize(str, red) else str
    fun bold(str: String) = if (colorize) Ansi.colorize(str, bold) else str
}
