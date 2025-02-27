// Updated DriverViewModel.kt
package com.example.driverapp

import androidx.lifecycle.ViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.viewModelScope
import com.example.driverapp.data.Driver
import kotlinx.coroutines.launch

class DriverViewModel(private val repo: DriverRepository = DriverRepository()) : ViewModel() {
    val driverData: LiveData<Driver?> = repo.driverData

    init {
        fetchDriverData()
    }

    fun fetchDriverData() {
        viewModelScope.launch {
            repo.fetchDriverData()
        }
    }

    override fun onCleared() {
        super.onCleared()
        repo.removeListener()
    }
}