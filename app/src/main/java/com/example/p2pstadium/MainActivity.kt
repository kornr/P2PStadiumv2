package com.example.p2pstadium

import android.Manifest
import android.app.AlertDialog
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.util.Random

class MainActivity : AppCompatActivity(), NetworkManager.Listener {

    companion object {
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1
    }

    private lateinit var statusText: TextView
    private lateinit var modeText: TextView
    private lateinit var peerList: ListView
    private lateinit var messageInput: EditText
    private lateinit var sendButton: Button
    private lateinit var messageLog: TextView
    private lateinit var clientList: ListView
    private lateinit var networkManager: NetworkManager
    private lateinit var radioAp: RadioButton
    private lateinit var radioClient: RadioButton
    private lateinit var usernameInput: EditText
    private lateinit var saveUsernameButton: Button
    private lateinit var timerText: TextView
    private lateinit var restartButton: Button
    private lateinit var refreshButton: Button
    private lateinit var forceDiscoverButton: Button
    private lateinit var logConsole: TextView
    private lateinit var configureNetworkButton: Button
    private lateinit var ipAddress: EditText
    private lateinit var subnetMask: EditText
    private lateinit var ssid: EditText
    private lateinit var topologyButton: Button
    private lateinit var topologyView: TextView
    private lateinit var deployButton: Button

    private var peers = mutableListOf<NetworkManager.DeviceInfo>()
    private val peerAdapter: ArrayAdapter<NetworkManager.DeviceInfo> by lazy {
        ArrayAdapter(this, android.R.layout.simple_list_item_1, peers)
    }

    private val clientData = mutableListOf<String>()
    private val clientListAdapter: ArrayAdapter<String> by lazy {
        ArrayAdapter(this, android.R.layout.simple_list_item_1, clientData)
    }

    private var username = "Anònim"
    private var acceptedTerms = false
    private var apName = "AP Desconegut"
    private var connectionTimer: CountDownTimer? = null
    private var currentApCount = 0
    private val MAX_CLIENTS_PER_AP = 2
    private var isTorre1 = false
    private val deviceUsernames = mutableMapOf<String, String>()
    private val deviceStatus = mutableMapOf<String, String>()
    private val devicePositions = mutableMapOf<String, String>()
    private var currentLocation: Location? = null
    private var locationManager: LocationManager? = null
    private var networkDeployed = false
    private var deploymentTimer: CountDownTimer? = null

    // Per actualitzar la llista periòdicament
    private var refreshHandler = Handler(Looper.getMainLooper())
    private val refreshRunnable = object : Runnable {
        override fun run() {
            updateDeviceList()
            refreshHandler.postDelayed(this, 2000) // Actualitza cada 2 segons
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Sol·licita permís de localització
        checkLocationPermission()

        // Inicialitzem totes les vistes
        radioAp = findViewById(R.id.radioAp)
        radioClient = findViewById(R.id.radioClient)
        statusText = findViewById(R.id.statusText)
        modeText = findViewById(R.id.modeText)
        peerList = findViewById(R.id.peerList)
        messageInput = findViewById(R.id.messageInput)
        sendButton = findViewById(R.id.sendButton)
        messageLog = findViewById(R.id.messageLog)
        clientList = findViewById(R.id.clientList)
        usernameInput = findViewById(R.id.usernameInput)
        saveUsernameButton = findViewById(R.id.saveUsernameButton)
        timerText = findViewById(R.id.timerText)
        restartButton = findViewById(R.id.restartButton)
        refreshButton = findViewById(R.id.refreshButton)
        forceDiscoverButton = findViewById(R.id.forceDiscoverButton)
        logConsole = findViewById(R.id.logConsole)
        configureNetworkButton = findViewById(R.id.configureNetworkButton)
        ipAddress = findViewById(R.id.ipAddress)
        subnetMask = findViewById(R.id.subnetMask)
        ssid = findViewById(R.id.ssid)
        topologyButton = findViewById(R.id.topologyButton)
        topologyView = findViewById(R.id.topologyView)
        deployButton = findViewById(R.id.deployButton)

        peerList.adapter = peerAdapter
        clientList.adapter = clientListAdapter

        // Valors per defecte
        ipAddress.setText("192.168.4.1")
        subnetMask.setText("255.255.255.0")
        ssid.setText("P2PStadium")

        val prefs = getSharedPreferences("P2P_PREFS", Context.MODE_PRIVATE)
        acceptedTerms = prefs.getBoolean("terms_accepted", false)
        username = prefs.getString("username", "Anònim") ?: "Anònim"

        if (!acceptedTerms) {
            showTermsDialog()
        } else {
            if (username == "Anònim") {
                usernameInput.visibility = View.VISIBLE
                saveUsernameButton.visibility = View.VISIBLE
                usernameInput.setText("")
                usernameInput.setOnEditorActionListener { _, actionId, _ ->
                    if (actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_ACTION_UNSPECIFIED) {
                        saveUsername()
                        true
                    } else {
                        false
                    }
                }
            } else {
                isTorre1 = (username == "torre1")
                initNetwork()
            }
        }

        // Configura els listeners dels botons de mode
        radioAp.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                usernameInput.visibility = View.VISIBLE
                saveUsernameButton.visibility = View.VISIBLE
                usernameInput.hint = "Nom per AP (ex: torre1)"
                usernameInput.setText(username.takeIf { it != "Anònim" } ?: "")
                usernameInput.requestFocus()
                usernameInput.setOnEditorActionListener { _, actionId, _ ->
                    if (actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_ACTION_UNSPECIFIED) {
                        saveUsername()
                        true
                    } else {
                        false
                    }
                }
                updateRefreshButtonVisibility()
            }
        }

