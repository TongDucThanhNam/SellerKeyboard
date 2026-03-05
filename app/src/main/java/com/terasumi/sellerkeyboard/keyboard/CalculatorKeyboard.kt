package com.terasumi.sellerkeyboard.keyboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.automirrored.filled.KeyboardReturn
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.terasumi.sellerkeyboard.R
import com.terasumi.sellerkeyboard.keyboard.theme.LightKeyboardColors
import com.terasumi.sellerkeyboard.keyboard.theme.KeyboardColors

private enum class CalculatorButtonKind {
    Number,
    Operator,
    Utility,
    Danger,
    Confirm,
}

private data class CalculatorButtonSpec(
    val label: String,
    val action: CalculatorAction,
    val kind: CalculatorButtonKind,
    val value: String = label
)

private data class CalculatorUiMetrics(
    val panelCorner: Dp,
    val panelPadding: Dp,
    val rowSpacing: Dp,
    val cellSpacing: Dp,
    val buttonHeight: Dp,
    val actionHeight: Dp,
    val valueFontSize: Float,
    val iconSize: Dp
)

@Composable
fun CalculatorKeyboard(
    myColor: KeyboardColors,
    isLandscape: Boolean,
    onAction: (KeyboardAction) -> Unit,
) {
    var expression by rememberSaveable { mutableStateOf("") }
    var result by rememberSaveable { mutableStateOf("") }
    var hasError by rememberSaveable { mutableStateOf(false) }

    val expressionText = stringResource(R.string.enter_expression)
    val sendExpression = stringResource(R.string.send_expression)
    val sendResult = stringResource(R.string.send_result)
    val resultLabel = stringResource(R.string.result_label)
    val invalidExpression = stringResource(R.string.invalid_expression)

    val rows = remember(isLandscape) {
        if (isLandscape) landscapeCalculatorButtons else portraitCalculatorButtons
    }
    val metrics = remember(isLandscape) { calculatorUiMetrics(isLandscape) }

    val state = remember(expression, result, hasError) {
        CalculatorState(
            expression = expression,
            result = result,
            hasError = hasError
        )
    }

    fun applyState(next: CalculatorState) {
        expression = next.expression
        result = next.result
        hasError = next.hasError
    }

    val canSendExpression = expression.isNotBlank()
    val canSendResult = result.isNotBlank() || evaluateExpression(expression) != null

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = if (isLandscape) 8.dp else 10.dp,
                vertical = if (isLandscape) 4.dp else 6.dp
            ),
        verticalArrangement = Arrangement.spacedBy(metrics.rowSpacing)
    ) {
        CalculatorExpressionPanel(
            expression = expression,
            result = result,
            hasError = hasError,
            expressionPlaceholder = expressionText,
            resultLabel = resultLabel,
            invalidExpression = invalidExpression,
            myColor = myColor,
            metrics = metrics
        )

        CalculatorPrimaryActions(
            sendExpressionLabel = sendExpression,
            sendResultLabel = sendResult,
            canSendExpression = canSendExpression,
            canSendResult = canSendResult,
            myColor = myColor,
            metrics = metrics,
            onSendExpression = {
                onAction(KeyboardAction.KeyPress(expression))
            },
            onSendResult = {
                val (nextState, output) = resolveResultForCommit(state)
                applyState(nextState)
                if (output != null) {
                    onAction(KeyboardAction.KeyPress(output))
                }
            }
        )

        CalculatorButtonGrid(
            rows = rows,
            isLandscape = isLandscape,
            myColor = myColor,
            metrics = metrics,
            onPress = { button ->
                val nextState = reduceCalculatorState(
                    state = state,
                    action = button.action,
                    value = button.value
                )
                applyState(nextState)
            }
        )
    }
}

