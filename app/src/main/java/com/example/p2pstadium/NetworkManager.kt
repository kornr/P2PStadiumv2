package com.example.p2pstadium

import android.content.Context
import android.net.wifi.WifiConfiguration
import android.net.wifi.WifiManager
import android.net.wifi.WifiManager.LocalOnlyHotspotCallback
import android.net.wifi.WifiManager.LocalOnlyHotspotReservation
import android.net.wifi.WifiManager.WifiLock
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.annotation.RequiresApi
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.*

class NetworkManager(
    private val context: Context,
    private val listener: Listener,
    private val username: String
) {

    interface Listener {
        fun onNetworkStatusChanged(status: String)
        fun onConnectionInfoAvailable(info: NetworkInfo)
        fun onPeerListUpdated(peers: List<DeviceInfo>)
        fun onGroupInfoAvailable(group: NetworkGroup?)
        fun onMessageReceived(message: String)
        fun onDeviceDiscovered(device: DeviceInfo, username: String)
        fun onDeviceStatusChanged(device: DeviceInfo, status: String)
    }

    private val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private var wifiLock: WifiLock? = null
    private var hotspotReservation: LocalOnlyHotspotReservation? = null
    private var serverThread: Thread? = null
    private var clientThread: Thread? = null
    private var serverSocket: ServerSocket? = null
    private var currentSocket: Socket? = null
    private var isHotspotActive = false
    private var isClientConnected = false

    data class NetworkInfo(
        val groupFormed: Boolean,
        val isGroupOwner: Boolean,
        val groupOwnerAddress: String
    )

    data class NetworkGroup(
        val clientList: List<DeviceInfo>
    )

    data class DeviceInfo(
        val deviceName: String,
        val deviceAddress: String,
        val ipAddress: String
    )

    fun startClient() {
        listener.onNetworkStatusChanged("Iniciant mode Client...")
        // Aquí implementaríem la connexió com a client
        // En una implementació real, buscaríem xarxes amb el SSID específic
        listener.onNetworkStatusChanged("Mode Client actiu")
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun createNetwork(ipAddress: String, subnetMask: String, ssid: String) {
        listener.onNetworkStatusChanged("Creant xarxa amb SSID: $ssid")
        
        // Configura la xarxa
        val config = WifiConfiguration().apply {
            Ssid = "\"$ssid\""
            PreSharedKey = "\"password\""
            allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK)
        }
        
        // Crea la xarxa
        val networkId = wifiManager.addNetwork(config)
        if (networkId != -1) {
            wifiManager.enableNetwork(networkId, true)
            listener.onNetworkStatusChanged("Xarxa creada amb èxit: $ssid")
        } else {
            listener.onNetworkStatusChanged("Error al crear la xarxa")
        }
        
        // En una implementació real, configuraríem l'IP estàtica
        // Això és més complex i depèn de la versió d'Android
    }

    fun stop() {
        try {
            currentSocket?.close()
            serverSocket?.close()
            serverThread?.interrupt()
            clientThread?.interrupt()
            
            if (isHotspotActive) {
                hotspotReservation?.close()
                isHotspotActive = false
                listener.onNetworkStatusChanged("Hotspot desactivat")
            }
            
            if (wifiLock != null && wifiLock!!.isHeld) {
                wifiLock?.release()
                wifiLock = null
            }
            
            listener.onNetworkStatusChanged("Xarxa aturada")
        } catch (e: Exception) {
            Log.e("NetworkManager", "Error al aturar", e)
        }
    }

    fun discoverPeers() {
        listener.onNetworkStatusChanged("Cercant dispositius propers...")
        // En una implementació real, aquí buscaríem altres dispositius en la mateixa xarxa
        // Això podria implicar escaneig de xarxa o protocol de descoberta
        val mockPeers = listOf(
            DeviceInfo("Dispositiu 1", "192.168.4.2", "192.168.4.2"),
            DeviceInfo("Dispositiu 2", "192.168.4.3", "192.168.4.3")
        )
        listener.onPeerListUpdated(mockPeers)
    }

    fun forceDiscoverPeers() {
        stop()
        startClient()
        discoverPeers()
    }

    fun connectToDevice(device: DeviceInfo) {
        listener.onNetworkStatusChanged("Connectant al dispositiu: ${device.deviceName}")
        // En una implementació real, aquí establiríem la connexió amb el dispositiu
        listener.onNetworkStatusChanged("Connectat a ${device.deviceName}")
    }

    fun startServer() {
        serverThread = Thread {
            try {
                serverSocket = ServerSocket(8988)
                listener.onNetworkStatusChanged("📡 Servidor actiu al port 8988")
                while (!Thread.currentThread().isInterrupted) {
                    val socket = serverSocket?.accept()
                    if (socket != null) {
                        currentSocket = socket
                        startMessageReader(socket)
                    }
                }
            } catch (e: Exception) {
                if (!Thread.currentThread().isInterrupted) {
                    Log.e("NetworkManager", "Error servidor", e)
                }
            }
        }
        serverThread!!.start()
    }

    fun connectAsClient(host: String) {
        clientThread = Thread {
            try {
                val socket = Socket()
                socket.connect(InetSocketAddress(host, 8988), 10000)
                currentSocket = socket
                startMessageReader(socket)
                listener.onNetworkStatusChanged("✅ Connectat a $host")
            } catch (e: Exception) {
                Log.e("NetworkManager", "Error client", e)
                listener.onNetworkStatusChanged("❌ Error connectant com a client")
            }
        }
        clientThread!!.start()
    }

    private fun startMessageReader(socket: Socket) {
        Thread {
            try {
                val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    Handler(Looper.getMainLooper()).post {
                        listener.onMessageReceived(line!!)
                    }
                }
            } catch (e: Exception) {
                Log.e("NetworkManager", "Error llegint missatge", e)
            }
        }.start()
    }

    fun sendMessage(message: String) {
        try {
            currentSocket?.getOutputStream()?.let { output ->
                PrintWriter(output, true).println(message)
            }
        } catch (e: Exception) {
            Log.e("NetworkManager", "Error enviant missatge", e)
        }
    }

    fun sendDeviceInfo() {
        val deviceInfo = "DEVICE_INFO:$username"
        sendMessage(deviceInfo)
    }

    // Mètodes auxiliars
    private fun getLocalIpAddress(): String {
        try {
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            for (interface_ in interfaces) {
                val addresses = Collections.list(interface_.inetAddresses)
                for (address in addresses) {
                    if (!address.isLoopbackAddress && address is Inet4Address) {
                        return address.hostAddress
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("NetworkManager", "Error obtenint IP local", e)
        }
        return "127.0.0.1"
    }

    // Mètodes per a la simulació
    fun simulateConnection() {
        val info = NetworkInfo(
            groupFormed = true,
            isGroupOwner = false,
            groupOwnerAddress = "192.168.4.1"
        )
        listener.onConnectionInfoAvailable(info)
        
        val group = NetworkGroup(
            clientList = listOf(
                DeviceInfo("Client 1", "192.168.4.2", "192.168.4.2"),
                DeviceInfo("Client 2", "192.168.4.3", "192.168.4.3")
            )
        )
        listener.onGroupInfoAvailable(group)
    }
}