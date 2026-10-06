package com.chy.muscletome.domain.search

import com.chy.muscletome.data.local.dao.ExerciseLibraryRow
import com.chy.muscletome.data.local.entity.ExerciseEntity
import com.chy.muscletome.domain.model.MovementFamilies
import java.util.Locale

data class SearchToken(
    val raw: String,
    val stem: String,
)

object ExerciseSearchEngine {

    /**
     * Splits a raw query into individual tokens with normalized stems
     * to support plural/singular variations (e.g. "shrugs" -> "shrug", "presses" -> "press").
     */
    fun tokenize(query: String): List<SearchToken> {
        val cleaned = query.trim().lowercase(Locale.US)
        if (cleaned.isBlank()) return emptyList()

        return cleaned.split(Regex("[^a-z0-9]+"))
            .filter { it.isNotBlank() }
            .map { token ->
                SearchToken(raw = token, stem = stem(token))
            }
    }

    /**
     * Normalizes a token by stripping common English plural endings and suffixes.
     */
    fun stem(token: String): String {
        val t = token.trim().lowercase(Locale.US)
        if (t.length <= 3) return t

        return when {
            t.endsWith("ies") -> t.dropLast(3) + "y" // e.g. flies -> fly
            t.endsWith("es") && (t.endsWith("shes") || t.endsWith("ches") || t.endsWith("xes") || t.endsWith("sses") || t.endsWith("zss")) ->
                t.dropLast(2) // e.g. presses -> press
            t.endsWith("s") && !t.endsWith("ss") -> t.dropLast(1) // e.g. shrugs -> shrug, curls -> curl, cables -> cable
            else -> t
        }
    }

    /**
     * Checks if all tokens in query match at least one attribute of the given [ExerciseEntity].
     */
    fun matches(
        exercise: ExerciseEntity,
        query: String,
        equipmentNames: List<String> = emptyList(),
        secondaryMuscleNames: List<String> = emptyList(),
        familyLabel: String? = null,
    ): Boolean {
        val tokens = tokenize(query)
        if (tokens.isEmpty()) return true

        val searchableText = buildSearchableText(
            name = exercise.name,
            category = exercise.category,
            muscleGroup = exercise.muscleGroup,
            primaryMuscle = exercise.primaryMuscleGroupId,
            movementPattern = exercise.movementPattern.name,
            description = exercise.description,
            notes = exercise.notes,
            family = familyLabel,
            equipmentNames = equipmentNames,
            secondaryMuscleNames = secondaryMuscleNames,
        )

        val nameStemmed = tokenize(exercise.name)

        return tokens.all { token ->
            tokenMatchesText(token, searchableText, nameStemmed)
        }
    }

    /**
     * Checks if all tokens in query match at least one attribute of the given [ExerciseLibraryRow].
     */
    fun matchesRow(row: ExerciseLibraryRow, query: String): Boolean {
        val tokens = tokenize(query)
        if (tokens.isEmpty()) return true

        val familyId = row.metadata?.movementFamilyId
        val familyLabel = familyId?.let { MovementFamilies.label(it) } ?: familyId

        val searchableText = buildSearchableText(
            name = row.exercise.name,
            category = row.exercise.category,
            muscleGroup = row.exercise.muscleGroup,
            primaryMuscle = row.exercise.primaryMuscleGroupId,
            movementPattern = row.exercise.movementPattern.name,
            description = row.exercise.description,
            notes = row.exercise.notes,
            family = familyLabel,
            equipmentNames = emptyList(),
            secondaryMuscleNames = row.secondaryTargets.map { it.name.ifBlank { it.id } },
        )

        val nameTokens = tokenize(row.exercise.name)

        return tokens.all { token ->
            tokenMatchesText(token, searchableText, nameTokens)
        }
    }

    /**
     * Calculates a relevance score for an exercise given a query string.
     * Higher score means higher relevance.
     */
    fun calculateScore(
        exerciseName: String,
        query: String,
        secondaryAttributes: String = "",
    ): Int {
        val rawQuery = query.trim().lowercase(Locale.US)
        val nameLower = exerciseName.trim().lowercase(Locale.US)
        if (rawQuery.isBlank()) return 0

        var score = 0

        // Exact match
        if (nameLower == rawQuery) {
            score += 1000
        }
        // Name starts with exact query
        else if (nameLower.startsWith(rawQuery)) {
            score += 500
        }
        // Name contains full exact query substring
        else if (nameLower.contains(rawQuery)) {
            score += 300
        }

        // Stemmed exact phrase match
        val queryTokens = tokenize(rawQuery)
        val nameTokens = tokenize(nameLower)

        val queryStems = queryTokens.map { it.stem }
        val nameStems = nameTokens.map { it.stem }

        if (queryStems.isNotEmpty() && nameStems.isNotEmpty()) {
            if (nameStems.joinToString(" ") == queryStems.joinToString(" ")) {
                score += 800
            } else if (nameStems.joinToString(" ").startsWith(queryStems.joinToString(" "))) {
                score += 400
            } else if (nameStems.joinToString(" ").contains(queryStems.joinToString(" "))) {
                score += 200
            }
        }

        // Token matches count in name vs secondary attributes
        val tokensInName = queryTokens.count { q ->
            nameTokens.any { n -> n.raw == q.raw || n.stem == q.stem || n.raw.contains(q.stem) || n.stem.contains(q.stem) }
        }
        score += tokensInName * 50

        if (secondaryAttributes.isNotBlank()) {
            val secLower = secondaryAttributes.lowercase(Locale.US)
            val secTokens = tokenize(secLower)
            val tokensInSec = queryTokens.count { q ->
                secTokens.any { s -> s.raw == q.raw || s.stem == q.stem || s.raw.contains(q.stem) }
            }
            score += tokensInSec * 10
        }

        return score
    }

