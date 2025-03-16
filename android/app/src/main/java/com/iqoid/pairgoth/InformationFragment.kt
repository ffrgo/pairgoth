package com.iqoid.pairgoth

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.iqoid.pairgoth.client.model.TournamentDetails
import com.iqoid.pairgoth.client.network.NetworkManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class InformationFragment : Fragment() {

    private lateinit var tournamentName: TextView
    private lateinit var tournamentDates: TextView
    private lateinit var tournamentCountry: TextView
    private lateinit var tournamentLocation: TextView
    private lateinit var tournamentDirector: TextView
    private lateinit var tournamentShortName: TextView
    private lateinit var tournamentType: TextView
    private lateinit var tournamentRounds: TextView
    private lateinit var pairingConfigContainer: LinearLayout
    private lateinit var tournamentRules: TextView
    private lateinit var tournamentGobanSize: TextView
    private lateinit var tournamentKomi: TextView
    private lateinit var timeSystemContainer: LinearLayout

    companion object {
        const val TOURNAMENT_ID_EXTRA = "tournamentId"
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_information, container, false)

        tournamentName = view.findViewById(R.id.tournamentName)
        tournamentDates = view.findViewById(R.id.tournamentDates)
        tournamentCountry = view.findViewById(R.id.tournamentCountry)
        tournamentLocation = view.findViewById(R.id.tournamentLocation)
        tournamentDirector = view.findViewById(R.id.tournamentDirector)
        tournamentShortName = view.findViewById(R.id.tournamentShortName)
        tournamentType = view.findViewById(R.id.tournamentType)
        tournamentRounds = view.findViewById(R.id.tournamentRounds)
        pairingConfigContainer = view.findViewById(R.id.pairingConfigContainer)
        tournamentRules = view.findViewById(R.id.tournamentRules)
        tournamentGobanSize = view.findViewById(R.id.tournamentGobanSize)
        tournamentKomi = view.findViewById(R.id.tournamentKomi)
        timeSystemContainer = view.findViewById(R.id.timeSystemContainer)

        // Get the tournament ID from the arguments
        val tournamentId = arguments?.getString(TOURNAMENT_ID_EXTRA)

        if (tournamentId != null) {
            fetchTournamentDetails(tournamentId)
        } else {
            Log.e("InformationFragment", "Tournament ID not found in arguments")
            // Handle the error, e.g., show an error message or navigate back
        }

        return view
    }

    private fun fetchTournamentDetails(tournamentId: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = NetworkManager.pairGothApiService.getTournament(tournamentId)
                if (response.isSuccessful) {
                    val tournamentDetails = response.body()
                    if (tournamentDetails != null) {
                        withContext(Dispatchers.Main) {
                            updateUI(tournamentDetails)
                        }
                    } else {
                        Log.e("InformationFragment", "Error: The response body is null")
                    }
                } else {
                    Log.e("InformationFragment", "Error fetching tournament details: ${response.errorBody()}")
                }
            } catch (e: Exception) {
                Log.e("InformationFragment", "Error fetching tournament details", e)
            }
        }
    }

    private fun updateUI(tournament: TournamentDetails) {
        tournamentName.text = "Name: ${tournament.name}"
        tournamentDates.text = "Dates: ${tournament.startDate} - ${tournament.endDate}"
        tournamentCountry.text = "Country: ${tournament.country}"
        tournamentLocation.text = "Location: ${tournament.location}"
        tournamentDirector.text = "Director: ${tournament.director}"
        tournamentShortName.text = "Short Name: ${tournament.shortName}"
        tournamentType.text = "Type: ${tournament.type}"
        tournamentRounds.text = "Rounds: ${tournament.rounds}"
        tournamentRules.text = "Rules: ${tournament.rules}"
        tournamentGobanSize.text = "Goban Size: ${tournament.gobanSize}"
        tournamentKomi.text = "Komi: ${tournament.komi}"

        // Pairing
        with(tournament.pairing) {
            addTextViewToLinearLayout(pairingConfigContainer, "Pairing Type: ${type}")
            with(main) {
                addTextViewToLinearLayout(pairingConfigContainer, "Main configs: catWeight=$categoriesWeight, scoreWeight=$scoreWeight, upDownWeight=$upDownWeight")
                addTextViewToLinearLayout(pairingConfigContainer, "firstSeed=$firstSeed, secondSeed=$secondSeed")
            }
            addTextViewToLinearLayout(pairingConfigContainer,"Placement: ${placement.joinToString()}")
        }

        // Time System
        with(tournament.timeSystem) {
            addTextViewToLinearLayout(timeSystemContainer, "Time System Type: ${type}")
            addTextViewToLinearLayout(timeSystemContainer, "Main Time: ${mainTime}")
            increment?.let {
                addTextViewToLinearLayout(timeSystemContainer, "Increment: ${it}")
            }
        }
    }

    private fun addTextViewToLinearLayout(container: LinearLayout, text: String) {
        val textView = TextView(context)
        textView.text = text
        container.addView(textView)
    }
}
