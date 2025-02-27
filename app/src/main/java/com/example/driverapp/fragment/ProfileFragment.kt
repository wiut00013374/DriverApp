// fragment/ProfileFragment.kt
package com.example.driverapp.fragment

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels  // <-- Add this import
import com.example.driverapp.DriverViewModel
import com.example.driverapp.R
import com.example.driverapp.databinding.FragmentProfileBinding
import com.example.driverapp.data.Driver

class ProfileFragment : Fragment(R.layout.fragment_profile) {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    private val driverViewModel: DriverViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        _binding = FragmentProfileBinding.bind(view)

        driverViewModel.driverData.observe(viewLifecycleOwner) { driver ->
            if (driver != null) {
                displayDriverData(driver)
                Log.d("ProfileFragment", "Driver data observed.")
            } else {
                Toast.makeText(requireContext(), "Driver data not found.", Toast.LENGTH_SHORT).show()
                Log.e("ProfileFragment", "Driver data is null.")
            }
        }
    }

    private fun displayDriverData(driver: Driver) {
        binding.textViewFullName.text = driver.name
        binding.textViewEmail.text = driver.email
        binding.textViewPhoneNumber.text = driver.phoneNumber
        binding.textViewTruckType.text = driver.truckType
        binding.textViewLicensePlate.text = driver.licensePlate
        binding.textViewStatus.text = if (driver.available) "Available" else "Unavailable"
        // Populate other views as needed
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}