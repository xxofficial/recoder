package com.recoder.stockledger.data.importer

import java.io.File
import java.util.Properties

object TestConfig {
    private val properties = Properties()

    init {
        val paths = listOf(
            "../Statements/passwords.properties",
            "Statements/passwords.properties",
            "../../Statements/passwords.properties",
            "../local.properties",
            "local.properties"
        )
        for (path in paths) {
            val file = File(path)
            if (file.exists()) {
                runCatching {
                    file.inputStream().use { properties.load(it) }
                }
            }
        }
    }

    fun getPassword(key: String, default: String): String {
        val envKey = "STATEMENT_PASSWORD_${key.uppercase()}"
        val envValue = System.getenv(envKey)
        if (!envValue.isNullOrBlank()) {
            return envValue
        }

        val propValue = properties.getProperty(key)
        if (!propValue.isNullOrBlank()) {
            return propValue
        }

        return default
    }
}
