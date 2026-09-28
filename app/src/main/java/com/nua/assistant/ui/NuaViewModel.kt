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
import com.nua.assistant.security.egress.DataEgressGateway
import com.nua.assistant.ai.NuaActionType
import com.nua.assistant.ai.PersonalityEngine
import com.nua.assistant.ai.SecondBrainResult
import com.nua.assistant.ai.SecondBrainSearch
import com.nua.assistant.ai.PlannedStep
import com.nua.assistant.ai.SuggestedReminder
import com.nua.assistant.ai.TaskPlan
import com.nua.assistant.ai.TaskPlanner
import com.nua.assistant.ai.UsageSummary
import com.nua.assistant.ai.UsageTracker
import com.nua.assistant.automation.NuaIntentRouter
import com.nua.assistant.automation.SkillCatalog
import com.nua.assistant.automation.SkillDescriptor
import com.nua.assistant.automation.NuaRouteResult
import com.nua.assistant.briefing.BriefingSchedule
import com.nua.assistant.briefing.BriefingScheduleStore
import com.nua.assistant.briefing.BriefingScheduler
import com.nua.assistant.context.WhatNowAdvisor
import com.nua.assistant.context.WhatNowResult
import com.nua.assistant.context.chatSummary
import com.nua.assistant.decisions.DecisionRepository
import com.nua.assistant.diagnostics.DiagnosticCategory
import com.nua.assistant.diagnostics.DiagnosticCheck
import com.nua.assistant.diagnostics.SelfDiagnosticsRepository
import com.nua.assistant.documents.DocumentAnalyzer
import com.nua.assistant.security.UserUtterance
import com.nua.assistant.state.NuaState
import com.nua.assistant.state.NuaStateRepository
import com.nua.assistant.ui.orb.OrbState
import com.nua.assistant.ui.palette.PaletteMemory
import com.nua.assistant.ui.palette.PaletteSkill
import com.nua.assistant.ui.trust.TrustUiController
import com.nua.assistant.documents.DocumentRepository
import com.nua.assistant.documents.DocumentType
import com.nua.assistant.documents.DocxTextExtractor
import com.nua.assistant.documents.PdfTextExtractor
import com.nua.assistant.documents.documentTypeForMime
import com.nua.assistant.dreams.DreamRepository
import com.nua.assistant.world.WorldModelRepository
import com.nua.assistant.geofencing.GeofenceManager
import com.nua.assistant.goals.GoalRepository
import com.nua.assistant.goals.GoalType
import com.nua.assistant.memory.DecisionEntity
import com.nua.assistant.memory.DocumentEntity
import com.nua.assistant.memory.DreamEntity
import com.nua.assistant.memory.GeofenceDao
import com.nua.assistant.memory.GeofenceEntity
import com.nua.assistant.memory.GoalEntity
import com.nua.assistant.memory.GoalObservationEntity
import com.nua.assistant.memory.MemoryDao
import com.nua.assistant.memory.MemoryPrivacyLevel
import com.nua.assistant.memory.MessageEntity
import com.nua.assistant.memory.MessageRole
import com.nua.assistant.memory.MemoryType
import com.nua.assistant.memory.SecureKeyRepository
import com.nua.assistant.memory.UserFactEntity
import com.nua.assistant.memory.VisionMonitorEntity
import com.nua.assistant.network.ConnectivityMonitor
import com.nua.assistant.notifications.NotificationReplySender
import com.nua.assistant.notifications.NotificationRepository
import com.nua.assistant.notifications.NotificationSummary
import com.nua.assistant.privacy.PrivacyRepository
import com.nua.assistant.privacy.buildDataExport
import com.nua.assistant.sms.SmsSender
import com.nua.assistant.timeline.TimelineBuilder
import com.nua.assistant.timeline.TimelineEntry
import com.nua.assistant.trust.ActionOutcomeState
import com.nua.assistant.trust.AutonomyTier
import com.nua.assistant.trust.finalAutoApproveDecision
import com.nua.assistant.trust.TrustRepository
import com.nua.assistant.trust.idempotencyKeyFor
import com.nua.assistant.trust.sameDayWindowStart
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
import com.nua.assistant.vision.VisionAnalysis
import com.nua.assistant.vision.VisionAnalyzer
import com.nua.assistant.vision.VisionMonitorRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ChatMessage(val role: MessageRole, val content: String)

/** The most recent photo NUA has understood but not yet acted on — offered for "remember" or "monitor". */
data class LastVisionResult(val analysis: VisionAnalysis, val imageUri: Uri, val imageBase64: String, val mediaType: String)

