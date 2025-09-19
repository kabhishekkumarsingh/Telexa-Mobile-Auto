package com.example.telexamobileauto

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.telexamobileauto.ui.theme.TelexaMobileAutoTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen() {
    var showDialog by remember { mutableStateOf(false) }
    var devices by remember { mutableStateOf(listOf<DeviceInfo>()) }
    var connectionStatus by remember { mutableStateOf("Disconnected") }

    val context = LocalContext.current

    // Initialize MQTT helper
    val mqttHelper = remember {
        MqttHelper(context) { message ->
            // Handle incoming MQTT messages
            println("Received: $message")
        }
    }

    // Set connection status callback
    LaunchedEffect(mqttHelper) {
        mqttHelper.setConnectionStatusCallback(object : ConnectionStatusCallback {
            override fun onConnectionStatusChanged(connected: Boolean, message: String) {
                connectionStatus = if (connected) "Connected" else "Disconnected: $message"
            }
        })
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Dashboard")
                        Text(
                            text = connectionStatus,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (connectionStatus.startsWith("Connected"))
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.error
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Add")
            }
        },
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            if (devices.isEmpty()) {
                Text("No devices added yet", modifier = Modifier.padding(16.dp))
            } else {
                devices.forEach { device ->
                    DeviceCard(
                        device = device,
                        mqttHelper = mqttHelper,
                        onEdit = { /* Handle edit */ },
                        onSettings = { /* Handle settings */ }
                    )
                }
            }
        }

        if (showDialog) {
            AddDeviceDialog(
                onDismiss = { showDialog = false },
                onAdd = { mobile, field, device ->
                    val newDevice = DeviceInfo(mobile, field, device)
                    devices = devices + newDevice

                    // Connect to MQTT with device topic when first device is added
                    if (devices.size == 1) {
                        val topic = "${mobile}_${device}" // Topic format: mobile_deviceType
                        mqttHelper.connect(topic)
                    }

                    showDialog = false
                }
            )
        }
    }
}

data class DeviceInfo(
    val mobile: String,
    val field: String,
    val device: String,
    var motorState: Boolean = false,
    var autoModeState: Boolean = false,
    var phaseStatus: String = "OK",
    var currentValue: String = "00",
    var signalStrength: String = "000%",
    var dryRunState: Boolean = false,
    var overloadState: Boolean = false,
    var lockState: Boolean = false
)

@Composable
fun DeviceCard(
    device: DeviceInfo,
    mqttHelper: MqttHelper,
    onEdit: () -> Unit,
    onSettings: () -> Unit
) {
    var localDevice by remember { mutableStateOf(device) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {

            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("${device.mobile} | ${device.field}", style = MaterialTheme.typography.titleMedium)
                Row {
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit")
                    }
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Motor + Auto Mode
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                MotorToggle(
                    label = "MOTOR",
                    isOn = localDevice.motorState,
                    onClick = {
                        localDevice = localDevice.copy(motorState = !localDevice.motorState)
                        val command = if (localDevice.motorState) "101" else "100" // Motor ON/OFF
                        mqttHelper.publish(command)
                    }
                )
                MotorToggle(
                    label = "AUTO MODE",
                    isOn = localDevice.autoModeState,
                    onClick = {
                        localDevice = localDevice.copy(autoModeState = !localDevice.autoModeState)
                        val command = if (localDevice.autoModeState) "201" else "200" // Auto Mode ON/OFF
                        mqttHelper.publish(command)
                    }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Phase, Current, Signal
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                InfoBox(
                    label = "PHASE",
                    value = localDevice.phaseStatus,
                    valueColor = Color.Blue,
                    onClick = {
                        mqttHelper.publish("111") // Request phase status
                    }
                )
                InfoBox(
                    label = "CURRENT",
                    value = localDevice.currentValue,
                    valueColor = Color.Black,
                    onClick = {
                        mqttHelper.publish("111") // Request current reading
                    }
                )
                InfoBox(
                    label = "SIGNAL",
                    value = localDevice.signalStrength,
                    valueColor = Color.Red,
                    onClick = {
                        mqttHelper.publish("333") // Request signal strength
                    }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Dry Run, Overload, Lock
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                ToggleSwitch(
                    label = "Dry Run",
                    checked = localDevice.dryRunState,
                    onCheckedChange = { checked ->
                        localDevice = localDevice.copy(dryRunState = checked)
                        val command = if (checked) "301" else "300" // Dry Run ON/OFF
                        mqttHelper.publish(command)
                    }
                )
                ToggleSwitch(
                    label = "Overload",
                    checked = localDevice.overloadState,
                    onCheckedChange = { checked ->
                        localDevice = localDevice.copy(overloadState = checked)
                        val command = if (checked) "401" else "400" // Overload ON/OFF
                        mqttHelper.publish(command)
                    }
                )
                ToggleSwitch(
                    label = "Lock",
                    checked = localDevice.lockState,
                    onCheckedChange = { checked ->
                        localDevice = localDevice.copy(lockState = checked)
                        val command = if (checked) "501" else "500" // Lock ON/OFF
                        mqttHelper.publish(command)
                    }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Refresh button
            Button(
                onClick = {
                    mqttHelper.publish("999") // Refresh all status
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color.Blue),
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Text("Refresh", color = Color.White)
            }
        }
    }
}

@Composable
fun MotorToggle(label: String, isOn: Boolean, onClick: () -> Unit) {
    val bgColor = if (isOn) Color(0xFF4CAF50) else Color.Red
    val text = if (isOn) "ON" else "OFF"

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable { onClick() }
    ) {
        Text(label)
        Surface(
            color = bgColor,
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.padding(top = 4.dp)
        ) {
            Text(
                text,
                color = Color.White,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
            )
        }
    }
}

@Composable
fun InfoBox(label: String, value: String, valueColor: Color, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable { onClick() }
    ) {
        Text(label)
        Surface(
            color = Color.White,
            shape = RoundedCornerShape(6.dp),
            modifier = Modifier.padding(4.dp),
            shadowElevation = 2.dp
        ) {
            Text(
                value,
                color = valueColor,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
    }
}

@Composable
fun ToggleSwitch(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label)
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = Color(0xFF673AB7),
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = Color.Gray
            )
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddDeviceDialog(
    onDismiss: () -> Unit,
    onAdd: (String, String, String) -> Unit
) {
    var mobileNumber by remember { mutableStateOf(TextFieldValue("")) }
    var fieldName by remember { mutableStateOf(TextFieldValue("")) }
    var selectedDevice by remember { mutableStateOf("TTMA3R3P") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Device") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = mobileNumber,
                    onValueChange = { if (it.text.length <= 10) mobileNumber = it },
                    label = { Text("Mobile Number") },
                    placeholder = { Text("Enter 10 Digit Mobile Number") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = fieldName,
                    onValueChange = { if (it.text.length <= 10) fieldName = it },
                    label = { Text("Field Name") },
                    placeholder = { Text("Enter Field Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = selectedDevice,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Device") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                if (mobileNumber.text.isNotEmpty() && fieldName.text.isNotEmpty()) {
                    onAdd(mobileNumber.text, fieldName.text, selectedDevice)
                }
            }) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun DashboardPreview() {
    TelexaMobileAutoTheme {
        DashboardScreen()
    }
}
