package com.terasumi.sellerkeyboard.ime

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthenticationFieldDetectorTest {
    @Test
    fun explicitOtpAndVerificationHints_areDetectedCaseInsensitively() {
        assertTrue(AuthenticationFieldDetector.isAuthenticationValue("OTP code"))
        assertTrue(AuthenticationFieldDetector.isAuthenticationValue(null, "Verification Code"))
        assertTrue(AuthenticationFieldDetector.isAuthenticationValue("Enter your 2FA value"))
    }

    @Test
    fun ordinarySellerFields_areNotAuthenticationValues() {
        assertFalse(AuthenticationFieldDetector.isAuthenticationValue("Order code"))
        assertFalse(AuthenticationFieldDetector.isAuthenticationValue("Discount code"))
        assertFalse(AuthenticationFieldDetector.isAuthenticationValue(null, null))
    }
}
