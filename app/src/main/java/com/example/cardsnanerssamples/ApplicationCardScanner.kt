package com.example.cardsnanerssamples

import android.app.Application
import android.content.Context
import com.google.firebase.FirebaseApp

class ApplicationCardScanner : Application() {
    companion object{
        lateinit var context: Context
    }

    override fun onCreate() {
        super.onCreate()
        FirebaseApp.initializeApp(this)
        context = this
    }
}