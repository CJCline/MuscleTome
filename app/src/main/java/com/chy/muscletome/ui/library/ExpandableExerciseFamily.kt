package com.chy.muscletome.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.chy.muscletome.data.local.entity.ExerciseEntity
import com.chy.muscletome.ui.components.LedgerDivider

@Composable
internal fun ExpandableExerciseFamily(
    familyLabel: String,
    members: List<ExerciseEntity>,
    expanded: Boolean,
    onToggle: () -> Unit,
    onOpenExercise: (String) -> Unit,
    firstRowIndex: Int = 0,
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .semantics {
                    contentDescription = "$familyLabel exercise family"
                    stateDescription = if (expanded) "Expanded" else "Collapsed"
                }
                .padding(vertical = 12.dp, horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(familyLabel, style = MaterialTheme.typography.titleMedium)
                Text("${members.size} variations", style = MaterialTheme.typography.bodySmall)
            }
            Text(if (expanded) "−" else "+", style = MaterialTheme.typography.titleLarge)
        }
        if (expanded) {
            members.forEachIndexed { index, variant ->
                ExerciseRow(variant, firstRowIndex + index) { onOpenExercise(variant.id) }
            }
        }
        LedgerDivider()
    }
}
