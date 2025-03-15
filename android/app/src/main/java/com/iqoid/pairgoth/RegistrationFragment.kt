package com.iqoid.pairgoth

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.gson.Gson
import com.iqoid.pairgoth.client.model.ErrorResponse
import com.iqoid.pairgoth.client.model.Player
import com.iqoid.pairgoth.client.model.Search
import com.iqoid.pairgoth.client.network.NetworkManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class RegistrationFragment : Fragment() {
    private lateinit var nameEditText: EditText
    private lateinit var searchButton: Button
    private lateinit var playerRecyclerView: RecyclerView
    private lateinit var registerButton: Button
    private lateinit var playerAdapter: PlayerAdapter
    private var players: List<Player> = emptyList()
    private var selectedPlayer: Player? = null
    private lateinit var tournamentId: String

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_registration, container, false)
        tournamentId = requireActivity().intent.getStringExtra("tournamentId") ?: ""
        nameEditText = view.findViewById(R.id.nameEditText)
        searchButton = view.findViewById(R.id.searchButton)
        playerRecyclerView = view.findViewById(R.id.playerRecyclerView)
        registerButton = view.findViewById(R.id.registerButton)
        registerButton.isEnabled = false

        playerAdapter = PlayerAdapter(players) { player ->
            selectedPlayer = player
            registerButton.isEnabled = true
            playerAdapter.setSelectedPlayer(player)
        }
        playerRecyclerView.adapter = playerAdapter
        playerRecyclerView.layoutManager = LinearLayoutManager(context)

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
                                Log.e("RegistrationFragment", "Error parsing rank for player ${playerResponse.name}: ${e.message}")
                                // Handle the error appropriately, e.g., skip the player or set a default rank
                                // For now, we'll skip the player
                                null
                            }
                        }
                        requireActivity().runOnUiThread {
                            updatePlayerList(players)
                        }
                    } else {
                        Log.e("RegistrationFragment", "Search Error: ${response.errorBody()}")
                        requireActivity().runOnUiThread {
                            Toast.makeText(context, "Search Error", Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: Exception) {
                    Log.e("RegistrationFragment", "Search Exception: ${e.message}")
                    requireActivity().runOnUiThread {
                        Toast.makeText(context, "Search Exception", Toast.LENGTH_SHORT).show()
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
                            requireActivity().runOnUiThread {
                                val intent = Intent(context, SuccessActivity::class.java)
                                startActivity(intent)
                            }
                        } else {
                            val errorBody = response.errorBody()?.string() // Get the error body as a string
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
                            Log.e("RegistrationFragment", "Registration Error: $errorMessage")
                            requireActivity().runOnUiThread {
                                Toast.makeText(context, "Registration Error: $errorMessage", Toast.LENGTH_SHORT).show()
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("RegistrationFragment", "Registration Exception: ${e.message}")
                        requireActivity().runOnUiThread {
                            Toast.makeText(context, "Registration Exception", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } ?: run {
                Toast.makeText(context, "Please select a player", Toast.LENGTH_SHORT).show()
            }
        }
        return view
    }

    private fun updatePlayerList(players: List<Player>) {
        if (players.isNotEmpty()) {
            playerAdapter.updatePlayers(players)
            playerRecyclerView.visibility = View.VISIBLE
        } else {
            playerRecyclerView.visibility = View.GONE
            Toast.makeText(context, "Player not found", Toast.LENGTH_SHORT).show()
        }
        registerButton.isEnabled = false
        selectedPlayer = null
    }
}