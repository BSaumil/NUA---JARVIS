package com.nua.assistant.connectors

import com.nua.assistant.security.egress.DataCategory

object ConnectorCatalog {
    val google = ConnectorManifest(
        id = "google",
        displayName = "Google",
        version = "1",
        authType = ConnectorAuthType.OAUTH2_EXTERNAL,
        allowedHosts = setOf("www.googleapis.com", "gmail.googleapis.com"),
        readCapabilities = setOf("gmail.read", "drive.read", "calendar.read"),
        writeCapabilities = setOf("gmail.send", "drive.write", "calendar.write"),
        dataCategories = setOf(DataCategory.STANDARD_FACTS, DataCategory.SENSITIVE_FACTS),
        remoteEndpointIdForWrites = "google",
    )

    val microsoft = ConnectorManifest(
        id = "microsoft",
        displayName = "Microsoft Graph",
        version = "1",
        authType = ConnectorAuthType.OAUTH2_EXTERNAL,
        allowedHosts = setOf("graph.microsoft.com"),
        readCapabilities = setOf("outlook.read", "onedrive.read", "calendar.read"),
        writeCapabilities = setOf("outlook.send", "onedrive.write", "calendar.write"),
        dataCategories = setOf(DataCategory.STANDARD_FACTS, DataCategory.SENSITIVE_FACTS),
        remoteEndpointIdForWrites = "microsoft",
    )

    val dropbox = ConnectorManifest(
        id = "dropbox",
        displayName = "Dropbox",
        version = "1",
        authType = ConnectorAuthType.OAUTH2_EXTERNAL,
        allowedHosts = setOf("api.dropboxapi.com"),
        readCapabilities = setOf("files.read"),
        writeCapabilities = setOf("files.write"),
        dataCategories = setOf(DataCategory.DOCUMENT_CONTENT),
        remoteEndpointIdForWrites = "dropbox",
    )

    val slack = ConnectorManifest(
        id = "slack",
        displayName = "Slack",
        version = "1",
        authType = ConnectorAuthType.OAUTH2_EXTERNAL,
        allowedHosts = setOf("slack.com"),
        readCapabilities = setOf("messages.read"),
        writeCapabilities = setOf("messages.send"),
        dataCategories = setOf(DataCategory.STANDARD_FACTS),
        remoteEndpointIdForWrites = "slack",
    )

    val homeAssistant = ConnectorManifest(
        id = "home-assistant",
        displayName = "Home Assistant",
        version = "1",
        authType = ConnectorAuthType.BEARER_TOKEN,
        allowedHosts = setOf("homeassistant.local"),
        readCapabilities = setOf("home.read"),
        writeCapabilities = setOf("home.control"),
        dataCategories = setOf(DataCategory.STANDARD_FACTS),
        remoteEndpointIdForWrites = "home-assistant",
    )

    // Read-only, keyless public APIs: no OAuth or API key to provision, so these are
    // usable out of the box (unlike google/microsoft/dropbox/slack, which need an
    // external OAuth grant, or home-assistant, which needs a user-supplied token).
    val weather = ConnectorManifest(
        id = "weather",
        displayName = "Open-Meteo Weather",
        version = "1",
        authType = ConnectorAuthType.NONE,
        allowedHosts = setOf("api.open-meteo.com"),
        readCapabilities = setOf("weather.forecast"),
        dataCategories = setOf(DataCategory.STANDARD_FACTS),
    )

    val news = ConnectorManifest(
        id = "news",
        displayName = "Hacker News",
        version = "1",
        authType = ConnectorAuthType.NONE,
        allowedHosts = setOf("hacker-news.firebaseio.com"),
        readCapabilities = setOf("news.topStories", "news.item"),
        dataCategories = setOf(DataCategory.STANDARD_FACTS),
    )

    val finance = ConnectorManifest(
        id = "finance",
        displayName = "Frankfurter Exchange Rates",
        version = "1",
        authType = ConnectorAuthType.NONE,
        allowedHosts = setOf("api.frankfurter.app"),
        readCapabilities = setOf("finance.exchangeRate"),
        dataCategories = setOf(DataCategory.STANDARD_FACTS),
    )

    // COMPANION_PAIRED: served by CompanionFilesConnector over a paired companion's own
    // signed Local API, never by SafeHttpConnector (which refuses this authType
    // outright). "allowedHosts" doesn't apply to this transport -- there is no single
    // fixed host, since it reaches whichever companion node this device is paired with --
    // so this is a documented sentinel, not an enforced allowlist. See validateConnectorManifest.
    val companionFiles = ConnectorManifest(
        id = "companion-files",
        displayName = "Companion Mounted Files",
        version = "1",
        authType = ConnectorAuthType.COMPANION_PAIRED,
        allowedHosts = setOf("paired-companion"),
        readCapabilities = setOf("files.read"),
        dataCategories = setOf(DataCategory.DOCUMENT_CONTENT),
    )

    fun all(): List<ConnectorManifest> =
        listOf(google, microsoft, dropbox, slack, homeAssistant, weather, news, finance, companionFiles)
}
