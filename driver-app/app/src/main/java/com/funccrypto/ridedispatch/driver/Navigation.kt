package com.funccrypto.ridedispatch.driver

internal fun normalizeNavigationAddress(address: String): String? =
    address.trim().takeIf { it.isNotEmpty() }
