package com.lyape.notification

import org.junit.Assert.assertTrue
import org.junit.Test

class YapePackageTest {

    @Test
    fun `provisional package id has a valid Android package shape`() {
        val id = YapePackage.PROVISIONAL_PACKAGE_ID

        assertTrue("no debe estar vacío", id.isNotBlank())
        assertTrue(
            "debe tener forma de paquete Android: $id",
            Regex("^[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z][a-zA-Z0-9_]*)+$").matches(id),
        )
    }
}