        radioClient.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                usernameInput.visibility = View.VISIBLE
                saveUsernameButton.visibility = View.VISIBLE
                usernameInput.hint = "Nom d'usuari"
                usernameInput.setText(username.takeIf { it != "Anònim" } ?: "")
                usernameInput.requestFocus()
                usernameInput.setOnEditorActionListener { _, actionId, _ ->
                    if (actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_ACTION_UNSPECIFIED) {
                        saveUsername()
                        true
                    } else {
                        false
                    }
                }
                updateRefreshButtonVisibility()
            }
        }

        saveUsernameButton.setOnClickListener {
            saveUsername()
        }

        restartButton.setOnClickListener {
            showPasswordDialog()
        }

        refreshButton.setOnClickListener {
            if (radioAp.isChecked) {
                statusText.text = "Cercant dispositius propers..."
                networkManager.discoverPeers()
                addLogMessage("Cerca de dispositius iniciada manualment")
            }
        }

        forceDiscoverButton.setOnClickListener {
            if (radioAp.isChecked) {
                statusText.text = "Forçant cerca de dispositius propers..."
                networkManager.forceDiscoverPeers()
                addLogMessage("Cerca de dispositius forçada")
            }
        }

        configureNetworkButton.setOnClickListener {
            configureNetwork()
        }

        topologyButton.setOnClickListener {
            updateTopologyView()
        }

        deployButton.setOnClickListener {
            startNetworkDeployment()
        }

        sendButton.setOnClickListener {
            val msg = messageInput.text.toString().trim()
            if (msg.isNotEmpty()) {
                val gps = getCurrentPosition()
                val message = "$username: $msg | $gps"
                networkManager.sendMessage(message)
                broadcastMessageToAll("$username: $msg | $gps")
                appendMessage("Me: $message")
                messageInput.text.clear()
            }
        }

        peerList.onItemClickListener = AdapterView.OnItemClickListener { _, _, position, _ ->
            networkManager.connectToDevice(peers[position])
        }
    }

    private fun checkLocationPermission(): Boolean {
        return if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), LOCATION_PERMISSION_REQUEST_CODE)
            false
        } else {
            true
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                addLogMessage("Permís de localització concedit")
                initLocationSystem()
            } else {
                addLogMessage("Permís de localització denegat")
                Toast.makeText(this, "El permís de localització és necessari", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun initLocationSystem() {
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val locationListener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                currentLocation = location
                addLogMessage("Posició actualitzada: ${location.latitude}, ${location.longitude}")
                sendPositionToAll()
            }

            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {}
        }

        try {
            locationManager?.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                5000,
                10f,
                locationListener
            )
            addLogMessage("Sistema de localització iniciat")
        } catch (e: SecurityException) {
            addLogMessage("Error d'accés a la localització: ${e.message}")
        }
    }

    private fun getCurrentPosition(): String {
        return if (currentLocation != null) {
            "GPS: ${currentLocation?.latitude}, ${currentLocation?.longitude}"
        } else {
            "GPS: 0.0, 0.0"
        }
    }

    private fun sendPositionToAll() {
        val position = getCurrentPosition()
        networkManager.sendPosition(position)
        addLogMessage("Posició enviada: $position")
    }

    private fun broadcastMessageToAll(message: String) {
        networkManager.broadcastMessage(message)
        addLogMessage("Missatge difós: $message")
    }

    override fun onResume() {
        super.onResume()
        // Inicia l'actualització periòdica
        refreshHandler.post(refreshRunnable)
    }

    override fun onPause() {
        super.onPause()
        // Atura l'actualització periòdica
        refreshHandler.removeCallbacks(refreshRunnable)
    }

    private fun showPasswordDialog() {
        val passwordInput = EditText(this).apply {
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        
        val passwordDialog = AlertDialog.Builder(this)
            .setTitle("Reiniciar xarxa")
            .setMessage("Introdueix la contrasenya:")
            .setView(passwordInput)
            .setPositiveButton("Acceptar") { _, _ ->
                val password = passwordInput.text.toString()
                if (password == "torre1") {
                    addLogMessage("Reinici de xarxa iniciat per contrasenya")
                    restartNetworkProcess()
                } else {
                    Toast.makeText(this, "Contrasenya incorrecta", Toast.LENGTH_SHORT).show()
                    addLogMessage("Intent de reinici amb contrasenya incorrecta")
                }
            }
            .setNegativeButton("Cancel·lar", null)
            .create()
        
        passwordDialog.show()
    }

    private fun saveUsername() {
        val name = usernameInput.text.toString().trim()
        if (name.isNotEmpty()) {
            username = name
            isTorre1 = (name == "torre1")
            getSharedPreferences("P2P_PREFS", Context.MODE_PRIVATE).edit()
                .putString("username", username)
                .putBoolean("terms_accepted", true)
                .apply()
            usernameInput.visibility = View.GONE
            saveUsernameButton.visibility = View.GONE
            initNetwork()
        } else {
            Toast.makeText(this, "El nom no pot estar buit", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showTermsDialog() {
        val termsText = "TERMES I CONDICIONS D'ÚS\n\n" +
                "1. Aquesta aplicació utilitza xarxes Wi-Fi per establir connexions directes entre dispositius.\n" +
                "2. L'ús de l'aplicació és sota la vostra pròpia responsabilitat.\n" +
                "3. El desenvolupador no es fa responsable de cap dany, pèrdua de dades o problema de xarxa.\n" +
                "4. Els dispositius han d'estar propers per a una connexió efectiva.\n" +
                "5. L'aplicació no garanteix la privadesa total de les dades compartides.\n" +
                "6. L'usuari és responsable de les dades que comparteix a través de l'aplicació.\n" +
                "7. Aquesta aplicació no és una eina mèdica ni de seguretat.\n" +
                "8. En cas de problemes, contacteu amb el suport tècnic.\n" +
                "9. El desenvolupador es reserva el dret de modificar aquest avís legal.\n" +
                "10. L'ús continuat de l'aplicació implica l'acceptació de tots aquests termes.\n\n" +
                "AVÍS IMPORTANT:\n" +
                "Aquesta aplicació és per ús en entorns controlats i no substitueix les mesures de seguretat estàndard.\n" +
                "No utilitzeu aquesta aplicació en situacions crítiques o de perill.\n\n" +
                "En acceptar aquest avís, declareu que enteneu i accepteu tots els termes i condicions."

        val signatureView = SignatureView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                200
            )
        }

        val textView = TextView(this).apply {
            text = termsText
            textSize = 14f
            setTextIsSelectable(true)
        }

        val scrollView = ScrollView(this).apply {
            addView(textView)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                400
            )
        }

        val button = Button(this).apply {
            text = "Acceptar i Signar"
            setPadding(0, 16, 0, 0)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val dialogLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)
            addView(scrollView)
            addView(signatureView)
            addView(button)
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle("Termes i condicions")
            .setView(dialogLayout)
            .setCancelable(false)
            .create()

        button.setOnClickListener {
            if (signatureView.isSigned) {
                acceptedTerms = true
                getSharedPreferences("P2P_PREFS", Context.MODE_PRIVATE).edit()
                    .putBoolean("terms_accepted", true)
                    .apply()

                dialog.dismiss()

                usernameInput.visibility = View.VISIBLE
                saveUsernameButton.visibility = View.VISIBLE
                usernameInput.setText("")
                usernameInput.requestFocus()
                usernameInput.setOnEditorActionListener { _, actionId, _ ->
                    if (actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_ACTION_UNSPECIFIED) {
                        saveUsername()
                        true
                    } else {
                        false
                    }
                }
            } else {
                Toast.makeText(this, "Cal signar per acceptar", Toast.LENGTH_SHORT).show()
            }
        }

        dialog.show()
    }

    private fun initNetwork() {
        startConnectionTimer(30000) // 30 segons de temps per a la primera connexió
        networkManager = NetworkManager(this, this, username)

        val prefs = getSharedPreferences("P2P_PREFS", Context.MODE_PRIVATE)
        val lastMode = prefs.getString("mode", "ap") ?: "ap"

        if (lastMode == "client") {
            radioClient.isChecked = true
            modeText.text = "Mode: Client"
            networkManager.startClient()
        } else {
            radioAp.isChecked = true
            modeText.text = "Mode: AP"
            networkManager.createNetwork(
                ipAddress.text.toString(),
                subnetMask.text.toString(),
                ssid.text.toString()
            )
        }

        radioAp.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                networkManager.stop()
                networkManager.createNetwork(
                    ipAddress.text.toString(),
                    subnetMask.text.toString(),
                    ssid.text.toString()
                )
                modeText.text = "Mode: AP"
                prefs.edit().putString("mode", "ap").apply()
                updateRefreshButtonVisibility()
            }
        }

        radioClient.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                networkManager.stop()
                networkManager.startClient()
                modeText.text = "Mode: Client"
                prefs.edit().putString("mode", "client").apply()
                updateRefreshButtonVisibility()
            }
        }
    }

    private fun startConnectionTimer(duration: Long) {
        connectionTimer?.cancel()
        connectionTimer = object : CountDownTimer(duration, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val seconds = millisUntilFinished / 1000
                timerText.text = "Temps restant: $seconds s"
            }

            override fun onFinish() {
                timerText.text = "Temps esgotat! Reiniciant..."
                restartNetworkProcess()
            }
        }.start()
    }

    private fun restartNetworkProcess() {
        networkManager.stop()
        currentApCount = 0
        clientData.clear()
        clientListAdapter.notifyDataSetChanged()
        modeText.text = "Mode: No definit"
        statusText.text = "Estat: Reiniciant..."
        addLogMessage("Reinici de xarxa iniciat")
        initNetwork()
    }

    private fun startNetworkDeployment() {
        if (networkDeployed) {
            addLogMessage("La xarxa ja està desplegada")
            return
        }

        networkDeployed = true
        addLogMessage("Iniciant desplegament de xarxa amb paritat 1:2")

        // Forcem la cerca de dispositius
        networkManager.forceDiscoverPeers()

        // Iniciem un temporitzador per al desplegament
        deploymentTimer = object : CountDownTimer(60000, 2000) {
            override fun onTick(millisUntilFinished: Long) {
                // Revisem la xarxa cada 2 segons
                val currentClientCount = clientData.size
                val currentApCount = this@MainActivity.currentApCount + 1 // Inclou l'AP principal

                addLogMessage("Estat del desplegament: $currentClientCount clients, $currentApCount APs")

                // Comprovem si podem crear un nou AP
                if (currentClientCount >= MAX_CLIENTS_PER_AP * currentApCount) {
                    // Cal crear un nou AP
                    addLogMessage("S'ha arribat al límit de clients. Iniciant creació d'un nou AP")
                    startNewApSelection()
                }
            }

            override fun onFinish() {
                addLogMessage("Temps d'exploració esgotat. Finalitzant desplegament.")
                finalizeNetworkDeployment()
            }
        }.start()
    }

    private fun finalizeNetworkDeployment() {
        networkDeployed = false
        deploymentTimer = null
        addLogMessage("Desplegament de xarxa finalitzat")
        Toast.makeText(this, "Desplegament finalitzat", Toast.LENGTH_SHORT).show()
    }

    private fun appendMessage(msg: String) {
        messageLog.append("\n$msg")
        messageLog.post { messageLog.scrollTo(0, messageLog.bottom) }
    }

    override fun onNetworkStatusChanged(status: String) {
        statusText.text = status
        addLogMessage("Estat de xarxa: $status")
    }

    override fun onConnectionInfoAvailable(info: NetworkManager.NetworkInfo) {
        if (info.groupFormed) {
            val isAp = info.isGroupOwner
            val baseMode = if (isAp) "✅ AP (Grup Owner)" else "🔵 Client"
            modeText.text = baseMode
            addLogMessage("S'ha format un grup. És AP: $isAp")

            if (isAp) {
                statusText.text = "🔥 AP actiu. IP: ${info.groupOwnerAddress}"
                apName = info.groupOwnerAddress
                networkManager.startServer()
                networkManager.sendDeviceInfo()
                addLogMessage("AP actiu. IP: ${info.groupOwnerAddress}")
                sendPositionToAll() // Enviar la posició de l'AP
            } else {
                statusText.text = "🔗 Connectat a AP. IP: ${info.groupOwnerAddress}"
                apName = info.groupOwnerAddress
                val gps = getCurrentPosition()
                networkManager.sendMessage("CLIENT:$username:$gps")
                addLogMessage("Connectat a AP. IP: ${info.groupOwnerAddress}")
            }
        }
        updateRefreshButtonVisibility()
    }

    override fun onPeerListUpdated(peers: List<NetworkManager.DeviceInfo>) {
        this.peers.clear()
        this.peers.addAll(peers)
        peerAdapter.notifyDataSetChanged()
        addLogMessage("Dispositius propers actualitzats: ${peers.size} dispositius")

        // Si som un client i no estem connectats, cerca "torre1"
        if (radioClient.isChecked && !isConnectedToTorre1()) {
            val torre1Device = findTorre1Device()
            if (torre1Device != null) {
                networkManager.connectToDevice(torre1Device)
                addLogMessage("Connectant a torre1: ${torre1Device.deviceName}")
            }
        }
        updateRefreshButtonVisibility()
    }

    override fun onGroupInfoAvailable(group: NetworkManager.NetworkGroup?) {
        val count = group?.clientList?.size ?: 0
        val baseMode = if (radioAp.isChecked) "✅ AP" else "🔵 Client"
        modeText.text = "$baseMode ($count clients)"
        addLogMessage("Informació del grup actualitzada. Clients: $count")

        // Comprova si s'ha arribat al límit de clients
        if (count >= MAX_CLIENTS_PER_AP && radioAp.isChecked) {
            timerText.text = "Cercant nou AP per expansió..."
            startNewApSelection()
            addLogMessage("S'ha arribat al límit de clients. Iniciant cerca de nou AP")
        }

        clientData.clear()
        group?.clientList?.forEach { device ->
            val deviceName = deviceUsernames[device.deviceAddress] ?: device.deviceName
            val status = deviceStatus[device.deviceAddress] ?: "Desconegut"
            clientData.add("$deviceName ($status)")
        }
        clientListAdapter.notifyDataSetChanged()
        updateRefreshButtonVisibility()
    }

    override fun onDeviceDiscovered(device: NetworkManager.DeviceInfo, username: String) {
        deviceUsernames[device.deviceAddress] = username
        deviceStatus[device.deviceAddress] = "Disponible"
        updateDeviceList()
        addLogMessage("Dispositiu descobert: ${device.deviceName} (nom d'usuari: $username)")
    }

    override fun onDeviceStatusChanged(device: NetworkManager.DeviceInfo, status: String) {
        deviceStatus[device.deviceAddress] = status
        updateDeviceList()
        addLogMessage("Estat del dispositiu actualitzat: ${device.deviceName} - $status")
    }

    override fun onMessageReceived(message: String) {
        if (message.startsWith("CLIENT:")) {
            val parts = message.split(":", limit = 3)
            if (parts.size == 3) {
                val name = parts[1]
                val gps = parts[2]
                devicePositions[parts[1]] = gps
                clientData.add("$name → $gps")
                clientListAdapter.notifyDataSetChanged()
                addLogMessage("Missatge de client: $name → $gps")
            }
        } else if (message.startsWith("POSITION:")) {
            val parts = message.split(":", limit = 3)
            if (parts.size == 3) {
                val name = parts[1]
                val position = parts[2]
                devicePositions[name] = position
                addLogMessage("Posició rebuda: $name → $position")
            }
        } else if (message.startsWith("DEVICE_INFO:")) {
            val username = message.substringAfter("DEVICE_INFO:", "Desconegut")
            addLogMessage("Informació del dispositiu: $username")
        } else {
            appendMessage("Peer: $message")
            addLogMessage("Missatge del peer: $message")
        }
    }

    private fun updateDeviceList() {
        peerAdapter.notifyDataSetChanged()
        clientListAdapter.notifyDataSetChanged()
    }

    private fun findTorre1Device(): NetworkManager.DeviceInfo? {
        return peers.firstOrNull { device ->
            deviceUsernames[device.deviceAddress] == "torre1"
        }
    }

    private fun isConnectedToTorre1(): Boolean {
        return deviceUsernames.containsValue("torre1")
    }

    private fun startNewApSelection() {
        addLogMessage("Iniciant cerca de nou AP per expansió")
        
        // En una implementació real, aquí s'enviaria un missatge als clients per obtenir les seves coordenades GPS
        // i es triaria el client amb les coordenades més allunyades per ser el nou AP

        // Simulació: després de 10 segons, es canvia el mode a AP
        Handler(Looper.getMainLooper()).postDelayed({
            if (radioClient.isChecked) {
                radioAp.isChecked = true
                modeText.text = "Mode: AP (expansió)"
                currentApCount++
                Toast.makeText(this, "Nou AP seleccionat! (Simulació)", Toast.LENGTH_SHORT).show()
                addLogMessage("Nou AP seleccionat: mode canviat a AP")
            }
        }, 10000)
    }

    override fun onDestroy() {
        connectionTimer?.cancel()
        networkManager.stop()
        deploymentTimer?.cancel()
        super.onDestroy()
    }

    private fun updateRefreshButtonVisibility() {
        refreshButton.visibility = if (radioAp.isChecked) View.VISIBLE else View.GONE
        forceDiscoverButton.visibility = if (radioAp.isChecked) View.VISIBLE else View.GONE
    }

    private fun addLogMessage(message: String) {
        val timestamp = android.text.format.DateFormat.format("HH:mm:ss", System.currentTimeMillis())
        val logEntry = "[$timestamp] $message"
        
        // Afegeix el missatge a la consola
        logConsole.append("\n$logEntry")
        
        // Desplaça la vista al final
        logConsole.post {
            logConsole.scrollTo(0, logConsole.bottom)
        }
        
        // Registra el missatge als logs de sistema també
        Log.d("P2PStadium", message)
    }

    private fun configureNetwork() {
        val ip = ipAddress.text.toString()
        val mask = subnetMask.text.toString()
        val networkSsid = ssid.text.toString()
        
        addLogMessage("Configurant xarxa amb: IP=$ip, Màscara=$mask, SSID=$networkSsid")
        
        // Reinicia la xarxa amb la nova configuració
        networkManager.stop()
        networkManager.createNetwork(ip, mask, networkSsid)
        
        Toast.makeText(this, "Xarxa configurada", Toast.LENGTH_SHORT).show()
    }

    private fun updateTopologyView() {
        val topology = buildTopologyString()
        topologyView.text = topology
        topologyView.visibility = if (topologyView.visibility == View.VISIBLE) View.GONE else View.VISIBLE
    }

    private fun buildTopologyString(): String {
        val sb = StringBuilder()
        sb.append("Topologia de la xarxa:\n\n")
        
        // Mostra l'AP principal
        sb.append("🔥 AP Principal (tu)\n")
        sb.append("  - IP: $apName\n")
        sb.append("  - Posició: ${getCurrentPosition()}\n\n")
        
        // Mostra els clients connectats
        sb.append("👥 Clients connectats:\n")
        clientData.forEach { client ->
            sb.append("  - $client\n")
        }
        
        // Mostra la informació de la xarxa
        sb.append("\n📊 Estat de la xarxa:\n")
        sb.append("  - Mode: ${modeText.text}\n")
        sb.append("  - Clients: ${clientData.size}\n")
        sb.append("  - Límit per AP: $MAX_CLIENTS_PER_AP\n")
        sb.append("  - Nombre d'APs: ${currentApCount + 1}\n")
        
        // Si hi ha més de 2 clients, avisa que s'està iniciant la expansió
        if (clientData.size > MAX_CLIENTS_PER_AP) {
            sb.append("  ⚠️ S'està iniciant la creació d'un nou AP\n")
        }
        
        return sb.toString()
    }

    class SignatureView(context: Context) : View(context) {
        private val path = Path()
        private val paint = Paint().apply {
            isAntiAlias = true
            color = Color.BLACK
            style = Paint.Style.STROKE
            strokeJoin = Paint.Join.ROUND
            strokeCap = Paint.Cap.ROUND
            strokeWidth = 8f
        }
        var isSigned = false
            private set

        override fun onDraw(canvas: Canvas) {
            canvas.drawColor(Color.WHITE)
            canvas.drawPath(path, paint)
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            val x = event.x
            val y = event.y
            isSigned = true
            when (event.action) {
                MotionEvent.ACTION_DOWN -> path.moveTo(x, y)
                MotionEvent.ACTION_MOVE -> {
                    path.lineTo(x, y)
                    invalidate()
                }
                else -> return false
            }
            return true
        }
    }
}