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

    // Emit ANSI colour only when stdout can render it. An explicit `pairgoth.console.color` system
    // property wins ; otherwise detect terminal capability.
    val colorize: Boolean = System.getProperty("pairgoth.console.color")?.toBoolean() ?: terminalSupportsColor()

    // NO_COLOR / FORCE_COLOR overrides first, then require an attached console (so redirected or
    // daemon output stays clean), a non-dumb TERM, and on Windows a modern terminal.
    fun terminalSupportsColor(): Boolean {
        if (System.getenv("NO_COLOR") != null) return false
        if (System.getenv("FORCE_COLOR") != null || System.getenv("CLICOLOR_FORCE") == "1") return true
        if (System.console() == null) return false
        if (System.getenv("TERM") == "dumb") return false
        val os = System.getProperty("os.name").lowercase(Locale.ROOT)
        return if (os.contains("win")) System.getenv("WT_SESSION") != null || System.getenv("ConEmuANSI") == "ON"
        else true
    }

    fun blue(str: String) = if (colorize) Ansi.colorize(str, blue) else str
    fun green(str: String) = if (colorize) Ansi.colorize(str, green) else str
    fun red(str: String) = if (colorize) Ansi.colorize(str, red) else str
    fun bold(str: String) = if (colorize) Ansi.colorize(str, bold) else str
}
