package com.example.driverapp.data

import android.os.Parcel
import android.os.Parcelable
import com.google.firebase.firestore.GeoPoint

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
    var status: String = "Pending", // pending, accepted, in_progress, picked_up, delivered, cancelled
    val driversContacted: MutableMap<String, String> = mutableMapOf(), // Track contacted drivers
    val timestamp: Long = System.currentTimeMillis(), // Add a timestamp
    var driversContactList: MutableMap<String, String> = mutableMapOf(),
    var lastDriverNotificationTime: Long = 0L,
    var currentDriverIndex: Int = 0,
    var acceptedAt: Long = 0L,
    var pickedUpAt: Long = 0L,
    var deliveredAt: Long = 0L,
    var cancelledAt: Long = 0L,
    var cancelledBy: String = "",
    var distanceToPickup: Double = 0.0,
    var distanceToDelivery: Double = 0.0,
    var driverCanPickup: Boolean = false,
    var driverCanDeliver: Boolean = false,
    var driverLocation: GeoPoint? = null,
    var driverHeading: Float = 0f,
    var driverSpeed: Float = 0f,
    var driverLastUpdate: Long = 0L,
    var pickupEta: Long = 0L,           // ETA for pickup in milliseconds (timestamp)
    var deliveryEta: Long = 0L,         // ETA for delivery in milliseconds (timestamp)
    var lastStatusMessage: String = "",  // Last status message
    var lastStatusUpdate: Long = 0L      // Timestamp of last status update
) : Parcelable {

    // Since GeoPoint is not Parcelable, we handle it separately
    private constructor(parcel: Parcel) : this(
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
        parcel.readLong(),
        mutableMapOf<String, String>().apply {
            val count = parcel.readInt()
            for (i in 0 until count) {
                val key = parcel.readString() ?: ""
                val value = parcel.readString() ?: ""
                put(key, value)
            }
        },
        parcel.readLong(),
        parcel.readInt(),
        parcel.readLong(),
        parcel.readLong(),
        parcel.readLong(),
        parcel.readLong(),
        parcel.readString() ?: "",
        parcel.readDouble(),
        parcel.readDouble(),
        parcel.readInt() == 1,
        parcel.readInt() == 1,
        null, // GeoPoint cannot be directly parceled
        parcel.readFloat(),
        parcel.readFloat(),
        parcel.readLong(),
        parcel.readLong(),
        parcel.readLong(),
        parcel.readString() ?: "",
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

        // Write driversContacted map
        parcel.writeInt(driversContacted.size)
        for ((key, value) in driversContacted) {
            parcel.writeString(key)
            parcel.writeString(value)
        }

        parcel.writeLong(timestamp)

        // Write driversContactList map
        parcel.writeInt(driversContactList.size)
        for ((key, value) in driversContactList) {
            parcel.writeString(key)
            parcel.writeString(value)
        }

        parcel.writeLong(lastDriverNotificationTime)
        parcel.writeInt(currentDriverIndex)
        parcel.writeLong(acceptedAt)
        parcel.writeLong(pickedUpAt)
        parcel.writeLong(deliveredAt)
        parcel.writeLong(cancelledAt)
        parcel.writeString(cancelledBy)
        parcel.writeDouble(distanceToPickup)
        parcel.writeDouble(distanceToDelivery)
        parcel.writeInt(if (driverCanPickup) 1 else 0)
        parcel.writeInt(if (driverCanDeliver) 1 else 0)
        // We cannot directly write GeoPoint, so we just skip it
        parcel.writeFloat(driverHeading)
        parcel.writeFloat(driverSpeed)
        parcel.writeLong(driverLastUpdate)
        parcel.writeLong(pickupEta)
        parcel.writeLong(deliveryEta)
        parcel.writeString(lastStatusMessage)
        parcel.writeLong(lastStatusUpdate)
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