package com.nua.assistant.trust

import com.nua.assistant.memory.ActionOutcomeEntity
import kotlin.math.roundToInt

/**
 * Turns raw action history into a single 0-100 "how much autonomy has NUA earned" number.
 * Grounded only in outcomes NUA actually logged (ActionOutcomeEntity) — nothing here is
 * estimated or asserted. A rejection counts for half a failure, since turning down a
 * proposal isn't necessarily a mistake, just an unwanted one.
 */
object TrustScoreEngine {
    private const val REJECTION_WEIGHT = 0.5

    fun score(outcomes: List<ActionOutcomeEntity>): Int {
        if (outcomes.isEmpty()) return 100

        var totalWeight = 0.0
        var badWeight = 0.0
        outcomes.forEach { outcome ->
            val weight = if (outcome.wasRejection) REJECTION_WEIGHT else 1.0
            totalWeight += weight
            if (!outcome.succeeded) badWeight += weight
        }
        if (totalWeight == 0.0) return 100
        return (100 * (1 - badWeight / totalWeight)).roundToInt().coerceIn(0, 100)
    }
}
