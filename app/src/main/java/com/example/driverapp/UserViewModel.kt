// Updated DriverViewModel.kt
package com.example.driverapp

import androidx.lifecycle.ViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.viewModelScope
import com.example.driverapp.data.User
import kotlinx.coroutines.launch

class UserViewModel(private val repo: UserRepository = UserRepository()) : ViewModel() {
    val userData: LiveData<User?> = repo.userData

    init {
        fetchUserData()
    }

    fun fetchUserData() {
        viewModelScope.launch {
            repo.fetchUserData()
        }
    }

    override fun onCleared() {
        super.onCleared()
        repo.removeListener()
    }
}