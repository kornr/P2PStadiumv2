package com.example.p2pstadium

import android.content.Context
import android.net.wifi.WifiConfiguration
import android.net.wifi.WifiManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
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

    private val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private var serverThread: Thread? = null
    private var clientThread: Thread? = null
    private var serverSocket: ServerSocket? = null
    private var currentSocket: Socket? = null
    private var isNetworkActive = false
    private var networkSsid = "P2PStadium"
    private var networkPassword = "password"
    private var ipAddress = "192.168.4.1"
    private var subnetMask = "255.255.255.0"
    private var isAp = false

    fun startClient() {
        listener.onNetworkStatusChanged("Iniciant mode Client...")
        isAp = false
        // En una implementació real, aquí buscaríem xarxes amb el SSID específic
        listener.onNetworkStatusChanged("Mode Client actiu")
        discoverPeers()
    }

    fun createNetwork(ipAddress: String, subnetMask: String, ssid: String) {
        this.ipAddress = ipAddress
        this.subnetMask = subnetMask
        this.networkSsid = ssid
        isAp = true
        
        listener.onNetworkStatusChanged("Creant xarxa amb SSID: $ssid")
        
        // En una implementació real, aquí configuraríem la xarxa
        // Aquesta és una implementació simplificada
        listener.onNetworkStatusChanged("Xarxa creada amb èxit: $ssid")
        isNetworkActive = true
        
        // Simulació de la connexió
        Handler(Looper.getMainLooper()).postDelayed({
            val info = NetworkInfo(
                groupFormed = true,
                isGroupOwner = true,
                groupOwnerAddress = this.ipAddress
            )
            listener.onConnectionInfoAvailable(info)
            
            val group = NetworkGroup(
                clientList = listOf(
                    DeviceInfo("Client 1", "192.168.4.2", "192.168.4.2"),
                    DeviceInfo("Client 2", "192.168.4.3", "192.168.4.3")
                )
            )
            listener.onGroupInfoAvailable(group)
        }, 1000)
    }

    fun stop() {
        try {
            currentSocket?.close()
            serverSocket?.close()
            serverThread?.interrupt()
            clientThread?.interrupt()
            
            isNetworkActive = false
            listener.onNetworkStatusChanged("Xarxa aturada")
        } catch (e: Exception) {
            Log.e("NetworkManager", "Error al aturar", e)
        }
    }

    fun discoverPeers() {
        listener.onNetworkStatusChanged("Cercant dispositius propers...")
        
        // En una implementació real, aquí buscaríem altres dispositius en la mateixa xarxa
        // Aquesta és una implementació simplificada
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
        
        // Simulació de la connexió
        Handler(Looper.getMainLooper()).postDelayed({
            val info = NetworkInfo(
                groupFormed = true,
                isGroupOwner = false,
                groupOwnerAddress = device.ipAddress
            )
            listener.onConnectionInfoAvailable(info)
        }, 1000)
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
                    // Creem una còpia local no mutable de la línia
                    val currentLine = line
                    if (currentLine != null) {
                        // Processa el missatge i reenvia a tots els clients si som AP
                        Handler(Looper.getMainLooper()).post {
                            listener.onMessageReceived(currentLine)
                        }
                        
                        // Si som AP, reenvia el missatge a tots els altres clients
                        if (isAp) {
                            retransmitMessage(currentLine)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("NetworkManager", "Error llegint missatge", e)
            }
        }.start()
    }

    private fun retransmitMessage(message: String) {
        // En una implementació real, aquí enviaríem el missatge a tots els clients connectats
        // Aquesta és una implementació simplificada
        Log.d("NetworkManager", "Retransmetent missatge: $message")
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

    fun sendPosition(position: String) {
        val positionInfo = "POSITION:$username:$position"
        sendMessage(positionInfo)
    }

    fun broadcastMessage(message: String) {
        sendMessage(message)
    }
}