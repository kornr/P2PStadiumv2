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
    private var isDiscovering = false

    private val broadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action
            Log.d("P2PManager", "Rebut intent: $action")
            
            when (action) {
                WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION -> {
                    val state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1)
                    if (state == WifiP2pManager.WIFI_P2P_STATE_ENABLED) {
                        Log.d("P2PManager", "Wi-Fi Direct estat: ACTIVAT")
                        listener.onP2PStatusChanged("✅ Wi-Fi Direct activat")
                        discoverPeers()
                    } else {
                        Log.d("P2PManager", "Wi-Fi Direct estat: DESACTIVAT")
                        listener.onP2PStatusChanged("❌ Wi-Fi Direct desactivat")
                    }
                }
                WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> {
                    Log.d("P2PManager", "Canvi en la llista de dispositius propers")
                    if (isDiscovering) {
                        manager.requestPeers(channel, this@P2PManager)
                    }
                }
                WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> {
                    Log.d("P2PManager", "Canvi en la connexió Wi-Fi Direct")
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
        Log.d("P2PManager", "P2PManager iniciat")
        listener.onP2PStatusChanged("Iniciant Wi-Fi Direct...")
    }

    fun stop() {
        isDiscovering = false
        try {
            currentSocket?.close()
            serverSocket?.close()
            serverThread?.interrupt()
            clientThread?.interrupt()
            context.unregisterReceiver(broadcastReceiver)
            manager.removeGroup(channel, null)
            Log.d("P2PManager", "P2PManager aturat")
        } catch (e: Exception) {
            Log.e("P2PManager", "Error al aturar", e)
        }
    }

    fun discoverPeers() {
        isDiscovering = true
        Log.d("P2PManager", "Iniciant cerca de dispositius propers")
        manager.discoverPeers(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                Log.d("P2PManager", "Cerca de dispositius iniciada amb èxit")
                listener.onP2PStatusChanged("🔍 Cerca de dispositius iniciada")
                isDiscovering = true
            }

            override fun onFailure(reasonCode: Int) {
                Log.e("P2PManager", "Error en iniciar cerca: $reasonCode")
                listener.onP2PStatusChanged("❌ Error en iniciar cerca: $reasonCode")
                isDiscovering = false
            }
        })
    }

    fun forceDiscoverPeers() {
        stop()
        start()
        discoverPeers()
        Log.d("P2PManager", "Forçant cerca de dispositius propers")
    }

    fun connectToDevice(device: WifiP2pDevice) {
        Log.d("P2PManager", "Connectant al dispositiu: ${device.deviceName} (${device.deviceAddress})")
        val config = WifiP2pConfig().apply {
            deviceAddress = device.deviceAddress
        }
        manager.connect(channel, config, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                Log.d("P2PManager", "Connexió iniciada amb èxit")
                listener.onP2PStatusChanged("🔗 Connexió iniciada")
            }

            override fun onFailure(reasonCode: Int) {
                Log.e("P2PManager", "Error en connectar: $reasonCode")
                listener.onP2PStatusChanged("❌ Error en connectar: $reasonCode")
            }
        })
    }

    fun createGroup() {
        Log.d("P2PManager", "Creant grup Wi-Fi Direct")
        manager.createGroup(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                Log.d("P2PManager", "Grup creat amb èxit")
                listener.onP2PStatusChanged("✅ Grup creat amb èxit")
            }

            override fun onFailure(reasonCode: Int) {
                Log.e("P2PManager", "Error en crear grup: $reasonCode")
                listener.onP2PStatusChanged("❌ Error en crear grup: $reasonCode")
            }
        })
    }

    fun startServer() {
        Log.d("P2PManager", "Iniciant servidor P2P")
        serverThread = Thread {
            try {
                serverSocket = ServerSocket(8988)
                Log.d("P2PManager", "Servidor P2P actiu al port 8988")
                listener.onP2PStatusChanged("📡 Servidor P2P actiu al port 8988")
                while (!Thread.currentThread().isInterrupted) {
                    val socket = serverSocket?.accept()
                    if (socket != null) {
                        Log.d("P2PManager", "Nova connexió establerta")
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
        Log.d("P2PManager", "Connectant com a client a $host")
        clientThread = Thread {
            try {
                val socket = Socket()
                socket.connect(InetSocketAddress(host, 8988), 10000)
                Log.d("P2PManager", "Connectat com a client")
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
                    Log.d("P2PManager", "Missatge rebut: $line")
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
                Log.d("P2PManager", "Enviant missatge: $message")
                PrintWriter(output, true).println(message)
            }
        } catch (e: Exception) {
            Log.e("P2PManager", "Error enviant missatge", e)
        }
    }

    fun sendDeviceInfo() {
        val deviceInfo = "DEVICE_INFO:$username"
        sendMessage(deviceInfo)
        Log.d("P2PManager", "Enviat informació del dispositiu: $username")
    }

    override fun onChannelDisconnected() {
        Log.d("P2PManager", "Canal P2P desconnectat")
        listener.onP2PStatusChanged("⚠️ Canal P2P desconnectat")
    }

    override fun onConnectionInfoAvailable(info: WifiP2pInfo) {
        Log.d("P2PManager", "Informació de connexió disponible")
        if (info.groupFormed) {
            Log.d("P2PManager", "S'ha format un grup. És AP: ${info.isGroupOwner}")
            if (info.isGroupOwner) {
                Log.d("P2PManager", "Sóc el propietari del grup (AP)")
                listener.onP2PStatusChanged("🔥 AP actiu. IP: ${info.groupOwnerAddress}")
                startServer()
                sendDeviceInfo()
            } else {
                Log.d("P2PManager", "Sóc un client connectat")
                listener.onP2PStatusChanged("🔗 Client connectat")
                connectAsClient(info.groupOwnerAddress.hostAddress)
            }
        }
    }

    override fun onPeersAvailable(peers: WifiP2pDeviceList) {
        Log.d("P2PManager", "Dispositius propers disponibles: ${peers.deviceList.size}")
        val deviceList = peers.deviceList.toList()
        
        // Notifica els dispositius disponibles
        listener.onPeerListUpdated(deviceList)
        
        // Per cada dispositiu, enviem una sol·licitud d'informació
        for (device in deviceList) {
            Log.d("P2PManager", "Dispositiu descobert: ${device.deviceName} (${device.deviceAddress})")
            listener.onDeviceDiscovered(device, "Desconegut")
        }
        
        // Forçar actualització de la llista de dispositius
        Handler(Looper.getMainLooper()).postDelayed({
            manager.requestPeers(channel, this@P2PManager)
        }, 500)
    }

    override fun onGroupInfoAvailable(group: WifiP2pGroup?) {
        Log.d("P2PManager", "Informació del grup disponible")
        listener.onGroupInfoAvailable(group)
        
        // Notifica els canvis en els clients connectats
        group?.clientList?.forEach { device ->
            Log.d("P2PManager", "Client connectat: ${device.deviceName}")
            listener.onDeviceStatusChanged(device, "Connectat")
        }
    }
}