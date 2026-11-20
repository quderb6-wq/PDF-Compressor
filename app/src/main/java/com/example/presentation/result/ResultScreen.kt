package com.example.presentation.result

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.SaveAlt
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.presentation.components.SizeComparisonBar
import com.example.presentation.theme.LocalSemanticColors
import com.example.utils.FileSizeFormatter
import com.example.utils.ShareAndOpenHelper
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultScreen(
    viewModel: ResultViewModel,
    onNavigateBack: () -> Unit,
    onShowSnackbar: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val semantic = LocalSemanticColors.current

    val saveSafLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf"),
        onResult = { uri ->
            if (uri != null) {
                viewModel.saveToSafUri(uri)
            }
        }
    )

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is ResultEvent.ShowMessage -> onShowSnackbar(event.message)
                is ResultEvent.NavigateBackHome -> onNavigateBack()
            }
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Text(
                    text = stringResource(R.string.results_title),
                    style = MaterialTheme.typography.titleLarge
                )
            },
            navigationIcon = {
                IconButton(
                    onClick = onNavigateBack,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("result_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Back"
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background
            )
        )

        val result = uiState.result
        if (result == null) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Compression result unavailable.",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(onClick = onNavigateBack) {
                        Text("Return Home")
                    }
                }
            }
            return
        }

        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 680.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Status Hero Card
                item {
                    if (result.isSmaller) {
                        Card(
                            shape = MaterialTheme.shapes.large,
                            colors = CardDefaults.cardColors(containerColor = semantic.successContainer),
                            border = BorderStroke(1.dp, semantic.success.copy(alpha = 0.4f))
                        ) {
                            Column(modifier = Modifier.padding(20.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.CheckCircle,
                                        contentDescription = "Compression succeeded",
                                        tint = semantic.success,
                                        modifier = Modifier.size(28.dp)
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "PDF Successfully Compressed",
                                            style = MaterialTheme.typography.titleLarge,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "Verified readable • ${result.originalInfo.pageCount} ${if (result.originalInfo.pageCount == 1) "page" else "pages"} preserved",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                Row(
                                    verticalAlignment = Alignment.Bottom,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Text(
                                        text = "-${FileSizeFormatter.formatCompressionPercentage(result.compressionPercentage)}",
                                        style = MaterialTheme.typography.displaySmall,
                                        color = semantic.success,
                                        modifier = Modifier.testTag("compression_percentage_text")
                                    )
                                    Column(modifier = Modifier.padding(bottom = 4.dp)) {
                                        Text(
                                            text = "Saved ${FileSizeFormatter.formatBytes(result.spaceSavedBytes)}",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "Original PDF kept untouched",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        Card(
                            shape = MaterialTheme.shapes.large,
                            colors = CardDefaults.cardColors(containerColor = semantic.warningContainer),
                            border = BorderStroke(1.dp, semantic.warning.copy(alpha = 0.5f))
                        ) {
                            Column(modifier = Modifier.padding(20.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.WarningAmber,
                                        contentDescription = "Larger output warning",
                                        tint = semantic.warning,
                                        modifier = Modifier.size(28.dp)
                                    )
                                    Text(
                                        text = "No Size Reduction Achieved",
                                        style = MaterialTheme.typography.titleLarge
                                    )
                                }
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = "This PDF appears to be already tightly compressed (or mostly vector/text). The output file (${FileSizeFormatter.formatBytes(result.compressedSizeBytes)}) is larger than or equal to the original (${FileSizeFormatter.formatBytes(result.originalInfo.sizeBytes)}). Keeping the original file is recommended.",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Spacer(modifier = Modifier.height(14.dp))
                                Button(
                                    onClick = viewModel::keepOriginalInstead,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .testTag("keep_original_button")
                                ) {
                                    Text(stringResource(R.string.keep_original))
                                }
                            }
                        }
                    }
                }

                // Detailed Breakdown Card
                item {
                    Card(
                        shape = MaterialTheme.shapes.medium,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "Document Summary",
                                style = MaterialTheme.typography.titleMedium
                            )

                            SizeComparisonBar(
                                originalBytes = result.originalInfo.sizeBytes,
                                compressedBytes = result.compressedSizeBytes
                            )

                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                            MetricRow("Source File", result.originalInfo.displayName)
                            MetricRow("Original Size", FileSizeFormatter.formatBytes(result.originalInfo.sizeBytes))
                            MetricRow("Compressed Size", FileSizeFormatter.formatBytes(result.compressedSizeBytes))
                            MetricRow(
                                label = if (result.isSmaller) "Space Saved" else "Size Change",
                                value = if (result.isSmaller) {
                                    FileSizeFormatter.formatBytes(result.spaceSavedBytes)
                                } else {
                                    "+${FileSizeFormatter.formatBytes(-result.spaceSavedBytes)}"
                                }
                            )
                            MetricRow(
                                label = "Compression Ratio",
                                value = FileSizeFormatter.formatCompressionPercentage(result.compressionPercentage)
                            )
                            MetricRow("Compression Mode", result.configUsed.preset.title)
                            MetricRow("Page Count", "${result.originalInfo.pageCount}")
                            MetricRow("Images Optimized", "${result.imagesOptimizedCount}")
                            MetricRow("Processing Time", FileSizeFormatter.formatDuration(result.durationMs))
                            MetricRow(
                                label = "Status",
                                value = if (result.isSmaller) "Compressed & Verified" else "Larger Than Original"
                            )
                        }
                    }
                }

                // Error Notice if saving failed
                if (uiState.errorMessage != null) {
                    item {
                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.errorContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.ErrorOutline,
                                    contentDescription = "Save error",
                                    tint = MaterialTheme.colorScheme.error
                                )
                                Text(
                                    text = uiState.errorMessage.orEmpty(),
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }

                // Output File Management Card
                item {
                    Card(
                        shape = MaterialTheme.shapes.medium,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "Output File Management",
                                style = MaterialTheme.typography.titleMedium
                            )

                            if (!uiState.isSaved) {
                                OutlinedTextField(
                                    value = uiState.outputFileName,
                                    onValueChange = viewModel::updateOutputFileName,
                                    label = { Text("Output filename") },
                                    singleLine = true,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("output_filename_input")
                                )

                                Text(
                                    text = "Choose any folder on your device via Storage Access Framework. Your original PDF will never be overwritten.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Button(
                                    onClick = {
                                        saveSafLauncher.launch(viewModel.getNormalizedSaveFileName())
                                    },
                                    enabled = !uiState.isSaving,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(52.dp)
                                        .testTag("save_pdf_button")
                                ) {
                                    if (uiState.isSaving) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(20.dp),
                                            strokeWidth = 2.dp,
                                            color = MaterialTheme.colorScheme.onPrimary
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text("Saving & verifying...")
                                    } else {
                                        Icon(
                                            imageVector = Icons.Outlined.SaveAlt,
                                            contentDescription = null
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(stringResource(R.string.save_pdf))
                                    }
                                }
                            } else {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = semantic.successContainer,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Verified,
                                            contentDescription = "Saved and verified",
                                            tint = semantic.success
                                        )
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "Saved & Verified Readable",
                                                style = MaterialTheme.typography.titleSmall
                                            )
                                            Text(
                                                text = result.savedDisplayName ?: uiState.outputFileName,
                                                style = MaterialTheme.typography.bodySmall,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        val accessibleUri = ShareAndOpenHelper.resolveAccessibleUri(
                                            context,
                                            result.savedUriString,
                                            result.managedCopyPath
                                        ) ?: run {
                                            val tempFile = File(result.tempFilePath)
                                            if (tempFile.exists()) {
                                                FileProvider.getUriForFile(
                                                    context,
                                                    "${context.packageName}.fileprovider",
                                                    tempFile
                                                )
                                            } else null
                                        }
                                        if (accessibleUri != null) {
                                            ShareAndOpenHelper.openPdf(context, accessibleUri).onFailure {
                                                onShowSnackbar(it.message ?: "Unable to open PDF.")
                                            }
                                        } else {
                                            onShowSnackbar("Output PDF file could not be located.")
                                        }
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .testTag("open_pdf_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(stringResource(R.string.open_pdf))
                                }

                                OutlinedButton(
                                    onClick = {
                                        val accessibleUri = ShareAndOpenHelper.resolveAccessibleUri(
                                            context,
                                            result.savedUriString,
                                            result.managedCopyPath
                                        ) ?: run {
                                            val tempFile = File(result.tempFilePath)
                                            if (tempFile.exists()) {
                                                FileProvider.getUriForFile(
                                                    context,
                                                    "${context.packageName}.fileprovider",
                                                    tempFile
                                                )
                                            } else null
                                        }
                                        if (accessibleUri != null) {
                                            ShareAndOpenHelper.sharePdf(
                                                context,
                                                accessibleUri,
                                                result.savedDisplayName ?: viewModel.getNormalizedSaveFileName()
                                            ).onFailure {
                                                onShowSnackbar(it.message ?: "Unable to share PDF.")
                                            }
                                        } else {
                                            onShowSnackbar("Output PDF file could not be located.")
                                        }
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .testTag("share_pdf_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Share,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(stringResource(R.string.share_pdf))
                                }
                            }

                            OutlinedButton(
                                onClick = viewModel::onCompressAgain,
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.primary
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("compress_again_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Refresh,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(R.string.compress_again))
                            }
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
private fun MetricRow(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