data class NuaUiState(
    val messages: List<ChatMessage> = emptyList(),
    val inputText: String = "",
    val isProcessing: Boolean = false,
    /** Partial assistant text as it streams in, rendered as an extra bubble until the reply completes. */
    val streamingReply: String? = null,
    val pendingPlan: TaskPlan? = null,
    val pendingReply: NuaRouteResult.ReplyProposed? = null,
    val pendingSms: NuaRouteResult.SmsProposed? = null,
    /**
     * Whether the current pending* action was auto-approved (see [NuaViewModel.enableAutoApprove]).
     * Tells the UI to skip the "do you want to do this?" tap and trigger its gated confirm
     * immediately — it must NOT be read as license to skip step-up. Exactly one pending*
     * field is ever non-null at a time (sendMessage routes to at most one proposal per
     * turn), so a single flag is enough to describe all three.
     */
    val autoApprovedPending: Boolean = false,
    /** Set only when a shadow-mode Contextual Autonomy Contract recorded a prediction for
     *  the current pending* proposal — see TrustRepository.recordShadowPrediction. Cleared,
     *  and the prediction resolved against what the user actually did, by whichever
     *  confirmPending-/dismissPending- method fires next. Never influences autoApprovedPending
     *  — shadow mode predicts, it never acts. */
    val pendingShadowPredictionId: Long? = null,
    val needsApiKey: Boolean = false,
)

