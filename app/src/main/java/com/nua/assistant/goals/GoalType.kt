package com.nua.assistant.goals

/**
 * What kind of thing a goal actually is — a flat, undifferentiated "goals" list treats
 * "learn Spanish" (aspiration, no deadline) the same as "reply to Sam by Friday"
 * (commitment, time-bound) even though they warrant different review cadence and
 * different weight in What Now?/Daily Intelligence. User-set at creation time, never
 * inferred from conversation — the same "written to, not inferred" discipline the
 * Decision Journal already uses, so a type is never assigned without the user's own
 * explicit choice.
 */
enum class GoalType {
    /** A direction with no concrete finish line — "get better at cooking." */
    ASPIRATION,
    /** A defined outcome, no fixed deadline — "get my mornings under control." */
    GOAL,
    /** Multiple steps toward one outcome — "repaint the living room." */
    PROJECT,
    /** Made to another person — "call Mom back," "send Sam the file." */
    COMMITMENT,
    /** A single concrete action — "renew my passport." */
    TASK,
    /** Recurring by nature, not a one-time finish — "keep up with the gym." */
    ROUTINE,
}
