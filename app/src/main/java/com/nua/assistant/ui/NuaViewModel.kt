package com.nua.assistant.ui

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nua.assistant.ai.ClaudeApiClient
import com.nua.assistant.ai.ClaudeMessage
import com.nua.assistant.ai.ClaudeResult
import com.nua.assistant.ai.ClaudeStreamEvent
import com.nua.assistant.ai.FactExtractor
import com.nua.assistant.ai.FactRelevance
import com.nua.assistant.ai.NuaActionType
import com.nua.assistant.ai.PersonalityEngine
import com.nua.assistant.ai.TaskPlan
import com.nua.assistant.ai.TaskPlanner
import com.nua.assistant.ai.UsageSummary
import com.nua.assistant.ai.UsageTracker
import com.nua.assistant.automation.NuaIntentRouter
import com.nua.assistant.automation.NuaRouteResult
import com.nua.assistant.briefing.BriefingSchedule
import com.nua.assistant.briefing.BriefingScheduleStore
import com.nua.assistant.briefing.BriefingScheduler
import com.nua.assistant.context.WhatNowAdvisor
import com.nua.assistant.dreams.DreamRepository
import com.nua.assistant.geofencing.GeofenceManager
import com.nua.assistant.goals.GoalRepository
import com.nua.assistant.memory.DreamEntity
import com.nua.assistant.memory.GeofenceDao
import com.nua.assistant.memory.GeofenceEntity
import com.nua.assistant.memory.GoalEntity
import com.nua.assistant.memory.GoalObservationEntity
import com.nua.assistant.memory.MemoryDao
import com.nua.assistant.memory.MessageEntity
import com.nua.assistant.memory.MessageRole
import com.nua.assistant.memory.MemoryType
import com.nua.assistant.memory.SecureKeyRepository
import com.nua.assistant.memory.UserFactEntity
import com.nua.assistant.network.ConnectivityMonitor
import com.nua.assistant.notifications.NotificationReplySender
import com.nua.assistant.notifications.NotificationRepository
import com.nua.assistant.notifications.NotificationSummary
import com.nua.assistant.trust.AutonomyTier
import com.nua.assistant.trust.TrustRepository
import com.nua.assistant.memory.ActionOutcomeEntity
import com.nua.assistant.memory.AutonomyPreferenceEntity
import com.nua.assistant.memory.TrustLedgerEntity
import com.nua.assistant.voice.EnrollmentStep
import com.nua.assistant.voice.LanguagePreferenceStore
import com.nua.assistant.voice.NuaLanguage
import com.nua.assistant.voice.OwnerEnrollment
import com.nua.assistant.voice.OwnerVerifier
import com.nua.assistant.voice.VoiceManager
import com.nua.assistant.voice.VoiceProsody
import com.nua.assistant.voice.VoiceTone
import com.nua.assistant.vision.ImageEncoder
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ChatMessage(val role: MessageRole, val content: String)

data class NuaUiState(
    val messages: List<ChatMessage> = emptyList(),
    val inputText: String = "",
    val isProcessing: Boolean = false,
    /** Partial assistant text as it streams in, rendered as an extra bubble until the reply completes. */
    val streamingReply: String? = null,
    val pendingPlan: TaskPlan? = null,
    val pendingReply: NuaRouteResult.ReplyProposed? = null,
    val needsApiKey: Boolean = false,
)

