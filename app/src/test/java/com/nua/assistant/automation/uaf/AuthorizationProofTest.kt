package com.nua.assistant.automation.uaf

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthorizationProofTest {

    @Test
    fun `NONE_REQUIRED accepts any proof, including NotRequired`() {
        assertTrue(isAuthorizationSufficient(ConfirmationPolicy.NONE_REQUIRED, AuthorizationProof.NotRequired))
        assertTrue(isAuthorizationSufficient(ConfirmationPolicy.NONE_REQUIRED, AuthorizationProof.UserConfirmed(0L)))
    }

    @Test
    fun `CONFIRM_BEFORE_EXECUTE accepts only a real UserConfirmed proof`() {
        assertTrue(isAuthorizationSufficient(ConfirmationPolicy.CONFIRM_BEFORE_EXECUTE, AuthorizationProof.UserConfirmed(123L)))
    }

    @Test
    fun `CONFIRM_BEFORE_EXECUTE rejects NotRequired -- a policy can't be satisfied by omission`() {
        assertFalse(isAuthorizationSufficient(ConfirmationPolicy.CONFIRM_BEFORE_EXECUTE, AuthorizationProof.NotRequired))
    }
}