/** How far out "Remind later" on the next-best-action card schedules its reminder. */
private const val REMIND_LATER_OFFSET_MILLIS = 60 * 60_000L

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
    private val smsSender: SmsSender,
    private val languagePreferenceStore: LanguagePreferenceStore,
    private val briefingScheduleStore: BriefingScheduleStore,
    private val briefingScheduler: BriefingScheduler,
    private val ownerEnrollment: OwnerEnrollment,
    private val ownerVerifier: OwnerVerifier,
    private val connectivityMonitor: ConnectivityMonitor,
    private val imageEncoder: ImageEncoder,
    private val visionAnalyzer: VisionAnalyzer,
    private val visionMonitorRepository: VisionMonitorRepository,
    private val documentRepository: DocumentRepository,
    private val documentAnalyzer: DocumentAnalyzer,
    private val pdfTextExtractor: PdfTextExtractor,
    private val docxTextExtractor: DocxTextExtractor,
    private val geofenceManager: GeofenceManager,
    private val geofenceDao: GeofenceDao,
    private val usageTracker: UsageTracker,
    private val trustRepository: TrustRepository,
    private val trustUiController: TrustUiController,
    private val goalRepository: GoalRepository,
    private val whatNowAdvisor: WhatNowAdvisor,
    private val dreamRepository: DreamRepository,
    private val decisionRepository: DecisionRepository,
    private val privacyRepository: PrivacyRepository,
    private val worldModelRepository: WorldModelRepository,
    private val selfDiagnosticsRepository: SelfDiagnosticsRepository,
    private val nuaStateRepository: NuaStateRepository,
    private val skillCatalog: SkillCatalog,
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

    /** Null until the first refresh completes — the Command Centre shows a quiet loading state rather than fake zeroes. */
    private val _nuaState = MutableStateFlow<NuaState?>(null)
    val nuaState: StateFlow<NuaState?> = _nuaState.asStateFlow()

    /** The most recent Dream, surfaced on the Command Centre's intelligence card. Null when there genuinely isn't one. */
    private val _latestInsight = MutableStateFlow<String?>(null)
    val latestInsight: StateFlow<String?> = _latestInsight.asStateFlow()

    /** Populated only after the user asks — never pre-generated, since it costs a Claude call. */
    private val _nextBestAction = MutableStateFlow<WhatNowResult?>(null)
    val nextBestAction: StateFlow<WhatNowResult?> = _nextBestAction.asStateFlow()

    /**
     * What NUA can actually do, read from the registered skill bindings — so the Act
     * screen can't drift from what's really dispatchable.
     */
    val skills: List<SkillDescriptor> = skillCatalog.all()

    /** The same capabilities, flattened for the command palette's pure ranking. */
    val paletteSkills: List<PaletteSkill> = skills.map { PaletteSkill(it.action, it.displayName, it.tier) }

    private val _paletteQuery = MutableStateFlow("")
    val paletteQuery: StateFlow<String> = _paletteQuery.asStateFlow()

    private val _diagnostics = MutableStateFlow<List<DiagnosticCheck>>(emptyList())
    val diagnostics: StateFlow<List<DiagnosticCheck>> = _diagnostics.asStateFlow()

    private val _testingApiConnection = MutableStateFlow(false)
    val testingApiConnection: StateFlow<Boolean> = _testingApiConnection.asStateFlow()

    // trustScore/trustLedger/autonomySuggestions moved to TrustUiController (ui/trust/) —
    // the first ViewModel-decomposition seam, per an architecture review that found this
    // class's size (1008 lines, 35 dependencies at the time) the proven origin of two
    // serious bugs this session. Re-exposed here unchanged: same StateFlow instances (not
    // copies), same public names and types, so NuaScreen.kt/SettingsScreen.kt need no
    // changes at all. actionOutcomes stays here — it's shared with the Act destination
    // (refreshAct()), which is out of scope for the Trust/Autonomy seam.
    val trustScore: StateFlow<Int?> = trustUiController.trustScore
    val trustLedger: StateFlow<List<TrustLedgerEntity>> = trustUiController.trustLedger
    val autonomySuggestions: StateFlow<List<AutonomyPreferenceEntity>> = trustUiController.autonomySuggestions
    val activeAutonomyGrants: StateFlow<List<AutonomyPreferenceEntity>> = trustUiController.activeAutonomyGrants

    private val _actionOutcomes = MutableStateFlow<List<ActionOutcomeEntity>>(emptyList())
    val actionOutcomes: StateFlow<List<ActionOutcomeEntity>> = _actionOutcomes.asStateFlow()

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

    /** dreamId -> what it connected (resolved summaries, e.g. a fact's value or a goal's
     *  text) — World Model read-side resolution, the first real reader of the
     *  world_relationships rows DreamRepository writes. Orphaned/unresolvable connections
     *  are silently dropped here (see WorldModelRepository.ResolvedRelationship.summary's
     *  own doc comment for why null isn't shown as a placeholder). */
    val dreamConnections: StateFlow<Map<Long, List<String>>> = dreams
        .map { list ->
            list.associate { dream ->
                dream.id to worldModelRepository.relationshipsWithSummaries("DREAM", dream.id).mapNotNull { it.summary }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val decisions: StateFlow<List<DecisionEntity>> = decisionRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * What the palette can search over. Facts and dreams only — the things NUA actually
     * remembers — rather than every row in the database, so the palette stays a shortcut.
     *
     * Declared after [facts] and [dreams]: property initialisers run in declaration order,
     * so a flow built from later-declared properties won't compile.
     */
    val paletteMemories: StateFlow<List<PaletteMemory>> =
        combine(facts, dreams) { facts, dreams ->
            facts.map { PaletteMemory(it.value, "Memory") } + dreams.map { PaletteMemory(it.text, "Insight") }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val timeline: StateFlow<List<TimelineEntry>> =
        combine(facts, dreams, decisions, goalObservations) { facts, dreams, decisions, goalObservations ->
            TimelineBuilder.build(facts, dreams, decisions, goalObservations)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _secondBrainQuery = MutableStateFlow("")
    val secondBrainQuery: StateFlow<String> = _secondBrainQuery.asStateFlow()

    val secondBrainResults: StateFlow<List<SecondBrainResult>> =
        combine(_secondBrainQuery, facts, dreams, decisions) { query, facts, dreams, decisions ->
            SecondBrainSearch.search(query, facts, dreams, decisions)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun updateSecondBrainQuery(query: String) {
        _secondBrainQuery.value = query
    }

    val visionMonitors: StateFlow<List<VisionMonitorEntity>> = visionMonitorRepository.observeActive()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _lastVisionResult = MutableStateFlow<LastVisionResult?>(null)
    val lastVisionResult: StateFlow<LastVisionResult?> = _lastVisionResult.asStateFlow()

    val documents: StateFlow<List<DocumentEntity>> = documentRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Ingests a PDF, .docx, or image the user picked: extracts its full text (PdfTextExtractor
     * rasterizes pages and transcribes each via Claude vision, DocxTextExtractor reads the
     * XML directly, images go straight through DocumentAnalyzer), then summarizes it and
     * looks for an expiry date. No text extracted means nothing gets saved — never a document
     * entry claiming a capability it doesn't have.
     */
    fun ingestDocument(uri: Uri, mimeType: String?, fileName: String) {
        if (_uiState.value.isProcessing) return
        val type = documentTypeForMime(mimeType)
        if (type == null) {
            _uiState.update { it.copy(messages = it.messages + ChatMessage(MessageRole.ASSISTANT, "That's not a file type NUA can read yet.")) }
            return
        }
        _uiState.update {
            it.copy(isProcessing = true, messages = it.messages + ChatMessage(MessageRole.USER, "[Document: $fileName]"))
        }
        viewModelScope.launch {
            memoryDao.insertMessage(MessageEntity(role = MessageRole.USER, content = "[Document: $fileName]"))
            if (!connectivityMonitor.isOnline()) {
                respond("No connection right now — can't read that document until you're back online.", extractFacts = false)
                return@launch
            }
            val text = when (type) {
                DocumentType.PDF -> pdfTextExtractor.extract(uri)
                DocumentType.WORD -> docxTextExtractor.extract(uri)
                DocumentType.IMAGE -> imageEncoder.encode(uri)?.let { documentAnalyzer.transcribePage(it.base64, it.mediaType) }
            }
            if (text.isNullOrBlank()) {
                respond("Couldn't read any text out of \"$fileName\".", extractFacts = false)
                return@launch
            }
            val summary = documentAnalyzer.summarize(text)
            documentRepository.save(fileName, type, text, summary.summary, summary.expiryDate)
            respond(summary.summary, extractFacts = false)
        }
    }

    /** Targeted Q&A over one or more ingested documents — also how "compare these two" works, by including both. */
    fun askAboutDocuments(documentIds: List<Long>, question: String) {
        if (_uiState.value.isProcessing || question.isBlank()) return
        val selected = documents.value.filter { it.id in documentIds }
        if (selected.isEmpty()) return
        _uiState.update {
            it.copy(isProcessing = true, messages = it.messages + ChatMessage(MessageRole.USER, question))
        }
        viewModelScope.launch {
            memoryDao.insertMessage(MessageEntity(role = MessageRole.USER, content = question))
            if (!connectivityMonitor.isOnline()) {
                respond("No connection right now — can't check those documents until you're back online.", extractFacts = false)
                return@launch
            }
            val answer = documentAnalyzer.answer(selected.map { it.fileName to it.extractedText }, question)
            respond(answer, extractFacts = false)
        }
    }

    fun removeDocument(id: Long) {
        viewModelScope.launch { documentRepository.delete(id) }
    }

    fun addDecision(
        decision: String,
        reasoning: String?,
        facts: String? = null,
        unknowns: String? = null,
        constraints: String? = null,
        options: String? = null,
    ) {
        viewModelScope.launch { decisionRepository.record(decision, reasoning, facts, unknowns, constraints, options) }
    }

    fun recordDecisionOutcome(id: Long, outcome: String) {
        viewModelScope.launch { decisionRepository.recordOutcome(id, outcome) }
    }

    fun removeDecision(id: Long) {
        viewModelScope.launch { decisionRepository.delete(id) }
    }

    /** A fresh export text built from whatever's currently loaded — cheap, pure, no need
     *  to cache since it's only read when the user actually taps Export. */
    fun dataExportText(): String =
        buildDataExport(facts.value, goals.value, decisions.value, dreams.value)

    /** Irreversible — PrivacyCentreContent's own confirmation dialog is the only gate;
     *  this makes no second check, matching PrivacyRepository.resetAllData's contract. */
    fun resetDeviceData() {
        viewModelScope.launch { privacyRepository.resetAllData() }
    }

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

            when (val routed = intentRouter.route(UserUtterance(message), _pinnedLanguage.value)) {
                is NuaRouteResult.ActionTaken -> respond(routed.message, extractFacts = false)
                // "Always allow" (see enableAutoApprove) answers one question — do you want
                // to be asked "do you want to do this?" every time — and answers only that
                // one. It must never also answer "is this actually you?": these actions are
                // T3/T4, and biometric step-up is gated on the tier alone (requiresStepUpAuth),
                // not on whether a confirmation dialog is shown. So every proposal still goes
                // through pendingEffectFor and the same rememberStepUpGatedAction the manual-
                // confirm path uses in NuaScreen — auto-approve only tells the UI to skip the
                // "do you want to?" tap and trigger that gated action immediately. A prior
                // version of this branch called executeConfirmed* here directly, which
                // skipped step-up entirely — fixed after an architecture review found it.
                // See pendingEffectFor's doc comment for the full story.
                is NuaRouteResult.PlanProposed -> applyPendingEffect(
                    PendingProposal.Plan(routed.plan), NuaActionType.PLAN_TASK,
                )
                is NuaRouteResult.ReplyProposed -> applyPendingEffect(
                    PendingProposal.Reply(routed), NuaActionType.REPLY_TO_NOTIFICATION,
                )
                is NuaRouteResult.SmsProposed -> applyPendingEffect(
                    PendingProposal.Sms(routed), NuaActionType.SMS_SEND,
                )
                NuaRouteResult.FallThroughToChat -> replyConversationally(message)
            }
        }
    }

    /**
     * Publishes a routed proposal to [NuaUiState] via [pendingEffectFor] — see that
     * function's doc comment. The combined auto-approve answer — the legacy, unscoped
     * [TrustRepository.isAutoApproved] grant, OR a live (non-shadow) Contextual Autonomy
     * Contract (Feature 5) — is decided by the one shared pure rule,
     * `trust/AutonomyContract.kt`'s `finalAutoApproveDecision`, not recomputed here, so
     * this call site and `recipes/RecipeRepository.kt`'s `wouldAutoApprove` can never
     * independently drift on it. A shadow-mode contract never contributes no matter what
     * it decides; it only records a prediction (see
     * [TrustRepository.recordShadowPrediction]) for later comparison against what the
     * user actually does with this same proposal.
     */
    private suspend fun applyPendingEffect(proposal: PendingProposal, actionType: NuaActionType) {
        val legacyAutoApproved = trustRepository.isAutoApproved(actionType)
        val recipient = recipientFor(proposal)
        val (contract, decision) = trustRepository.contractDecisionFor(actionType, recipient)
        var shadowPredictionId: Long? = null
        if (contract != null && contract.shadowMode) {
            shadowPredictionId = trustRepository.recordShadowPrediction(contract, actionType, recipient, decision)
        }
        val autoApproved = finalAutoApproveDecision(legacyAutoApproved, contract, decision)
        val effect = pendingEffectFor(proposal, autoApproved = autoApproved)
        _uiState.update {
            it.copy(
                isProcessing = false,
                pendingPlan = effect.pendingPlan,
                pendingReply = effect.pendingReply,
                pendingSms = effect.pendingSms,
                autoApprovedPending = effect.autoApprovedPending,
                pendingShadowPredictionId = shadowPredictionId,
            )
        }
    }

    private suspend fun replyConversationally(userMessage: String) {
        if (!connectivityMonitor.isOnline()) {
            respond(offlineMessage(), extractFacts = false)
            return
        }

        val rankedFacts = FactRelevance.rank(memoryDao.getAllFacts(), userMessage)
        // Data Egress Gateway (security/egress/DataEgressGateway.kt): no capsule is
        // supplied at this call site yet, so this is the strictest policy -- Sensitive-
        // marked facts never reach the prompt, Standard ones pass through as before.
        val (relevantFacts, _egressDecision) = DataEgressGateway.filterFacts(capsule = null, facts = rankedFacts)
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

    /**
     * See + understand: sends a just-captured photo to Claude's vision, structures the
     * result (category + description), and speaks/shows it like any other reply. Doesn't
     * remember or act on its own — [rememberLastVisionResult] and
     * [startMonitoringLastVisionResult] are deliberate follow-up actions the user takes
     * on the result this leaves in [lastVisionResult], the same "write to it on purpose"
     * philosophy as the Decision Journal.
     */
    fun describeImage(uri: Uri) {
        if (_uiState.value.isProcessing) return
        _uiState.update {
            it.copy(isProcessing = true, messages = it.messages + ChatMessage(MessageRole.USER, "[Photo]"))
        }
        viewModelScope.launch {
            memoryDao.insertMessage(MessageEntity(role = MessageRole.USER, content = "[Photo]"))
            if (!connectivityMonitor.isOnline()) {
                respond("No connection right now — can't look at that photo until you're back online.", extractFacts = false)
                return@launch
            }
            val encoded = imageEncoder.encode(uri)
            if (encoded == null) {
                respond("Couldn't read that photo.", extractFacts = false)
                return@launch
            }
            val analysis = visionAnalyzer.analyze(encoded.base64, encoded.mediaType)
            if (analysis == null) {
                respond("Couldn't make sense of that photo — mind trying again?", extractFacts = false)
                return@launch
            }
            _lastVisionResult.value = LastVisionResult(analysis, uri, encoded.base64, encoded.mediaType)
            respond(analysis.description, extractFacts = false)
        }
    }

    /** Remember: turns the last vision result into a durable fact. Deliberate, not automatic. */
    fun rememberLastVisionResult() {
        val result = _lastVisionResult.value ?: return
        viewModelScope.launch {
            memoryDao.upsertFact(
                key = "vision_${System.currentTimeMillis()}",
                value = result.analysis.description,
                category = result.analysis.category.name.lowercase(),
                memoryType = MemoryType.EPISODIC,
                source = "a photo you shared",
            )
            _lastVisionResult.value = null
        }
    }

    /**
     * Monitor: opts the last vision result into a recurring check. NUA can't take photos
     * on its own, so this doesn't watch anything by itself — it records a baseline and,
     * once [intervalDays] has passed, VisionMonitorWorker reminds the user to snap a
     * fresh photo for [recheckVisionMonitor] to compare against it.
     */
    fun startMonitoringLastVisionResult(subject: String, intervalDays: Int) {
        val result = _lastVisionResult.value ?: return
        viewModelScope.launch {
            val durablePath = imageEncoder.persistDurably(result.imageUri) ?: return@launch
            visionMonitorRepository.start(
                subject = subject,
                baselineDescription = result.analysis.description,
                baselineImagePath = durablePath,
                intervalDays = intervalDays,
            )
            _lastVisionResult.value = null
        }
    }

    /** Dismisses the last vision result without remembering or monitoring it. */
    fun dismissLastVisionResult() {
        _lastVisionResult.value = null
    }

    /** A fresh photo taken specifically to check on an existing vision monitor. */
    fun recheckVisionMonitor(monitorId: Long, uri: Uri) {
        if (_uiState.value.isProcessing) return
        val monitor = visionMonitors.value.find { it.id == monitorId } ?: return
        _uiState.update {
            it.copy(isProcessing = true, messages = it.messages + ChatMessage(MessageRole.USER, "[Photo: checking on ${monitor.subject}]"))
        }
        viewModelScope.launch {
            memoryDao.insertMessage(MessageEntity(role = MessageRole.USER, content = "[Photo: checking on ${monitor.subject}]"))
            if (!connectivityMonitor.isOnline()) {
                respond("No connection right now — can't check that photo until you're back online.", extractFacts = false)
                return@launch
            }
            val encoded = imageEncoder.encode(uri)
            if (encoded == null) {
                respond("Couldn't read that photo.", extractFacts = false)
                return@launch
            }
            val comparison = visionAnalyzer.compareAgainstBaseline(encoded.base64, encoded.mediaType, monitor.baselineDescription)
            visionMonitorRepository.markChecked(monitorId)
            respond(comparison ?: "Couldn't compare that photo — try again in a bit.", extractFacts = false)
        }
    }

    fun removeVisionMonitor(id: Long) {
        viewModelScope.launch { visionMonitorRepository.stop(id) }
    }

    // confirmPendingPlan/Reply/Sms cannot execute their underlying action twice from a
    // duplicated or retried confirm (double tap, auto-approve racing a manual tap, etc.):
    // viewModelScope uses Dispatchers.Main.immediate, so a tap on the already-foregrounded
    // main thread runs this coroutine body inline, synchronously, up to its first real
    // suspension point — and the `_uiState.update { pending = null }` guard clear happens
    // before that point (the first suspend call is inside trustRepository/executeConfirmed*,
    // which hits Room). A second tap that lands before the first coroutine's clear has
    // committed cannot happen either, because Android's Looper serializes all main-thread
    // events — there is no genuine concurrency between two taps to race in the first place.
    // The equivalent dismissPendingPlan/Reply/Sms clear the guard synchronously outside the
    // launch entirely, so they carry the same guarantee with no ambiguity at all.
    //
    // That guarantee is specific to *this* UI path, though — it says nothing about a
    // future caller that doesn't go through pendingPlan/Reply/Sms at all (a retried
    // dispatch, a replayed confirmation from some other surface). executeConfirmedPlan/
    // Reply/Sms each additionally check TrustRepository.wasRecentlyExecuted before
    // calling out, so the real-world side effect itself can't repeat even from a caller
    // this reasoning doesn't cover — see trust/IdempotencyKey.kt.
    fun confirmPendingPlan() {
        val plan = _uiState.value.pendingPlan ?: return
        val shadowPredictionId = _uiState.value.pendingShadowPredictionId
        viewModelScope.launch {
            _uiState.update { it.copy(pendingPlan = null, pendingShadowPredictionId = null) }
            shadowPredictionId?.let { trustRepository.resolveShadowPrediction(it, approved = true) }
            trustRepository.recordApproval(NuaActionType.PLAN_TASK)
            executeConfirmedPlan(plan)
        }
    }

    private suspend fun executeConfirmedPlan(plan: TaskPlan) {
        val stepsKey = plan.steps.joinToString("|") { "${it.title}@${it.suggestedReminder?.whenMillis ?: -1}" }
        val idempotencyKey = idempotencyKeyFor(NuaActionType.PLAN_TASK.name, plan.summary, stepsKey)
        if (trustRepository.wasRecentlyExecuted(idempotencyKey)) {
            respond(DUPLICATE_PLAN_SUPPRESSED_MESSAGE, extractFacts = false)
            return
        }
        val results = taskPlanner.confirmPlan(plan)
        val confirmation = planConfirmationMessage(results)
        trustRepository.recordOutcome(
            actionType = NuaActionType.PLAN_TASK.name,
            tier = AutonomyTier.T4,
            summary = "Confirmed plan: ${plan.summary} — $confirmation",
            outcome = planConfirmationOutcome(results),
            idempotencyKey = idempotencyKey,
        )
        respond(confirmation, extractFacts = false)
    }

    fun dismissPendingPlan() {
        val plan = _uiState.value.pendingPlan ?: return
        val shadowPredictionId = _uiState.value.pendingShadowPredictionId
        _uiState.update { it.copy(pendingPlan = null, pendingShadowPredictionId = null) }
        viewModelScope.launch {
            shadowPredictionId?.let { trustRepository.resolveShadowPrediction(it, approved = false) }
            trustRepository.recordOutcome(
                actionType = NuaActionType.PLAN_TASK.name,
                tier = AutonomyTier.T4,
                summary = "Declined plan: ${plan.summary}",
                outcome = ActionOutcomeState.FAILED,
                wasRejection = true,
            )
        }
    }

    fun confirmPendingReply() {
        val pending = _uiState.value.pendingReply ?: return
        val shadowPredictionId = _uiState.value.pendingShadowPredictionId
        viewModelScope.launch {
            _uiState.update { it.copy(pendingReply = null, pendingShadowPredictionId = null) }
            shadowPredictionId?.let { trustRepository.resolveShadowPrediction(it, approved = true) }
            trustRepository.recordApproval(NuaActionType.REPLY_TO_NOTIFICATION)
            executeConfirmedReply(pending)
        }
    }

    private suspend fun executeConfirmedReply(pending: NuaRouteResult.ReplyProposed) {
        val idempotencyKey = idempotencyKeyFor(
            NuaActionType.REPLY_TO_NOTIFICATION.name,
            pending.notification.title,
            pending.message,
        )
        if (trustRepository.wasRecentlyExecuted(idempotencyKey)) {
            respond(DUPLICATE_REPLY_SUPPRESSED_MESSAGE, extractFacts = false)
            return
        }
        val replyAction = pending.notification.replyAction
        val outcome = if (replyAction != null) {
            notificationReplySender.sendReply(replyAction, pending.message)
        } else {
            ActionOutcomeState.FAILED
        }
        val confirmation = replyConfirmationMessage(outcome, pending.notification.title)
        trustRepository.recordOutcome(
            actionType = NuaActionType.REPLY_TO_NOTIFICATION.name,
            tier = AutonomyTier.T3,
            summary = confirmation,
            outcome = outcome,
            idempotencyKey = idempotencyKey,
        )
        respond(confirmation, extractFacts = false)
    }

    fun dismissPendingReply() {
        val pending = _uiState.value.pendingReply ?: return
        val shadowPredictionId = _uiState.value.pendingShadowPredictionId
        _uiState.update { it.copy(pendingReply = null, pendingShadowPredictionId = null) }
        viewModelScope.launch {
            shadowPredictionId?.let { trustRepository.resolveShadowPrediction(it, approved = false) }
            trustRepository.recordOutcome(
                actionType = NuaActionType.REPLY_TO_NOTIFICATION.name,
                tier = AutonomyTier.T3,
                summary = "Declined proposed reply to ${pending.notification.title}",
                outcome = ActionOutcomeState.FAILED,
                wasRejection = true,
            )
        }
    }

    fun confirmPendingSms() {
        val pending = _uiState.value.pendingSms ?: return
        val shadowPredictionId = _uiState.value.pendingShadowPredictionId
        viewModelScope.launch {
            _uiState.update { it.copy(pendingSms = null, pendingShadowPredictionId = null) }
            shadowPredictionId?.let { trustRepository.resolveShadowPrediction(it, approved = true) }
            trustRepository.recordApproval(NuaActionType.SMS_SEND)
            executeConfirmedSms(pending)
        }
    }

    private suspend fun executeConfirmedSms(pending: NuaRouteResult.SmsProposed) {
        val idempotencyKey = idempotencyKeyFor(NuaActionType.SMS_SEND.name, pending.phoneNumber, pending.message)
        if (trustRepository.wasRecentlyExecuted(idempotencyKey)) {
            respond(DUPLICATE_SMS_SUPPRESSED_MESSAGE, extractFacts = false)
            return
        }
        val priorSendsToday = trustRepository.recentSendsTo(
            recipient = pending.phoneNumber,
            actionType = NuaActionType.SMS_SEND.name,
            sinceMillis = sameDayWindowStart(System.currentTimeMillis()),
        ).size
        val outcome = smsSender.send(pending.phoneNumber, pending.message)
        val confirmation = smsConfirmationMessage(outcome, smsSender.hasPermission(), pending.contactName, priorSendsToday)
        trustRepository.recordOutcome(
            actionType = NuaActionType.SMS_SEND.name,
            tier = AutonomyTier.T3,
            summary = confirmation,
            outcome = outcome,
            idempotencyKey = idempotencyKey,
            recipient = pending.phoneNumber,
        )
        respond(confirmation, extractFacts = false)
    }

    fun dismissPendingSms() {
        val pending = _uiState.value.pendingSms ?: return
        val shadowPredictionId = _uiState.value.pendingShadowPredictionId
        _uiState.update { it.copy(pendingSms = null, pendingShadowPredictionId = null) }
        viewModelScope.launch {
            shadowPredictionId?.let { trustRepository.resolveShadowPrediction(it, approved = false) }
            trustRepository.recordOutcome(
                actionType = NuaActionType.SMS_SEND.name,
                tier = AutonomyTier.T3,
                summary = "Declined proposed text to ${pending.contactName}",
                outcome = ActionOutcomeState.FAILED,
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

    fun forgetFactsByType(memoryType: MemoryType) {
        viewModelScope.launch { memoryDao.deleteFactsByType(memoryType) }
    }

    fun correctFact(factId: Long, newValue: String) {
        if (newValue.isBlank()) return
        viewModelScope.launch { memoryDao.correctFact(factId, newValue.trim(), System.currentTimeMillis()) }
    }

    fun setFactPrivacyLevel(factId: Long, privacyLevel: MemoryPrivacyLevel) {
        viewModelScope.launch { memoryDao.updatePrivacyLevel(factId, privacyLevel) }
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
            _nextBestAction.value = recommendation
            respond(recommendation.chatSummary(), extractFacts = false)
        }
    }

    /** "Do it" on the Command Centre's next-action card — routes the recommended action
     *  through the normal message pipeline, so it goes through the same firewall/sandbox/
     *  confirmation path any typed request would, never a separate execution route. */
    fun doNextBestAction() {
        val recommendation = _nextBestAction.value as? WhatNowResult.Recommendation ?: return
        _nextBestAction.value = null
        sendMessage(recommendation.action)
    }

    /** "Remind later" — creates a plain reminder an hour out via the same reminder path
     *  TaskPlanner's confirmed plans use, rather than a new one-off mechanism. */
    fun remindNextBestActionLater() {
        val recommendation = _nextBestAction.value as? WhatNowResult.Recommendation ?: return
        _nextBestAction.value = null
        viewModelScope.launch {
            val plan = TaskPlan(
                summary = recommendation.action,
                steps = listOf(
                    PlannedStep(
                        title = recommendation.action,
                        detail = recommendation.reason,
                        suggestedReminder = SuggestedReminder(
                            title = recommendation.action,
                            whenMillis = System.currentTimeMillis() + REMIND_LATER_OFFSET_MILLIS,
                        ),
                    ),
                ),
            )
            val results = taskPlanner.confirmPlan(plan)
            val confirmation = if (results.all { it.isSuccess }) {
                "Okay, I'll remind you about that in an hour."
            } else {
                "Couldn't set that reminder."
            }
            respond(confirmation, extractFacts = false)
        }
    }

    /** "Not relevant" — just dismisses the card. Not logged to the Trust Ledger: this is
     *  advice, not a proposed action the user approved or declined. */
    fun dismissNextBestAction() {
        _nextBestAction.value = null
    }

    fun addGoal(text: String, type: GoalType = GoalType.GOAL) {
        if (text.isBlank()) return
        viewModelScope.launch { goalRepository.addGoal(text.trim(), type) }
    }

    fun removeGoal(id: Long) {
        viewModelScope.launch { goalRepository.deactivateGoal(id) }
    }

    /**
     * Recomputes the Command Centre's state from real sources. Called when the home
     * surface appears rather than polled — every input is a cheap local read, but none of
     * them change often enough to be worth a live subscription.
     */
    fun refreshNuaState() {
        viewModelScope.launch {
            _nuaState.value = nuaStateRepository.currentState()
            // The insight card shows a real Dream or nothing at all; it is never
            // generated on demand to fill the slot.
            _latestInsight.value = runCatching { dreamRepository.recent(limit = 1).firstOrNull()?.text }.getOrNull()
        }
    }

    /**
     * What the Orb should be showing. Derived from state NUA already tracks rather than
     * being driven separately, so the Orb can't disagree with the rest of the screen.
     */
    fun currentOrbState(): OrbState {
        val state = _nuaState.value
        return when {
            _uiState.value.isProcessing -> OrbState.THINKING
            state == null -> OrbState.IDLE
            state.isOffline -> OrbState.OFFLINE
            state.recentMistakes.isNotEmpty() -> OrbState.ERROR
            state.pendingTasks.isNotEmpty() -> OrbState.WARNING
            else -> OrbState.IDLE
        }
    }

    /** Called when Settings opens — usage isn't worth keeping live-updated, just fresh on view. */
    fun refreshUsage() {
        viewModelScope.launch { _usageThisMonth.value = usageTracker.summaryThisMonth() }
    }

    fun updatePaletteQuery(query: String) {
        _paletteQuery.value = query
    }

    /** Called when the Act destination appears: pending work plus the recent-action log. */
    fun refreshAct() {
        viewModelScope.launch {
            _nuaState.value = nuaStateRepository.currentState()
            _actionOutcomes.value = trustRepository.recentOutcomes()
        }
    }

    /** Called when Settings opens — same reasoning as [refreshUsage]. Permission checks are cheap, but not worth polling. */
    fun refreshDiagnostics() {
        viewModelScope.launch { _diagnostics.value = selfDiagnosticsRepository.runChecks() }
    }

    /** A real network round trip, so it's opt-in via a button rather than run automatically on every Settings open. */
    fun testApiConnection() {
        viewModelScope.launch {
            _testingApiConnection.value = true
            val result = selfDiagnosticsRepository.testApiConnection()
            _diagnostics.update { current ->
                if (current.any { it.category == DiagnosticCategory.API }) {
                    current.map { if (it.category == DiagnosticCategory.API) result else it }
                } else {
                    current + result
                }
            }
            _testingApiConnection.value = false
        }
    }

    /** Called when Settings opens — same reasoning as [refreshUsage]. */
    fun refreshTrust() {
        viewModelScope.launch {
            trustUiController.refresh()
            _actionOutcomes.value = trustRepository.recentOutcomes()
        }
    }

    fun enableAutoApprove(actionType: NuaActionType) {
        viewModelScope.launch { trustUiController.enableAutoApprove(actionType) }
    }

    fun disableAutoApprove(actionType: NuaActionType) {
        viewModelScope.launch { trustUiController.disableAutoApprove(actionType) }
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
