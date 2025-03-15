package com.iqoid.pairgoth

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.widget.ArrayAdapter
import android.widget.ListView
import androidx.appcompat.app.AppCompatActivity
import com.iqoid.pairgoth.client.model.Tournament
import com.iqoid.pairgoth.client.network.NetworkManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.appcompat.widget.Toolbar

class TournamentActivity : AppCompatActivity() {
    private lateinit var tournamentListView: ListView
    private lateinit var tournaments: Map<String, Tournament> // Changed to Map
    private lateinit var toolbar: Toolbar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tournament)

        tournamentListView = findViewById(R.id.tournamentListView)
//        toolbar = findViewById(R.id.toolbar)
//        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = NetworkManager.pairGothApiService.getTours()
                if (response.isSuccessful) {
                    tournaments = response.body() ?: emptyMap() // Store the map directly
                    // log all the tournaments
                    tournaments.forEach { (key, value) ->
                        Log.d("TournamentActivity", "Key: $key, Value: $value")
                    }
                    runOnUiThread {
                        updateTournamentList()
                    }
                } else {
                    Log.e("TournamentActivity", "Error: ${response.errorBody()}")
                }
            } catch (e: Exception) {
                Log.e("TournamentActivity", "Exception: ${e.message}")
            }
        }

        tournamentListView.setOnItemClickListener { _, _, position, _ ->
            val selectedTournamentEntry = tournaments.entries.toList()[position] // Get the selected entry
            val tournamentKey = selectedTournamentEntry.key // Get the key
            val selectedTournament = selectedTournamentEntry.value // Get the tournament object
            val intent = Intent(this, TournamentDetailsActivity::class.java).apply { // Change to TournamentDetailsActivity
                putExtra("tournamentName", selectedTournament.name)
                putExtra("tournamentId", tournamentKey) // Pass the key
            }
            startActivity(intent)
        }
    }

    private fun updateTournamentList() {
        val tournamentNames = tournaments.values.map { it.name } // Get names from values
        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, tournamentNames)
        tournamentListView.adapter = adapter
    }
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                onBackPressedDispatcher.onBackPressed()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}