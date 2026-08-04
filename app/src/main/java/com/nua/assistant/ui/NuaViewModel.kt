package com.nua.assistant.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nua.assistant.ai.ClaudeApiClient
import com.nua.assistant.ai.ClaudeMessage
import com.nua.assistant.ai.ClaudeResult
import com.nua.assistant.ai.PersonalityEngine
import com.nua.assistant.ai.TaskPlan
import com.nua.assistant.ai.TaskPlanner
import com.nua.assistant.automation.NuaIntentRouter
import com.nua.assistant.automation.NuaRouteResult
import com.nua.assistant.ai.FactExtractor
import com.nua.assistant.memory.MemoryDao
import com.nua.assistant.memory.MessageEntity
import com.nua.assistant.memory.MessageRole
import com.nua.assistant.memory.SecureKeyRepository
import com.nua.assistant.notifications.NotificationRepository
import com.nua.assistant.notifications.NotificationSummary
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
    val pendingPlan: TaskPlan? = null,
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
) : ViewModel() {

    private val _uiState = MutableStateFlow(NuaUiState())
    val uiState: StateFlow<NuaUiState> = _uiState.asStateFlow()

    val notificationSummary: StateFlow<NotificationSummary> = notificationRepository.notifications
        .map { notificationRepository.summary() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NotificationSummary(emptyList(), emptyList()))

    init {
        viewModelScope.launch {
            _uiState.update { it.copy(needsApiKey = !secureKeyRepository.hasApiKey()) }
            val recent = memoryDao.getRecentMessages(RECENT_MESSAGE_LIMIT).asReversed()
            _uiState.update { it.copy(messages = recent.map { m -> ChatMessage(m.role, m.content) }) }
        }
    }

    fun onInputChanged(text: String) {
        _uiState.update { it.copy(inputText = text) }
    }

    fun onWakeWordDetected() {
        voiceManager.startListening(
            onResult = { heard -> sendMessage(heard) },
            onError = { /* stays silent; the mic UI affordance covers the retry path */ },
        )
    }

    fun sendMessage(text: String? = null) {
        val message = (text ?: _uiState.value.inputText).trim()
        if (message.isBlank() || _uiState.value.isProcessing) return

        _uiState.update {
            it.copy(inputText = "", isProcessing = true, messages = it.messages + ChatMessage(MessageRole.USER, message))
        }

        viewModelScope.launch {
            memoryDao.insertMessage(MessageEntity(role = MessageRole.USER, content = message))

            when (val routed = intentRouter.route(message)) {
                is NuaRouteResult.ActionTaken -> respond(routed.message, extractFacts = false)
                is NuaRouteResult.PlanProposed -> _uiState.update { it.copy(isProcessing = false, pendingPlan = routed.plan) }
                NuaRouteResult.FallThroughToChat -> replyConversationally(message)
            }
        }
    }

    private suspend fun replyConversationally(userMessage: String) {
        val facts = memoryDao.getAllFacts()
        val history = memoryDao.getRecentMessages(CONVERSATION_HISTORY_LIMIT).asReversed().map {
            ClaudeMessage(role = if (it.role == MessageRole.USER) "user" else "assistant", content = it.content)
        }

        val result = claudeApiClient.sendMessage(messages = history, system = personalityEngine.systemPrompt(facts))
        val reply = when (result) {
            is ClaudeResult.Success -> result.text
            is ClaudeResult.Failure -> "Sorry — ${result.message}"
        }

        respond(reply, extractFacts = true, sourceUserMessage = userMessage)
    }

    private suspend fun respond(text: String, extractFacts: Boolean, sourceUserMessage: String? = null) {
        memoryDao.insertMessage(MessageEntity(role = MessageRole.ASSISTANT, content = text))
        _uiState.update { it.copy(isProcessing = false, messages = it.messages + ChatMessage(MessageRole.ASSISTANT, text)) }
        voiceManager.speak(text)

        if (extractFacts && sourceUserMessage != null) {
            maybeExtractFacts(sourceUserMessage, text)
        }
    }

    private suspend fun maybeExtractFacts(userMessage: String, assistantReply: String) {
        val turnIndex = memoryDao.countUserMessages()
        if (!factExtractor.shouldConsider(userMessage, turnIndex)) return

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

    fun saveApiKey(apiKey: String) {
        viewModelScope.launch {
            secureKeyRepository.setApiKey(apiKey)
            _uiState.update { it.copy(needsApiKey = false) }
        }
    }

    override fun onCleared() {
        voiceManager.release()
    }

    private companion object {
        const val RECENT_MESSAGE_LIMIT = 30
        const val CONVERSATION_HISTORY_LIMIT = 20
    }
}
