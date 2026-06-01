package com.terasumi.sellerkeyboard.keyboard

import net.objecthunter.exp4j.ExpressionBuilder
import java.math.BigDecimal

internal enum class CalculatorAction {
    Input,
    Backspace,
    Clear,
    NewLine,
    Evaluate,
}

internal data class CalculatorState(
    val expression: String = "",
    val result: String = "",
    val hasError: Boolean = false
)

internal fun reduceCalculatorState(
    state: CalculatorState,
    action: CalculatorAction,
    value: String = ""
): CalculatorState {
    return when (action) {
        CalculatorAction.Input -> {
            state.copy(
                expression = state.expression + value,
                result = "",
                hasError = false
            )
        }

        CalculatorAction.Backspace -> {
            if (state.expression.isEmpty()) {
                state.copy(result = "", hasError = false)
            } else {
                state.copy(
                    expression = state.expression.dropLast(1),
                    result = "",
                    hasError = false
                )
            }
        }

        CalculatorAction.Clear -> {
            CalculatorState()
        }

        CalculatorAction.NewLine -> {
            state.copy(
                expression = state.expression + "\n",
                result = "",
                hasError = false
            )
        }

        CalculatorAction.Evaluate -> {
            val output = evaluateExpression(state.expression)
            if (output == null) {
                state.copy(result = "", hasError = true)
            } else {
                state.copy(result = output, hasError = false)
            }
        }
    }
}

internal fun resolveResultForCommit(state: CalculatorState): Pair<CalculatorState, String?> {
    val output = state.result.takeIf { it.isNotBlank() } ?: evaluateExpression(state.expression)
    return if (output == null) {
        state.copy(hasError = true, result = "") to null
    } else {
        state.copy(result = output, hasError = false) to output
    }
}

internal fun evaluateExpression(expression: String): String? {
    val normalized = expression
        .replace("\n", "")
        .replace("√", "sqrt")
        .trim()

    if (normalized.isBlank()) {
        return null
    }

    val value = runCatching {
        ExpressionBuilder(normalized).build().evaluate()
    }.getOrNull() ?: return null

    if (!value.isFinite()) {
        return null
    }

    return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString()
}
