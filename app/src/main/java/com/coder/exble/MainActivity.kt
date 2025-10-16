// MainActivity.kt

package com.coder.exble

import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
import android.os.Build
import android.os.Bundle
import android.util.SparseArray
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SignalCellular0Bar
import androidx.compose.material.icons.filled.SignalCellularAlt1Bar
import androidx.compose.material.icons.filled.SignalCellularAlt2Bar
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.coder.ble.models.ConnectionState
import com.coder.ble.utils.PermissionUtils
import com.coder.exble.ui.theme.ExBleTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    // 新的权限请求方式
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            // 权限已授予，可以开始扫描
            viewModel.startScan()
        } else {
            // 用户拒绝了权限
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 请求权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissionLauncher.launch(PermissionUtils.getRequiredScanPermissions())
        }

        setContent {
            ExBleTheme {
                MainScreen(viewModel = viewModel)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MainViewModel) {
    val isBluetoothEnabled by viewModel.isBluetoothEnabled.collectAsStateWithLifecycle()
    val isScanning by viewModel.isScanning
    val devices = viewModel.devices
    val interactingDevice by viewModel.interactingDevice
    val connectionState by viewModel.connectionState
    val receivedData by viewModel.receivedData
    val services = viewModel.services
    val characteristics = viewModel.characteristics
    val selectedService by viewModel.selectedService
    val isCharacteristicSelected by viewModel.isCharacteristicSelected
    Scaffold(
        topBar = { TopAppBar(title = { Text("蓝牙库") }) }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).padding(16.dp)) {
            BluetoothStatusCard(isBluetoothEnabled, isScanning) {
                if (!isScanning) viewModel.startScan() else viewModel.stopScan()
            }
            Spacer(Modifier.height(16.dp))
            if (interactingDevice == null) {
                DeviceList(devices = devices, onDeviceClick = { viewModel.connectToDevice(it) })
            } else {

                when {
                    connectionState == ConnectionState.CONNECTING -> {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator()
                            Spacer(Modifier.height(16.dp))
                            Text(connectionState.displayName)
                        }
                    }

                    connectionState == ConnectionState.CONNECTED && !isCharacteristicSelected -> {
                        if (selectedService == null) {
                            // 显示服务列表
                            ServiceList(services = services, onServiceClick = { viewModel.selectService(it) })
                        } else {
                            // 显示特征列表
                            CharacteristicList(
                                characteristics = characteristics,
                                onCharacteristicClick = { viewModel.selectCharacteristic(it) })
                        }
                    }


                    connectionState == ConnectionState.CONNECTED && isCharacteristicSelected -> {
                        DeviceControlPanel(
                            state = connectionState,
                            scannedDevice = interactingDevice,
                            receivedData = receivedData,
                            onDisconnect = { viewModel.disconnect() },
                            onSend = { viewModel.sendData(it) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ServiceList(services: List<BluetoothGattService>, onServiceClick: (BluetoothGattService) -> Unit) {
    Column {
        Text("选择一个服务", style = MaterialTheme.typography.headlineSmall)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
            items(services, key = { it.uuid }) { service ->
                Card(modifier = Modifier.fillMaxWidth().clickable { onServiceClick(service) }) {
                    Text(
                        text = "服务UUID:\n${service.uuid}",
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun CharacteristicList(
    characteristics: List<BluetoothGattCharacteristic>,
    onCharacteristicClick: (BluetoothGattCharacteristic) -> Unit
) {
    Column {
        Text("选择一个特征", style = MaterialTheme.typography.headlineSmall)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
            items(characteristics, key = { it.uuid }) { characteristic ->
                Card(modifier = Modifier.fillMaxWidth().clickable { onCharacteristicClick(characteristic) }) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("特征UUID:\n${characteristic.uuid}")
                        Text(
                            "属性: ${getPropertiesString(characteristic)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun BluetoothStatusCard(isEnabled: Boolean, isScanning: Boolean, onScanClick: () -> Unit) {
    Card {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("蓝牙状态: ${if (isEnabled) "开启" else "关闭"}", color = if (isEnabled) Color.Green else Color.Red)
            Button(onClick = onScanClick, enabled = isEnabled) {
                Text(if (!isScanning) "扫描" else "停止")
            }
        }
    }
}

@Composable
fun DeviceList(devices: List<ScannedDevice>, onDeviceClick: (ScannedDevice) -> Unit) { // <-- 新的
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(devices, key = { it.device.address }) { scannedDevice ->
            Card(modifier = Modifier.fillMaxWidth().clickable { onDeviceClick(scannedDevice) }) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // 显示设备名和地址 (不变)
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${scannedDevice.device.name ?: "未知设备"}\n${scannedDevice.device.address}",
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f)
                        )
                        RssiStateView(scannedDevice.rssi)
                    }
                    // --- 新增：显示厂商数据 ---
                    scannedDevice.manufacturerData?.let { data ->
                        if (data.size() > 0) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "厂商数据: ${formatManufacturerData(data)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Gray
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DeviceControlPanel(
    state: ConnectionState,
    scannedDevice: ScannedDevice?,
    receivedData: String,
    onDisconnect: () -> Unit,
    onSend: (String) -> Unit
) {
    var textToSend by remember { mutableStateOf("D3960468ef5707") }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("连接状态: ${state.displayName}", style = MaterialTheme.typography.headlineSmall)
        scannedDevice?.manufacturerData?.let { data ->
            if (data.size() > 0) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "厂商数据: ${formatManufacturerData(data)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = receivedData,
            onValueChange = {},
            readOnly = true,
            label = { Text("接收到的数据") }
        )
        Spacer(Modifier.height(16.dp))
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            if (state == ConnectionState.CONNECTED) {
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = textToSend,
                    onValueChange = { textToSend = it },
                    label = { Text("输入要发送的数据") }
                )
                Spacer(Modifier.height(8.dp))
                Button(onClick = { onSend(textToSend) }) {
                    Text("发送")
                }
            }

            Spacer(Modifier.height(32.dp))
            Button(onClick = onDisconnect) {
                Text("断开连接")
            }
        }
    }
}

/** 辅助函数，将特征属性转换为可读字符串 */
fun getPropertiesString(characteristic: BluetoothGattCharacteristic): String {
    val properties = characteristic.properties
    val props = mutableListOf<String>()
    if ((properties and BluetoothGattCharacteristic.PROPERTY_READ) > 0) props.add("Read")
    if ((properties and BluetoothGattCharacteristic.PROPERTY_WRITE) > 0) props.add("Write")
    if ((properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) > 0) props.add("Write (No Response)")
    if ((properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY) > 0) props.add("Notify")
    if ((properties and BluetoothGattCharacteristic.PROPERTY_INDICATE) > 0) props.add("Indicate")
    return props.joinToString(", ")
}

private fun formatManufacturerData(data: SparseArray<ByteArray>): String {
    if (data.size() == 0) return "无"
    val builder = StringBuilder()
    for (i in 0 until data.size()) {
        val manufacturerId = data.keyAt(i)
        val hexData = data.valueAt(i).joinToString("") { "%02X".format(it) }
        builder.append("ID: $manufacturerId, 数据: 0x$hexData; ")
    }
    return builder.toString().trimEnd(';', ' ')
}

@Composable
private fun getRssiColor(rssi: Int): Color {
    return when {
        rssi > -70 -> Color(0xFF00B050) // 强信号 (绿色)
        rssi > -85 -> Color(0xFFE59400) // 中等信号 (橙色)
        else -> MaterialTheme.colorScheme.error // 弱信号 (红色)
    }
}

@Composable
fun RssiStateView(rssi: Int) {
    val icon = when {
        rssi > -70 -> Icons.Default.SignalCellularAlt // 3格信号, 强
        rssi > -85 -> Icons.Default.SignalCellularAlt2Bar // 2格信号, 中
        rssi > -90 -> Icons.Default.SignalCellularAlt1Bar // 1格信号, 弱
        else -> Icons.Default.SignalCellular0Bar // 0格信号, 极差
    }
    Row {
        Icon(
            imageVector = icon,
            contentDescription = "信号强度: $rssi dBm",
            tint = getRssiColor(rssi)
        )
        Text(
            text = " $rssi dBm",
            style = MaterialTheme.typography.titleMedium,
            color = getRssiColor(rssi) // 根据信号强度显示不同颜色
        )
    }
}