@Composable
private fun CalculatorExpressionPanel(
    expression: String,
    result: String,
    hasError: Boolean,
    expressionPlaceholder: String,
    resultLabel: String,
    invalidExpression: String,
    myColor: KeyboardColors,
    metrics: CalculatorUiMetrics
) {
    val panelShape = RoundedCornerShape(metrics.panelCorner)
    val panelBorderColor = myColor.keyBackground.copy(alpha = 0.35f)
    val panelColor = myColor.spaceBarBackground.copy(alpha = 0.14f)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(color = panelColor, shape = panelShape)
            .border(width = 1.dp, color = panelBorderColor, shape = panelShape)
            .padding(metrics.panelPadding),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Calculate,
                contentDescription = null,
                tint = myColor.iconColor.copy(alpha = 0.7f),
                modifier = Modifier.size(if (metrics.buttonHeight < 52.dp) 20.dp else 22.dp)
            )
            Text(
                text = expression.ifBlank { expressionPlaceholder },
                color = if (expression.isBlank()) {
                    myColor.iconColor.copy(alpha = 0.45f)
                } else {
                    myColor.iconColor
                },
                fontSize = if (metrics.buttonHeight < 68.dp) 20.sp else 22.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        when {
            hasError -> {
                Text(
                    text = invalidExpression,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelMedium
                )
            }

            result.isNotBlank() -> {
                Text(
                    text = "$resultLabel: $result",
                    color = myColor.iconColor.copy(alpha = 0.86f),
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun CalculatorPrimaryActions(
    sendExpressionLabel: String,
    sendResultLabel: String,
    canSendExpression: Boolean,
    canSendResult: Boolean,
    myColor: KeyboardColors,
    metrics: CalculatorUiMetrics,
    onSendExpression: () -> Unit,
    onSendResult: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(metrics.cellSpacing)
    ) {
        CalculatorActionButton(
            label = sendExpressionLabel,
            enabled = canSendExpression,
            myColor = myColor,
            metrics = metrics,
            emphasized = false,
            onClick = onSendExpression,
            modifier = Modifier.weight(1f)
        )

        CalculatorActionButton(
            label = sendResultLabel,
            enabled = canSendResult,
            myColor = myColor,
            metrics = metrics,
            emphasized = true,
            onClick = onSendResult,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun CalculatorActionButton(
    label: String,
    enabled: Boolean,
    myColor: KeyboardColors,
    metrics: CalculatorUiMetrics,
    emphasized: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(if (metrics.buttonHeight < 52.dp) 16.dp else 18.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (emphasized) myColor.keyActionBackground else myColor.spaceBarBackground,
            contentColor = myColor.iconColor,
            disabledContainerColor = if (emphasized) myColor.keyActionBackground.copy(alpha = 0.35f) else myColor.spaceBarBackground.copy(
                alpha = 0.35f
            ),
            disabledContentColor = myColor.iconColor.copy(alpha = 0.4f)
        ),
        modifier = modifier.height(metrics.actionHeight)
    ) {
        Text(
            text = label,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun CalculatorButtonGrid(
    rows: List<List<CalculatorButtonSpec>>,
    isLandscape: Boolean,
    myColor: KeyboardColors,
    metrics: CalculatorUiMetrics,
    onPress: (CalculatorButtonSpec) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(metrics.cellSpacing)
    ) {
        for (row in rows) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(metrics.cellSpacing),
                verticalAlignment = Alignment.CenterVertically
            ) {
                for (button in row) {
                    CalculatorCellButton(
                        button = button,
                        myColor = myColor,
                        isLandscape = isLandscape,
                        metrics = metrics,
                        onClick = { onPress(button) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun CalculatorCellButton(
    button: CalculatorButtonSpec,
    myColor: KeyboardColors,
    isLandscape: Boolean,
    metrics: CalculatorUiMetrics,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val containerColor = colorForButton(button = button, myColor = myColor)
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = myColor.iconColor
        ),
        shape = RoundedCornerShape(if (isLandscape) 14.dp else 16.dp),
        contentPadding = PaddingValues(0.dp),
        modifier = modifier.height(metrics.buttonHeight)
    ) {
        when (button.action) {
            CalculatorAction.Backspace -> {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Backspace,
                    contentDescription = "Backspace",
                    modifier = Modifier.size(metrics.iconSize)
                )
            }

            CalculatorAction.NewLine -> {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardReturn,
                    contentDescription = "Enter",
                    modifier = Modifier.size(metrics.iconSize)
                )
            }

            CalculatorAction.Clear -> {
                Icon(
                    imageVector = Icons.Default.Clear,
                    contentDescription = "Clear",
                    modifier = Modifier.size(metrics.iconSize)
                )
            }

            else -> {
                Text(
                    text = button.label,
                    fontSize = metrics.valueFontSize.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun colorForButton(
    button: CalculatorButtonSpec,
    myColor: KeyboardColors
): Color {
    return when (button.kind) {
        CalculatorButtonKind.Number -> myColor.spaceBarBackground.copy(alpha = 0.95f)
        CalculatorButtonKind.Operator -> myColor.keyBackground.copy(alpha = 0.82f)
        CalculatorButtonKind.Utility -> myColor.keyBackground.copy(alpha = 0.95f)
        CalculatorButtonKind.Danger -> MaterialTheme.colorScheme.error.copy(alpha = 0.24f)
        CalculatorButtonKind.Confirm -> myColor.keyActionBackground
    }
}

private fun calculatorUiMetrics(isLandscape: Boolean): CalculatorUiMetrics {
    return if (isLandscape) {
        CalculatorUiMetrics(
            panelCorner = 14.dp,
            panelPadding = 12.dp,
            rowSpacing = 8.dp,
            cellSpacing = 6.dp,
            buttonHeight = 64.dp,
            actionHeight = 56.dp,
            valueFontSize = 16f,
            iconSize = 24.dp
        )
    } else {
        CalculatorUiMetrics(
            panelCorner = 16.dp,
            panelPadding = 14.dp,
            rowSpacing = 10.dp,
            cellSpacing = 8.dp,
            buttonHeight = 70.dp,
            actionHeight = 58.dp,
            valueFontSize = 18f,
            iconSize = 26.dp
        )
    }
}

private fun input(label: String, kind: CalculatorButtonKind): CalculatorButtonSpec {
    return CalculatorButtonSpec(
        label = label,
        action = CalculatorAction.Input,
        kind = kind
    )
}

private val portraitCalculatorButtons = listOf(
    listOf(
        input("(", CalculatorButtonKind.Operator),
        input("1", CalculatorButtonKind.Number),
        input("2", CalculatorButtonKind.Number),
        input("3", CalculatorButtonKind.Number),
        input(")", CalculatorButtonKind.Operator),
        CalculatorButtonSpec("D", CalculatorAction.Backspace, CalculatorButtonKind.Utility)
    ),
    listOf(
        input("-", CalculatorButtonKind.Operator),
        input("4", CalculatorButtonKind.Number),
        input("5", CalculatorButtonKind.Number),
        input("6", CalculatorButtonKind.Number),
        input("+", CalculatorButtonKind.Operator),
        CalculatorButtonSpec("C", CalculatorAction.Clear, CalculatorButtonKind.Danger)
    ),
    listOf(
        input("/", CalculatorButtonKind.Operator),
        input("7", CalculatorButtonKind.Number),
        input("8", CalculatorButtonKind.Number),
        input("9", CalculatorButtonKind.Number),
        input("*", CalculatorButtonKind.Operator),
        CalculatorButtonSpec("E", CalculatorAction.NewLine, CalculatorButtonKind.Utility)
    ),
    listOf(
        input("√", CalculatorButtonKind.Operator),
        input(";", CalculatorButtonKind.Operator),
        input("0", CalculatorButtonKind.Number),
        input(".", CalculatorButtonKind.Operator),
        input("^", CalculatorButtonKind.Operator),
        CalculatorButtonSpec("=", CalculatorAction.Evaluate, CalculatorButtonKind.Confirm)
    )
)

private val landscapeCalculatorButtons = listOf(
    listOf(
        input("1", CalculatorButtonKind.Number),
        input("2", CalculatorButtonKind.Number),
        input("3", CalculatorButtonKind.Number),
        input("4", CalculatorButtonKind.Number),
        input("5", CalculatorButtonKind.Number),
        input("6", CalculatorButtonKind.Number),
        input("7", CalculatorButtonKind.Number),
        input("8", CalculatorButtonKind.Number),
        input("9", CalculatorButtonKind.Number),
        input("0", CalculatorButtonKind.Number)
    ),
    listOf(
        input("+", CalculatorButtonKind.Operator),
        input("-", CalculatorButtonKind.Operator),
        input("*", CalculatorButtonKind.Operator),
        input("/", CalculatorButtonKind.Operator),
        input("(", CalculatorButtonKind.Operator),
        input(")", CalculatorButtonKind.Operator),
        input("^", CalculatorButtonKind.Operator),
        input("√", CalculatorButtonKind.Operator),
        input(";", CalculatorButtonKind.Operator),
        input(".", CalculatorButtonKind.Operator)
    ),
    listOf(
        CalculatorButtonSpec("E", CalculatorAction.NewLine, CalculatorButtonKind.Utility),
        CalculatorButtonSpec("=", CalculatorAction.Evaluate, CalculatorButtonKind.Confirm),
        CalculatorButtonSpec("D", CalculatorAction.Backspace, CalculatorButtonKind.Utility),
        CalculatorButtonSpec("C", CalculatorAction.Clear, CalculatorButtonKind.Danger)
    )
)

@Preview
@Composable
fun CalculateContentPreview() {
    val myColor = LightKeyboardColors
    CalculatorKeyboard(
        myColor = myColor,
        isLandscape = false,
        onAction = {}
    )
}

@Preview(device = "spec:width=1280dp,height=800dp,dpi=480")
@Composable
fun CalculateContentLandscapePreview() {
    val myColor = LightKeyboardColors
    CalculatorKeyboard(
        myColor = myColor,
        isLandscape = true,
        onAction = {}
    )
}
