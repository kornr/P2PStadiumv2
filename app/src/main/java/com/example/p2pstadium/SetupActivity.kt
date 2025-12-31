package com.example.p2pstadium

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.RadioButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class SetupActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_setup)

        val usernameInput = findViewById<EditText>(R.id.usernameInput)
        val radioAp = findViewById<RadioButton>(R.id.radioAp)
        val startButton = findViewById<Button>(R.id.startButton)

        radioAp.isChecked = true

        startButton.setOnClickListener {
            val username = usernameInput.text.toString().trim()
            if (username.isEmpty()) {
                usernameInput.error = "Cal un nom"
                return@setOnClickListener
            }

            val isAp = radioAp.isChecked

            getSharedPreferences("P2P_PREFS", MODE_PRIVATE).edit()
                .putString("username", username)
                .putBoolean("is_ap", isAp)
                .apply()

            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
    }
}
