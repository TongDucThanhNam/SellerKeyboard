package com.terasumi.sellerkeyboard.main

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.terasumi.sellerkeyboard.R
import com.terasumi.sellerkeyboard.backend.RestorePlan
import com.terasumi.sellerkeyboard.ui.theme.SellerKeyboardTheme
import com.terasumi.sellerkeyboard.ui.theme.spacing

@Composable
fun KeyboardQuickActionCard(onOpenKeyboardPicker: () -> Unit) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(MaterialTheme.spacing.medium),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xSmall)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Icon(
                        imageVector = Icons.Default.Keyboard,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(8.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.switch_to_seller_keyboard),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = stringResource(R.string.switch_keyboard_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            FilledTonalButton(
                onClick = onOpenKeyboardPicker,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Keyboard,
                    contentDescription = null
                )
                Spacer(modifier = Modifier.width(MaterialTheme.spacing.small))
                Text(text = stringResource(R.string.open_keyboard_picker))
            }
        }
    }
}

@Composable
fun CloudBackupCard(
    signedInEmail: String,
    email: String,
    password: String,
    isBusy: Boolean,
    isCloudAvailable: Boolean,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onRegister: () -> Unit,
    onLogin: () -> Unit,
    onLogout: () -> Unit,
    onBackupNow: () -> Unit,
    onRestore: () -> Unit
) {
    val isSignedIn = signedInEmail.isNotBlank()
    val canSubmitAuth = !isBusy && isCloudAvailable && email.isNotBlank() && password.isNotBlank()
    val canUseBackup = !isBusy && isCloudAvailable && isSignedIn

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(MaterialTheme.spacing.medium),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudUpload,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(8.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.cloud_backup_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = if (isSignedIn) {
                            stringResource(R.string.cloud_backup_signed_in, signedInEmail)
                        } else {
                            stringResource(R.string.cloud_backup_signed_out)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (isSignedIn) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)
                ) {
                    Button(
                        onClick = onBackupNow,
                        enabled = canUseBackup,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("cloud_backup_now")
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudUpload,
                            contentDescription = null
                        )
                        Spacer(modifier = Modifier.width(MaterialTheme.spacing.xSmall))
                        Text(text = stringResource(R.string.cloud_backup_now))
                    }
                    FilledTonalButton(
                        onClick = onRestore,
                        enabled = canUseBackup,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("cloud_restore")
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudDownload,
                            contentDescription = null
                        )
                        Spacer(modifier = Modifier.width(MaterialTheme.spacing.xSmall))
                        Text(text = stringResource(R.string.cloud_backup_restore))
                    }
                }
                TextButton(
                    onClick = onLogout,
                    enabled = !isBusy && isCloudAvailable,
                    modifier = Modifier
                        .align(Alignment.End)
                        .testTag("cloud_logout")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Logout,
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(MaterialTheme.spacing.xSmall))
                    Text(text = stringResource(R.string.cloud_backup_logout))
                }
            } else {
                OutlinedTextField(
                    value = email,
                    onValueChange = onEmailChange,
                    enabled = !isBusy && isCloudAvailable,
                    singleLine = true,
                    label = { Text(text = stringResource(R.string.cloud_backup_email)) },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Email,
                        imeAction = ImeAction.Next
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("cloud_email")
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = onPasswordChange,
                    enabled = !isBusy && isCloudAvailable,
                    singleLine = true,
                    label = { Text(text = stringResource(R.string.cloud_backup_password)) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("cloud_password")
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)
                ) {
                    OutlinedButton(
                        onClick = onRegister,
                        enabled = canSubmitAuth,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("cloud_register")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Login,
                            contentDescription = null
                        )
                        Spacer(modifier = Modifier.width(MaterialTheme.spacing.xSmall))
                        Text(text = stringResource(R.string.cloud_backup_register))
                    }
                    Button(
                        onClick = onLogin,
                        enabled = canSubmitAuth,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("cloud_login")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Login,
                            contentDescription = null
                        )
                        Spacer(modifier = Modifier.width(MaterialTheme.spacing.xSmall))
                        Text(text = stringResource(R.string.cloud_backup_login))
                    }
                }
            }
        }
    }
}

