package com.example.documentscandemo

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.PointF
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class ResultActivity : AppCompatActivity() {

    private lateinit var ivScannedDocument: ImageView
    private lateinit var btnBack: Button
    private lateinit var btnSave: Button
    private lateinit var btnRetake: Button
    private lateinit var btnRecrop: Button
    private lateinit var btnShare: Button

    private var scannedBitmap: Bitmap? = null
    private var imagePath: String? = null
    private var imageUri: Uri? = null
    private var isFromGallery = false
    private var originalImageUri: String? = null // Orijinal görüntü URI'si (tekrar kırpma için)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        hideSystemUI()
        setContentView(R.layout.activity_result)

        initViews()
        setupClickListeners()
        loadScannedImage()
    }

    private fun initViews() {
        ivScannedDocument = findViewById(R.id.ivScannedDocument)
        btnBack = findViewById(R.id.btnBack)
        btnSave = findViewById(R.id.btnSave)
        btnRetake = findViewById(R.id.btnRetake)
        btnRecrop = findViewById(R.id.btnRecrop)
        btnShare = findViewById(R.id.btnShare)
    }

    private fun setupClickListeners() {
        btnBack.setOnClickListener {
            finish()
        }

        btnSave.setOnClickListener {
            saveImage()
        }

        btnRetake.setOnClickListener {
            finish() // Ana ekrana geri dön
        }

        btnRecrop.setOnClickListener {
            recropImage()
        }

        btnShare.setOnClickListener {
            shareImage()
        }
    }

    private fun loadScannedImage() {
        isFromGallery = intent.getBooleanExtra(EXTRA_FROM_GALLERY, false)
        originalImageUri = intent.getStringExtra(EXTRA_ORIGINAL_IMAGE_URI) // Orijinal görüntü URI'si

        // Önce URI'yi kontrol et
        val uriString = intent.getStringExtra(EXTRA_IMAGE_URI)
        if (uriString != null) {
            try {
                imageUri = Uri.parse(uriString)

                // Bitmap'i yükle
                val inputStream = contentResolver.openInputStream(imageUri!!)
                scannedBitmap = BitmapFactory.decodeStream(inputStream)
                inputStream?.close()

                if (isFromGallery) {
                    // Galeriden gelen fotoğraf için belge tespiti yap
                    processGalleryImage()
                } else {
                    // ML Kit'ten gelen işlenmiş fotoğraf
                    ivScannedDocument.setImageBitmap(scannedBitmap)
                }

            } catch (e: Exception) {
                Toast.makeText(this, "Görüntü yüklenemedi: ${e.message}", Toast.LENGTH_SHORT).show()
                finish()
            }
            return
        }

        // Fallback: Eski yöntem (dosya yolu)
        imagePath = intent.getStringExtra(EXTRA_IMAGE_PATH)
        imagePath?.let { path ->
            try {
                scannedBitmap = BitmapFactory.decodeFile(path)
                ivScannedDocument.setImageBitmap(scannedBitmap)
            } catch (e: Exception) {
                Toast.makeText(this, "Görüntü yüklenemedi", Toast.LENGTH_SHORT).show()
                finish()
            }
        } ?: run {
            Toast.makeText(this, "Görüntü bulunamadı", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun saveImage() {
        scannedBitmap?.let { bitmap ->
            try {
                val fileName = "scanned_document_${System.currentTimeMillis()}.jpg"
                val file = File(getExternalFilesDir(null), fileName)
                
                FileOutputStream(file).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                }
                
                Toast.makeText(this, "Görüntü kaydedildi: ${file.absolutePath}", Toast.LENGTH_LONG).show()
                
            } catch (e: IOException) {
                Toast.makeText(this, "Kaydetme hatası: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun shareImage() {
        scannedBitmap?.let { bitmap ->
            try {
                val fileName = "shared_document_${System.currentTimeMillis()}.jpg"
                val file = File(cacheDir, fileName)
                
                FileOutputStream(file).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                }
                
                val uri = FileProvider.getUriForFile(
                    this,
                    "${packageName}.fileprovider",
                    file
                )
                
                val shareIntent = Intent().apply {
                    action = Intent.ACTION_SEND
                    type = "image/jpeg"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                
                startActivity(Intent.createChooser(shareIntent, "Belgeyi Paylaş"))
                
            } catch (e: Exception) {
                Toast.makeText(this, "Paylaşma hatası: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun processGalleryImage() {
        scannedBitmap?.let { bitmap ->
            try {
                Toast.makeText(this, "Belge tespiti yapılıyor...", Toast.LENGTH_SHORT).show()

                // Basit belge tespiti yap
                val documentDetector = SimpleDocumentDetector()
                val detectedCorners = documentDetector.detectDocument(bitmap)

                val documentProcessor = DocumentProcessor()

                if (detectedCorners != null && detectedCorners.size == 4) {
                    // Belge tespit edildi, köşeleri kullan
                    Toast.makeText(this, "Belge tespit edildi! İşleniyor...", Toast.LENGTH_SHORT).show()

                    val processedBitmap = documentProcessor.cropAndCorrectPerspective(bitmap, detectedCorners)

                    if (processedBitmap != null) {
                        ivScannedDocument.setImageBitmap(processedBitmap)
                        scannedBitmap = processedBitmap
                        Toast.makeText(this, "Belge başarıyla işlendi!", Toast.LENGTH_SHORT).show()
                    } else {
                        // İşleme başarısız, orijinal görüntüyü göster
                        ivScannedDocument.setImageBitmap(bitmap)
                        Toast.makeText(this, "İşleme hatası, orijinal gösteriliyor", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    // Belge tespit edilemedi, tüm görüntüyü iyileştir
                    Toast.makeText(this, "Belge tespit edilemedi, görüntü iyileştiriliyor...", Toast.LENGTH_SHORT).show()

                    val enhancedBitmap = documentProcessor.enhanceDocument(bitmap)
                    ivScannedDocument.setImageBitmap(enhancedBitmap)
                    scannedBitmap = enhancedBitmap
                }

            } catch (e: Exception) {
                // Hata durumunda orijinal görüntüyü göster
                ivScannedDocument.setImageBitmap(bitmap)
                Toast.makeText(this, "İşleme hatası: ${e.message}", Toast.LENGTH_LONG).show()
                e.printStackTrace()
            }
        }
    }

    private fun recropImage() {
        // Orijinal görüntü URI'si varsa onu kullan, yoksa mevcut görüntüyü kullan
        val imageUriToRecrop = originalImageUri ?: imageUri?.toString()

        if (imageUriToRecrop != null) {
            try {
                Toast.makeText(this, "Manuel kırpma ekranına yönlendiriliyor...", Toast.LENGTH_SHORT).show()

                val intent = Intent(this, CropActivity::class.java)
                intent.putExtra(CropActivity.EXTRA_IMAGE_URI, imageUriToRecrop)
                intent.putExtra(CropActivity.EXTRA_FROM_RECROP, true) // Tekrar kırpma işareti
                startActivity(intent)
                finish() // Bu aktiviteyi kapat

            } catch (e: Exception) {
                Toast.makeText(this, "Tekrar kırpma hatası: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        } else {
            // Orijinal URI yoksa, mevcut bitmap'i geçici dosyaya kaydet
            scannedBitmap?.let { bitmap ->
                try {
                    val fileName = "recrop_temp_${System.currentTimeMillis()}.jpg"
                    val file = File(cacheDir, fileName)

                    FileOutputStream(file).use { out ->
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                    }

                    val intent = Intent(this, CropActivity::class.java)
                    intent.putExtra(CropActivity.EXTRA_IMAGE_URI, Uri.fromFile(file).toString())
                    intent.putExtra(CropActivity.EXTRA_FROM_RECROP, true)
                    startActivity(intent)
                    finish()

                } catch (e: Exception) {
                    Toast.makeText(this, "Geçici dosya oluşturulamadı: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            } ?: run {
                Toast.makeText(this, "Tekrar kırpma için görüntü bulunamadı", Toast.LENGTH_SHORT).show()
            }
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
        const val EXTRA_IMAGE_PATH = "extra_image_path"
        const val EXTRA_IMAGE_URI = "extra_image_uri"
        const val EXTRA_FROM_GALLERY = "extra_from_gallery"
        const val EXTRA_ORIGINAL_IMAGE_URI = "extra_original_image_uri" // Orijinal görüntü URI'si
    }
}
