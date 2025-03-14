package com.iqoid.pairgoth

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.gson.Gson
import com.iqoid.pairgoth.client.model.ErrorResponse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.iqoid.pairgoth.client.model.Player
import com.iqoid.pairgoth.client.model.Search
import com.iqoid.pairgoth.client.network.NetworkManager

class PlayerSearchActivity : AppCompatActivity() {
    private lateinit var nameEditText: EditText
    private lateinit var searchButton: Button
    private lateinit var playerRecyclerView: RecyclerView
    private lateinit var registerButton: Button
    private lateinit var playerAdapter: PlayerAdapter
    private var players: List<Player> = emptyList()
    private var selectedPlayer: Player? = null
    private lateinit var tournamentId: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player_search)
        tournamentId = intent.getStringExtra("tournamentId") ?: ""
        nameEditText = findViewById(R.id.nameEditText)
        searchButton = findViewById(R.id.searchButton)
        playerRecyclerView = findViewById(R.id.playerRecyclerView)
        registerButton = findViewById(R.id.registerButton)
        registerButton.isEnabled = false

        playerAdapter = PlayerAdapter(players) { player ->
            selectedPlayer = player
            registerButton.isEnabled = true
            playerAdapter.setSelectedPlayer(player)
        }
        playerRecyclerView.adapter = playerAdapter
        playerRecyclerView.layoutManager = LinearLayoutManager(this)

        searchButton.setOnClickListener {
            val name = nameEditText.text.toString()
            val search = Search(needle = name, egf = true, ffg = false)
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val response = NetworkManager.pairGothApiService.searchPlayer(search)
                    if (response.isSuccessful) {
                        val playersResponse = response.body() ?: emptyList()
                        players = playersResponse.mapNotNull { playerResponse ->
                            try {
                                playerResponse.rankAsString = playerResponse.rank
                                val rankInt = Player.parseRank(playerResponse.rank)
                                playerResponse.rank = rankInt.toString()
                                return@mapNotNull playerResponse
                            } catch (e: Exception) {
                                Log.e("PlayerSearchActivity", "Error parsing rank for player ${playerResponse.name}: ${e.message}")
                                // Handle the error appropriately, e.g., skip the player or set a default rank
                                // For now, we'll skip the player
                                null
                            }
                        }
                        runOnUiThread {
                            updatePlayerList(players)
                        }
                    } else {
                        Log.e("PlayerSearchActivity", "Search Error: ${response.errorBody()}")
                        runOnUiThread {
                            Toast.makeText(this@PlayerSearchActivity, "Search Error", Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: Exception) {
                    Log.e("PlayerSearchActivity", "Search Exception: ${e.message}")
                    runOnUiThread {
                        Toast.makeText(this@PlayerSearchActivity, "Search Exception", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        registerButton.setOnClickListener {
            selectedPlayer?.let { player ->
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val response = NetworkManager.pairGothApiService.registerPlayer(tournamentId, player)
                        if (response.isSuccessful) {
                            runOnUiThread {
                                val intent = Intent(this@PlayerSearchActivity, SuccessActivity::class.java)
                                startActivity(intent)
                            }
                        } else {
                            val errorBody = response.errorBody()?.string()
                            val errorMessage = if (errorBody != null) {
                                try {
                                    val errorResponse = Gson().fromJson(errorBody, ErrorResponse::class.java)
                                    errorResponse.error // Extract the error message
                                } catch (e: Exception) {
                                    "Unknown error" // Handle parsing errors
                                }
                            } else {
                                "Unknown error" // Handle null error body
                            }
                            Log.e("PlayerSearchActivity", "Registration Error: $errorMessage")
                            runOnUiThread {
                                Toast.makeText(this@PlayerSearchActivity, "Registration Error: $errorMessage", Toast.LENGTH_SHORT).show()
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("PlayerSearchActivity", "Registration Exception: ${e.message}")
                        runOnUiThread {
                            Toast.makeText(this@PlayerSearchActivity, "Registration Exception", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } ?: run {
                Toast.makeText(this, "Please select a player", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updatePlayerList(players: List<Player>) {
        if (players.isNotEmpty()) {
            playerAdapter.updatePlayers(players)
            playerRecyclerView.visibility = View.VISIBLE
        } else {
            playerRecyclerView.visibility = View.GONE
            Toast.makeText(this, "Player not found", Toast.LENGTH_SHORT).show()
        }
        registerButton.isEnabled = false
        selectedPlayer = null
    }
}