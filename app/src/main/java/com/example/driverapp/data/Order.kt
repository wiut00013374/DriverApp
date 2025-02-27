package com.example.driverapp.data

import android.os.Parcel
import android.os.Parcelable

data class Order(
    var id: String = "",
    val uid: String = "", // Customer UID
    var driverUid: String? = null, // Assigned Driver UID
    var originCity: String = "",
    var destinationCity: String = "",
    var originLat: Double = 0.0,
    var originLon: Double = 0.0,
    var destinationLat: Double = 0.0,
    var destinationLon: Double = 0.0,
    var totalPrice: Double = 0.0,
    var truckType: String = "",
    var volume: Double = 0.0,
    var weight: Double = 0.0,
    var status: String = "Pending", // pending, accepted, in_progress, delivered, cancelled
    val driversContacted: MutableMap<String, String> = mutableMapOf(), // Track contacted drivers
    val timestamp: Long = System.currentTimeMillis() // Add a timestamp
) : Parcelable {

    constructor(parcel: Parcel) : this(
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.readString(),
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.readDouble(),
        parcel.readDouble(),
        parcel.readDouble(),
        parcel.readDouble(),
        parcel.readDouble(),
        parcel.readString() ?: "",
        parcel.readDouble(),
        parcel.readDouble(),
        parcel.readString() ?: "",
        mutableMapOf<String, String>().apply {
            val count = parcel.readInt()
            for (i in 0 until count) {
                val key = parcel.readString() ?: ""
                val value = parcel.readString() ?: ""
                put(key, value)
            }
        },
        parcel.readLong()
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(id)
        parcel.writeString(uid)
        parcel.writeString(driverUid)
        parcel.writeString(originCity)
        parcel.writeString(destinationCity)
        parcel.writeDouble(originLat)
        parcel.writeDouble(originLon)
        parcel.writeDouble(destinationLat)
        parcel.writeDouble(destinationLon)
        parcel.writeDouble(totalPrice)
        parcel.writeString(truckType)
        parcel.writeDouble(volume)
        parcel.writeDouble(weight)
        parcel.writeString(status)
        parcel.writeInt(driversContacted.size)
        for ((key, value) in driversContacted) {
            parcel.writeString(key)
            parcel.writeString(value)
        }
        parcel.writeLong(timestamp)
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<Order> {
        override fun createFromParcel(parcel: Parcel): Order {
            return Order(parcel)
        }

        override fun newArray(size: Int): Array<Order?> {
            return arrayOfNulls(size)
        }
    }
}