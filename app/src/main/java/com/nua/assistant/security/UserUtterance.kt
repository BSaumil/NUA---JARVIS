package com.nua.assistant.security

/**
 * The literal text of the user's own chat/voice turn — the only kind of input allowed to
 * reach classification/dispatch (`NuaIntentRouter.route`, `IntentClassifier.classify`).
 * Both take this instead of a bare `String` so a future call site that wants to route
 * document/vision/notification/email-derived text into action dispatch has to explicitly
 * (and suspiciously) construct a `UserUtterance` around content that didn't come from the
 * user — visible in code review, not a silent String-to-String pass-through. This is the
 * structural half of the prompt-injection firewall; `UntrustedContent.kt` is the other
 * half, for content that legitimately needs to reach a Claude prompt (summarizing a
 * document) without ever reaching dispatch.
 */
@JvmInline
value class UserUtterance(val text: String)