@Composable
fun RestoreBackupDialog(
    restorePlan: RestorePlan,
    isBusy: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = {
            if (!isBusy) {
                onDismiss()
            }
        },
        title = {
            Text(text = stringResource(R.string.cloud_restore_title))
        },
        text = {
            Text(
                text = stringResource(
                    R.string.cloud_restore_message,
                    restorePlan.snippetsToInsert.size,
                    restorePlan.skippedExactMatches
                )
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = !isBusy,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.testTag("cloud_restore_confirm")
            ) {
                Text(text = stringResource(R.string.cloud_restore_confirm))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isBusy
            ) {
                Text(text = stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
fun SnippetSectionHeader(snippetCount: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = stringResource(R.string.snippet_list),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = stringResource(R.string.snippet_swipe_hint),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Surface(
            shape = RoundedCornerShape(999.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f)
        ) {
            Text(
                text = stringResource(R.string.snippet_total_count, snippetCount),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(
                    horizontal = MaterialTheme.spacing.small,
                    vertical = MaterialTheme.spacing.xSmall
                )
            )
        }
    }
}

@Composable
fun EmptySnippetsState(onAddSnippet: () -> Unit) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(MaterialTheme.spacing.large),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)
        ) {
            Icon(
                imageVector = Icons.Default.Star,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                text = stringResource(R.string.empty_snippet_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = stringResource(R.string.empty_snippet_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Button(
                onClick = onAddSnippet,
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null
                )
                Spacer(modifier = Modifier.width(MaterialTheme.spacing.small))
                Text(text = stringResource(R.string.add_snippet))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeBackground(
    dismissDirection: SwipeToDismissBoxValue,
    progress: Float
) {
    val clampedProgress = progress.coerceIn(0f, 1f)
    val isDeleting = dismissDirection == SwipeToDismissBoxValue.EndToStart
    val visualProgress = if (isDeleting) clampedProgress else 0f
    val targetContainerColor = if (isDeleting) {
        lerp(
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
            MaterialTheme.colorScheme.errorContainer,
            clampedProgress
        )
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
    }
    val targetContentColor = if (isDeleting) {
        lerp(
            MaterialTheme.colorScheme.onSurfaceVariant,
            MaterialTheme.colorScheme.onErrorContainer,
            clampedProgress
        )
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val containerColor = animateColorAsState(
        targetValue = targetContainerColor,
        label = "swipe-bg-container"
    )
    val contentColor = animateColorAsState(
        targetValue = targetContentColor,
        label = "swipe-bg-content"
    )
    val iconScale = animateFloatAsState(
        targetValue = 0.92f + (visualProgress * 0.2f),
        label = "swipe-bg-icon-scale"
    )
    val textAlpha = animateFloatAsState(
        targetValue = 0.45f + (visualProgress * 0.55f),
        label = "swipe-bg-text-alpha"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
    ) {
        ElevatedCard(
            colors = CardDefaults.elevatedCardColors(
                containerColor = containerColor.value
            ),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(MaterialTheme.spacing.medium),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.swipe_release_delete),
                    style = MaterialTheme.typography.labelMedium,
                    color = contentColor.value,
                    modifier = Modifier.graphicsLayer { alpha = textAlpha.value }
                )
                Spacer(modifier = Modifier.width(MaterialTheme.spacing.small))
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = stringResource(R.string.delete_snippet),
                    tint = contentColor.value,
                    modifier = Modifier.graphicsLayer {
                        scaleX = iconScale.value
                        scaleY = iconScale.value
                    }
                )
            }
        }
    }
}

@Preview(showBackground = true, name = "Keyboard Quick Action")
@Composable
private fun KeyboardQuickActionCardPreview() {
    SellerKeyboardTheme {
        KeyboardQuickActionCard(onOpenKeyboardPicker = {})
    }
}

@Preview(showBackground = true, name = "Snippet Section Header")
@Composable
private fun SnippetSectionHeaderPreview() {
    SellerKeyboardTheme {
        SnippetSectionHeader(snippetCount = 12)
    }
}

@Preview(showBackground = true, name = "Empty Snippets State")
@Composable
private fun EmptySnippetsStatePreview() {
    SellerKeyboardTheme {
        EmptySnippetsState(onAddSnippet = {})
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview(showBackground = true, name = "Swipe Background")
@Composable
private fun SwipeBackgroundPreview() {
    SellerKeyboardTheme {
        SwipeBackground(
            dismissDirection = SwipeToDismissBoxValue.EndToStart,
            progress = 0.72f
        )
    }
}
