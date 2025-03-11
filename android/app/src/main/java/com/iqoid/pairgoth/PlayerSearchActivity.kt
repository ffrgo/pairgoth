package com.iqoid.pairgoth

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.iqoid.pairgoth.client.model.Player
import com.iqoid.pairgoth.client.model.Search
import com.iqoid.pairgoth.client.network.NetworkManager

class PlayerSearchActivity : AppCompatActivity() {
    private lateinit var nameEditText: EditText
    private lateinit var searchButton: Button
    private lateinit var playerNameTextView: TextView
    private lateinit var playerFirstnameTextView: TextView
    private lateinit var playerCountryTextView: TextView
    private lateinit var playerClubTextView: TextView
    private lateinit var playerRankTextView: TextView
    private lateinit var playerRatingTextView: TextView
    private lateinit var registerButton: Button
    private lateinit var player: Player

    private lateinit var tournamentId: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player_search)
        tournamentId = intent.getStringExtra("tournamentId") ?: ""
        nameEditText = findViewById(R.id.nameEditText)
        searchButton = findViewById(R.id.searchButton)
        playerNameTextView = findViewById(R.id.playerNameTextView)
        playerFirstnameTextView = findViewById(R.id.playerFirstnameTextView)
        playerCountryTextView = findViewById(R.id.playerCountryTextView)
        playerClubTextView = findViewById(R.id.playerClubTextView)
        playerRankTextView = findViewById(R.id.playerRankTextView)
        playerRatingTextView = findViewById(R.id.playerRatingTextView)
        registerButton = findViewById(R.id.registerButton)
        registerButton.isEnabled = false

        searchButton.setOnClickListener {
            val name = nameEditText.text.toString()
            val search = Search(needle = name, egf = false, ffg = false)
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val response = NetworkManager.pairGothApiService.searchPlayer(search)
                    if (response.isSuccessful) {
                        if (response.body()!!.isNotEmpty()){
                            player = response.body()!![0]
                            runOnUiThread {
                                updatePlayerInfo(player)
                            }
                        }
                        else {
                            runOnUiThread {
                                playerNameTextView.text = "Player not found"
                                playerFirstnameTextView.text = ""
                                playerCountryTextView.text = ""
                                playerClubTextView.text = ""
                                playerRankTextView.text = ""
                                playerRatingTextView.text = ""
                                registerButton.isEnabled = false
                            }
                        }
                    } else {
                        Log.e("PlayerSearchActivity", "Search Error: ${response.errorBody()}")
                    }
                } catch (e: Exception) {
                    Log.e("PlayerSearchActivity", "Search Exception: ${e.message}")
                }
            }
        }

        registerButton.setOnClickListener {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val response = NetworkManager.pairGothApiService.registerPlayer(tournamentId,player)
                    if (response.isSuccessful) {
                        val registeredPlayer = response.body()
                        runOnUiThread {
                            //navigate to another activity or show a success message
                            val intent = Intent(this@PlayerSearchActivity, SuccessActivity::class.java)
                            startActivity(intent)
                        }
                    } else {
                        Log.e("PlayerSearchActivity", "Registration Error: ${response.errorBody()}")
                    }
                } catch (e: Exception) {
                    Log.e("PlayerSearchActivity", "Registration Exception: ${e.message}")
                }
            }
        }
    }

    private fun updatePlayerInfo(player: Player) {
        playerNameTextView.text = "Name: ${player.name}"
        playerFirstnameTextView.text = "Firstname: ${player.firstname}"
        playerCountryTextView.text = "Country: ${player.country}"
        playerClubTextView.text = "Club: ${player.club}"
        playerRankTextView.text = "Rank: ${player.rank}"
        playerRatingTextView.text = "Rating: ${player.rating}"
        registerButton.isEnabled = true
    }
}
