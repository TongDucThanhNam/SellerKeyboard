package com.terasumi.sellerkeyboard.main

import android.graphics.Bitmap
import androidx.annotation.DrawableRes
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.terasumi.sellerkeyboard.R
import com.terasumi.sellerkeyboard.ui.theme.sizes
import com.terasumi.sellerkeyboard.ui.theme.spacing

const val SNIPPET_TITLE_MAX_LENGTH = 50
const val SNIPPET_CONTENT_MAX_LENGTH = 1000
const val SNIPPET_MAX_IMAGES = 5

@Composable
fun SnippetEditorBottomBar(
    saveLabel: String,
    isSaving: Boolean,
    canSave: Boolean,
    onSave: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme

    Surface(
        color = colorScheme.surface,
        tonalElevation = 0.dp,
        shadowElevation = 2.dp,
        modifier = modifier
            .fillMaxWidth()
            .imePadding()
            .navigationBarsPadding()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = MaterialTheme.spacing.medium,
                    vertical = MaterialTheme.spacing.small
                ),
            contentAlignment = Alignment.Center
        ) {
            Button(
                onClick = onSave,
                enabled = canSave,
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 560.dp)
                    .height(52.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colorScheme.primary,
                    contentColor = colorScheme.onPrimary,
                    disabledContainerColor = colorScheme.primary.copy(alpha = 0.28f),
                    disabledContentColor = colorScheme.onSurface.copy(alpha = 0.62f)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = colorScheme.onPrimary
                    )
                } else {
                    Text(text = saveLabel)
                }
            }
        }
    }
}

@Composable
fun SnippetEditorCard(
    title: String,
    content: String,
    bitmaps: List<Bitmap>,
    attemptedSave: Boolean,
    isLoadingImages: Boolean,
    @DrawableRes imagePlaceholderRes: Int,
    onTitleChange: (String) -> Unit,
    onContentChange: (String) -> Unit,
    onClearTitle: () -> Unit,
    onClearContent: () -> Unit,
    onAddImages: () -> Unit,
    onPreviewImage: (Int) -> Unit,
    onRemoveImage: (Int) -> Unit,
    modifier: Modifier = Modifier,
    maxImages: Int = SNIPPET_MAX_IMAGES
) {
    val colorScheme = MaterialTheme.colorScheme
    val focusManager = LocalFocusManager.current
    val fieldShape = RoundedCornerShape(10.dp)
    val formFieldColors = snippetFormFieldColors()

    ElevatedCard(
        colors = CardDefaults.elevatedCardColors(
            containerColor = colorScheme.surface
        ),
        shape = RoundedCornerShape(8.dp),
        modifier = modifier
            .fillMaxWidth()
            .widthIn(max = 560.dp),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = MaterialTheme.spacing.large,
                    vertical = MaterialTheme.spacing.medium
                ),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium)
        ) {
            SnippetEditorHeader()

            OutlinedTextField(
                value = title,
                maxLines = 1,
                onValueChange = {
                    if (it.length <= SNIPPET_TITLE_MAX_LENGTH) onTitleChange(it)
                },
                label = { Text(stringResource(R.string.enter_label)) },
                placeholder = { Text(stringResource(R.string.snippet_title_placeholder)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = MaterialTheme.sizes.inputHeight),
                trailingIcon = {
                    if (title.isNotEmpty()) {
                        IconButton(onClick = onClearTitle) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = stringResource(R.string.clear_title)
                            )
                        }
                    }
                },
                shape = fieldShape,
                colors = formFieldColors,
                isError = attemptedSave && title.isBlank(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = {
                    focusManager.moveFocus(FocusDirection.Down)
                }),
                supportingText = {
                    SnippetFieldSupportingText(
                        isError = attemptedSave && title.isBlank(),
                        errorText = stringResource(R.string.title_required),
                        helperText = stringResource(R.string.title_helper),
                        countText = "${title.length}/$SNIPPET_TITLE_MAX_LENGTH"
                    )
                }
            )

            OutlinedTextField(
                value = content,
                onValueChange = {
                    if (it.length <= SNIPPET_CONTENT_MAX_LENGTH) onContentChange(it)
                },
                label = { Text(stringResource(R.string.enter_content)) },
                placeholder = { Text(stringResource(R.string.snippet_content_placeholder)) },
                maxLines = 6,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(148.dp),
                trailingIcon = {
                    if (content.isNotEmpty()) {
                        IconButton(onClick = onClearContent) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = stringResource(R.string.clear_content)
                            )
                        }
                    }
                },
                shape = fieldShape,
                colors = formFieldColors,
                isError = attemptedSave && content.isBlank(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                supportingText = {
                    SnippetFieldSupportingText(
                        isError = attemptedSave && content.isBlank(),
                        errorText = stringResource(R.string.content_required),
                        helperText = stringResource(R.string.content_helper),
                        countText = "${content.length}/$SNIPPET_CONTENT_MAX_LENGTH"
                    )
                }
            )

            HorizontalDivider(
                modifier = Modifier.padding(vertical = MaterialTheme.spacing.xSmall),
                color = colorScheme.outline.copy(alpha = 0.2f)
            )

            SnippetImagePickerSection(
                bitmaps = bitmaps,
                isLoadingImages = isLoadingImages,
                imagePlaceholderRes = imagePlaceholderRes,
                maxImages = maxImages,
                onAddImages = onAddImages,
                onPreviewImage = onPreviewImage,
                onRemoveImage = onRemoveImage
            )
        }
    }
}

