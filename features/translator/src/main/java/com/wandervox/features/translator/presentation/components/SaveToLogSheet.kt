package com.wandervox.features.translator.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.wandervox.core.domain.model.LogCategory
import com.wandervox.core.ui.theme.WanderVoxTheme
import com.wandervox.translator.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SaveToLogSheet(
    onDismissRequest: () -> Unit,
    onConfirm: (category: LogCategory, notes: String, isPublic: Boolean) -> Unit,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
    ) {
        SaveToLogForm(
            onConfirm = onConfirm,
            modifier = Modifier
                .navigationBarsPadding()
                .padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
        )
    }
}

/** Sheet body, split out so it can be previewed and reused in a dialog if needed. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SaveToLogForm(
    onConfirm: (category: LogCategory, notes: String, isPublic: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Saveable so the form survives rotation while the sheet is open.
    var selectedCategory by rememberSaveable { mutableStateOf(LogCategory.FOOD) }
    var notes by rememberSaveable { mutableStateOf("") }
    // Private by default: sharing with the Community is always an explicit choice.
    var isPublic by rememberSaveable { mutableStateOf(false) }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(text = stringResource(R.string.translator_save_sheet_title), style = MaterialTheme.typography.titleLarge)

        Text(
            text = stringResource(R.string.translator_category_label),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LogCategory.entries.forEach { category ->
                FilterChip(
                    selected = category == selectedCategory,
                    onClick = { selectedCategory = category },
                    label = { Text(stringResource(category.labelRes)) },
                )
            }
        }

        OutlinedTextField(
            value = notes,
            onValueChange = { notes = it },
            label = { Text(stringResource(R.string.translator_notes_label)) },
            minLines = 2,
            maxLines = 4,
            modifier = Modifier.fillMaxWidth(),
        )

        ShareWithCommunityRow(isPublic = isPublic, onIsPublicChange = { isPublic = it })

        Button(
            onClick = { onConfirm(selectedCategory, notes, isPublic) },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp),
        ) {
            Text(stringResource(R.string.translator_save_confirm), style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** The whole row toggles the switch, so the label is a large touch target too. */
@Composable
private fun ShareWithCommunityRow(isPublic: Boolean, onIsPublicChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = isPublic, role = Role.Switch, onValueChange = onIsPublicChange),
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
            Text(text = stringResource(R.string.translator_share_label), style = MaterialTheme.typography.labelLarge)
            Text(
                text = stringResource(
                    if (isPublic) R.string.translator_share_on_hint else R.string.translator_share_off_hint
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // onCheckedChange = null: the row's toggleable owns the click and the accessibility semantics.
        Switch(checked = isPublic, onCheckedChange = null)
    }
}

@Preview(showBackground = true)
@Composable
private fun SaveToLogFormPreview() {
    WanderVoxTheme {
        SaveToLogForm(onConfirm = { _, _, _ -> }, modifier = Modifier.padding(24.dp))
    }
}
