package com.iqoid.pairgoth

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.ArrayAdapter
import android.widget.ListView
import androidx.appcompat.app.AppCompatActivity
import com.iqoid.pairgoth.client.model.Tournament
import com.iqoid.pairgoth.client.network.NetworkManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class TournamentActivity : AppCompatActivity() {
    private lateinit var tournamentListView: ListView
    private lateinit var tournaments: List<Tournament>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tournament)

        tournamentListView = findViewById(R.id.tournamentListView)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = NetworkManager.pairGothApiService.getTours()
                if (response.isSuccessful) {
                    val tournaments = response.body()
                    // log all the tournaments
                    tournaments?.forEach { (key, value) ->
                        Log.d("TournamentActivity", "Key: $key, Value: $value")
                    }
                    this@TournamentActivity.tournaments = tournaments?.values?.toList() ?: emptyList()
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
            val selectedTournament = tournaments[position]
            val intent = Intent(this, PlayerSearchActivity::class.java).apply {
                putExtra("tournamentName", selectedTournament.name)
            }
            startActivity(intent)
        }
    }

    private fun updateTournamentList() {
        val tournamentNames = tournaments.map { it.name }
        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, tournamentNames)
        tournamentListView.adapter = adapter
    }
}
