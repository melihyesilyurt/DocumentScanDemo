package com.example.documentscandemo

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.*
import android.os.Bundle
import android.util.Log
import android.util.Size
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.core.Camera
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class DocumentScannerActivity : AppCompatActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var overlayView: DocumentOverlayView
    private lateinit var tvStatus: TextView
    private lateinit var btnCapture: Button
    private lateinit var btnBack: Button
    private lateinit var btnFlash: Button

    private var imageCapture: ImageCapture? = null
    private var camera: Camera? = null
    private lateinit var cameraExecutor: ExecutorService
    private var isFlashOn = false

    private val documentDetector = DocumentDetector()
    private val documentProcessor = DocumentProcessor()
    private var currentDocumentCorners: Array<PointF>? = null

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            startCamera()
        } else {
            Toast.makeText(this, "Kamera izni gerekli", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_document_scanner)

        initViews()
        setupClickListeners()
        
        cameraExecutor = Executors.newSingleThreadExecutor()

        // Kamera izni kontrolü
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun initViews() {
        previewView = findViewById(R.id.previewView)
        overlayView = findViewById(R.id.overlayView)
        tvStatus = findViewById(R.id.tvStatus)
        btnCapture = findViewById(R.id.btnCapture)
        btnBack = findViewById(R.id.btnBack)
        btnFlash = findViewById(R.id.btnFlash)
    }

    private fun setupClickListeners() {
        btnBack.setOnClickListener {
            finish()
        }

        btnCapture.setOnClickListener {
            capturePhoto()
        }

        btnFlash.setOnClickListener {
            toggleFlash()
        }
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            // Preview
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            // ImageCapture
            imageCapture = ImageCapture.Builder().build()

            // ImageAnalysis for document detection
            val imageAnalyzer = ImageAnalysis.Builder()
                .setTargetResolution(Size(1280, 720)) // HD çözünürlük
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor, DocumentAnalyzer { corners ->
                        runOnUiThread {
                            currentDocumentCorners = corners
                            overlayView.updateDocumentCorners(corners)
                            updateStatus(corners != null)
                        }
                    })
                }

            // Kamera seçimi (arka kamera)
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                camera = cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageCapture, imageAnalyzer
                )
            } catch (exc: Exception) {
                Log.e(TAG, "Kamera başlatma hatası", exc)
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private fun updateStatus(documentDetected: Boolean) {
        tvStatus.text = if (documentDetected) {
            "✅ Belge tespit edildi! Fotoğraf çekmek için butona basın"
        } else {
            "🔍 Belge arayın... Kimlik kartı veya belgeyi kameraya gösterin"
        }

        // Buton durumunu güncelle
        btnCapture.isEnabled = documentDetected
        btnCapture.alpha = if (documentDetected) 1.0f else 0.5f
    }

    private fun capturePhoto() {
        val imageCapture = imageCapture ?: return
        val corners = currentDocumentCorners

        if (corners == null) {
            Toast.makeText(this, "Önce bir belge tespit edin", Toast.LENGTH_SHORT).show()
            return
        }

        // Geçici dosya oluştur
        val fileName = "captured_${System.currentTimeMillis()}.jpg"
        val outputFile = File(cacheDir, fileName)
        val outputFileOptions = ImageCapture.OutputFileOptions.Builder(outputFile).build()

        imageCapture.takePicture(
            outputFileOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exception: ImageCaptureException) {
                    Toast.makeText(this@DocumentScannerActivity,
                        "Fotoğraf çekme hatası: ${exception.message}",
                        Toast.LENGTH_SHORT).show()
                }

                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    processAndShowResult(outputFile.absolutePath, corners)
                }
            }
        )
    }

    private fun processAndShowResult(imagePath: String, corners: Array<PointF>) {
        try {
            // Orijinal görüntüyü yükle
            val originalBitmap = BitmapFactory.decodeFile(imagePath)

            // Perspektif düzeltme ve kırpma uygula
            val processedBitmap = documentProcessor.cropAndCorrectPerspective(originalBitmap, corners)

            if (processedBitmap != null) {
                // İşlenmiş görüntüyü kaydet
                val processedFileName = "processed_${System.currentTimeMillis()}.jpg"
                val processedFile = File(cacheDir, processedFileName)

                FileOutputStream(processedFile).use { out ->
                    processedBitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                }

                // Sonuç ekranına git
                val intent = Intent(this, ResultActivity::class.java)
                intent.putExtra(ResultActivity.EXTRA_IMAGE_PATH, processedFile.absolutePath)
                startActivity(intent)

            } else {
                Toast.makeText(this, "Görüntü işleme hatası", Toast.LENGTH_SHORT).show()
            }

        } catch (e: Exception) {
            Toast.makeText(this, "İşleme hatası: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun toggleFlash() {
        camera?.let { camera ->
            isFlashOn = !isFlashOn
            camera.cameraControl.enableTorch(isFlashOn)
            btnFlash.text = if (isFlashOn) "🔦" else "⚡"
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    companion object {
        private const val TAG = "DocumentScanner"
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }
}
