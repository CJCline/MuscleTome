package com.chy.muscletome.data.remote

data class WgerPage(
    val count: Int,
    val next: String?,
    val results: List<WgerExerciseInfo>,
)

data class WgerExerciseInfo(
    val id: Int,
    val categoryName: String?,
    val muscles: List<WgerMuscle>,
    val musclesSecondary: List<WgerMuscle>,
    val equipment: List<WgerEquipment>,
    val licenseAuthor: String?,
    val licenseName: String?,
    val englishName: String?,
    val englishDescription: String?,
)

data class WgerMuscle(
    val id: Int,
    val nameEn: String,
)

data class WgerEquipment(
    val id: Int,
    val name: String,
)