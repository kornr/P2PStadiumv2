package com.example.p2pstadium

import android.app.AlertDialog
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pGroup
import android.net.wifi.p2p.WifiP2pInfo
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import java.util.Random

class MainActivity : AppCompatActivity(), P2PManager.Listener {

    private lateinit var statusText: TextView
    private lateinit var modeText: TextView
    private lateinit var peerList: ListView
    private lateinit var messageInput: EditText
    private lateinit var sendButton: Button
    private lateinit var messageLog: TextView
    private lateinit var clientList: ListView
    private lateinit var p2pManager: P2PManager
    private lateinit var radioAp: RadioButton
    private lateinit var radioClient: RadioButton
    private lateinit var usernameInput: EditText
    private lateinit var saveUsernameButton: Button

    private var peers = mutableListOf<WifiP2pDevice>()
    private val peerAdapter: ArrayAdapter<WifiP2pDevice> by lazy {
        ArrayAdapter(this, android.R.layout.simple_list_item_1, peers)
    }

    private val clientData = mutableListOf<String>()
    private val clientListAdapter: ArrayAdapter<String> by lazy {
        ArrayAdapter(this, android.R.layout.simple_list_item_1, clientData)
    }

    private var username = "Anònim"
    private var acceptedTerms = false
    private var apName = "AP Desconegut"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

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

        peerList.adapter = peerAdapter
        clientList.adapter = clientListAdapter

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
                initP2P()
            }
        }

        radioAp.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                usernameInput.visibility = View.VISIBLE
                saveUsernameButton.visibility = View.VISIBLE
                usernameInput.hint = "Nom per AP (ex: AP_Joan)"
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
            }
        }

        saveUsernameButton.setOnClickListener {
            saveUsername()
        }
    }

    private fun saveUsername() {
        val name = usernameInput.text.toString().trim()
        if (name.isNotEmpty()) {
            username = name
            getSharedPreferences("P2P_PREFS", Context.MODE_PRIVATE).edit()
                .putString("username", username)
                .putBoolean("terms_accepted", true)
                .apply()
            usernameInput.visibility = View.GONE
            saveUsernameButton.visibility = View.GONE
            initP2P()
        } else {
            Toast.makeText(this, "El nom no pot estar buit", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showTermsDialog() {
        val termsText = "TERMES I CONDICIONS D'ÚS\n\n" +
                "1. Aquesta aplicació utilitza Wi-Fi Direct per establir connexions directes entre dispositius.\n" +
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

    private fun initP2P() {
        p2pManager = P2PManager(this, this)

        val prefs = getSharedPreferences("P2P_PREFS", Context.MODE_PRIVATE)
        val lastMode = prefs.getString("mode", "ap") ?: "ap"

        if (lastMode == "client") {
            radioClient.isChecked = true
            modeText.text = "Mode: Client"
        } else {
            radioAp.isChecked = true
            modeText.text = "Mode: AP"
        }

        radioAp.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                p2pManager.stop()
                p2pManager.createGroup()
                modeText.text = "Mode: AP"
                prefs.edit().putString("mode", "ap").apply()
            }
        }

        radioClient.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                p2pManager.stop()
                p2pManager.start()
                modeText.text = "Mode: Client"
                prefs.edit().putString("mode", "client").apply()
            }
        }

        sendButton.setOnClickListener {
            val msg = messageInput.text.toString().trim()
            if (msg.isNotEmpty()) {
                val simulatedGPS = "GPS: ${Random().nextInt(100)},${Random().nextInt(100)}"
                p2pManager.sendMessage("$username: $msg | $simulatedGPS")
                appendMessage("Me: $msg | $simulatedGPS")
                messageInput.text.clear()
            }
        }

        peerList.onItemClickListener = AdapterView.OnItemClickListener { _, _, position, _ ->
            p2pManager.connectToDevice(peers[position])
        }
    }

    private fun appendMessage(msg: String) {
        messageLog.append("\n$msg")
        messageLog.post { messageLog.scrollTo(0, messageLog.bottom) }
    }

    override fun onP2PStatusChanged(status: String) {
        statusText.text = status
    }

    override fun onConnectionInfoAvailable(info: WifiP2pInfo) {
        if (info.groupFormed) {
            val isAp = info.isGroupOwner
            val baseMode = if (isAp) "✅ AP (Grup Owner)" else "🔵 Client"
            modeText.text = baseMode

            if (isAp) {
                statusText.text = "🔥 AP actiu. IP: ${info.groupOwnerAddress}"
                apName = info.groupOwnerAddress.hostAddress
                p2pManager.startServer()
            } else {
                statusText.text = "🔗 Connectat a AP. IP: ${info.groupOwnerAddress}"
                apName = info.groupOwnerAddress.hostAddress
                val myGps = "GPS: ${Random().nextInt(100)},${Random().nextInt(100)}"
                p2pManager.sendMessage("CLIENT:$username:$myGps")
            }
        }
    }

    override fun onPeerListUpdated(peers: List<WifiP2pDevice>) {
        this.peers.clear()
        this.peers.addAll(peers)
        peerAdapter.notifyDataSetChanged()

        if (peers.isNotEmpty() && radioClient.isChecked) {
            p2pManager.connectToDevice(peers.first())
        }
    }

    override fun onGroupInfoAvailable(group: WifiP2pGroup?) {
    // Obté la llista de clients (segura contra null)
    val deviceList = group?.clientList?.deviceList ?: emptyList()
    val count = deviceList.size
    val baseMode = if (radioAp.isChecked) "✅ AP" else "🔵 Client"
    modeText.text = "$baseMode ($count clients)"

    // Mostra els clients connectats
    clientData.clear()
    deviceList.forEach { device ->
        clientData.add("${device.deviceName} (${device.deviceAddress})")
    }
    clientListAdapter.notifyDataSetChanged()
}

    override fun onMessageReceived(message: String) {
        if (message.startsWith("CLIENT:")) {
            val parts = message.split(":", limit = 3)
            if (parts.size == 3) {
                val name = parts[1]
                val gps = parts[2]
                clientData.add("$name → $gps")
                clientListAdapter.notifyDataSetChanged()
            }
        } else {
            appendMessage("Peer: $message")
        }
    }

    override fun onDestroy() {
        p2pManager.stop()
        super.onDestroy()
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