@Composable
fun SnippetImagePreviewDialog(
    bitmaps: List<Bitmap>,
    previewIndex: Int?,
    onDismiss: () -> Unit
) {
    val index = previewIndex ?: return
    if (index !in bitmaps.indices) return

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 3.dp
        ) {
            Image(
                bitmap = bitmaps[index].asImageBitmap(),
                contentDescription = stringResource(R.string.preview_image),
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(MaterialTheme.spacing.small)
                    .clip(RoundedCornerShape(6.dp))
            )
        }
    }
}

@Composable
private fun SnippetEditorHeader() {
    val colorScheme = MaterialTheme.colorScheme

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = colorScheme.primaryContainer
        ) {
            Icon(
                imageVector = Icons.Default.Edit,
                contentDescription = null,
                tint = colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(8.dp)
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = stringResource(R.string.snippet_form_section_title),
                style = MaterialTheme.typography.titleMedium,
                color = colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = stringResource(R.string.snippet_form_section_description),
                style = MaterialTheme.typography.bodySmall,
                color = colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SnippetFieldSupportingText(
    isError: Boolean,
    errorText: String,
    helperText: String,
    countText: String
) {
    val colorScheme = MaterialTheme.colorScheme

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = if (isError) errorText else helperText,
            color = if (isError) colorScheme.error else colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Surface(
            shape = RoundedCornerShape(999.dp),
            color = colorScheme.surfaceVariant.copy(alpha = 0.7f)
        ) {
            Text(
                text = countText,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                color = colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SnippetImagePickerSection(
    bitmaps: List<Bitmap>,
    isLoadingImages: Boolean,
    @DrawableRes imagePlaceholderRes: Int,
    maxImages: Int,
    onAddImages: () -> Unit,
    onPreviewImage: (Int) -> Unit,
    onRemoveImage: (Int) -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val visibleCount = 4
    val total = bitmaps.size
    val showCount = minOf(total, visibleCount)
    val canAddMore = total < maxImages && !isLoadingImages

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = colorScheme.surface,
        tonalElevation = 0.dp,
        modifier = Modifier
            .fillMaxWidth()
            .border(
                1.dp,
                colorScheme.outline.copy(alpha = 0.16f),
                RoundedCornerShape(8.dp)
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
                    shape = RoundedCornerShape(8.dp),
                    color = colorScheme.secondaryContainer.copy(alpha = 0.72f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Image,
                        contentDescription = null,
                        tint = colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(8.dp)
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = stringResource(R.string.images_section_title),
                        style = MaterialTheme.typography.titleSmall,
                        color = colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = stringResource(R.string.images_section_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.onSurfaceVariant
                    )
                }
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = colorScheme.surfaceVariant.copy(alpha = 0.7f)
                ) {
                    Text(
                        text = "$total/$maxImages",
                        style = MaterialTheme.typography.labelSmall,
                        color = colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
                modifier = Modifier.fillMaxWidth()
            ) {
                itemsIndexed(bitmaps.take(showCount)) { index, bitmap ->
                    SnippetImageTile(
                        bitmap = bitmap,
                        index = index,
                        onPreviewImage = onPreviewImage,
                        onRemoveImage = onRemoveImage
                    )
                }

                item {
                    SnippetAddImageTile(
                        canAddMore = canAddMore,
                        isLoadingImages = isLoadingImages,
                        imagePlaceholderRes = imagePlaceholderRes,
                        total = total,
                        visibleCount = visibleCount,
                        maxImages = maxImages,
                        onAddImages = onAddImages
                    )
                }
            }

            Crossfade(
                targetState = when {
                    isLoadingImages -> stringResource(R.string.snippet_images_loading)
                    bitmaps.isEmpty() -> stringResource(R.string.snippet_tap_to_add_images)
                    else -> stringResource(R.string.snippet_images_selected, bitmaps.size)
                },
                label = "image-selection"
            ) { animatedText ->
                Text(
                    text = animatedText,
                    color = colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun SnippetImageTile(
    bitmap: Bitmap,
    index: Int,
    onPreviewImage: (Int) -> Unit,
    onRemoveImage: (Int) -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme

    Surface(
        modifier = Modifier
            .size(MaterialTheme.sizes.imageThumb)
            .clip(RoundedCornerShape(8.dp))
            .border(
                1.dp,
                colorScheme.outline.copy(alpha = 0.24f),
                RoundedCornerShape(8.dp)
            )
            .clickable { onPreviewImage(index) },
        shape = RoundedCornerShape(8.dp),
        tonalElevation = 2.dp,
        shadowElevation = 1.dp,
        color = colorScheme.surface
    ) {
        Box {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = stringResource(R.string.selected_image, index + 1),
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            Surface(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(MaterialTheme.spacing.small),
                shape = CircleShape,
                color = colorScheme.surface.copy(alpha = 0.92f),
                tonalElevation = 2.dp
            ) {
                IconButton(
                    onClick = { onRemoveImage(index) },
                    modifier = Modifier.size(MaterialTheme.sizes.iconMedium)
                ) {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = stringResource(R.string.remove_image),
                        tint = colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
private fun SnippetAddImageTile(
    canAddMore: Boolean,
    isLoadingImages: Boolean,
    @DrawableRes imagePlaceholderRes: Int,
    total: Int,
    visibleCount: Int,
    maxImages: Int,
    onAddImages: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme

    Surface(
        modifier = Modifier
            .size(MaterialTheme.sizes.imageThumb)
            .clip(RoundedCornerShape(8.dp))
            .border(
                1.dp,
                colorScheme.outline.copy(alpha = 0.2f),
                RoundedCornerShape(8.dp)
            )
            .clickable(enabled = canAddMore) {
                if (canAddMore) onAddImages()
            },
        shape = RoundedCornerShape(8.dp),
        tonalElevation = 1.dp,
        shadowElevation = 1.dp,
        color = if (canAddMore) {
            colorScheme.primaryContainer.copy(alpha = 0.18f)
        } else {
            colorScheme.surfaceVariant.copy(alpha = 0.5f)
        }
    ) {
        Box(contentAlignment = Alignment.Center) {
            when {
                isLoadingImages -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(MaterialTheme.sizes.iconMedium)
                    )
                }

                total == 0 -> {
                    Image(
                        painter = painterResource(id = imagePlaceholderRes),
                        contentDescription = stringResource(R.string.add_images),
                        modifier = Modifier.size(64.dp)
                    )
                }

                else -> {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = stringResource(R.string.add_images),
                        tint = if (canAddMore) colorScheme.primary else colorScheme.outline,
                        modifier = Modifier.size(MaterialTheme.sizes.iconMedium)
                    )
                    if (total > visibleCount) {
                        Surface(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(MaterialTheme.spacing.small),
                            shape = CircleShape,
                            color = colorScheme.secondaryContainer,
                            tonalElevation = 2.dp
                        ) {
                            Text(
                                text = "+${total - visibleCount}",
                                color = colorScheme.onSecondaryContainer,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(
                                    horizontal = MaterialTheme.spacing.xSmall,
                                    vertical = 2.dp
                                )
                            )
                        }
                    }
                }
            }

            if (!canAddMore && !isLoadingImages) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = MaterialTheme.spacing.small),
                    shape = RoundedCornerShape(999.dp),
                    color = colorScheme.surface.copy(alpha = 0.92f),
                    tonalElevation = 2.dp
                ) {
                    Text(
                        text = stringResource(R.string.max_images_count, maxImages),
                        style = MaterialTheme.typography.labelSmall,
                        color = colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(
                            horizontal = MaterialTheme.spacing.small,
                            vertical = 2.dp
                        )
                    )
                }
            }
        }
    }
}
