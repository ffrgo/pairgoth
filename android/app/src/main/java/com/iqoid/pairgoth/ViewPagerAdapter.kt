package com.iqoid.pairgoth

import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter

class ViewPagerAdapter(fragmentActivity: FragmentActivity, private val tournamentId: String) :
    FragmentStateAdapter(fragmentActivity) {
    override fun getItemCount(): Int = 4

    override fun createFragment(position: Int): Fragment {
        val fragment = when (position) {
            0 -> InformationFragment()
            1 -> RegistrationFragment()
            2 -> ResultsFragment()
            3 -> StandingsFragment()
            else -> throw IllegalArgumentException("Invalid position")
        }

        fragment.arguments = Bundle().apply {
            putString(InformationFragment.TOURNAMENT_ID_EXTRA, tournamentId)
        }
        return fragment
    }
}