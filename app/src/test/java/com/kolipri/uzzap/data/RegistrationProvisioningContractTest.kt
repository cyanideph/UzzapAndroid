package com.kolipri.uzzap.data

import org.junit.Assert.assertEquals
import org.junit.Test

class RegistrationProvisioningContractTest {
    @Test fun registrationIdentityHasSafeMaximumLength() {
        assertEquals(40, normalizeRegistrationIdentity("a".repeat(80)).length)
    }

    @Test fun registrationIdentityCollapsesWhitespace() {
        assertEquals("Maria Santos", normalizeRegistrationIdentity(" Maria   Santos "))
    }
}
