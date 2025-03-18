package com.iqoid.pairgoth

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.TableLayout
import android.widget.TableRow
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.gson.Gson
import com.iqoid.pairgoth.client.model.ErrorResponse
import com.iqoid.pairgoth.client.model.Game
import com.iqoid.pairgoth.client.model.GamesResponse
import com.iqoid.pairgoth.client.model.Player
import com.iqoid.pairgoth.client.model.Player.Companion.formatRank
import com.iqoid.pairgoth.client.model.TournamentDetails
import com.iqoid.pairgoth.client.network.NetworkManager
import kotlinx.coroutines.launch
import retrofit2.Response

class PairingFragment : Fragment() {

    private lateinit var roundSpinner: Spinner
    private lateinit var pairingTable: TableLayout
    private var tournamentId: String = "1"
    private var tournamentDetails: TournamentDetails? = null
    private var players: List<Player> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_pairing, container, false)
        roundSpinner = view.findViewById(R.id.roundSpinner)
        pairingTable = view.findViewById(R.id.pairingTable)

        // Get the tournament ID from the arguments
        val tournamentId = arguments?.getString(InformationFragment.TOURNAMENT_ID_EXTRA)

        if (tournamentId == null) {
            Log.e("PairingFragment", "Tournament ID not found in arguments")
            // Handle the error, e.g., show an error message or navigate back
        } else {
            fetchTournamentDetails(tournamentId)
        }

        return view
    }

    private fun fetchTournamentDetails(tournamentId: String) {
        lifecycleScope.launch {
            try {
                val response = NetworkManager.pairGothApiService.getTournament(tournamentId)
                if (response.isSuccessful) {
                    tournamentDetails = response.body()
                    if (tournamentDetails != null) {
                        fetchParticipants(tournamentId)
                        setupRoundSpinner()
                    } else {
                        Log.e("PairingFragment", "Error: The response body is null")
                    }
                } else {
                    Log.e("PairingFragment", "Error fetching tournament details: ${response.errorBody()}")
                }
            } catch (e: Exception) {
                Log.e("PairingFragment", "Error fetching tournament details", e)
            }
        }
    }

    private fun fetchParticipants(tournamentId: String) {
        lifecycleScope.launch {
            try {
                val response = NetworkManager.pairGothApiService.getParticipants(tournamentId)
                if (response.isSuccessful) {
                    players = response.body() ?: emptyList()
                } else {
                    Log.e("PairingFragment", "Error fetching participants: ${response.errorBody()}")
                }
            } catch (e: Exception) {
                Log.e("PairingFragment", "Error fetching participants", e)
            }
        }
    }

    private fun setupRoundSpinner() {
        val rounds = (1..(tournamentDetails?.rounds ?: 0)).toList()
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, rounds)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        roundSpinner.adapter = adapter

        roundSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                val selectedRound = rounds[position]
                fetchPairings(tournamentId, selectedRound)
            }

            override fun onNothingSelected(parent: AdapterView<*>) {
                // Do nothing
            }
        }
    }

    private fun fetchPairings(tournamentId: String, round: Int) {
        lifecycleScope.launch {
            try {
                val response = NetworkManager.pairGothApiService.getPairing(tournamentId, round)
                if (response.isSuccessful) {
                    val pairings = response.body()?.games
                    if (pairings != null) {
                        displayPairings(pairings)
                    } else {
                        Log.e("PairingFragment", "Error: The response body is null")
                    }
                } else {
                    displayError(response)
                }
            } catch (e: Exception) {
                Log.e("PairingFragment", "Error fetching pairings", e)
            }
        }
    }

    private fun displayPairings(pairings: List<Game>) {
        pairingTable.removeAllViews()

        // Table Header
        val headerRow = TableRow(context)
        val headers = listOf("#", "White Player", "Rank", "Rank", "Black Player", "Hd")
        for (header in headers) {
            val headerTextView = TextView(context)
            headerTextView.text = header
            headerTextView.setPadding(8, 8, 8, 8)
            headerRow.addView(headerTextView)
        }
        pairingTable.addView(headerRow)

        // Table Rows
        for (pairing in pairings) {
            val row = TableRow(context)

            // Table Number
            val tableTextView = TextView(context)
            tableTextView.text = pairing.t.toString()
            tableTextView.setPadding(8, 8, 8, 8)
            row.addView(tableTextView)

            // White Player
            val whitePlayer = players.find { it.id == pairing.w }
            val whitePlayerTextView = TextView(context)
            whitePlayerTextView.text = if(whitePlayer != null) {
                "${whitePlayer.firstname} ${whitePlayer.name}"
            } else {
                "BYE"
            }
            whitePlayerTextView.setPadding(8, 8, 8, 8)
            row.addView(whitePlayerTextView)

            // White Rank
            val whiteRankTextView = TextView(context)
            whiteRankTextView.text = formatRank(whitePlayer?.rank)
            whiteRankTextView.setPadding(8, 8, 8, 8)
            row.addView(whiteRankTextView)

            // Black Rank
            val blackPlayer = players.find { it.id == pairing.b }
            val blackRankTextView = TextView(context)
            blackRankTextView.text = formatRank(blackPlayer?.rank)
            blackRankTextView.setPadding(8, 8, 8, 8)
            row.addView(blackRankTextView)

            // Black Player
            val blackPlayerTextView = TextView(context)
            blackPlayerTextView.text = if(blackPlayer != null) {
                "${blackPlayer.firstname} ${blackPlayer.name}"
            } else {
                "BYE"
            }
            blackPlayerTextView.setPadding(8, 8, 8, 8)
            row.addView(blackPlayerTextView)

            // Handicap
            val handicapTextView = TextView(context)
            handicapTextView.text = if (pairing.h == 0) {
                ""
            } else {
                getString(R.string.handicap_format, pairing.h)
            }
            handicapTextView.setPadding(8, 8, 8, 8)
            row.addView(handicapTextView)

            pairingTable.addView(row)
        }
    }

    private fun displayError(response: Response<GamesResponse>) {
        Log.e("PairingFragment", "Error fetching pairings: ${response.errorBody()}")
        // clean up the table
        pairingTable.removeAllViews()

        // display error
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
        Log.e("PairingFragment", "Display Pairing Error: $errorMessage")

        Toast.makeText(context, errorMessage, Toast.LENGTH_SHORT).show()
    }
}