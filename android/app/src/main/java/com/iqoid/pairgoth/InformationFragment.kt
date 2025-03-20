package com.iqoid.pairgoth

import android.annotation.SuppressLint
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableString
import android.text.style.StyleSpan
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.graphics.Typeface
import androidx.fragment.app.Fragment
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.iqoid.pairgoth.client.model.Player.Companion.formatRank
import com.iqoid.pairgoth.client.model.TournamentDetails
import com.iqoid.pairgoth.client.network.NetworkManager
import com.iqoid.pairgoth.utils.CountriesTool
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class InformationFragment : Fragment() {

    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
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

    private var tournamentId: String = "1"

    private val countriesTool: CountriesTool by lazy {
        // This block will be executed only once, the first time countriesTool is accessed
        // ... your complex initialization logic ...
        CountriesTool()
    }

    companion object {
        const val TOURNAMENT_ID_EXTRA = "tournamentId"
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_information, container, false)

        swipeRefreshLayout = view.findViewById(R.id.swipeRefreshLayout)

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
        tournamentId = arguments?.getString(TOURNAMENT_ID_EXTRA)?: "1"

        // Set up the refresh listener
        swipeRefreshLayout.setOnRefreshListener {
            refreshData()
        }

        if (tournamentId != null) {
            fetchTournamentDetails(tournamentId)
        } else {
            Log.e("InformationFragment", "Tournament ID not found in arguments")
            // Handle the error, e.g., show an error message or navigate back
        }

        return view
    }

    private fun refreshData() {
        swipeRefreshLayout.isRefreshing = true
        fetchTournamentDetails(tournamentId)
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
        swipeRefreshLayout.isRefreshing = false
    }

    private fun updateUI(tournament: TournamentDetails) {
        tournamentName.text = formatLabelAndValue("Name", tournament.name)
        tournamentDates.text = formatLabelAndValue("Dates", "${tournament.startDate} - ${tournament.endDate}")
        val countryName = tournament.country.lowercase().let { countriesTool.getCountry(it) }
        tournamentCountry.text = formatLabelAndValue("Country", countryName ?: tournament.country)
        tournamentLocation.text = formatLabelAndValue("Location", tournament.location)
        tournamentDirector.text = formatLabelAndValue("Director", tournament.director)
        tournamentShortName.text = formatLabelAndValue("Short Name", tournament.shortName)
        tournamentType.text = formatLabelAndValue("Tournament type", convertTournamentTypeToString(tournament.type))
        tournamentRounds.text = formatLabelAndValue("Rounds", "${tournament.rounds}")
        tournamentRules.text = formatLabelAndValue("Rules", convertRuleToDetailedDescription(tournament.rules))
        tournamentGobanSize.text = formatLabelAndValue("Goban Size", convertGobanSizeToString(tournament.gobanSize))
        tournamentKomi.text = formatLabelAndValue("Komi", "${tournament.komi}")

        // Pairing Details
        pairingConfigContainer.removeAllViews() // Clear previous views
        pairingConfigContainer.orientation = LinearLayout.VERTICAL
        with(tournament.pairing) {
            addTextViewToLinearLayout(pairingConfigContainer, formatLabelAndValue("Pairing", convertPairingTypeToString(type)))
            addTextViewToLinearLayout(pairingConfigContainer, formatLabelAndValue("MM floor", formatRank(mmFloor)))
            addTextViewToLinearLayout(pairingConfigContainer, formatLabelAndValue("MM bar", formatRank(mmBar)))
        }
        with(tournament.pairing.handicap) {
            // convert correction according to this logic:
            addTextViewToLinearLayout(pairingConfigContainer, formatLabelAndValue("Hd correction", "${correction * -1}"))
            addTextViewToLinearLayout(pairingConfigContainer, formatLabelAndValue("No hd threshold", formatRank(threshold)))
        }

        // Time System
        timeSystemContainer.removeAllViews() // Clear previous views
        timeSystemContainer.orientation = LinearLayout.VERTICAL
        with(tournament.timeSystem) {
            addTextViewToLinearLayout(timeSystemContainer, formatLabelAndValue("Time System", convertTimeSystemTypeToString(type)))
            addTextViewToLinearLayout(timeSystemContainer, formatLabelAndValue("Main Time", toHMS(mainTime)))
            increment?.let {
                addTextViewToLinearLayout(timeSystemContainer, formatLabelAndValue("Increment",toHMS(it)))
            }
        }
    }

    private fun addTextViewToLinearLayout(container: LinearLayout, text: SpannableString) {
            val textView = TextView(context)
            textView.text = text
            container.addView(textView)
    }
    private fun formatLabelAndValue(label: String, value: String): SpannableString {
        val fullText = "$label: $value"
        val spannableString = SpannableString(fullText)
        spannableString.setSpan(StyleSpan(Typeface.BOLD), 0, label.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        return spannableString
    }

    private fun convertRuleToDetailedDescription(rule: String): String {
        return when (rule) {
            "AGA" -> "American Game Association rules"
            "CHINESE" -> "Chinese rules"
            "FRENCH" -> "French rules"
            "JAPANESE" -> "Japanese rules"
            else -> rule
        }
    }

    private fun convertGobanSizeToString(gobanSize: Int): String {
        return gobanSize.toString() + "x" + gobanSize.toString()
    }

    private fun convertTimeSystemTypeToString(timeSystemType: String): String {
        return when (timeSystemType) {
            "FISCHER" -> "Fischer timing"
            "CANADIAN" -> "Canadian byo-yomi"
            "JAPANESE" -> "Japanese byo-yomi"
            "SUDDEN_DEATH" -> "Sudden death"
            else -> timeSystemType
        }
    }

    private fun convertTournamentTypeToString(tournamentType: String): String {
        return when (tournamentType) {
            "INDIVIDUAL" -> "Individual players"
            "PAIRGO" -> "Pair-go tournament"
            "RENGO2" -> "Rengo with 2 players teams"
            "RENGO3" -> "Rengo with 3 players team"
            else -> tournamentType
        }
    }

    private fun convertPairingTypeToString(pairingType: String): String {
        return when (pairingType) {
            "MAC_MAHON" -> "Mac Mahon"
            "SWISS" -> "Swiss"
            "ROUND_ROBIN" -> "Round-robin"
            else -> pairingType
        }
    }

    @SuppressLint("DefaultLocale")
    private fun toHMS(seconds: Int): String {
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        val remainingSeconds = seconds % 60

        return String.format("%02d:%02d:%02d", hours, minutes, remainingSeconds)
    }
}
