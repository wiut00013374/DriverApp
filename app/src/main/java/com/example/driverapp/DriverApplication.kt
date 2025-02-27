package com.example.driverapp

import android.app.Application
import android.content.Context
import com.google.firebase.FirebaseApp
import org.osmdroid.config.Configuration

/**
 * Application class for initializing app-wide components
 */
class DriverApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // Initialize Firebase
        FirebaseApp.initializeApp(this)

        // Initialize OSMDroid (for maps)
        Configuration.getInstance().userAgentValue = packageName

        // Create notification channels (if needed)
        // NotificationHandler.createNotificationChannels(this)

        // Initialize other app-wide components as needed
    }

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        // If you need to use MultiDex, initialize it here
    }
}