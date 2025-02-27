//MainActivity.kt
package com.example.driverapp

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.example.driverapp.fragment.ChatsFragment
import com.example.driverapp.fragment.HomeFragment
import com.example.driverapp.fragment.OrdersFragment
import com.example.driverapp.fragment.ProfileFragment
import com.example.driverapp.login.DriverSignInActivity
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.auth.FirebaseAuth

class MainActivity : AppCompatActivity() {
    private lateinit var bottomNavigation: BottomNavigationView
    private val auth by lazy { FirebaseAuth.getInstance()}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (auth.currentUser == null) {
            // Not authenticated; redirect to DriverSignInActivity
            startActivity(Intent(this, DriverSignInActivity::class.java))
            finish()
            return
        }
        checkPermissions()

        bottomNavigation = findViewById(R.id.driver_bottom_navigation)

        // Load default fragment (e.g., DriverHomeFragment)
        if (savedInstanceState == null) {
            loadFragment(HomeFragment()) // Or OrdersFragment if you prefer
            bottomNavigation.selectedItemId = R.id.navigation_home // Or R.id.navigation_orders
        }

        bottomNavigation.setOnNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.navigation_home -> {
                    loadFragment(HomeFragment())
                    true
                }
                R.id.navigation_orders -> {
                    loadFragment(OrdersFragment())
                    true
                }
                R.id.navigation_chats -> {
                    loadFragment(ChatsFragment())
                    true
                }
                R.id.navigation_profile -> {
                    loadFragment(ProfileFragment())
                    true
                }
                else -> false
            }
        }
    }

    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.driver_nav_host_fragment, fragment)
            .commit()
    }



    private fun checkPermissions() {
        val permissions = arrayOf(
            android.Manifest.permission.INTERNET,
            android.Manifest.permission.ACCESS_FINE_LOCATION,
            android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
            android.Manifest.permission.POST_NOTIFICATIONS
        )
        val permissionsToRequest = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (permissionsToRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissionsToRequest.toTypedArray(), REQUEST_PERMISSIONS_REQUEST_CODE)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_PERMISSIONS_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                // Permissions granted, proceed with map-related features
                Toast.makeText(this, "Permissions granted.", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Permissions are required for this app to function.", Toast.LENGTH_LONG).show()
                // Optionally, disable certain features or close the app
            }
        }
    }

    companion object {
        private const val REQUEST_PERMISSIONS_REQUEST_CODE = 101
    }

}