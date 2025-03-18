package com.iqoid.pairgoth

import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter

class ViewPagerAdapter(fragmentActivity: FragmentActivity, private val tournamentId: String) :
    FragmentStateAdapter(fragmentActivity) {
    override fun getItemCount(): Int = 5

    override fun createFragment(position: Int): Fragment {
        val fragment = when (position) {
            0 -> InformationFragment()
            1 -> RegistrationFragment()
            2 -> PairingFragment()
            3 -> ResultsFragment()
            4 -> StandingsFragment()
            else -> throw IllegalArgumentException("Invalid position")
        }

        fragment.arguments = Bundle().apply {
            putString(InformationFragment.TOURNAMENT_ID_EXTRA, tournamentId)
        }
        return fragment
    }
}