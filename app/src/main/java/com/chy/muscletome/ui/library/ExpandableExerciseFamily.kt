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
import com.chy.muscletome.ui.theme.MuscleTomeTextStyles
import java.util.Locale

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
                    contentDescription = "$familyLabel EXERCISE FAMILY"
                    stateDescription = if (expanded) "EXPANDED" else "COLLAPSED"
                }
                .padding(vertical = 12.dp, horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(familyLabel.uppercase(Locale.US), style = MuscleTomeTextStyles.sectionTitle)
                Text("${members.size} VARIATIONS", style = MuscleTomeTextStyles.tag, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(if (expanded) "−" else "+", style = MaterialTheme.typography.titleLarge)
        }
        if (expanded) {
            members.forEach { variant ->
                ParentExerciseRow(variant) { onOpenExercise(variant.id) }
            }
        }
        LedgerDivider()
    }
}
