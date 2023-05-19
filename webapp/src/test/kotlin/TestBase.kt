package org.jeudego.pairgoth.test

import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.TestInfo
import org.slf4j.LoggerFactory
import java.io.File

abstract class TestBase {
    companion object {
        val logger = LoggerFactory.getLogger("test")

        @BeforeAll
        @JvmStatic
        fun prepare() {
        }
    }

    @BeforeEach
    fun before(testInfo: TestInfo) {
        val testName = testInfo.displayName.removeSuffix("()")
        logger.info("===== Running $testName =====")
    }
}