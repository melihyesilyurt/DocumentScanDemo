package com.example.documentscandemo

import android.content.Intent
import android.graphics.*
import android.media.ExifInterface
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import java.io.File
import java.io.FileOutputStream

class CropActivity : AppCompatActivity() {

    private lateinit var cropView: CropImageView
    private lateinit var btnCrop: Button
    private lateinit var btnReset: Button
    private lateinit var btnRotateLeft: Button
    private lateinit var btnRotateRight: Button
    private var originalBitmap: Bitmap? = null
    private var currentBitmap: Bitmap? = null
    private var rotationAngle = 0f
    private var isFromRecrop = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        hideSystemUI()
        setContentView(R.layout.activity_crop)

        initViews()
        loadImage()
        setupClickListeners()
    }

    private fun initViews() {
        cropView = findViewById(R.id.cropView)
        btnCrop = findViewById(R.id.btnCrop)
        btnReset = findViewById(R.id.btnReset)
        btnRotateLeft = findViewById(R.id.btnRotateLeft)
        btnRotateRight = findViewById(R.id.btnRotateRight)
    }

    private fun loadImage() {
        isFromRecrop = intent.getBooleanExtra(EXTRA_FROM_RECROP, false)

        val uriString = intent.getStringExtra(EXTRA_IMAGE_URI)
        if (uriString != null) {
            try {
                val imageUri = Uri.parse(uriString)
                val inputStream = contentResolver.openInputStream(imageUri)
                originalBitmap = BitmapFactory.decodeStream(inputStream)
                inputStream?.close()

                originalBitmap?.let { bitmap ->
                    // EXIF verilerini kontrol et ve otomatik döndür
                    currentBitmap = correctImageOrientation(imageUri, bitmap)
                    updateImageView()
                }
                
            } catch (e: Exception) {
                Toast.makeText(this, "Görüntü yüklenemedi: ${e.message}", Toast.LENGTH_SHORT).show()
                finish()
            }
        } else {
            Toast.makeText(this, "Görüntü bulunamadı", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun setupClickListeners() {
        btnCrop.setOnClickListener {
            cropImage()
        }

        btnReset.setOnClickListener {
            resetCorners()
        }

        btnRotateLeft.setOnClickListener {
            rotateImage(-90f)
        }

        btnRotateRight.setOnClickListener {
            rotateImage(90f)
        }
    }

    private fun updateImageView() {
        currentBitmap?.let { bitmap ->
            cropView.setImageBitmap(bitmap)

            // Önce varsayılan köşeleri ayarla (görünür olması için)
            resetCorners()

            // Sonra otomatik belge tespiti dene
            val detector = SimpleDocumentDetector()
            val corners = detector.detectDocument(bitmap)

            if (corners != null) {
                // Tespit edilen köşeleri kullan
                cropView.setCorners(corners)
                val message = if (isFromRecrop) {
                    "Köşeler yeniden tespit edildi! İstediğiniz gibi ayarlayın."
                } else {
                    "Belge otomatik tespit edildi! Köşeleri ayarlayabilirsiniz."
                }
                Toast.makeText(this, message, Toast.LENGTH_LONG).show()
            } else {
                val message = if (isFromRecrop) {
                    "Köşeleri istediğiniz gibi ayarlayın"
                } else {
                    "Köşeleri manuel olarak ayarlayın"
                }
                Toast.makeText(this, message, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun resetCorners() {
        currentBitmap?.let { bitmap ->
            val defaultCorners = arrayOf(
                PointF(bitmap.width * 0.1f, bitmap.height * 0.1f),
                PointF(bitmap.width * 0.9f, bitmap.height * 0.1f),
                PointF(bitmap.width * 0.9f, bitmap.height * 0.9f),
                PointF(bitmap.width * 0.1f, bitmap.height * 0.9f)
            )
            cropView.setCorners(defaultCorners)
        }
    }

    private fun rotateImage(degrees: Float) {
        currentBitmap?.let { bitmap ->
            try {
                rotationAngle = (rotationAngle + degrees) % 360f

                val matrix = Matrix()
                matrix.postRotate(degrees)

                currentBitmap = Bitmap.createBitmap(
                    bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true
                )

                updateImageView()
                Toast.makeText(this, "Görüntü ${degrees.toInt()}° döndürüldü", Toast.LENGTH_SHORT).show()

            } catch (e: Exception) {
                Toast.makeText(this, "Döndürme hatası: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun cropImage() {
        currentBitmap?.let { bitmap ->
            try {
                val corners = cropView.getCorners()
                val processor = DocumentProcessor()
                val croppedBitmap = processor.cropAndCorrectPerspective(bitmap, corners)

                if (croppedBitmap != null) {
                    // Kırpılmış görüntüyü kaydet
                    val fileName = "cropped_${System.currentTimeMillis()}.jpg"
                    val file = File(cacheDir, fileName)

                    FileOutputStream(file).use { out ->
                        croppedBitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                    }

                    // ResultActivity'ye git
                    val intent = Intent(this, ResultActivity::class.java)
                    intent.putExtra(ResultActivity.EXTRA_IMAGE_URI, Uri.fromFile(file).toString())
                    startActivity(intent)
                    finish()

                } else {
                    Toast.makeText(this, "Kırpma işlemi başarısız", Toast.LENGTH_SHORT).show()
                }

            } catch (e: Exception) {
                Toast.makeText(this, "Kırpma hatası: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun correctImageOrientation(imageUri: Uri, bitmap: Bitmap): Bitmap {
        try {
            val inputStream = contentResolver.openInputStream(imageUri)
            val exif = ExifInterface(inputStream!!)
            inputStream.close()

            val orientation = exif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )

            val matrix = Matrix()
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> {
                    matrix.postRotate(90f)
                    rotationAngle = 90f
                }
                ExifInterface.ORIENTATION_ROTATE_180 -> {
                    matrix.postRotate(180f)
                    rotationAngle = 180f
                }
                ExifInterface.ORIENTATION_ROTATE_270 -> {
                    matrix.postRotate(270f)
                    rotationAngle = 270f
                }
                else -> return bitmap
            }

            return Bitmap.createBitmap(
                bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true
            )

        } catch (e: Exception) {
            android.util.Log.e("CropActivity", "EXIF okuma hatası", e)
            return bitmap
        }
    }

    private fun hideSystemUI() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            hideSystemUI()
        }
    }

    companion object {
        const val EXTRA_IMAGE_URI = "extra_image_uri"
        const val EXTRA_FROM_RECROP = "extra_from_recrop"
    }
}
