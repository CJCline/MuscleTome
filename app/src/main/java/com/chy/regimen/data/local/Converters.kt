package com.chy.regimen.data.local

import androidx.room.TypeConverter
import com.chy.regimen.domain.model.Difficulty
import com.chy.regimen.domain.model.ExerciseSource
import com.chy.regimen.domain.model.MatchStrictness
import com.chy.regimen.domain.model.MovementPattern
import com.chy.regimen.domain.model.MovementType
import com.chy.regimen.domain.model.SelectionReason
import com.chy.regimen.domain.model.SlotType
import com.chy.regimen.domain.model.SubscriptionStatus
import com.chy.regimen.domain.model.TargetMovementType
import com.chy.regimen.domain.model.WeightUnit

class Converters {
    @TypeConverter fun fromMovementPattern(v: MovementPattern) = v.name
    @TypeConverter fun toMovementPattern(v: String) = MovementPattern.valueOf(v)

    @TypeConverter fun fromMovementType(v: MovementType) = v.name
    @TypeConverter fun toMovementType(v: String) = MovementType.valueOf(v)

    @TypeConverter fun fromDifficulty(v: Difficulty) = v.name
    @TypeConverter fun toDifficulty(v: String) = Difficulty.valueOf(v)

    @TypeConverter fun fromExerciseSource(v: ExerciseSource) = v.name
    @TypeConverter fun toExerciseSource(v: String) = ExerciseSource.valueOf(v)

    @TypeConverter fun fromSlotType(v: SlotType) = v.name
    @TypeConverter fun toSlotType(v: String) = SlotType.valueOf(v)

    @TypeConverter fun fromTargetMovementType(v: TargetMovementType) = v.name
    @TypeConverter fun toTargetMovementType(v: String) = TargetMovementType.valueOf(v)

    @TypeConverter fun fromSelectionReason(v: SelectionReason) = v.name
    @TypeConverter fun toSelectionReason(v: String) = SelectionReason.valueOf(v)

    @TypeConverter fun fromWeightUnit(v: WeightUnit) = v.name
    @TypeConverter fun toWeightUnit(v: String) = WeightUnit.valueOf(v)

    @TypeConverter fun fromMatchStrictness(v: MatchStrictness) = v.name
    @TypeConverter fun toMatchStrictness(v: String) = MatchStrictness.valueOf(v)

    @TypeConverter fun fromSubscriptionStatus(v: SubscriptionStatus) = v.name
    @TypeConverter fun toSubscriptionStatus(v: String) = SubscriptionStatus.valueOf(v)
}