package com.example.p2pstadium

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pDeviceList
import android.net.wifi.p2p.WifiP2pGroup
import android.net.wifi.p2p.WifiP2pInfo
import android.net.wifi.p2p.WifiP2pManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket

class P2PManager(
    private val context: Context,
    private val listener: Listener,
    private val username: String
) : WifiP2pManager.ChannelListener,
    WifiP2pManager.ConnectionInfoListener,
    WifiP2pManager.PeerListListener,
    WifiP2pManager.GroupInfoListener {

    interface Listener {
        fun onP2PStatusChanged(status: String)
        fun onConnectionInfoAvailable(info: WifiP2pInfo)
        fun onPeerListUpdated(peers: List<WifiP2pDevice>)
        fun onGroupInfoAvailable(group: WifiP2pGroup?)
        fun onMessageReceived(message: String)
        fun onDeviceDiscovered(device: WifiP2pDevice, username: String)
        fun onDeviceStatusChanged(device: WifiP2pDevice, status: String)
    }

    private val manager: WifiP2pManager = context.getSystemService(Context.WIFI_P2P_SERVICE) as WifiP2pManager
    private val channel = manager.initialize(context, context.mainLooper, null)
    private var serverThread: Thread? = null
    private var clientThread: Thread? = null
    private var serverSocket: ServerSocket? = null
    private var currentSocket: Socket? = null

    private val broadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION -> {
                    val state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1)
                    if (state == WifiP2pManager.WIFI_P2P_STATE_ENABLED) {
                        listener.onP2PStatusChanged("✅ Wi-Fi Direct activat")
                        discoverPeers()
                    } else {
                        listener.onP2PStatusChanged("❌ Wi-Fi Direct desactivat")
                    }
                }
                WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> {
                    manager.requestPeers(channel, this@P2PManager)
                }
                WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> {
                    manager.requestConnectionInfo(channel, this@P2PManager)
                    manager.requestGroupInfo(channel, this@P2PManager)
                }
            }
        }
    }

    fun start() {
        context.registerReceiver(broadcastReceiver, IntentFilter().apply {
            addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
        })
        listener.onP2PStatusChanged("Iniciant Wi-Fi Direct...")
    }

    fun stop() {
        try {
            currentSocket?.close()
            serverSocket?.close()
            serverThread?.interrupt()
            clientThread?.interrupt()
            context.unregisterReceiver(broadcastReceiver)
            manager.removeGroup(channel, null)
        } catch (e: Exception) {
            Log.e("P2PManager", "Error al aturar", e)
        }
    }

    fun discoverPeers() {
        manager.discoverPeers(channel, null)
    }

    fun connectToDevice(device: WifiP2pDevice) {
        val config = WifiP2pConfig().apply {
            deviceAddress = device.deviceAddress
        }
        manager.connect(channel, config, null)
    }

    fun createGroup() {
        manager.createGroup(channel, null)
    }

    fun startServer() {
        serverThread = Thread {
            try {
                serverSocket = ServerSocket(8988)
                listener.onP2PStatusChanged("📡 Servidor P2P actiu al port 8988")
                while (!Thread.currentThread().isInterrupted) {
                    val socket = serverSocket?.accept()
                    if (socket != null) {
                        currentSocket = socket
                        startMessageReader(socket)
                    }
                }
            } catch (e: Exception) {
                if (!Thread.currentThread().isInterrupted) {
                    Log.e("P2PManager", "Error servidor", e)
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
                listener.onP2PStatusChanged("📨 Connectat a $host")
            } catch (e: Exception) {
                Log.e("P2PManager", "Error client", e)
                listener.onP2PStatusChanged("❌ Error connectant com a client")
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
                Log.e("P2PManager", "Error llegint missatge", e)
            }
        }.start()
    }

    fun sendMessage(message: String) {
        try {
            currentSocket?.getOutputStream()?.let { output ->
                PrintWriter(output, true).println(message)
            }
        } catch (e: Exception) {
            Log.e("P2PManager", "Error enviant missatge", e)
        }
    }

    fun sendDeviceInfo() {
        val deviceInfo = "DEVICE_INFO:$username"
        sendMessage(deviceInfo)
    }

    override fun onChannelDisconnected() {
        listener.onP2PStatusChanged("⚠️ Canal P2P desconnectat")
    }

    override fun onConnectionInfoAvailable(info: WifiP2pInfo) {
        if (info.groupFormed) {
            if (info.isGroupOwner) {
                listener.onP2PStatusChanged("🔥 AP actiu. IP: ${info.groupOwnerAddress}")
                startServer()
                sendDeviceInfo()
            } else {
                listener.onP2PStatusChanged("🔗 Client connectat")
                connectAsClient(info.groupOwnerAddress.hostAddress)
            }
        }
    }

    override fun onPeersAvailable(peers: WifiP2pDeviceList) {
        val deviceList = peers.deviceList.toList()
        
        // Notifica els dispositius disponibles
        listener.onPeerListUpdated(deviceList)
        
        // Per cada dispositiu, enviem una sol·licitud d'informació
        for (device in deviceList) {
            listener.onDeviceDiscovered(device, "Desconegut")
        }
    }

    override fun onGroupInfoAvailable(group: WifiP2pGroup?) {
        listener.onGroupInfoAvailable(group)
        
        // Notifica els canvis en els clients connectats
        group?.clientList?.forEach { device ->
            listener.onDeviceStatusChanged(device, "Connectat")
        }
    }
}