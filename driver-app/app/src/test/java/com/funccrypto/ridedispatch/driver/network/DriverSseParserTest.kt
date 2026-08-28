package com.funccrypto.ridedispatch.driver.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DriverSseParserTest {

    @Test
    fun parsesEventAndDataOnlyWhenFrameIsComplete() {
        val parser = DriverSseParser()

        assertNull(parser.onLine("event: DRIVER_NEW_DISPATCH"))
        assertNull(parser.onLine("data: {\"attemptId\":7}"))
        val event = parser.onLine("")

        assertEquals("DRIVER_NEW_DISPATCH", event?.name)
        assertEquals("{\"attemptId\":7}", event?.data)
        assertNull(parser.flush())
    }

    @Test
    fun ignoresCommentsAndJoinsMultilineData() {
        val parser = DriverSseParser()

        parser.onLine(": heartbeat")
        parser.onLine("data: first")
        parser.onLine("data: second")
        val event = parser.onLine("")

        assertEquals("message", event?.name)
        assertEquals("first\nsecond", event?.data)
    }
}
