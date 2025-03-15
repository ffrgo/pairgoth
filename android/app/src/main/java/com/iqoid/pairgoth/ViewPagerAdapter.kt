package com.iqoid.pairgoth

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter

class ViewPagerAdapter(fragmentActivity: FragmentActivity) :
    FragmentStateAdapter(fragmentActivity) {
    override fun getItemCount(): Int = 4

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> InformationFragment()
            1 -> RegistrationFragment()
            2 -> ResultsFragment()
            3 -> StandingsFragment()
            else -> throw IllegalArgumentException("Invalid position")
        }
    }
}