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

    fun all(): List<ConnectorManifest> = listOf(google, microsoft, dropbox, slack, homeAssistant)
}
