package com.jackcui.blecommdemo

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothGatt
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.clj.fastble.BleManager
import com.clj.fastble.callback.BleGattCallback
import com.clj.fastble.callback.BleNotifyCallback
import com.clj.fastble.callback.BleReadCallback
import com.clj.fastble.callback.BleScanCallback
import com.clj.fastble.callback.BleWriteCallback
import com.clj.fastble.data.BleDevice
import com.clj.fastble.data.BleScanState
import com.clj.fastble.exception.BleException
import com.clj.fastble.scan.BleScanRuleConfig
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.jackcui.blecommdemo.databinding.ActivityMainBinding
import com.permissionx.guolindev.PermissionX
import com.permissionx.guolindev.request.ForwardScope
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// BLE连接库：https://github.com/Jasonchenlijian/FastBle

class MainActivity : AppCompatActivity() {
    private lateinit var bd: ActivityMainBinding
    private val devices = mutableListOf<Device>()
    private val deviceInfoList = mutableListOf<String>()
    private var selectedIndex = -1

    // 用于开启蓝牙的Activity Result Launcher，成功开启则调用initBLE()初始化BLE模块，否则关闭应用
    private val enableBtLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (it.resultCode == RESULT_OK) {
                Toast.makeText(this, "蓝牙已开启", Toast.LENGTH_SHORT).show()
                initBle()
            } else {
                Toast.makeText(this, "蓝牙未开启", Toast.LENGTH_SHORT).show()
                finish()
            }
        }

    /**
     *  常量，UUID在此处为预设固定值
     */
    companion object {
        private const val TAG = "BLECommDemo"
        private const val UUID_SERVICE = "2a5f54ed-1727-4127-9797-fbcdfc0a15bd"
        private const val UUID_CHARACTERISTIC_WRITE = "8f9a8a48-12b2-4627-8872-17ca0b99fad0"
        private const val UUID_CHARACTERISTIC_READ = "de657422-bc07-4eaa-a321-8965221294d0"
        private const val UUID_CHARACTERISTIC_NOTIFY = "de657422-bc07-4eaa-a321-8965221294d0"
    }

    /**
     * 蓝牙设备数据类型，包含设备实例、名称和mac地址
     */
    data class Device(
        val bleDevice: BleDevice,
        val name: String,
        val mac: String
    ) {
        // 重写toString()方法以方便打印
        override fun toString(): String {
            return "${name.ifEmpty { "未知设备" }} [$mac]"
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        bd = ActivityMainBinding.inflate(layoutInflater)
        setContentView(bd.root)

        // 请求权限
        requestPerm()
        // 设置监听器
        setListener()
    }

    /**
     * 设置监听器
     */
    private fun setListener() {
        bd.actvDevices.setOnItemClickListener { _, _, position, _ ->
            selectedIndex = position
        }

        bd.btnScan.setOnClickListener {
            if (BleManager.getInstance().scanSate == BleScanState.STATE_SCANNING) {
                Toast.makeText(this, "已经在扫描设备，请稍后", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            bleScan()
        }

        bd.btnConnect.setOnClickListener {
            if (bd.actvDevices.text.isEmpty()) {
                Toast.makeText(this, "请选择一个设备", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (BleManager.getInstance().scanSate == BleScanState.STATE_SCANNING) {
                Toast.makeText(this, "正在扫描设备，请稍后", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val selectedDevice = devices[selectedIndex].bleDevice
            bleConnect(selectedDevice)
        }

        bd.btnWrite.setOnClickListener {
            if (BleManager.getInstance().allConnectedDevice.isEmpty()) {
                Toast.makeText(this, "未连接到设备", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (bd.tietContent.text.isNullOrEmpty()) {
                Toast.makeText(this, "请输入要发送的数据", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val selectedDevice = devices[selectedIndex].bleDevice
            val msgData = bd.tietContent.text.toString().encodeToByteArray()
            bleWrite(selectedDevice, UUID_SERVICE, UUID_CHARACTERISTIC_WRITE, msgData)
        }

        bd.btnRead.setOnClickListener {
            if (BleManager.getInstance().allConnectedDevice.isEmpty()) {
                Toast.makeText(this, "未连接到设备", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val selectedDevice = devices[selectedIndex].bleDevice
            bleRead(selectedDevice, UUID_SERVICE, UUID_CHARACTERISTIC_READ)
        }
    }

    /**
     * BLE - 初始化模块
     */
    private fun initBle() {
        val bleManager = BleManager.getInstance()
        bleManager.init(application)
        bleManager.enableLog(true)
            .setReConnectCount(1, 5000)
            .setSplitWriteNum(20)
            .setConnectOverTime(10000)
            .setOperateTimeout(5000);
        val scanRuleConfig = BleScanRuleConfig.Builder()
            .setAutoConnect(false)
            .setScanTimeOut(10000)
            .build()
        bleManager.initScanRule(scanRuleConfig)
        appendLog("BLE初始化完成")
    }

    /**
     * BLE - 扫描设备
     */
    private fun bleScan() {
        BleManager.getInstance().scan(object : BleScanCallback() {
            override fun onScanStarted(success: Boolean) {
                Log.d(TAG, "onScanStarted: success = $success")
                appendLog("开始扫描")
                devices.clear()
                deviceInfoList.clear()
            }

            override fun onScanning(bleDevice: BleDevice) {
                Log.d(TAG, "onScanning")
                val baData = BleUtil.parseAdvertisedData(bleDevice.scanRecord)
                val dev = Device(bleDevice, bleDevice.name ?: baData.name, bleDevice.mac)
                devices.add(dev)
                deviceInfoList.add(dev.toString())
//                appendLog("扫描到设备：$dev")
                (bd.tilDevices.editText as? MaterialAutoCompleteTextView)?.setSimpleItems(
                    deviceInfoList.toTypedArray()
                )
            }

            override fun onScanFinished(scanResultList: List<BleDevice>) {
                Log.d(TAG, "onScanFinished")
                appendLog("扫描结束")
            }
        })
    }

    /**
     * BLE - 连接设备
     */
    private fun bleConnect(bleDevice: BleDevice) {
        BleManager.getInstance().connect(bleDevice, object : BleGattCallback() {
            override fun onStartConnect() {
                Log.d(TAG, "onStartConnect")
                appendLog("开始连接")
            }

            override fun onConnectFail(bleDevice: BleDevice, exception: BleException) {
                Log.d(
                    TAG,
                    "onConnectFail: code = ${exception.code}, description = ${exception.description}"
                )
                appendLog("连接失败，状态码：${exception.code}，描述：${exception.description}")
            }

            override fun onConnectSuccess(bleDevice: BleDevice, gatt: BluetoothGatt, status: Int) {
                Log.d(TAG, "onConnectSuccess: status = $status")
                appendLog("连接成功，状态码：$status")
                // 连接成功后，开始订阅数据变化（自动回馈）
                bleNotify(bleDevice, UUID_SERVICE, UUID_CHARACTERISTIC_NOTIFY)
            }

            override fun onDisConnected(
                isActiveDisConnected: Boolean,
                bleDevice: BleDevice,
                gatt: BluetoothGatt,
                status: Int
            ) {
                Log.d(TAG, "onDisconnected: status = $status")
                appendLog("连接已断开，状态码：$status")
            }
        })
    }

    /**
     * BLE - 写入数据
     */
    private fun bleWrite(
        bleDevice: BleDevice,
        uuidService: String,
        uuidCharacteristicWrite: String,
        data: ByteArray
    ) {
        BleManager.getInstance().write(
            bleDevice,
            uuidService,
            uuidCharacteristicWrite,
            data,
            object : BleWriteCallback() {
                override fun onWriteSuccess(current: Int, total: Int, justWrite: ByteArray) {
                    Log.d(TAG, "onWriteSuccess: current = $current, total = $total")
                    appendLog("写入成功，当前：$current，总计：$total")
                }

                override fun onWriteFailure(exception: BleException) {
                    Log.d(
                        TAG,
                        "onWriteFailure: code = ${exception.code}, description = ${exception.description}"
                    )
                    appendLog("写入失败，状态码：${exception.code}，描述：${exception.description}")
                }
            })
    }

    /**
     * BLE - 读取数据
     */
    private fun bleRead(
        bleDevice: BleDevice,
        uuidService: String,
        uuidCharacteristicRead: String
    ) {
        BleManager.getInstance().read(
            bleDevice,
            uuidService,
            uuidCharacteristicRead,
            object : BleReadCallback() {
                override fun onReadSuccess(data: ByteArray) {
                    Log.d(TAG, "onReadSuccess: data = ${data.toString(Charsets.UTF_8)}")
                    appendLog("读取成功，数据：${data.toString(Charsets.UTF_8)}")
                }

                override fun onReadFailure(exception: BleException) {
                    Log.d(
                        TAG,
                        "onReadFailure: code = ${exception.code}, description = ${exception.description}"
                    )
                    appendLog("读取失败，状态码：${exception.code}，描述：${exception.description}")
                }
            })
    }

    /**
     * BLE - 订阅数据变化
     */
    private fun bleNotify(
        bleDevice: BleDevice,
        uuidService: String,
        uuidCharacteristicNotify: String,
    ) {
        BleManager.getInstance().notify(
            bleDevice,
            uuidService,
            uuidCharacteristicNotify,
            object : BleNotifyCallback() {
                override fun onNotifySuccess() {
                    Log.d(TAG, "onNotifySuccess: 订阅成功")
                    appendLog("订阅成功")
                }

                override fun onNotifyFailure(exception: BleException) {
                    Log.d(
                        TAG,
                        "onNotifyFailure: code = ${exception.code}, description = ${exception.description}"
                    )
                    appendLog("订阅失败，状态码：${exception.code}，描述：${exception.description}")
                }

                override fun onCharacteristicChanged(data: ByteArray) {
                    Log.d(TAG, "onCharacteristicChanged: data = ${data.toString(Charsets.UTF_8)}")
                    appendLog("订阅的UUID值已变更，数据：${data.toString(Charsets.UTF_8)}")
                }
            })
    }

    /**
     * 申请必要运行时权限
     * 如申请成功，则发送开启蓝牙请求Intent，否则将退出应用
     */
    private fun requestPerm() {
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(android.Manifest.permission.BLUETOOTH_SCAN)
            permissions.add(android.Manifest.permission.BLUETOOTH_ADVERTISE)
            permissions.add(android.Manifest.permission.BLUETOOTH_CONNECT)
            permissions.add(android.Manifest.permission.ACCESS_COARSE_LOCATION)
            permissions.add(android.Manifest.permission.ACCESS_FINE_LOCATION)
        } else {
            permissions.add(android.Manifest.permission.ACCESS_COARSE_LOCATION)
            permissions.add(android.Manifest.permission.ACCESS_FINE_LOCATION)
        }
        PermissionX.init(this)
            .permissions(permissions)
            .explainReasonBeforeRequest()
            .onExplainRequestReason { scope, deniedList ->
                scope.showRequestReasonDialog(
                    deniedList,
                    "操作蓝牙必须使用以下权限",
                    "好的",
                    "取消"
                )
            }
            .onForwardToSettings { scope: ForwardScope, deniedList: MutableList<String> ->
                scope.showForwardToSettingsDialog(
                    deniedList,
                    "授权请求被永久拒绝\n您需要去应用程序设置中手动开启权限",
                    "跳转到设置",
                    "取消"
                )
            }
            .request { allGranted, grantedList, deniedList ->
                if (allGranted) {
                    enableBtLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
                } else {
                    Toast.makeText(this, "权限请求被拒绝", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
    }

    /**
     * 追加运行日记
     */
    private fun appendLog(msg: String) {
        runOnUiThread {
            val now = Date()
            val formatter = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
            val time = formatter.format(now)
            val old = bd.tvLog.text.toString()
            bd.tvLog.text = "$old\n[$time] $msg"
        }
    }
}