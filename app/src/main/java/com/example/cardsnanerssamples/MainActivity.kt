package com.example.cardsnanerssamples

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import cards.pay.paycardsrecognizer.sdk.Card
import cards.pay.paycardsrecognizer.sdk.ScanCardIntent
import com.google.firebase.FirebaseApp
import io.card.payment.CardIOActivity
import io.card.payment.CreditCard
import kotlinx.android.synthetic.main.activity_main.*


val PERMISSIONS = arrayOf(Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE)

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        requestPermissions(PERMISSIONS, 10)

        FirebaseApp.initializeApp(this)
        cardIO.setOnClickListener {
            startCardIoActivity()
        }

        payCard.setOnClickListener {
            startPayCardActivity()
        }

        inhouse.setOnClickListener {
            startInHouse()
        }
    }

    var callCode: Int? = null

    fun startCardIoActivity() {
        callCode = CARDIO
        val scanIntent = Intent(this, CardIOActivity::class.java)
        scanIntent.apply {
            putExtra(CardIOActivity.EXTRA_REQUIRE_EXPIRY, true)
            putExtra(CardIOActivity.EXTRA_REQUIRE_CVV, false)
            putExtra(CardIOActivity.EXTRA_REQUIRE_POSTAL_CODE, false)
            putExtra(CardIOActivity.EXTRA_HIDE_CARDIO_LOGO, true)
            putExtra(CardIOActivity.EXTRA_USE_PAYPAL_ACTIONBAR_ICON, false)
            putExtra(CardIOActivity.EXTRA_SCAN_EXPIRY, true)
            putExtra(CardIOActivity.EXTRA_SUPPRESS_CONFIRMATION, true)
        }
        startActivityForResult(scanIntent, SCAN_REQUEST_CODE)
    }

    fun startPayCardActivity() {
        callCode = PAYCARD

        val intent = ScanCardIntent.Builder(this).build()
        startActivityForResult(intent, REQUEST_CODE_SCAN_CARD)
    }

    fun startInHouse() {
        callCode = INHOUSE

        startActivityForResult(
            Intent(this, InHouseCardScannerActivity::class.java),
            REQUEST_CODE_SCAN_CARD
        )
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when (callCode) {
            CARDIO -> proccessCardIO(requestCode, resultCode, data)
            PAYCARD -> processPayCard(requestCode, resultCode, data)
            INHOUSE -> processInhouse(requestCode, resultCode, data)
        }

        callCode = null
        super.onActivityResult(requestCode, resultCode, data)
    }

    fun proccessCardIO(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == SCAN_REQUEST_CODE) {
            if (intent != null && intent.hasExtra(CardIOActivity.EXTRA_SCAN_RESULT)) {
                val scanResult: CreditCard =
                    intent.getParcelableExtra(CardIOActivity.EXTRA_SCAN_RESULT)
                number.text = scanResult.cardNumber
                name.text = scanResult.cardholderName
                date.text = "${scanResult.expiryMonth}/${scanResult.expiryYear}"
            }
        }
    }

    fun processPayCard(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == REQUEST_CODE_SCAN_CARD) {
            if (resultCode == Activity.RESULT_OK) {
                val card: Card = data!!.getParcelableExtra(ScanCardIntent.RESULT_PAYCARDS_CARD)
                    ?: throw RuntimeException("Null card")
                number.text = card.cardNumberRedacted.toString()
                name.text = card.cardHolderName.toString()
                date.text = card.expirationDate
            } else if (resultCode == Activity.RESULT_CANCELED) {
                Log.i(TAG, "Scan canceled")
            } else {
                Log.i(TAG, "Scan failed")
            }
        }
    }

    fun processInhouse(requestCode: Int, resultCode: Int, data: Intent?) {

    }

    companion object {
        const val CARDIO = 1
        const val PAYCARD = 2
        const val INHOUSE = 3

        const val SCAN_REQUEST_CODE = 10000
        const val REQUEST_CODE_SCAN_CARD = 1

        const val TAG = "deb@"
    }
}