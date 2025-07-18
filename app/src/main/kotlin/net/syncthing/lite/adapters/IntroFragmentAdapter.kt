package net.syncthing.lite.adapters

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter

class IntroFragmentAdapter(fragmentActivity: FragmentActivity) : FragmentStateAdapter(fragmentActivity) {
    
    private val fragments: List<() -> Fragment> = listOf(
        { net.syncthing.lite.activities.IntroActivity.IntroFragmentOne() },
        { net.syncthing.lite.activities.IntroActivity.IntroFragmentTwo() },
        { net.syncthing.lite.activities.IntroActivity.IntroFragmentThree() }
    )

    override fun getItemCount(): Int = fragments.size

    override fun createFragment(position: Int): Fragment = fragments[position]()
}