package com.nua.assistant.diagnostics

enum class DiagnosticStatus {
    OK,
    WARNING,
    ERROR,
    NOT_CONFIGURED,
    INFO,
}

enum class DiagnosticCategory(val label: String) {
    API("API"),
    MEMORY("Memory"),
    VOICE("Voice"),
    LOCATION("Location"),
    CALENDAR("Calendar"),
    EMAIL("Email"),
    AUTOMATION("Automation"),
    WEAR("Wear"),
    AUTO("Android Auto"),
}

data class DiagnosticCheck(
    val category: DiagnosticCategory,
    val status: DiagnosticStatus,
    val detail: String,
)
