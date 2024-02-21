package org.jeudego.pairgoth.util

import com.republicate.kson.Json
import java.io.File
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.sql.DriverManager

object CredentialsChecker {
    private const val CREDENTIALS_DB = "pairgoth.db"
    private val hasher = MessageDigest.getInstance("SHA-256")
    @OptIn(ExperimentalStdlibApi::class)
    fun check(email: String, password: String): Json.Object? {
        initDatabase()
        val sha256 = hasher.digest(password.toByteArray(StandardCharsets.UTF_8)).toHexString()
        DriverManager.getConnection("jdbc:sqlite:$CREDENTIALS_DB").use { conn ->
            val rs =
                conn.prepareStatement("SELECT 1 FROM cred WHERE email = ? AND password = ?").apply {
                    setString(1, email)
                    setString(2, password)
                }.executeQuery()
            return if (rs.next()) Json.Object("email" to email) else null
        }
    }

    @Synchronized
    fun initDatabase() {
        if (!File(CREDENTIALS_DB).exists()) {
            DriverManager.getConnection("jdbc:sqlite:$CREDENTIALS_DB").use { conn ->
                conn.createStatement().executeUpdate("CREATE TABLE cred (email VARCHAR(200) UNIQUE NOT NULL, password VARCHAR(200) NOT NULL)")
            }
        }
    }

}
