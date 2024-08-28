package com.terasumi.sellerkeyboard

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.navigation.NavDirections

class NavigationViewModel : ViewModel() {
    private val _navigationEvent = MutableLiveData<NavDirections?>()
    val navigationEvent: LiveData<NavDirections?> get() = _navigationEvent

    fun navigate(directions: NavDirections) {
        _navigationEvent.value = directions
    }

    fun onNavigationEventHandled() {
        _navigationEvent.value = null
    }
}