    /**
     * Filters and ranks a list of [ExerciseEntity] items by the query.
     */
    fun filterAndRank(
        exercises: List<ExerciseEntity>,
        query: String,
        equipmentMap: Map<String, List<String>> = emptyMap(),
        secondaryMusclesMap: Map<String, List<String>> = emptyMap(),
        familyLabelMap: Map<String, String> = emptyMap(),
    ): List<ExerciseEntity> {
        if (query.isBlank()) return exercises

        return exercises
            .filter { ex ->
                val equip = equipmentMap[ex.id].orEmpty()
                val secMuscles = secondaryMusclesMap[ex.id].orEmpty()
                val familyLabel = familyLabelMap[ex.id]
                matches(ex, query, equipmentNames = equip, secondaryMuscleNames = secMuscles, familyLabel = familyLabel)
            }
            .sortedByDescending { ex ->
                val equip = equipmentMap[ex.id].orEmpty().joinToString(" ")
                val secMuscles = secondaryMusclesMap[ex.id].orEmpty().joinToString(" ")
                val family = familyLabelMap[ex.id].orEmpty()
                val secAttr = "$equip $secMuscles $family ${ex.category} ${ex.muscleGroup} ${ex.primaryMuscleGroupId}"
                calculateScore(ex.name, query, secondaryAttributes = secAttr)
            }
    }

    /**
     * Filters and ranks a list of [ExerciseLibraryRow] items by the query.
     */
    fun filterAndRankRows(
        rows: List<ExerciseLibraryRow>,
        query: String,
    ): List<ExerciseLibraryRow> {
        if (query.isBlank()) return rows

        return rows
            .filter { matchesRow(it, query) }
            .sortedByDescending { row ->
                val familyId = row.metadata?.movementFamilyId
                val familyLabel = familyId?.let { MovementFamilies.label(it) } ?: familyId ?: ""
                val secMuscles = row.secondaryTargets.joinToString(" ") { it.name.ifBlank { it.id } }
                val secAttr = "$secMuscles $familyLabel ${row.exercise.category} ${row.exercise.muscleGroup} ${row.exercise.primaryMuscleGroupId}"
                calculateScore(row.exercise.name, query, secondaryAttributes = secAttr)
            }
    }

    private fun tokenMatchesText(
        token: SearchToken,
        searchableText: String,
        nameTokens: List<SearchToken>,
    ): Boolean {
        // Direct string match against raw or stem
        if (searchableText.contains(token.raw) || searchableText.contains(token.stem)) {
            return true
        }

        // Token match against any word stem in name tokens
        return nameTokens.any { nameToken ->
            nameToken.raw.contains(token.raw) ||
                nameToken.stem.contains(token.stem) ||
                nameToken.raw.contains(token.stem) ||
                nameToken.stem.contains(token.raw)
        }
    }

    private fun buildSearchableText(
        name: String,
        category: String,
        muscleGroup: String,
        primaryMuscle: String,
        movementPattern: String,
        description: String,
        notes: String,
        family: String?,
        equipmentNames: List<String>,
        secondaryMuscleNames: List<String>,
    ): String {
        return buildString {
            append(name.lowercase(Locale.US)).append(" ")
            append(category.lowercase(Locale.US)).append(" ")
            append(muscleGroup.lowercase(Locale.US)).append(" ")
            append(primaryMuscle.lowercase(Locale.US)).append(" ")
            append(movementPattern.lowercase(Locale.US)).append(" ")
            append(description.lowercase(Locale.US)).append(" ")
            append(notes.lowercase(Locale.US)).append(" ")
            if (family != null) {
                append(family.lowercase(Locale.US)).append(" ")
            }
            equipmentNames.forEach { append(it.lowercase(Locale.US)).append(" ") }
            secondaryMuscleNames.forEach { append(it.lowercase(Locale.US)).append(" ") }
        }
    }
}
