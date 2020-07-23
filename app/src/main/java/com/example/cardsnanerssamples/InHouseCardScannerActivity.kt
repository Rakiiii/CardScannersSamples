package com.example.cardsnanerssamples

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.Parcelable
import android.util.DisplayMetrics
import android.util.Log
import android.util.Rational
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.core.impl.ImageAnalysisConfig
import androidx.camera.core.impl.ImageCaptureConfig
import androidx.camera.core.impl.OptionsBundle
import androidx.camera.core.impl.PreviewConfig
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import cards.pay.paycardsrecognizer.sdk.Card
import cards.pay.paycardsrecognizer.sdk.ScanCardIntent
import com.google.firebase.FirebaseApp
import com.google.firebase.ml.vision.FirebaseVision
import com.google.firebase.ml.vision.common.FirebaseVisionImage
import com.google.firebase.ml.vision.common.FirebaseVisionImageMetadata
import com.google.firebase.ml.vision.objects.FirebaseVisionObjectDetector
import com.google.firebase.ml.vision.objects.FirebaseVisionObjectDetectorOptions
import com.google.firebase.ml.vision.objects.ObjectDetectorCreator
import kotlinx.android.synthetic.main.activity_in_house_card_scanner.*
import java.lang.Double.min
import java.util.Collections.max
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.abs


class InHouseCardScannerActivity : AppCompatActivity() {

    var imageCapture: ImageCapture? = null
    var imageAnalysis: ImageAnalysis? = null
    var preview: Preview? = null

    var camera: Camera? = null

    lateinit var cameraExecutor: ExecutorService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_in_house_card_scanner)
        try {
            cameraExecutor = Executors.newSingleThreadExecutor()
            previewView.post {
                startCamera()
            }


        } catch (e: SecurityException) {
        }

    }

    fun sendResultBack(card: Card) {
        val data = Intent();
        data.putExtra(ScanCardIntent.RESULT_PAYCARDS_CARD, card as Parcelable)
        setResult(RESULT_OK, data)
        finish()
    }

    companion object {
        const val OK = 1
        const val FAIL = 2

        private const val RATIO_4_3_VALUE = 4.0 / 3.0
        private const val RATIO_16_9_VALUE = 16.0 / 9.0
    }

    private class YourImageAnalyzer(
        val objectDetector: FirebaseVisionObjectDetector,
        val activity: InHouseCardScannerActivity
    ) :
        ImageAnalysis.Analyzer {

        companion object {
            fun objDetector(context: Context): FirebaseVisionObjectDetector {
                return FirebaseVision.getInstance().getOnDeviceObjectDetector(
                    FirebaseVisionObjectDetectorOptions.Builder()
                        .setDetectorMode(FirebaseVisionObjectDetectorOptions.STREAM_MODE)
                        .enableClassification()
                        .enableMultipleObjects()
                        .build()
                )
            }
        }

        var frame: Int = 0

        @SuppressLint("UnsafeExperimentalUsageError")
        override fun analyze(image: ImageProxy) {
            image.image?.let { image ->
                val betterImage = FirebaseVisionImage.fromMediaImage(
                    image,
                    FirebaseVisionImageMetadata.ROTATION_0
                )
                objectDetector.processImage(
                    betterImage
                ).addOnSuccessListener { detectedObjects ->
                    frame++
                    for (obj in detectedObjects) {
                        Log.d(
                            "detect@",
                            "id ${obj.trackingId} bounds left ${obj.boundingBox.left} right ${obj.boundingBox.right} bottom ${obj.boundingBox.bottom} top ${obj.boundingBox.top} category ${obj.classificationCategory} confidance ${obj.classificationConfidence}"
                        )
                        if (frame > 200) {
                            val firebaseVisionTextDetector =
                                FirebaseVision.getInstance().onDeviceTextRecognizer
                            firebaseVisionTextDetector.processImage(betterImage)
                                .addOnSuccessListener {
                                    Log.d("detect@", "text ${it.text}")
                                    getCard(it.text)
                                }
                        }
                    }
                }.addOnFailureListener {
                    Log.d(
                        "detect@", "filed ${it.message}"
                    )
                }
            }
            image.close()
        }

        fun getCard(text: String) {
            var num: String = ""
            var holder = "Можно достать, просто много кода"
            var years = ""
            val words = text.split("\n")
            for (word in words) {
                Log.e("TAG", word)
                //REGEX for detecting a credit card
                if (word.replace(" ", "")
                        .matches(Regex("^(?:4[0-9]{12}(?:[0-9]{3})?|[25][1-7][0-9]{14}|6(?:011|5[0-9][0-9])[0-9]{12}|3[47][0-9]{13}|3(?:0[0-5]|[68][0-9])[0-9]{11}|(?:2131|1800|35\\d{3})\\d{11})\$"))
                )
                    num = word
                //Find a better way to do this
                if (word.contains("/")) {
                    for (year in word.split(" ")) {
                        if (year.contains("/"))
                            years = year
                    }
                }
            }

            activity.sendResultBack(Card(num, holder, years))
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        // Get screen metrics used to setup camera for full screen resolution
        val metrics = DisplayMetrics().also { previewView.display.getRealMetrics(it) }

        val screenAspectRatio = aspectRatio(metrics.widthPixels, metrics.heightPixels)

        val rotation = previewView.display.rotation

        val imageAnalyzer = ImageAnalysis.Builder()
            .build()
            .also {
                it.setAnalyzer(
                    cameraExecutor,
                    YourImageAnalyzer(YourImageAnalyzer.objDetector(this), this)
                )
            }

        cameraProviderFuture.addListener(Runnable {


            // Used to bind the lifecycle of cameras to the lifecycle owner
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            // Preview
            preview = Preview.Builder()
                .build()

            // Select back camera
            val cameraSelector =
                CameraSelector.Builder().requireLensFacing(CameraSelector.LENS_FACING_BACK).build()

            cameraProvider.unbindAll()
            try {
                // Unbind use cases before rebinding
                cameraProvider.unbindAll()

                // Bind use cases to camera
                camera = cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageAnalyzer
                )
                preview?.setSurfaceProvider(previewView.createSurfaceProvider())
            } catch (exc: Exception) {
                Log.e("deb@", "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private fun aspectRatio(width: Int, height: Int): Int {
        val previewRatio = maxOf(width, height).toDouble() / minOf(width, height)
        if (abs(previewRatio - RATIO_4_3_VALUE) <= abs(previewRatio - RATIO_16_9_VALUE)) {
            return AspectRatio.RATIO_4_3
        }
        return AspectRatio.RATIO_16_9
    }

}