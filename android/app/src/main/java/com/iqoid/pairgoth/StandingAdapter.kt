package com.iqoid.pairgoth

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.iqoid.pairgoth.client.model.Standing

class StandingAdapter(
    private var standingList: List<Standing>,
    private var selectedRound: Int
) : RecyclerView.Adapter<StandingAdapter.StandingViewHolder>() {

    class StandingViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val numTextView: TextView = itemView.findViewById(R.id.numTextView)
        val plcTextView: TextView = itemView.findViewById(R.id.plcTextView)
        val nameTextView: TextView = itemView.findViewById(R.id.nameTextView)
        val rankTextView: TextView = itemView.findViewById(R.id.rankTextView)
        val ctrTextView: TextView = itemView.findViewById(R.id.ctrTextView)
        val nbwTextView: TextView = itemView.findViewById(R.id.nbwTextView)
        val r1TextView: TextView = itemView.findViewById(R.id.r1TextView)
        val r2TextView: TextView = itemView.findViewById(R.id.r2TextView)
        val r3TextView: TextView = itemView.findViewById(R.id.r3TextView)
        val r4TextView: TextView = itemView.findViewById(R.id.r4TextView)
        val r5TextView: TextView = itemView.findViewById(R.id.r5TextView)
        val mmsTextView: TextView = itemView.findViewById(R.id.mmsTextView)
        val sosmTextView: TextView = itemView.findViewById(R.id.sosmTextView)
        val sososmTextView: TextView = itemView.findViewById(R.id.sososmTextView)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StandingViewHolder {
        val itemView = LayoutInflater.from(parent.context)
            .inflate(R.layout.standing_item, parent, false) // Replace with your item layout
        return StandingViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: StandingViewHolder, position: Int) {
        val currentItem = standingList[position]

        holder.numTextView.text = currentItem.num.toString()
        holder.plcTextView.text = currentItem.place.toString()
        holder.nameTextView.text = currentItem.name
        holder.rankTextView.text = currentItem.rank.toString()
        holder.ctrTextView.text = currentItem.country
        holder.nbwTextView.text = currentItem.nbw.toString()
        holder.r1TextView.text = currentItem.results?.getOrNull(0) ?: ""
        holder.mmsTextView.text = currentItem.mms.toString()
        holder.sosmTextView.text = currentItem.sosm.toString()
        holder.sososmTextView.text = currentItem.sososm.toString()

        // Handle visibility based on selected round
        holder.r2TextView.visibility = if (selectedRound >= 2) View.VISIBLE else View.GONE
        holder.r3TextView.visibility = if (selectedRound >= 3) View.VISIBLE else View.GONE
        holder.r4TextView.visibility = if (selectedRound >= 4) View.VISIBLE else View.GONE
        holder.r5TextView.visibility = if (selectedRound >= 5) View.VISIBLE else View.GONE

        val results = currentItem.results ?: emptyList()
        holder.r2TextView.text = if (results.size > 1) results[1] else ""
        holder.r3TextView.text = if (results.size > 2) results[2] else ""
        holder.r4TextView.text = if (results.size > 3) results[3] else ""
        holder.r5TextView.text = if (results.size > 4) results[4] else ""
    }

    override fun getItemCount(): Int = standingList.size

    fun updateData(newData: List<Standing>, newRound: Int) {
        standingList = newData
        selectedRound = newRound
        notifyDataSetChanged()
    }
}