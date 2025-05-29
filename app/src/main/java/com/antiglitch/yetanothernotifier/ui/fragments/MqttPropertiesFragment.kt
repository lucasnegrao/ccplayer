package com.antiglitch.yetanothernotifier.ui.fragments

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Pending
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.*

import com.antiglitch.yetanothernotifier.utils.PermissionType
import com.antiglitch.yetanothernotifier.utils.PermissionUtil
import com.antiglitch.yetanothernotifier.services.DiscoveryState
import com.antiglitch.yetanothernotifier.ui.components.PermissionDialog
import com.antiglitch.yetanothernotifier.ui.components.TvFriendlyChipsSelect
import com.antiglitch.yetanothernotifier.ui.components.TvFriendlySlider
import com.antiglitch.yetanothernotifier.ui.components.TvFriendlySwitch
import com.antiglitch.yetanothernotifier.data.properties.EncryptionType
import com.antiglitch.yetanothernotifier.data.repository.MqttDiscoveryRepository
import com.antiglitch.yetanothernotifier.data.repository.MqttPropertiesRepository
import com.antiglitch.yetanothernotifier.data.properties.MqttPropertyRanges
import com.antiglitch.yetanothernotifier.data.properties.QosLevel
import com.antiglitch.yetanothernotifier.services.MqttService

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun MqttPropertiesFragment(
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester = remember { FocusRequester() },
    onBackPressed: () -> Unit = {},
    onOpenDrawer: () -> Unit = {}
) {
    val context = LocalContext.current
    val repository = MqttPropertiesRepository.getInstance(context)
    val discoveryRepository = MqttDiscoveryRepository.getInstance(context)
    val properties by repository.properties.collectAsState()
    val discoveryState by discoveryRepository.discoveryState.collectAsState()
    
    // Get MQTT service instance to monitor connection state
    val mqttService = remember { MqttService.getInstance(context, repository) }
    val isConnected by mqttService.connectionState.collectAsState()
    
    val scrollState = rememberScrollState()
    var isOptionChangeTrigger by remember { mutableStateOf(false) }
    var showDiscoveryResults by remember { mutableStateOf(false) }
    var showInternetPermissionDialog by remember { mutableStateOf(false) }
    var showLocationPermissionDialog by remember { mutableStateOf(false) }
    var isTestingConnection by remember { mutableStateOf(false) }
    var testConnectionResult by remember { mutableStateOf<String?>(null) }

    // Add dialog state variables
    var showServerHostDialog by remember { mutableStateOf(false) }
    var showUsernameDialog by remember { mutableStateOf(false) }
    var showPasswordDialog by remember { mutableStateOf(false) }
    var showClientIdDialog by remember { mutableStateOf(false) }
    var showSubscribeTopicDialog by remember { mutableStateOf(false) }
    var showPublishTopicDialog by remember { mutableStateOf(false) }

    // Add one more dialog state variable for port
    var showPortDialog by remember { mutableStateOf(false) }

    // Add temporary text input variable
    var tempTextInput by rememberSaveable { mutableStateOf("") }

    // Dialog to handle text input for TV interface
    @Composable
    fun TextInputDialog(
        title: String,
        value: String, // This 'value' param seems unused as tempTextInput is used directly. Consider removing or using it to initialize tempTextInput.
        onDismiss: () -> Unit,
        onConfirm: (String) -> Unit,
        keyboardType: KeyboardType = KeyboardType.Text
    ) {
        val confirmButtonFocusRequester = remember { FocusRequester() }
        val localFocusManager = LocalFocusManager.current

        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(title) },
            confirmButton = {
                Button(
                    onClick = {
                        onConfirm(tempTextInput)
                        onDismiss()
                    },
                    modifier = Modifier.focusRequester(confirmButtonFocusRequester)
                ) { Text("Confirm") }
            },
            dismissButton = { Button(onClick = onDismiss) { Text("Cancel") } },
            text = {
                Column {
                    // Since we're showing a dialog, we can use the standard TextField here
                    // as it will be controlled by a remote or other input device
                    TextField(
                        value = tempTextInput,
                        onValueChange = { newValue ->
                            // Filter out unwanted characters
                            tempTextInput = newValue.filter { it != '\n' && it != '\t' }
                        },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = keyboardType,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                localFocusManager.clearFocus() // Clear focus from TextField
                                confirmButtonFocusRequester.requestFocus() // Request focus for the Confirm button
                            }
                        ),
                        singleLine = true // Important for imeAction.Done to work as expected
                    )
                }
            }
        )
    }

    // Scroll to top and request initial focus on launch
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        scrollState.scrollTo(0)
    }

    // Handle discovery state changes
    LaunchedEffect(discoveryState) {
        when (val currentState = discoveryState) {
            is DiscoveryState.Found -> {
                if (currentState.services.isNotEmpty()) {
                    showDiscoveryResults = true
                }
            }

            is DiscoveryState.Idle -> {
                showDiscoveryResults = false
            }

            else -> {
                /* No action needed */
            }
        }
    }

    // Define the function here, before it's used in the UI
    val checkDiscoveryPermissions = {
        val hasInternetPermission = PermissionUtil.checkPermission(context, PermissionType.INTERNET)
        //        val hasLocationPermission = PermissionUtil.checkPermission(context,
        // PermissionType.LOCATION)

        when {
            !hasInternetPermission -> showInternetPermissionDialog = true
            //            !hasLocationPermission -> showLocationPermissionDialog = true
            else -> discoveryRepository.startMqttDiscovery()
        }
    }

    // Test connection function
    val testConnection = {
        isTestingConnection = true
        testConnectionResult = null

        // Get MqttService instance and test connection
        val mqttService = MqttService.getInstance(context, repository)
        mqttService.testConnection(
            testProperties = properties,
            onResult = { success, message ->
                isTestingConnection = false
                testConnectionResult = message
            }
        )
    }

    Column(
        modifier = modifier
            .background(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(16.dp)
            )
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header with back button and title (remove menu button)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = {
                    Log.d("MqttPropertiesFragment", "Back button clicked, calling onBackPressed")
                    onBackPressed()
                },
                colors = ButtonDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }

            Text(text = "MQTT Settings", style = MaterialTheme.typography.headlineMedium)
        }

        // MQTT Connection Status Indicator
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    color = if (isConnected) 
                        MaterialTheme.colorScheme.primaryContainer 
                    else 
                        MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(8.dp)
                )
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = when {
                    isConnected -> Icons.Default.CheckCircle
                    properties.enabled -> Icons.Default.Error
                    else -> Icons.Default.Pending
                },
                contentDescription = "Connection Status",
                tint = if (isConnected) 
                    MaterialTheme.colorScheme.onPrimaryContainer 
                else 
                    MaterialTheme.colorScheme.onErrorContainer
            )
            
            Text(
                text = when {
                    isConnected -> "Connected to ${properties.serverHost}:${properties.serverPort}"
                    properties.enabled -> "Disconnected"
                    else -> "MQTT Disabled"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (isConnected) 
                    MaterialTheme.colorScheme.onPrimaryContainer 
                else 
                    MaterialTheme.colorScheme.onErrorContainer
            )
        }

        // MQTT Enabled Control
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(8.dp)
                    )
                    .padding(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "MQTT Enabled",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TvFriendlySwitch(
                    modifier = Modifier.focusRequester(focusRequester),
                    checked = properties.enabled,
                    onCheckedChange = { repository.updateEnabled(it) }
                )
            }
        }

        // Server Configuration
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(8.dp)
                    )
                    .padding(12.dp)
        ) {
            Text(
                text = "Server Configuration",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Encryption Type
            CompositionLocalProvider(
                LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant
            ) {
                TvFriendlyChipsSelect(
                    title = "Encryption Type",
                    options = EncryptionType.values().toList(),
                    selectedOption = properties.encryption,
                    onOptionSelected = {
                        isOptionChangeTrigger = true
                        repository.updateEncryption(it)
                    },
                    optionLabel = { it.displayName }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Server and Port input
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CompactInputChip(
                    label = "Server",
                    value = properties.serverHost,
                    onClick = {
                        tempTextInput = properties.serverHost
                        showServerHostDialog = true
                    },
                    modifier = Modifier.weight(0.6f)
                )

                CompactInputChip(
                    label = "Port",
                    value = properties.serverPort.toString(),
                    onClick = {
                        tempTextInput = properties.serverPort.toString()
                        showPortDialog = true
                    },
                    modifier = Modifier.weight(0.4f)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Action buttons row: Test Connection, Discovery
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = { testConnection() },
                    enabled = !isTestingConnection && properties.serverHost.isNotEmpty(),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = if (isTestingConnection) "Testing..." else "Test Connection"
                    )
                }

                Button(
                    onClick = {
                        when (discoveryState) {
                            is DiscoveryState.Scanning -> discoveryRepository.stopDiscovery()
                            else -> checkDiscoveryPermissions()
                        }
                    },
                    enabled = discoveryState !is DiscoveryState.Scanning,
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector =
                                when (discoveryState) {
                                    is DiscoveryState.Scanning -> Icons.Default.Close
                                    else -> Icons.Default.Search
                                },
                            contentDescription =
                                when (discoveryState) {
                                    is DiscoveryState.Scanning -> "Stop Discovery"
                                    else -> "Discover MQTT Servers"
                                }
                        )
                        Text(
                            text = when (discoveryState) {
                                is DiscoveryState.Scanning -> "Stop"
                                else -> "Discover"
                            }
                        )
                    }
                }
            }

            // Test connection result
            testConnectionResult?.let { result ->
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = result,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (result.contains("successful"))
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.error
                )
            }

            // Discovery status
            when (val currentState = discoveryState) {
                is DiscoveryState.Scanning -> {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Scanning for MQTT servers...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                is DiscoveryState.Error -> {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Discovery error: ${currentState.message}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                is DiscoveryState.Found -> {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Found ${currentState.services.size} MQTT server(s)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                else -> {
                    /* No status message */
                }
            }

            // Discovery results
            if (showDiscoveryResults) {
                val currentState = discoveryState
                if (currentState is DiscoveryState.Found) {
                    Spacer(modifier = Modifier.height(8.dp))

                    val mqttServices =
                        currentState.services.filter {
                            it.type.contains("mqtt", ignoreCase = true)
                        }

                    if (mqttServices.isNotEmpty()) {
                        CompositionLocalProvider(
                            LocalContentColor provides
                                    MaterialTheme.colorScheme.onSurfaceVariant
                        ) {
                            TvFriendlyChipsSelect(
                                title = "Discovered MQTT Servers",
                                options = mqttServices,
                                selectedOption =
                                    mqttServices.firstOrNull {
                                        it.host == properties.serverHost
                                    }
                                        ?: mqttServices.first(),
                                onOptionSelected = { service ->
                                    repository.updateServerHost(service.host)
                                    repository.updateServerPort(service.port)
                                    showDiscoveryResults = false
                                },
                                optionLabel = { service ->
                                    "${service.displayName} (${service.hostPort})"
                                }
                            )
                        }
                    }
                }
            }
        }

        // Authentication
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(8.dp)
                    )
                    .padding(12.dp)
        ) {
            Text(
                text = "Authentication",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            CompactInputChip(
                label = "Username",
                value = properties.username,
                onClick = {
                    tempTextInput = properties.username
                    showUsernameDialog = true
                },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            CompactInputChip(
                label = "Password",
                value = if (properties.password.isEmpty()) "" else "••••••••",
                onClick = {
                    tempTextInput = properties.password
                    showPasswordDialog = true
                },
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Client Configuration
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(8.dp)
                    )
                    .padding(12.dp)
        ) {
            Text(
                text = "Client Configuration",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CompactInputChip(
                    label = "Client ID",
                    value = properties.clientId,
                    onClick = {
                        tempTextInput = properties.clientId
                        showClientIdDialog = true
                    },
                    modifier = Modifier.weight(1f)
                )

                Button(onClick = { repository.generateNewClientId() }) { Text("Generate") }
            }
        }

        // Topics Configuration
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(8.dp)
                    )
                    .padding(12.dp)
        ) {
            Text(
                text = "Topics",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            CompactInputChip(
                label = "Subscribe Topic",
                value = properties.subscribeTopic,
                onClick = {
                    tempTextInput = properties.subscribeTopic
                    showSubscribeTopicDialog = true
                },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            CompactInputChip(
                label = "Publish Topic",
                value = properties.publishTopic,
                onClick = {
                    tempTextInput = properties.publishTopic
                    showPublishTopicDialog = true
                },
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Connection Options
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(8.dp)
                    )
                    .padding(12.dp)
        ) {
            Text(
                text = "Connection Options",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // QoS Level
            CompositionLocalProvider(
                LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant
            ) {
                TvFriendlyChipsSelect(
                    title = "Quality of Service",
                    options = QosLevel.values().toList(),
                    selectedOption = properties.qos,
                    onOptionSelected = {
                        isOptionChangeTrigger = true
                        repository.updateQos(it)
                    },
                    optionLabel = { it.displayName }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Keep Alive
            Text(
                text = "Keep Alive: ${properties.keepAlive}s",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TvFriendlySlider(
                value = properties.keepAlive.toFloat(),
                onValueChange = { repository.updateKeepAlive(it.toInt()) },
                valueRange =
                    MqttPropertyRanges.KEEP_ALIVE_RANGE.first.toFloat()..MqttPropertyRanges
                        .KEEP_ALIVE_RANGE
                        .last.toFloat(),
                stepSize = 5f,
                formatValue = { "${it.toInt()}s" }
            )

            // Connection Timeout
            Text(
                text = "Connection Timeout: ${properties.connectionTimeout}s",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TvFriendlySlider(
                value = properties.connectionTimeout.toFloat(),
                onValueChange = { repository.updateConnectionTimeout(it.toInt()) },
                valueRange =
                    MqttPropertyRanges.CONNECTION_TIMEOUT_RANGE.first
                        .toFloat()..MqttPropertyRanges.CONNECTION_TIMEOUT_RANGE.last
                        .toFloat(),
                stepSize = 5f,
                formatValue = { "${it.toInt()}s" }
            )

            // Switches for boolean options
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Auto Reconnect",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TvFriendlySwitch(
                    checked = properties.autoReconnect,
                    onCheckedChange = { repository.updateAutoReconnect(it) }
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Clean Session",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TvFriendlySwitch(
                    checked = properties.cleanSession,
                    onCheckedChange = { repository.updateCleanSession(it) }
                )
            }
        }
    }

    // Add the dialogs outside the main composable content
    if (showServerHostDialog) {
        TextInputDialog(
            title = "Enter Server Address",
            value = properties.serverHost,
            onDismiss = { showServerHostDialog = false },
            onConfirm = { repository.updateServerHost(it) }
        )
    }

    if (showUsernameDialog) {
        TextInputDialog(
            title = "Enter Username",
            value = properties.username,
            onDismiss = { showUsernameDialog = false },
            onConfirm = { repository.updateUsername(it) }
        )
    }

    if (showPasswordDialog) {
        TextInputDialog(
            title = "Enter Password",
            value = properties.password,
            onDismiss = { showPasswordDialog = false },
            onConfirm = { repository.updatePassword(it) },
            keyboardType = KeyboardType.Password
        )
    }

    if (showClientIdDialog) {
        TextInputDialog(
            title = "Enter Client ID",
            value = properties.clientId,
            onDismiss = { showClientIdDialog = false },
            onConfirm = { repository.updateClientId(it) }
        )
    }

    if (showSubscribeTopicDialog) {
        TextInputDialog(
            title = "Enter Subscribe Topic",
            value = properties.subscribeTopic,
            onDismiss = { showSubscribeTopicDialog = false },
            onConfirm = { repository.updateSubscribeTopic(it) }
        )
    }

    if (showPublishTopicDialog) {
        TextInputDialog(
            title = "Enter Publish Topic",
            value = properties.publishTopic,
            onDismiss = { showPublishTopicDialog = false },
            onConfirm = { repository.updatePublishTopic(it) }
        )
    }

    // Add the port dialog
    if (showPortDialog) {
        TextInputDialog(
            title = "Enter Port Number",
            value = properties.serverPort.toString(),
            onDismiss = { showPortDialog = false },
            onConfirm = { portStr ->
                try {
                    val port = portStr.toInt()
                    if (port in MqttPropertyRanges.PORT_RANGE) {
                        repository.updateServerPort(port)
                    }
                } catch (e: NumberFormatException) {
                    // Handle invalid number format
                }
            },
            keyboardType = KeyboardType.Number
        )
    }

    // Permission dialogs
    if (showInternetPermissionDialog) {
        PermissionDialog(
            permissionType = PermissionType.INTERNET,
            title = "Internet Permission Required",
            description =
                "Internet access is required to discover MQTT servers on your network. Please grant this permission to continue.",
            onPermissionGranted = {
                showInternetPermissionDialog = false
                // Check if we also need location permission
                if (!PermissionUtil.checkPermission(context, PermissionType.LOCATION)) {
                    showLocationPermissionDialog = true
                } else {
                    // Both permissions granted, start discovery
                    discoveryRepository.startMqttDiscovery()
                }
            },
            onDismiss = { showInternetPermissionDialog = false }
        )
    }

    if (showLocationPermissionDialog) {
        PermissionDialog(
            permissionType = PermissionType.LOCATION,
            title = "Location Permission Required",
            description =
                "Location permission is required to discover devices on your local network. This is a security requirement from Android for network discovery features.",
            onPermissionGranted = {
                showLocationPermissionDialog = false
                // All permissions are now granted, start discovery
                discoveryRepository.startMqttDiscovery()
            },
            onDismiss = { showLocationPermissionDialog = false }
        )
    }
}

// Add one more dialog state variable for port

// CompactInputChip composable function
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun CompactInputChip(
    label: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    InputChip(
        selected = false,
        onClick = onClick,
        modifier = modifier,
        enabled = true,
        content = {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.weight(0.4f)
                )
                Text(
                    text = value.ifEmpty { "Not set" },
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(0.6f)
                )
            }
        }
    )
}
