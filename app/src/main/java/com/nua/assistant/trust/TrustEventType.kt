package com.nua.assistant.trust

/** A categorized way NUA got caught being wrong — see TrustLedgerEntity in memory/MemoryStore.kt. */
enum class TrustEventType(val label: String) {
    MISREAD_INTENT("misread what you meant"),
    STALE_FACT("remembered something no longer true"),
    REJECTED_PLAN("proposed something you turned down"),
    FAILED_ACTION("tried to do something that didn't work"),
    USER_CORRECTION("got corrected directly"),
}
