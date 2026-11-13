package com.nua.assistant.research

import com.nua.assistant.evolution.SafeSpecPolicyStore
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

fun buildClaims(evidence: List<EvidenceRecord>): Pair<List<ResearchClaim>, List<String>> {
    val groups = evidence.groupBy { it.claim.trim().lowercase() }
    val claims = groups.entries.mapIndexed { index, (_, items) ->
        val supports = items.filter { it.stance == EvidenceStance.SUPPORTS }.map { it.id }
        val contradicts = items.filter { it.stance == EvidenceStance.CONTRADICTS }.map { it.id }
        ResearchClaim(
            id = "C" + (index + 1),
            text = items.first().claim,
            supportingEvidenceIds = supports,
            contradictingEvidenceIds = contradicts,
            confidence = items.map { it.confidence }.average().coerceIn(0.0, 1.0),
        )
    }
    val contradictions = claims.filter {
        it.supportingEvidenceIds.isNotEmpty() && it.contradictingEvidenceIds.isNotEmpty()
    }.map { it.id + ": " + it.text }
    return claims to contradictions
}

@Singleton
class DeepResearchEngine @Inject constructor(
    private val repository: ResearchRepository,
    private val sourceProvider: DirectUrlResearchSourceProvider,
    private val reasoner: ModelMeshResearchReasoner,
    private val specPolicy: SafeSpecPolicyStore,
) {
    fun create(
        question: String,
        mode: ResearchMode,
        allowedUrls: List<String>,
        nowMillis: Long = System.currentTimeMillis(),
    ): ResearchSession {
        require(question.isNotBlank())
        val session = ResearchSession(
            id = UUID.randomUUID().toString(),
            question = question.trim(),
            mode = mode,
            allowedUrls = allowedUrls.distinct(),
            createdAtMillis = nowMillis,
            updatedAtMillis = nowMillis,
        )
        repository.save(session)
        return session
    }

    suspend fun run(id: String): ResearchSession? {
        var session = repository.get(id) ?: return null
        val policy = specPolicy.research()

        val plan = reasoner.plan(session.question, session.mode, policy.maxSubquestions)
        session = session.copy(
            status = ResearchStatus.PLANNED,
            subquestions = plan,
            updatedAtMillis = System.currentTimeMillis(),
        )
        repository.save(session)

        val gathered = linkedMapOf<String, ResearchSource>()
        for (subquestion in plan) {
            sourceProvider.gather(subquestion, session.allowedUrls, limit = policy.crossCheckDepth + 1)
                .forEach { gathered[it.id] = it }
        }
        session = session.copy(
            status = ResearchStatus.ANALYZING,
            sources = gathered.values.toList(),
            updatedAtMillis = System.currentTimeMillis(),
        )
        repository.save(session)

        val evidence = session.sources.flatMap { source ->
            reasoner.assess(session.question, source, session.mode)
        }
        val (claims, contradictions) = buildClaims(evidence)
        session = session.copy(
            status = ResearchStatus.SYNTHESIZING,
            evidence = evidence,
            claims = claims,
            contradictions = contradictions,
            updatedAtMillis = System.currentTimeMillis(),
        )
        repository.save(session)

        val synthesis = reasoner.synthesize(session.question, claims, session.sources, session.mode)
        val citations = Regex("""\[([^\]]+)]""").findAll(synthesis)
            .map { it.groupValues[1] }
            .filter { id -> session.sources.any { it.id == id } }
            .distinct()
            .toList()
        session = session.copy(
            status = ResearchStatus.COMPLETED,
            synthesis = synthesis,
            citations = citations,
            updatedAtMillis = System.currentTimeMillis(),
        )
        repository.save(session)
        return session
    }
}
