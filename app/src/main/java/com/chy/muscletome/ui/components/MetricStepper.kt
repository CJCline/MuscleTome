package com.chy.muscletome.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.chy.muscletome.ui.theme.MonoFont

/**
 * MetricStepper — the mid-set input primitive. A huge monospace value
 * flanked by steel +/- plate chips. Bigger numbers, bigger targets: you
 * should be able to adjust weight between sets without aiming.
 *
 * Stepping is handled by the caller via [onDelta] (e.g. ±2.5 kg / ±1 rep);
 * tapping the value opens the raw field for typing exact numbers.
 * When [longDeltaStep] is set, long-pressing a plate steps by that instead
 * (e.g. half plates in lbs: tap 5, long-press 2.5).
 */
@Composable
fun MetricStepper(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    onDelta: (Double) -> Unit,
    modifier: Modifier = Modifier,
    deltaStep: Double = 1.0,
    longDeltaStep: Double? = null,
    suffix: String = "",
    enabled: Boolean = true,
    large: Boolean = false,
) {
    var editing by rememberSaveable { mutableStateOf(value = false) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(editing) {
        if (editing) focusRequester.requestFocus()
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PlateChip(
                onClick = { onDelta(-deltaStep) },
                onLongClick = longDeltaStep?.let { long -> { onDelta(-long) } },
                enabled = enabled,
                modifier = Modifier.weight(1f),
            ) {
                Icon(
                    Icons.Default.Remove,
                    contentDescription = "$label minus $deltaStep",
                    modifier = Modifier.size(20.dp),
                )
            }

            Surface(
                onClick = { editing = true },
                enabled = enabled,
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outline,
                ),
                modifier = Modifier
                    .weight(2.4f)
                    .height(if (large) 96.dp else 64.dp),
            ) {
                AnimatedContent(
                    targetState = editing,
                    label = "stepper-editing",
                ) { isEditing ->
                    if (isEditing) {
                        OutlinedTextField(
                            value = value,
                            onValueChange = onValueChange,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(if (large) 96.dp else 64.dp)
                                .focusRequester(focusRequester),
                            textStyle = (if (large) MaterialTheme.typography.headlineLarge else MaterialTheme.typography.headlineSmall).copy(
                                fontFamily = MonoFont,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                            ),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Number,
                                imeAction = ImeAction.Done,
                            ),
                            keyboardActions = KeyboardActions(
                                onDone = { editing = false },
                            ),
                        )
                    } else {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                text = value.ifBlank { "0" } + suffix,
                                style = (if (large) MaterialTheme.typography.headlineLarge else MaterialTheme.typography.headlineSmall).copy(
                                    fontFamily = MonoFont,
                                    fontWeight = FontWeight.Bold,
                                ),
                                color = MaterialTheme.colorScheme.onBackground,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }

            PlateChip(
                onClick = { onDelta(deltaStep) },
                onLongClick = longDeltaStep?.let { long -> { onDelta(long) } },
                enabled = enabled,
                modifier = Modifier.weight(1f),
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = "$label plus $deltaStep",
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}
