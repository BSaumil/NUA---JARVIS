package com.nua.assistant.ai.mesh

import com.nua.assistant.memory.SecureKeyRepository
import com.nua.assistant.network.ConnectivityMonitor
import com.nua.assistant.telemetry.DeviceResourceSnapshotProvider
import com.nua.assistant.telemetry.InferenceTelemetryEvent
import com.nua.assistant.telemetry.InferenceTelemetryRecorder
import javax.inject.Inject
import javax.inject.Singleton

interface ModelAvailabilityDetector {
    suspend fun isAvailable(tier: ModelProviderTier, task: InferenceTaskType): Boolean
    fun routingEnvironment(): RoutingEnvironment = RoutingEnvironment()
    fun recordInference(event: InferenceTelemetryEvent) = Unit
}

/**
 * Runtime capability discovery for the Model Mesh.
 *
 * Availability is never inferred from device brand/model. PRIVATE_OS_MODEL asks the
 * actual AICore-backed provider. Any failure is treated as unavailable. Cloud requires
 * both connectivity and an API key.
 */
@Singleton
class RealModelAvailabilityDetector @Inject constructor(
    private val connectivityMonitor: ConnectivityMonitor,
    private val secureKeyRepository: SecureKeyRepository,
    private val privateOsCompletionProvider: PrivateOsCompletionProvider,
    private val resourceSnapshotProvider: DeviceResourceSnapshotProvider,
    private val telemetryRecorder: InferenceTelemetryRecorder,
) : ModelAvailabilityDetector {
    override fun routingEnvironment(): RoutingEnvironment =
        resourceSnapshotProvider.snapshot().routingEnvironment()

    override fun recordInference(event: InferenceTelemetryEvent) {
        telemetryRecorder.record(event)
    }

    override suspend fun isAvailable(
        tier: ModelProviderTier,
        task: InferenceTaskType,
    ): Boolean = when (tier) {
        ModelProviderTier.LOCAL_RULES -> task == InferenceTaskType.INTENT_CLASSIFICATION
        ModelProviderTier.LOCAL_MODEL -> false
        ModelProviderTier.PRIVATE_OS_MODEL -> privateOsCompletionProvider.isAvailable(task)
        ModelProviderTier.CLOUD_FAST,
        ModelProviderTier.CLOUD_FRONTIER,
        -> connectivityMonitor.isOnline() && !secureKeyRepository.getApiKey().isNullOrBlank()
    }
}