@HiltViewModel
class NuaViewModel @Inject constructor(
    private val memoryDao: MemoryDao,
    private val claudeApiClient: ClaudeApiClient,
    private val personalityEngine: PersonalityEngine,
    private val intentRouter: NuaIntentRouter,
    private val factExtractor: FactExtractor,
    private val taskPlanner: TaskPlanner,
    private val secureKeyRepository: SecureKeyRepository,
    private val voiceManager: VoiceManager,
    private val notificationRepository: NotificationRepository,
    private val notificationReplySender: NotificationReplySender,
    private val languagePreferenceStore: LanguagePreferenceStore,
    private val briefingScheduleStore: BriefingScheduleStore,
    private val briefingScheduler: BriefingScheduler,
    private val ownerEnrollment: OwnerEnrollment,
    private val ownerVerifier: OwnerVerifier,
    private val connectivityMonitor: ConnectivityMonitor,
    private val imageEncoder: ImageEncoder,
    private val geofenceManager: GeofenceManager,
    private val geofenceDao: GeofenceDao,
    private val usageTracker: UsageTracker,
    private val trustRepository: TrustRepository,
    private val goalRepository: GoalRepository,
    private val whatNowAdvisor: WhatNowAdvisor,
    private val dreamRepository: DreamRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(NuaUiState())
    val uiState: StateFlow<NuaUiState> = _uiState.asStateFlow()

    private val _pinnedLanguage = MutableStateFlow(languagePreferenceStore.getPinnedLanguage())
    val pinnedLanguage: StateFlow<NuaLanguage?> = _pinnedLanguage.asStateFlow()

    private val _briefingSchedule = MutableStateFlow(briefingScheduleStore.get())
    val briefingSchedule: StateFlow<BriefingSchedule> = _briefingSchedule.asStateFlow()

    private val _voiceEnrolled = MutableStateFlow(ownerVerifier.isEnrolled())
    val voiceEnrolled: StateFlow<Boolean> = _voiceEnrolled.asStateFlow()

    private val _enrollmentProgress = MutableStateFlow<Int?>(null)
    val enrollmentProgress: StateFlow<Int?> = _enrollmentProgress.asStateFlow()

    private val _usageThisMonth = MutableStateFlow<UsageSummary?>(null)
    val usageThisMonth: StateFlow<UsageSummary?> = _usageThisMonth.asStateFlow()

    private val _trustScore = MutableStateFlow<Int?>(null)
    val trustScore: StateFlow<Int?> = _trustScore.asStateFlow()

    private val _trustLedger = MutableStateFlow<List<TrustLedgerEntity>>(emptyList())
    val trustLedger: StateFlow<List<TrustLedgerEntity>> = _trustLedger.asStateFlow()

    private val _actionOutcomes = MutableStateFlow<List<ActionOutcomeEntity>>(emptyList())
    val actionOutcomes: StateFlow<List<ActionOutcomeEntity>> = _actionOutcomes.asStateFlow()

    private val _autonomySuggestions = MutableStateFlow<List<AutonomyPreferenceEntity>>(emptyList())
    val autonomySuggestions: StateFlow<List<AutonomyPreferenceEntity>> = _autonomySuggestions.asStateFlow()

    val notificationSummary: StateFlow<NotificationSummary> = notificationRepository.notifications
        .map { notificationRepository.summary() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NotificationSummary(emptyList(), emptyList()))

    val facts: StateFlow<List<UserFactEntity>> = memoryDao.observeFacts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val geofences: StateFlow<List<GeofenceEntity>> = geofenceDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val goals: StateFlow<List<GoalEntity>> = goalRepository.observeGoals()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val goalObservations: StateFlow<List<GoalObservationEntity>> = goalRepository.observeObservations()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val dreams: StateFlow<List<DreamEntity>> = dreamRepository.observeRecent()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // Continuous conversation mode: once woken by voice, keep listening for a few
    // follow-ups without repeating the wake word. Broken by typed input, a listening
    // error/timeout, or after MAX_VOICE_FOLLOW_UPS — never indefinite, so the mic isn't
    // just left hot.
    private var voiceSessionActive = false
    private var voiceFollowUpCount = 0

    // Set right before a voice turn's transcript comes back (see VoiceProsody), consumed
    // once by the next replyConversationally() call and cleared — a typed follow-up in
    // the same session shouldn't carry a stale spoken tone forward.
    private var lastVoiceTone: VoiceTone? = null

    init {
        viewModelScope.launch {
            _uiState.update { it.copy(needsApiKey = !secureKeyRepository.hasApiKey()) }
            val recent = memoryDao.getRecentMessages(RECENT_MESSAGE_LIMIT).asReversed()
            _uiState.update { it.copy(messages = recent.map { m -> ChatMessage(m.role, m.content) }) }
        }
        voiceManager.setOnFinalSpeechDoneListener { onReplyFinishedSpeaking() }
        viewModelScope.launch { geofenceManager.registerAll() }
        viewModelScope.launch { maybeShowSelfReport() }
        viewModelScope.launch { maybeShowDream() }
    }

    /**
     * Surfaces at most one unshown NUA Dream per app open — DreamSynthesisWorker is what
     * actually rate-limits how often a new one exists (weekly, and only if there's
     * something worth saying). Appended silently like the self-report and morning
     * briefing, in NUA's own voice rather than announced as a notification.
     */
    private suspend fun maybeShowDream() {
        val dream = dreamRepository.nextUnshown() ?: return
        dreamRepository.markShown(dream.id)
        val message = "Something occurred to me: ${dream.text}"
        memoryDao.insertMessage(MessageEntity(role = MessageRole.ASSISTANT, content = message))
        _uiState.update { it.copy(messages = it.messages + ChatMessage(MessageRole.ASSISTANT, message)) }
    }

    /**
     * At the highest familiarity tier, NUA occasionally reports on itself unprompted — see
     * TrustRepository.pendingSelfReport. Appended silently like the morning briefing (not
     * spoken aloud unprompted), since this fires on ordinary app open, not a voice turn.
     */
    private suspend fun maybeShowSelfReport() {
        val turnCount = memoryDao.countUserMessages()
        val factCount = memoryDao.countFacts()
        val tier = personalityEngine.familiarityTier(turnCount, factCount)
        val report = trustRepository.pendingSelfReport(tier) ?: return
        memoryDao.insertMessage(MessageEntity(role = MessageRole.ASSISTANT, content = report))
        _uiState.update { it.copy(messages = it.messages + ChatMessage(MessageRole.ASSISTANT, report)) }
    }

    fun onInputChanged(text: String) {
        _uiState.update { it.copy(inputText = text) }
    }

    /**
     * [wakePhraseId] (see WakePhrase.id) is which phrase woke NUA up — "jarvis",
     * "daddys_home", etc. Not used for anything yet beyond starting to listen; it's a
     * hook for future per-phrase personalization (a groggier reply for "wake up sleepy
     * head" vs. a plain one for "hey nua"), not required for wake-word detection itself.
     */
    fun onWakeWordDetected(wakePhraseId: String? = null) {
        voiceSessionActive = true
        voiceFollowUpCount = 0
        listenForVoiceTurn()
    }

    private fun onReplyFinishedSpeaking() {
        if (!voiceSessionActive || voiceFollowUpCount >= MAX_VOICE_FOLLOW_UPS) {
            voiceSessionActive = false
            return
        }
        voiceFollowUpCount++
        listenForVoiceTurn()
    }

    private fun listenForVoiceTurn() {
        voiceManager.startListening(
            language = _pinnedLanguage.value ?: NuaLanguage.ENGLISH,
            onResult = { heard -> sendMessage(heard, isVoiceTurn = true) },
            onError = { voiceSessionActive = false },
            onTone = { tone -> lastVoiceTone = tone },
        )
    }

    fun sendMessage(text: String? = null, isVoiceTurn: Boolean = false) {
        val message = (text ?: _uiState.value.inputText).trim()
        if (message.isBlank() || _uiState.value.isProcessing) return
        if (!isVoiceTurn) voiceSessionActive = false // typed input breaks the follow-up chain

        _uiState.update {
            it.copy(inputText = "", isProcessing = true, messages = it.messages + ChatMessage(MessageRole.USER, message))
        }

        viewModelScope.launch {
            memoryDao.insertMessage(MessageEntity(role = MessageRole.USER, content = message))

            when (val routed = intentRouter.route(message, _pinnedLanguage.value)) {
                is NuaRouteResult.ActionTaken -> respond(routed.message, extractFacts = false)
                is NuaRouteResult.PlanProposed -> {
                    if (trustRepository.isAutoApproved(NuaActionType.PLAN_TASK)) {
                        executeConfirmedPlan(routed.plan)
                    } else {
                        _uiState.update { it.copy(isProcessing = false, pendingPlan = routed.plan) }
                    }
                }
                is NuaRouteResult.ReplyProposed -> {
                    if (trustRepository.isAutoApproved(NuaActionType.REPLY_TO_NOTIFICATION)) {
                        executeConfirmedReply(routed)
                    } else {
                        _uiState.update { it.copy(isProcessing = false, pendingReply = routed) }
                    }
                }
                NuaRouteResult.FallThroughToChat -> replyConversationally(message)
            }
        }
    }

    private suspend fun replyConversationally(userMessage: String) {
        if (!connectivityMonitor.isOnline()) {
            respond(offlineMessage(), extractFacts = false)
            return
        }

        val relevantFacts = FactRelevance.rank(memoryDao.getAllFacts(), userMessage)
        val usedAt = System.currentTimeMillis()
        relevantFacts.forEach { fact -> memoryDao.touchFactUsage(fact.key, usedAt) }
        val turnCount = memoryDao.countUserMessages()
        val history = memoryDao.getRecentMessages(CONVERSATION_HISTORY_LIMIT).asReversed().map {
            ClaudeMessage(role = if (it.role == MessageRole.USER) "user" else "assistant", content = it.content)
        }
        val toneDirective = VoiceProsody.directiveFor(lastVoiceTone ?: VoiceTone.NEUTRAL)
        lastVoiceTone = null
        val system = personalityEngine.systemPrompt(relevantFacts, turnCount, _pinnedLanguage.value, toneDirective)
        val language = _pinnedLanguage.value ?: NuaLanguage.ENGLISH

        val fullText = StringBuilder()
        var spokenUpTo = 0
        var hasSpokenAnything = false
        var streamFailed = false

        claudeApiClient.streamMessage(messages = history, system = system).collect { event ->
            when (event) {
                is ClaudeStreamEvent.TextDelta -> {
                    fullText.append(event.text)
                    _uiState.update { it.copy(isProcessing = false, streamingReply = fullText.toString()) }

                    val boundary = spokenSentenceBoundary(fullText, spokenUpTo)
                    if (boundary > spokenUpTo) {
                        val toSpeak = fullText.substring(spokenUpTo, boundary).trim()
                        spokenUpTo = boundary
                        if (toSpeak.isNotEmpty()) {
                            voiceManager.speak(toSpeak, language, flush = !hasSpokenAnything)
                            hasSpokenAnything = true
                        }
                    }
                }

                is ClaudeStreamEvent.Done -> {
                    val remaining = fullText.substring(spokenUpTo).trim()
                    if (remaining.isNotEmpty()) voiceManager.speak(remaining, language, flush = !hasSpokenAnything)
                    voiceManager.markReplyComplete()
                }

                is ClaudeStreamEvent.Error -> {
                    streamFailed = true
                    fullText.clear()
                    fullText.append("Sorry — ${event.message}")
                    voiceManager.speak(fullText.toString(), language)
                    voiceManager.markReplyComplete()
                }
            }
        }

        _uiState.update { it.copy(streamingReply = null) }
        finalizeAssistantReply(fullText.toString(), userMessage, extractFacts = !streamFailed)
    }

    private suspend fun finalizeAssistantReply(text: String, sourceUserMessage: String, extractFacts: Boolean) {
        memoryDao.insertMessage(MessageEntity(role = MessageRole.ASSISTANT, content = text))
        _uiState.update { it.copy(isProcessing = false, messages = it.messages + ChatMessage(MessageRole.ASSISTANT, text)) }

        if (extractFacts) maybeExtractFacts(sourceUserMessage, text)
    }

    /** For non-streamed replies (action confirmations, plan-confirmation messages) — speaks the whole thing in one call. */
    private suspend fun respond(text: String, extractFacts: Boolean, sourceUserMessage: String? = null) {
        memoryDao.insertMessage(MessageEntity(role = MessageRole.ASSISTANT, content = text))
        _uiState.update { it.copy(isProcessing = false, messages = it.messages + ChatMessage(MessageRole.ASSISTANT, text)) }
        voiceManager.speak(text, _pinnedLanguage.value ?: NuaLanguage.ENGLISH)
        voiceManager.markReplyComplete()

        if (extractFacts && sourceUserMessage != null) {
            maybeExtractFacts(sourceUserMessage, text)
        }
    }

    private suspend fun maybeExtractFacts(userMessage: String, assistantReply: String) {
        val turnIndex = memoryDao.countUserMessages()
        if (!FactExtractor.shouldConsider(userMessage, turnIndex)) return

        factExtractor.extractFacts(userMessage, assistantReply).forEach { fact ->
            memoryDao.upsertFact(
                key = fact.key,
                value = fact.value,
                category = fact.category,
                memoryType = memoryTypeForCategory(fact.category),
                source = "said in conversation",
            )
        }
    }

    /** Sends a just-captured photo to Claude's vision and speaks/shows the response like any other reply. */
    fun describeImage(uri: Uri) {
        if (_uiState.value.isProcessing) return
        _uiState.update {
            it.copy(isProcessing = true, messages = it.messages + ChatMessage(MessageRole.USER, "[Photo]"))
        }
        viewModelScope.launch {
            if (!connectivityMonitor.isOnline()) {
                respond("No connection right now — can't look at that photo until you're back online.", extractFacts = false)
                return@launch
            }
            val encoded = imageEncoder.encode(uri)
            if (encoded == null) {
                respond("Couldn't read that photo.", extractFacts = false)
                return@launch
            }
            val result = claudeApiClient.describeImage(
                imageBase64 = encoded.base64,
                mediaType = encoded.mediaType,
                prompt = "Describe what's in this photo in a couple of sentences, and mention anything the user might want to know or act on.",
            )
            val text = when (result) {
                is ClaudeResult.Success -> result.text
                is ClaudeResult.Failure -> "Couldn't process that photo — ${result.message}"
            }
            respond(text, extractFacts = false)
        }
    }

    fun confirmPendingPlan() {
        val plan = _uiState.value.pendingPlan ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(pendingPlan = null) }
            trustRepository.recordApproval(NuaActionType.PLAN_TASK)
            executeConfirmedPlan(plan)
        }
    }

    private suspend fun executeConfirmedPlan(plan: TaskPlan) {
        taskPlanner.confirmPlan(plan)
        trustRepository.recordOutcome(
            actionType = NuaActionType.PLAN_TASK.name,
            tier = AutonomyTier.T4,
            summary = "Confirmed plan: ${plan.summary}",
            succeeded = true,
        )
        respond("Done — I've added reminders for that.", extractFacts = false)
    }

    fun dismissPendingPlan() {
        val plan = _uiState.value.pendingPlan ?: return
        _uiState.update { it.copy(pendingPlan = null) }
        viewModelScope.launch {
            trustRepository.recordOutcome(
                actionType = NuaActionType.PLAN_TASK.name,
                tier = AutonomyTier.T4,
                summary = "Declined plan: ${plan.summary}",
                succeeded = false,
                wasRejection = true,
            )
        }
    }

    fun confirmPendingReply() {
        val pending = _uiState.value.pendingReply ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(pendingReply = null) }
            trustRepository.recordApproval(NuaActionType.REPLY_TO_NOTIFICATION)
            executeConfirmedReply(pending)
        }
    }

    private suspend fun executeConfirmedReply(pending: NuaRouteResult.ReplyProposed) {
        val replyAction = pending.notification.replyAction
        val sent = replyAction != null && notificationReplySender.sendReply(replyAction, pending.message)
        val confirmation = if (sent) {
            "Sent — replied to ${pending.notification.title}."
        } else {
            "That reply didn't go through — the notification may have been dismissed."
        }
        trustRepository.recordOutcome(
            actionType = NuaActionType.REPLY_TO_NOTIFICATION.name,
            tier = AutonomyTier.T3,
            summary = confirmation,
            succeeded = sent,
        )
        respond(confirmation, extractFacts = false)
    }

    fun dismissPendingReply() {
        val pending = _uiState.value.pendingReply ?: return
        _uiState.update { it.copy(pendingReply = null) }
        viewModelScope.launch {
            trustRepository.recordOutcome(
                actionType = NuaActionType.REPLY_TO_NOTIFICATION.name,
                tier = AutonomyTier.T3,
                summary = "Declined proposed reply to ${pending.notification.title}",
                succeeded = false,
                wasRejection = true,
            )
        }
    }

    fun saveApiKey(apiKey: String) {
        viewModelScope.launch {
            secureKeyRepository.setApiKey(apiKey)
            _uiState.update { it.copy(needsApiKey = false) }
        }
    }

    fun forgetFact(factId: Long) {
        viewModelScope.launch { memoryDao.deleteFactById(factId) }
    }

    /** Null pins nothing — NUA goes back to auto-mirroring whatever language the user writes/speaks in. */
    fun setPinnedLanguage(language: NuaLanguage?) {
        languagePreferenceStore.setPinnedLanguage(language)
        _pinnedLanguage.value = language
    }

    fun setBriefingSchedule(schedule: BriefingSchedule) {
        briefingScheduler.applySchedule(schedule)
        _briefingSchedule.value = schedule
    }

    /** Records one short clip and feeds it to enrollment; call again until [voiceEnrolled] flips true. */
    fun recordVoiceEnrollmentClip() {
        viewModelScope.launch {
            when (val step = ownerEnrollment.enrollOneClip()) {
                is EnrollmentStep.Progress -> _enrollmentProgress.value = step.percentage
                EnrollmentStep.Complete -> {
                    _enrollmentProgress.value = null
                    _voiceEnrolled.value = true
                }
                is EnrollmentStep.Failed -> _enrollmentProgress.value = null
            }
        }
    }

    fun addGeofence(name: String, latitude: Double, longitude: Double, message: String) {
        viewModelScope.launch { geofenceManager.addGeofence(name, latitude, longitude, GEOFENCE_DEFAULT_RADIUS_METERS, message) }
    }

    fun removeGeofence(id: Long) {
        viewModelScope.launch { geofenceManager.removeGeofence(id) }
    }

    /** The "What should I do now?" entry point — same chat surface, no typing required. */
    fun whatShouldIDoNow() {
        if (_uiState.value.isProcessing) return
        val prompt = "What should I do now?"
        _uiState.update {
            it.copy(isProcessing = true, messages = it.messages + ChatMessage(MessageRole.USER, prompt))
        }
        viewModelScope.launch {
            memoryDao.insertMessage(MessageEntity(role = MessageRole.USER, content = prompt))
            if (!connectivityMonitor.isOnline()) {
                respond(offlineMessage(), extractFacts = false)
                return@launch
            }
            val recommendation = whatNowAdvisor.recommend(_pinnedLanguage.value)
            respond(recommendation, extractFacts = false)
        }
    }

    fun addGoal(text: String) {
        if (text.isBlank()) return
        viewModelScope.launch { goalRepository.addGoal(text.trim()) }
    }

    fun removeGoal(id: Long) {
        viewModelScope.launch { goalRepository.deactivateGoal(id) }
    }

    /** Called when Settings opens — usage isn't worth keeping live-updated, just fresh on view. */
    fun refreshUsage() {
        viewModelScope.launch { _usageThisMonth.value = usageTracker.summaryThisMonth() }
    }

    /** Called when Settings opens — same reasoning as [refreshUsage]. */
    fun refreshTrust() {
        viewModelScope.launch {
            _trustScore.value = trustRepository.scoreSnapshot()
            _trustLedger.value = trustRepository.recentLedger()
            _actionOutcomes.value = trustRepository.recentOutcomes()
            _autonomySuggestions.value = trustRepository.autonomySuggestions()
        }
    }

    fun enableAutoApprove(actionType: NuaActionType) {
        viewModelScope.launch {
            trustRepository.setAutoApprove(actionType, enabled = true)
            _autonomySuggestions.value = trustRepository.autonomySuggestions()
        }
    }

    fun resetVoiceEnrollment() {
        ownerEnrollment.resetEnrollment()
        _voiceEnrolled.value = false
        _enrollmentProgress.value = null
    }

    override fun onCleared() {
        voiceManager.release()
    }

    private companion object {
        const val RECENT_MESSAGE_LIMIT = 30
        const val CONVERSATION_HISTORY_LIMIT = 20
        const val MAX_VOICE_FOLLOW_UPS = 4
        const val GEOFENCE_DEFAULT_RADIUS_METERS = 150f

        private val SENTENCE_END_CHARS = charArrayOf('.', '!', '?', '\n')

        /** No network for the Claude round trip that CHAT-fallthrough always needs — surfaced immediately instead of timing out. */
        fun offlineMessage(): String {
            val time = java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault()).format(java.util.Date())
            return "No connection right now, so I can't think that through — it's $time here if that helps. " +
                "Opening apps, media controls, and reading notifications still work offline."
        }

        /** Furthest index in [text] (from [from] onward) that ends a complete sentence, or [from] if none yet. */
        fun spokenSentenceBoundary(text: StringBuilder, from: Int): Int {
            var lastBoundary = from
            for (i in from until text.length) {
                if (text[i] in SENTENCE_END_CHARS) lastBoundary = i + 1
            }
            return lastBoundary
        }

        /** FactExtractor's free-text category (Claude-generated) mapped onto the fixed MemoryType taxonomy. */
        fun memoryTypeForCategory(category: String): MemoryType = when (category.lowercase()) {
            "name" -> MemoryType.IDENTITY
            "relationship" -> MemoryType.RELATIONSHIP
            "routine" -> MemoryType.BEHAVIORAL
            "preference" -> MemoryType.SEMANTIC
            else -> MemoryType.SEMANTIC
        }
    }
}
