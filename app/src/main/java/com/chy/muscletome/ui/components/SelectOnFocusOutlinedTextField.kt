package com.chy.muscletome.ui.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation

/**
 * An [OutlinedTextField] wrapper that automatically selects all text when the
 * field gains focus (if populated), allowing immediate replacement by typing.
 * Tapping within the text of an already focused field places the cursor at the
 * tap location without clearing or re-selecting the text.
 */
@Composable
fun SelectOnFocusOutlinedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    textStyle: TextStyle = LocalTextStyle.current,
    label: @Composable (() -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    prefix: @Composable (() -> Unit)? = null,
    suffix: @Composable (() -> Unit)? = null,
    supportingText: @Composable (() -> Unit)? = null,
    isError: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = false,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    minLines: Int = 1,
    interactionSource: MutableInteractionSource? = null,
    shape: Shape = OutlinedTextFieldDefaults.shape,
    colors: TextFieldColors = OutlinedTextFieldDefaults.colors(),
    onFocused: ((Boolean) -> Unit)? = null,
    applyValueChange: ((old: String, typed: String) -> String)? = null,
) {
    var fieldValue by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(value, TextRange(value.length)))
    }
    var isFocused by remember { mutableStateOf(value = false) }
    var selectAllOnFocus by remember { mutableStateOf(value = false) }

    LaunchedEffect(value, isFocused) {
        if ((!isFocused) && (fieldValue.text != value)) {
            fieldValue = TextFieldValue(value, TextRange(value.length))
        }
    }

    OutlinedTextField(
        value = fieldValue,
        onValueChange = { typed ->
            val old = fieldValue.text
            if (selectAllOnFocus) {
                selectAllOnFocus = false
                if (typed.text == old) {
                    fieldValue = typed.copy(selection = TextRange(0, old.length))
                    return@OutlinedTextField
                }
            }
            val text = applyValueChange?.invoke(old, typed.text) ?: typed.text
            fieldValue = typed.copy(text = text).normalizeSelection(text)
            if (text != old) {
                onValueChange(text)
            }
        },
        modifier = modifier.onFocusChanged { focusState ->
            val gainedFocus = focusState.isFocused && !isFocused
            isFocused = focusState.isFocused
            if (focusState.isFocused) {
                onFocused?.invoke(true)
                if (gainedFocus) {
                    selectAllOnFocus = true
                    fieldValue = fieldValue.copy(
                        selection = TextRange(0, fieldValue.text.length),
                    )
                }
            } else {
                onFocused?.invoke(false)
                selectAllOnFocus = false
            }
        },
        enabled = enabled,
        readOnly = readOnly,
        textStyle = textStyle,
        label = label,
        placeholder = placeholder,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        prefix = prefix,
        suffix = suffix,
        supportingText = supportingText,
        isError = isError,
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        singleLine = singleLine,
        maxLines = maxLines,
        minLines = minLines,
        interactionSource = interactionSource,
        shape = shape,
        colors = colors,
    )
}

internal fun TextFieldValue.normalizeSelection(text: String): TextFieldValue =
    if ((selection.end > text.length) || (selection.start > text.length)) {
        copy(selection = TextRange(text.length))
    } else {
        this
    }
