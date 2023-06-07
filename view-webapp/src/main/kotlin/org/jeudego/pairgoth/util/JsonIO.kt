package org.jeudego.pairgoth.util

import com.republicate.kson.Json
import java.io.Reader
import java.io.Writer


fun Json.Companion.parse(reader: Reader) = Json.Companion.parse(object: Json.Input {
    override fun read() = reader.read().toChar()
})

fun Json.toString(writer: Writer) = toString(object: Json.Output {
    override fun writeChar(c: Char): Json.Output {
        writer.write(c.code)
        return this
    }
    override fun writeString(s: String): Json.Output {
        writer.write(s)
        return this
    }
    override fun writeString(s: String, from: Int, to: Int): Json.Output {
        writer.write(s, from, to)
        return this
    }
})
