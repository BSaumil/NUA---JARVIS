package com.nua.assistant.memory

/**
 * What kind of thing a stored fact represents — lets Settings group and explain memory
 * instead of showing one flat list, and gives future retrieval logic a way to weight
 * types differently (identity facts always relevant, behavioural facts only in context).
 */
enum class MemoryType {
    /** Who the user is: name, important people, important dates. */
    IDENTITY,
    /** What happened: an event, a decision, a conversation worth recalling later. */
    EPISODIC,
    /** What NUA knows: a standalone fact, preference, or project. */
    SEMANTIC,
    /** How the user behaves: a routine, a pattern, habitual timing. */
    BEHAVIORAL,
    /** How an interaction felt: NUA noticed frustration, excitement, a rejected idea. */
    EMOTIONAL,
    /** How people relate to the user — "Sam's daughter," not just a bare name. */
    RELATIONSHIP,
}
