package com.example.healthconnectsample.data

import android.content.Context
import android.content.SharedPreferences

data class ProfileData(
    val name: String = "",
    val age: String = "",
    val weight: String = "",
    val height: String = "",
    val heightUnit: String = "cm", // "cm" or "ft"
    val country: String = "",
    val goal: String = ""
)

class ProfileRepository(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("user_profile", Context.MODE_PRIVATE)

    fun getProfile(): ProfileData {
        return ProfileData(
            name = prefs.getString("name", "") ?: "",
            age = prefs.getString("age", "") ?: "",
            weight = prefs.getString("weight", "") ?: "",
            height = prefs.getString("height", "") ?: "",
            heightUnit = prefs.getString("height_unit", "cm") ?: "cm",
            country = prefs.getString("country", "") ?: "",
            goal = prefs.getString("goal", "") ?: ""
        )
    }

    fun saveProfile(profileData: ProfileData) {
        prefs.edit().apply {
            putString("name", profileData.name)
            putString("age", profileData.age)
            putString("weight", profileData.weight)
            putString("height", profileData.height)
            putString("height_unit", profileData.heightUnit)
            putString("country", profileData.country)
            putString("goal", profileData.goal)
            apply()
        }
    }
}
