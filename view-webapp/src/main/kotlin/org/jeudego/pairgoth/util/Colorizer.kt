package org.jeudego.pairgoth.util

import com.diogonunes.jcolor.Ansi
import com.diogonunes.jcolor.AnsiFormat
import com.diogonunes.jcolor.Attribute

private val blue = AnsiFormat(Attribute.BRIGHT_BLUE_TEXT())
private val green = AnsiFormat(Attribute.BRIGHT_GREEN_TEXT())
private val red = AnsiFormat(Attribute.BRIGHT_RED_TEXT())
private val bold = AnsiFormat(Attribute.BOLD())

object Colorizer {

    fun blue(str: String) = Ansi.colorize(str, blue)
    fun green(str: String) = Ansi.colorize(str, green)
    fun red(str: String) = Ansi.colorize(str, red)
    fun bold(str: String) = Ansi.colorize(str, bold)
}
