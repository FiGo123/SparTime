package com.example.spartime.data

import android.content.Context

class PreferencesProvider(context: Context) {
    private val sharedPreferences = context.getSharedPreferences("spar_time_prefs", 0)

    fun putString(key: String, value: String) = sharedPreferences.edit().putString(key,value).apply()

    fun getString(key: String): String? = sharedPreferences.getString(key, null)

    fun putInt(key: String, value: Int) =  sharedPreferences.edit().putInt(key,value).apply()

    fun getInt(key: String): Int = sharedPreferences.getInt(key,0)

    fun remove(key: String) = sharedPreferences.edit().remove(key).apply()

    fun clear() =   sharedPreferences.edit().clear().apply()
}