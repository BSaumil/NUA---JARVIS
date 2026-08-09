package com.nua.assistant.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nua.assistant.ai.ClaudeApiClient
import com.nua.assistant.ai.ClaudeMessage
import com.nua.assistant.ai.ClaudeStreamEvent
import com.nua.assistant.ai.FactExtractor
import com.nua.assistant.ai.FactRelevance
import com.nua.assistant.ai.PersonalityEngine
import com.nua.assistant.ai.TaskPlan
import com.nua.assistant.ai.TaskPlanner
import com.nua.assistant.automation.NuaIntentRouter
import com.nua.assistant.automation.NuaRouteResult
import com.nua.assistant.briefing.BriefingSchedule
import com.nua.assistant.briefing.BriefingScheduleStore
import com.nua.assistant.briefing.BriefingScheduler
import com.nua.assistant.memory.MemoryDao
import com.nua.assistant.memory.MessageEntity
import com.nua.assistant.memory.MessageRole
import com.nua.assistant.memory.SecureKeyRepository
import com.nua.assistant.memory.UserFactEntity
import com.nua.assistant.notifications.NotificationReplySender
import com.nua.assistant.notifications.NotificationRepository
import com.nua.assistant.notifications.NotificationSummary
import com.nua.assistant.voice.EnrollmentStep
import com.nua.assistant.voice.LanguagePreferenceStore
import com.nua.assistant.voice.NuaLanguage
import com.nua.assistant.voice.OwnerEnrollment
import com.nua.assistant.voice.OwnerVerifier
import com.nua.assistant.voice.VoiceManager
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

    val notificationSummary: StateFlow<NotificationSummary> = notificationRepository.notifications
        .map { notificationRepository.summary() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NotificationSummary(emptyList(), emptyList()))

    val facts: StateFlow<List<UserFactEntity>> = memoryDao.observeFacts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // Continuous conversation mode: once woken by voice, keep listening for a few
    // follow-ups without repeating the wake word. Broken by typed input, a listening
    // error/timeout, or after MAX_VOICE_FOLLOW_UPS — never indefinite, so the mic isn't
    // just left hot.
    private var voiceSessionActive = false
    private var voiceFollowUpCount = 0

    init {
        viewModelScope.launch {
            _uiState.update { it.copy(needsApiKey = !secureKeyRepository.hasApiKey()) }
            val recent = memoryDao.getRecentMessages(RECENT_MESSAGE_LIMIT).asReversed()
            _uiState.update { it.copy(messages = recent.map { m -> ChatMessage(m.role, m.content) }) }
        }
        voiceManager.setOnFinalSpeechDoneListener { onReplyFinishedSpeaking() }
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
                is NuaRouteResult.PlanProposed -> _uiState.update { it.copy(isProcessing = false, pendingPlan = routed.plan) }
                is NuaRouteResult.ReplyProposed -> _uiState.update { it.copy(isProcessing = false, pendingReply = routed) }
                NuaRouteResult.FallThroughToChat -> replyConversationally(message)
            }
        }
    }

    private suspend fun replyConversationally(userMessage: String) {
        val relevantFacts = FactRelevance.rank(memoryDao.getAllFacts(), userMessage)
        val turnCount = memoryDao.countUserMessages()
        val history = memoryDao.getRecentMessages(CONVERSATION_HISTORY_LIMIT).asReversed().map {
            ClaudeMessage(role = if (it.role == MessageRole.USER) "user" else "assistant", content = it.content)
        }
        val system = personalityEngine.systemPrompt(relevantFacts, turnCount, _pinnedLanguage.value)
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
            memoryDao.upsertFact(key = fact.key, value = fact.value, category = fact.category)
        }
    }

    fun confirmPendingPlan() {
        val plan = _uiState.value.pendingPlan ?: return
        viewModelScope.launch {
            taskPlanner.confirmPlan(plan)
            _uiState.update { it.copy(pendingPlan = null) }
            respond("Done — I've added reminders for that.", extractFacts = false)
        }
    }

    fun dismissPendingPlan() {
        _uiState.update { it.copy(pendingPlan = null) }
    }

    fun confirmPendingReply() {
        val pending = _uiState.value.pendingReply ?: return
        viewModelScope.launch {
            val replyAction = pending.notification.replyAction
            val sent = replyAction != null && notificationReplySender.sendReply(replyAction, pending.message)
            _uiState.update { it.copy(pendingReply = null) }
            val confirmation = if (sent) {
                "Sent — replied to ${pending.notification.title}."
            } else {
                "That reply didn't go through — the notification may have been dismissed."
            }
            respond(confirmation, extractFacts = false)
        }
    }

    fun dismissPendingReply() {
        _uiState.update { it.copy(pendingReply = null) }
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

        private val SENTENCE_END_CHARS = charArrayOf('.', '!', '?', '\n')

        /** Furthest index in [text] (from [from] onward) that ends a complete sentence, or [from] if none yet. */
        fun spokenSentenceBoundary(text: StringBuilder, from: Int): Int {
            var lastBoundary = from
            for (i in from until text.length) {
                if (text[i] in SENTENCE_END_CHARS) lastBoundary = i + 1
            }
            return lastBoundary
        }
    }
}
