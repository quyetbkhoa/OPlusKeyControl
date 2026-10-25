package dev.opluskeycontrol.vector

import org.junit.Assert.*
import org.junit.Test

class RuntimeProfileTest {
    @Test fun onlyInventoriedFirmwarePairsAreAccepted() {
        assertTrue(RuntimeProfile.supports(36, "CPH2745", "CPH2745_16.0.10.601(EX01)"))
        assertTrue(RuntimeProfile.supports(37, "CPH2745", "CPH2745_17.0.0.102(EX01)"))
        assertFalse(RuntimeProfile.supports(36, "CPH2745", "CPH2745_17.0.0.102(EX01)"))
        assertFalse(RuntimeProfile.supports(37, "CPH2745", "CPH2745_16.0.10.601(EX01)"))
        assertFalse(RuntimeProfile.supports(37, "CPH2745", "CPH2745_17.0.0.103(EX01)"))
        assertFalse(RuntimeProfile.supports(37, "OTHER", "CPH2745_17.0.0.102(EX01)"))
        assertFalse(RuntimeProfile.supports(38, "CPH2745", "CPH2745_17.0.0.102(EX01)"))
    }
}
