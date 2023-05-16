package org.jeudego.pairgoth.test

import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.TestInfo
import org.slf4j.LoggerFactory

abstract class TestBase {
    companion object {
        val logger = LoggerFactory.getLogger("test")
        private var testClassName: String? = null

        @BeforeAll
        @JvmStatic
        fun prepare() {
            testClassName = this::class.simpleName
        }
    }

    @BeforeEach
    fun before(testInfo: TestInfo) {
        val testName = testInfo.displayName.removeSuffix("()")
        logger.info("===== Running $testClassName.$testName =====")
    }
}