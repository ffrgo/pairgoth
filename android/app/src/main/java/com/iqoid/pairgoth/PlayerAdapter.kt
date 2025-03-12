package com.iqoid.pairgoth

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.compose.ui.semantics.text
import androidx.recyclerview.widget.RecyclerView
import com.iqoid.pairgoth.client.model.Player

class PlayerAdapter(
    private var players: List<Player>,
    private val onPlayerClick: (Player) -> Unit
) : RecyclerView.Adapter<PlayerAdapter.PlayerViewHolder>() {

    private var selectedPlayer: Player? = null

    class PlayerViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val playerNameTextView: TextView = view.findViewById(R.id.playerNameTextView)
        val playerFirstnameTextView: TextView = view.findViewById(R.id.playerFirstnameTextView)
        val playerCountryTextView: TextView = view.findViewById(R.id.playerCountryTextView)
        val playerClubTextView: TextView = view.findViewById(R.id.playerClubTextView)
        val playerRankTextView: TextView = view.findViewById(R.id.playerRankTextView)
        val playerRatingTextView: TextView = view.findViewById(R.id.playerRatingTextView)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PlayerViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.player_item, parent, false)
        return PlayerViewHolder(view)
    }

    override fun onBindViewHolder(holder: PlayerViewHolder, position: Int) {
        val player = players[position]
        holder.playerNameTextView.text = "Name: ${player.name}"
        holder.playerFirstnameTextView.text = "Firstname: ${player.firstname}"
        holder.playerCountryTextView.text = "Country: ${player.country}"
        holder.playerClubTextView.text = "Club: ${player.club}"
        holder.playerRankTextView.text = "Rank: ${player.rank}"
        holder.playerRatingTextView.text = "Rating: ${player.rating}"

        holder.itemView.setOnClickListener {
            onPlayerClick(player)
        }
        if (player == selectedPlayer) {
            holder.itemView.setBackgroundResource(android.R.color.darker_gray)
        } else {
            holder.itemView.setBackgroundResource(android.R.color.transparent)
        }
    }

    override fun getItemCount() = players.size

    fun updatePlayers(newPlayers: List<Player>) {
        players = newPlayers
        notifyDataSetChanged()
    }

    fun setSelectedPlayer(player: Player) {
        selectedPlayer = player
        notifyDataSetChanged()
    }
}