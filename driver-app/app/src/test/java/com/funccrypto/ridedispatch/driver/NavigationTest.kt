package com.funccrypto.ridedispatch.driver

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NavigationTest {

    @Test
    fun blankNavigationAddressIsRejectedBeforeUriConstruction() {
        assertNull(normalizeNavigationAddress("   \t\n"))
        assertEquals("扬州东站", normalizeNavigationAddress("  扬州东站  "))
    }
}
