package com.terasumi.sellerkeyboard.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CalculatorEngineTest {

    @Test
    fun evaluateExpression_returnsIntegerWithoutDecimal() {
        val result = evaluateExpression("1+2")
        assertEquals("3", result)
    }

    @Test
    fun evaluateExpression_supportsSqrtSymbol() {
        val result = evaluateExpression("√9")
        assertEquals("3", result)
    }

    @Test
    fun evaluateExpression_returnsNullForInvalidExpression() {
        val result = evaluateExpression("1+")
        assertNull(result)
    }

    @Test
    fun reduceCalculatorState_inputThenBackspace_updatesExpression() {
        val afterInput = reduceCalculatorState(
            state = CalculatorState(),
            action = CalculatorAction.Input,
            value = "12"
        )
        val afterBackspace = reduceCalculatorState(
            state = afterInput,
            action = CalculatorAction.Backspace
        )

        assertEquals("1", afterBackspace.expression)
        assertEquals("", afterBackspace.result)
        assertFalse(afterBackspace.hasError)
    }

    @Test
    fun reduceCalculatorState_evaluateInvalid_setsError() {
        val next = reduceCalculatorState(
            state = CalculatorState(expression = "1+"),
            action = CalculatorAction.Evaluate
        )

        assertTrue(next.hasError)
        assertEquals("", next.result)
    }

    @Test
    fun resolveResultForCommit_usesCachedResultWhenPresent() {
        val state = CalculatorState(expression = "1+2", result = "3", hasError = false)
        val (next, output) = resolveResultForCommit(state)

        assertEquals("3", output)
        assertEquals("3", next.result)
        assertFalse(next.hasError)
    }

    @Test
    fun resolveResultForCommit_evaluatesWhenResultMissing() {
        val state = CalculatorState(expression = "10/4", result = "", hasError = false)
        val (next, output) = resolveResultForCommit(state)

        assertEquals("2.5", output)
        assertEquals("2.5", next.result)
        assertFalse(next.hasError)
    }
}
