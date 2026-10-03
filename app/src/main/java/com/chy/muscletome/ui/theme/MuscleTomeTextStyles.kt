package com.chy.muscletome.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import java.util.Locale

/**
 * MuscleTome Centralized Typography Conventions & Text Roles.
 *
 * Rules:
 * 1. HEADINGS & SECTION TITLES: ALL UPPERCASE, wide letter-spacing.
 * 2. LABELS & TAGS: ALL UPPERCASE, small size, muted color, wide tracking.
 * 3. BUTTONS & PRIMARY ACTIONS: ALL UPPERCASE, bold, no punctuation.
 * 4. DATA VALUES: Monospaced numerals (MonoFont), NO uppercase transform—render exact numbers (e.g. 225.0).
 * 5. SYSTEM/STATUS MESSAGES & EMPTY STATES: ALL UPPERCASE, terse, no exclamation points, no motivational copy.
 * 6. DATA BRACKETS: Metadata and status indicators use square-bracket tags in uppercase (e.g. `[PR]`, `[CUSTOM]`, `[ACTIVE]`).
 */
object MuscleTomeTextStyles {

    val heading: TextStyle = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Black,
        fontSize = 28.sp,
        lineHeight = 32.sp,
        letterSpacing = 1.5.sp,
    )

    val sectionTitle: TextStyle = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 16.sp,
        lineHeight = 20.sp,
        letterSpacing = 2.0.sp,
    )

    val label: TextStyle = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 2.0.sp,
    )

    val tag: TextStyle = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 1.5.sp,
    )

    val button: TextStyle = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 1.5.sp,
    )

    val dataValue: TextStyle = TextStyle(
        fontFamily = MonoFont,
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.sp,
    )

    val systemMessage: TextStyle = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = 1.0.sp,
    )

    val emptyState: TextStyle = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        letterSpacing = 1.5.sp,
    )

    val dataBracket: TextStyle = TextStyle(
        fontFamily = MonoFont,
        fontWeight = FontWeight.Bold,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 1.0.sp,
    )

    /** Format weight consistently to 1 decimal place (e.g., 225.0). */
    fun formatWeight(weight: Double?): String {
        return if ((weight == null) || weight.isNaN()) "0.0"
        else String.format(Locale.US, "%.1f", weight)
    }

    /** Format weight consistently to 1 decimal place (e.g., 225.0). */
    fun formatWeight(weight: Float?): String {
        return if ((weight == null) || weight.isNaN()) "0.0"
        else String.format(Locale.US, "%.1f", weight.toDouble())
    }

    /** Format metadata/status indicator into uppercase square brackets (e.g., [PR], [CUSTOM]). */
    fun formatBracket(tag: String): String {
        val cleaned = tag.trim().trim('[', ']').uppercase(Locale.US)
        return "[$cleaned]"
    }
}
