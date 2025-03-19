package com.iqoid.pairgoth

import android.graphics.Color
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
import com.iqoid.pairgoth.client.model.Player
import com.iqoid.pairgoth.client.model.Player.Companion.formatRank
import com.iqoid.pairgoth.client.model.TournamentDetails
import com.iqoid.pairgoth.client.network.NetworkManager
import kotlinx.coroutines.launch
import retrofit2.Response

class ResultsFragment : Fragment() {

    private lateinit var roundSpinner: Spinner
    private lateinit var resultsTable: TableLayout
    private var tournamentId: String = "1"
    private var tournamentDetails: TournamentDetails? = null
    private var players: List<Player> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_results, container, false)
        roundSpinner = view.findViewById(R.id.roundSpinner)
        resultsTable = view.findViewById(R.id.resultsTable)

        // Get the tournament ID from the arguments
        tournamentId = arguments?.getString(InformationFragment.TOURNAMENT_ID_EXTRA)?: "1"

        fetchTournamentDetails(tournamentId)

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
                        Log.e("ResultsFragment", "Error: The response body is null")
                    }
                } else {
                    Log.e("ResultsFragment", "Error fetching tournament details: ${response.errorBody()}")
                }
            } catch (e: Exception) {
                Log.e("ResultsFragment", "Error fetching tournament details", e)
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
                    Log.e("ResultsFragment", "Error fetching participants: ${response.errorBody()}")
                }
            } catch (e: Exception) {
                Log.e("ResultsFragment", "Error fetching participants", e)
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
                fetchResults(tournamentId, selectedRound)
            }

            override fun onNothingSelected(parent: AdapterView<*>) {
                // Do nothing
            }
        }
    }

    private fun fetchResults(tournamentId: String, round: Int) {
        lifecycleScope.launch {
            try {
                val response = NetworkManager.pairGothApiService.getResults(tournamentId, round)
                if (response.isSuccessful) {
                    val results = response.body()
                    if (results != null) {
                        displayResults(results)
                    } else {
                        Log.e("ResultsFragment", "Error: The response body is null")
                    }
                } else {
                    displayError(response)
                }
            } catch (e: Exception) {
                Log.e("ResultsFragment", "Error fetching results", e)
            }
        }
    }

    private fun displayResults(results: List<Game>) {
        resultsTable.removeAllViews()

        // Table Header
        val headerRow = TableRow(context)
        val headers = listOf("#", "White Player", "Rank", "Rank", "Black Player", "Result", "Hd")
        for (header in headers) {
            val headerTextView = TextView(context)
            headerTextView.text = header
            headerTextView.setPadding(8, 8, 8, 8)
            headerRow.addView(headerTextView)
        }
        resultsTable.addView(headerRow)

        // Table Rows
        for (result in results) {
            val row = TableRow(context)

            // Table Number
            val tableTextView = TextView(context)
            tableTextView.text = result.t.toString()
            tableTextView.setPadding(8, 8, 8, 8)
            row.addView(tableTextView)

            // White Player
            val whitePlayer = players.find { it.id == result.w }
            val whitePlayerTextView = TextView(context)
            whitePlayerTextView.text = if(whitePlayer != null) {
                "${whitePlayer.name} ${whitePlayer.firstname}"
            } else {
                "BYE"
            }
            if (result.r == "w") {
                whitePlayerTextView.setTextColor(Color.BLUE)
            } else if (result.r == "b") {
                whitePlayerTextView.setTextColor(Color.parseColor("#8B0000")) // Dark Red
            }
            whitePlayerTextView.setPadding(8, 8, 8, 8)
            row.addView(whitePlayerTextView)

            // White Rank
            val whiteRankTextView = TextView(context)
            whiteRankTextView.text = formatRank(whitePlayer?.rank)
            whiteRankTextView.setPadding(8, 8, 8, 8)
            row.addView(whiteRankTextView)

            // Black Rank
            val blackPlayer = players.find { it.id == result.b }
            val blackRankTextView = TextView(context)
            blackRankTextView.text = formatRank(blackPlayer?.rank)
            blackRankTextView.setPadding(8, 8, 8, 8)
            row.addView(blackRankTextView)

            // Black Player
            val blackPlayerTextView = TextView(context)
            blackPlayerTextView.text = if(blackPlayer != null) {
                "${blackPlayer.name} ${blackPlayer.firstname}"
            } else {
                "BYE"
            }
            if (result.r == "b") {
                blackPlayerTextView.setTextColor(Color.BLUE)
            } else if (result.r == "w") {
                blackPlayerTextView.setTextColor(Color.parseColor("#8B0000")) // Dark Red
            }
            blackPlayerTextView.setPadding(8, 8, 8, 8)
            row.addView(blackPlayerTextView)

            // Result
            val resultTextView = TextView(context)
            resultTextView.text = when (result.r) {
                "w" -> "1-0"
                "b" -> "0-1"
                "=" -> "0.5-0.5"
                "x" -> "x"
                "?" -> "?"
                "#" -> "1-1"
                "0" -> "0-0"

                else -> result.r
            }
            resultTextView.setPadding(8, 8, 8, 8)
            row.addView(resultTextView)

            // Handicap
            val handicapTextView = TextView(context)
            handicapTextView.text = if (result.h == 0) {
                ""
            } else {
                getString(R.string.handicap_format, result.h)
            }
            handicapTextView.setPadding(8, 8, 8, 8)
            row.addView(handicapTextView)

            resultsTable.addView(row)
        }
    }

    private fun displayError(response: Response<List<Game>>) {
        Log.e("ResultsFragment", "Error fetching results: ${response.errorBody()}")
        // clean up the table
        resultsTable.removeAllViews()
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
        Log.e("ResultsFragment", "Display Results Error: $errorMessage")

        Toast.makeText(context, errorMessage, Toast.LENGTH_SHORT).show()
    }
}