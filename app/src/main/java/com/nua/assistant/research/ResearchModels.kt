package com.nua.assistant.research

import kotlinx.serialization.Serializable

@Serializable
enum class ResearchMode { FAST, STANDARD, DEEP, LOCAL_PRIVATE }

@Serializable
enum class ResearchStatus { CREATED, PLANNED, GATHERING, ANALYZING, SYNTHESIZING, COMPLETED, FAILED }

@Serializable
enum class EvidenceStance { SUPPORTS, CONTRADICTS, NEUTRAL }

@Serializable
data class ResearchSource(
    val id: String,
    val url: String,
    val title: String,
    val content: String,
    val retrievedAtMillis: Long,
    val untrustedExternalContent: Boolean = true,
)

@Serializable
data class EvidenceRecord(
    val id: String,
    val sourceId: String,
    val claim: String,
    val excerpt: String,
    val stance: EvidenceStance,
    val confidence: Double,
)

@Serializable
data class ResearchClaim(
    val id: String,
    val text: String,
    val supportingEvidenceIds: List<String>,
    val contradictingEvidenceIds: List<String>,
    val confidence: Double,
)

@Serializable
data class ResearchSession(
    val id: String,
    val question: String,
    val mode: ResearchMode,
    val status: ResearchStatus = ResearchStatus.CREATED,
    val subquestions: List<String> = emptyList(),
    val allowedUrls: List<String> = emptyList(),
    val sources: List<ResearchSource> = emptyList(),
    val evidence: List<EvidenceRecord> = emptyList(),
    val claims: List<ResearchClaim> = emptyList(),
    val contradictions: List<String> = emptyList(),
    val synthesis: String? = null,
    val citations: List<String> = emptyList(),
    val errorCode: String? = null,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
)
