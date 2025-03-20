package com.iqoid.pairgoth

import android.graphics.Typeface
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
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.gson.Gson
import com.iqoid.pairgoth.client.model.ErrorResponse
import com.iqoid.pairgoth.client.model.Player.Companion.formatRank
import com.iqoid.pairgoth.client.model.Standing
import com.iqoid.pairgoth.client.model.TournamentDetails
import com.iqoid.pairgoth.client.network.NetworkManager
import kotlinx.coroutines.launch
import retrofit2.Response

class StandingsFragment : Fragment() {

    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    private lateinit var roundSpinner: Spinner
    private lateinit var standingsTable: TableLayout
    private var tournamentId: String = "1"
    private var tournamentDetails: TournamentDetails? = null
    private var selectedRound: Int = 1 // Initialize with a default value

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_standings, container, false)

        swipeRefreshLayout = view.findViewById(R.id.swipeRefreshLayout)
        roundSpinner = view.findViewById(R.id.roundSpinner)
        standingsTable = view.findViewById(R.id.standingsTable)

        // Get the tournament ID from the arguments
        tournamentId = arguments?.getString(InformationFragment.TOURNAMENT_ID_EXTRA)?: "1"

        // Set up the refresh listener
        swipeRefreshLayout.setOnRefreshListener {
            refreshData()
        }

        fetchTournamentDetails(tournamentId)

        return view
    }

    private fun refreshData() {
        swipeRefreshLayout.isRefreshing = true
        fetchTournamentDetails(tournamentId)
    }

    private fun fetchTournamentDetails(tournamentId: String) {
        lifecycleScope.launch {
            try {
                val response = NetworkManager.pairGothApiService.getTournament(tournamentId)
                if (response.isSuccessful) {
                    tournamentDetails = response.body()
                    if (tournamentDetails != null) {
                        setupRoundSpinner()
                    } else {
                        Log.e("StandingsFragment", "Error: The response body is null")
                    }
                } else {
                    Log.e("StandingsFragment", "Error fetching tournament details: ${response.errorBody()}")
                }
            } catch (e: Exception) {
                Log.e("StandingsFragment", "Error fetching tournament details", e)
            }
        }
        swipeRefreshLayout.isRefreshing = false
    }

    private fun setupRoundSpinner() {
        val rounds = (1..(tournamentDetails?.rounds ?: 0)).toList()
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, rounds)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        roundSpinner.adapter = adapter

        // Re-select the previously selected round
        if (selectedRound in rounds) {
            roundSpinner.setSelection(rounds.indexOf(selectedRound))
        }

        roundSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                selectedRound = rounds[position]
                fetchStandings(selectedRound)
            }

            override fun onNothingSelected(parent: AdapterView<*>) {
                // Do nothing
            }
        }
    }

    private fun fetchStandings(round: Int) {
        lifecycleScope.launch {
            try {
                val response = NetworkManager.pairGothApiService.getStandings(tournamentId, round)
                if (response.isSuccessful) {
                    val results = response.body()
                    if (results != null) {
                        displayStandings(results)
                    } else {
                        Log.e("StandingsFragment", "Error: The response body is null")
                    }
                } else {
                    displayError(response)
                }
            } catch (e: Exception) {
                Log.e("StandingsFragment", "Error fetching results", e)
            }
        }
    }

    private fun displayStandings(results: List<Standing>) {
        standingsTable.removeAllViews()

        // get the max number of results from all the standings received
        val maxResults = results.maxByOrNull { it.results?.size ?: 0 }?.results?.size ?: 0

        // Table Header preparation
        val numberOfRounds = tournamentDetails?.rounds ?: 0
        val dynamicRoundHeaders = (1..maxResults).map { "R$it" }

        // Construct the headers list with the dynamic round columns inserted
        val headers = mutableListOf("Num", "Plc", "Name", "Rank", "Ctr", "Nbw")
        headers.addAll(dynamicRoundHeaders)
        headers.addAll(listOf("MMS", "SOSM", "SOSOSM"))

        // create headers row
        val headerRow = TableRow(context)
        for (header in headers) {
            val headerTextView = TextView(context)
            headerTextView.text = header
            headerTextView.setPadding(8, 8, 8, 8)
            headerRow.addView(headerTextView)
        }
        standingsTable.addView(headerRow)

        // Table Rows
        for (result in results) {
            val row = TableRow(context)
            // Number
            val tableTextView = TextView(context)
            tableTextView.text = result.num.toString()
            tableTextView.setPadding(8, 8, 8, 8)
            row.addView(tableTextView)

            // Placement
            val placementTextView = TextView(context)
            placementTextView.text = result.place.toString()
            placementTextView.setPadding(8, 8, 8, 8)
            row.addView(placementTextView)

            // Name
            val nameTextView = TextView(context)
            nameTextView.text = "${result.name} ${result.firstname}"
            nameTextView.setPadding(8, 8, 8, 8)
            row.addView(nameTextView)

            // Rank
            val rankTextView = TextView(context)
            rankTextView.text = result.rank?.let { formatRank(it) }
            rankTextView.setPadding(8, 8, 8, 8)
            row.addView(rankTextView)

            // Country
            val countryTextView = TextView(context)
            countryTextView.text = result.country
            countryTextView.setPadding(8, 8, 8, 8)
            row.addView(countryTextView)

            // Nbw
            val nbwTextView = TextView(context)
            nbwTextView.text = result.nbw.toString()
            nbwTextView.setPadding(8, 8, 8, 8)
            row.addView(nbwTextView)

            // Dynamic round columns
            for (i in 1..maxResults) {
                val roundTextView = TextView(context)
                val res: CharSequence = result.results?.get(i - 1) ?: ""
                if (res.contains("+")) {
                    roundTextView.setTypeface(null, Typeface.BOLD)
                }
                roundTextView.text = res
                roundTextView.setPadding(8, 8, 8, 8)
                row.addView(roundTextView)
            }

            // MMS
            val mmsTextView = TextView(context)
            mmsTextView.text = result.mms.toString()
            mmsTextView.setPadding(8, 8, 8, 8)
            row.addView(mmsTextView)

            // SOSM
            val sosmTextView = TextView(context)
            sosmTextView.text = result.sosm.toString()
            sosmTextView.setPadding(8, 8, 8, 8)
            row.addView(sosmTextView)

            // SOSOSM
            val sososmTextView = TextView(context)
            sososmTextView.text = result.sososm.toString()
            sososmTextView.setPadding(8, 8, 8, 8)
            row.addView(sososmTextView)

            standingsTable.addView(row)
        }
    }

    private fun displayError(response: Response<List<Standing>>) {
        Log.e("ResultsFragment", "Error fetching results: ${response.errorBody()}")
        // clean up the table
        standingsTable.removeAllViews()
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