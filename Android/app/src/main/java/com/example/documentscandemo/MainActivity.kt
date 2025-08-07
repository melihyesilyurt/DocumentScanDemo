package com.example.documentscandemo

import android.content.Intent
import android.graphics.*
import android.media.ExifInterface
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import java.io.File
import java.io.FileOutputStream
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.documentscandemo.ui.theme.DocumentScanDemoTheme
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult

class MainActivity : ComponentActivity() {

    private val scannerLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val scanningResult = GmsDocumentScanningResult.fromActivityResultIntent(result.data)
            scanningResult?.let { scanResult ->
                // Taranmış sayfaları al
                val pages = scanResult.pages
                if (pages != null && pages.isNotEmpty()) {
                    // İlk sayfayı ResultActivity'de göster
                    val firstPage = pages[0]
                    val intent = Intent(this, ResultActivity::class.java)
                    intent.putExtra(ResultActivity.EXTRA_IMAGE_URI, firstPage.imageUri.toString())
                    startActivity(intent)
                } else {
                    Toast.makeText(this, "Belge taranamadı", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            Toast.makeText(this, "Tarama iptal edildi", Toast.LENGTH_SHORT).show()
        }
    }

    private val galleryLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            // Seçilen fotoğrafı ML Kit ile işle
            processGalleryImage(it)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        hideSystemUI()
        setContent {
            DocumentScanDemoTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    MainScreen(
                        modifier = Modifier.padding(innerPadding),
                        onScanDocument = {
                            startDocumentScanning()
                        },
                        onSelectFromGallery = {
                            selectFromGallery()
                        }
                    )
                }
            }
        }
    }

    private fun startDocumentScanning() {
        val options = GmsDocumentScannerOptions.Builder()
            .setGalleryImportAllowed(true) // Galeri ve kamera kullan
            .setPageLimit(1) // Tek sayfa tara
            .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_BASE_WITH_FILTER) // Tam özellikli tarayıcı
            .build()

        val scanner = GmsDocumentScanning.getClient(options)

        scanner.getStartScanIntent(this)
            .addOnSuccessListener { intentSender ->
                scannerLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
            }
            .addOnFailureListener { exception ->
                Toast.makeText(this, "Tarayıcı başlatılamadı: ${exception.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun selectFromGallery() {
        galleryLauncher.launch("image/*")
    }

    private fun processGalleryImage(imageUri: Uri) {
        // Otomatik belge tespiti ve kırpma dene
        tryAutomaticDocumentProcessing(imageUri)
    }

    private fun tryAutomaticDocumentProcessing(imageUri: Uri) {
        try {
            Toast.makeText(this, "Otomatik belge tespiti deneniyor...", Toast.LENGTH_SHORT).show()

            // Görüntüyü yükle
            val inputStream = contentResolver.openInputStream(imageUri)
            val originalBitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()

            if (originalBitmap != null) {
                // EXIF düzeltmesi
                val correctedBitmap = correctImageOrientation(imageUri, originalBitmap)

                // Belge tespiti
                val detector = SimpleDocumentDetector()
                val corners = detector.detectDocument(correctedBitmap)

                android.util.Log.d("MainActivity", "Tespit sonucu: ${corners?.size ?: 0} köşe")

                if (corners != null) {
                    android.util.Log.d("MainActivity", "Köşeler: ${corners.contentToString()}")

                    if (isGoodDetection(corners, correctedBitmap)) {
                        // Otomatik tespit başarılı, direkt kırp
                        android.util.Log.d("MainActivity", "Otomatik tespit başarılı!")
                        processAutomaticDetection(correctedBitmap, corners, imageUri)
                    } else {
                        // Tespit var ama kalitesi düşük
                        android.util.Log.d("MainActivity", "Tespit kalitesi düşük, manuel ayarlama")
                        Toast.makeText(this, "Belge tespit edildi ama manuel ayarlama öneriliyor", Toast.LENGTH_LONG).show()
                        val intent = Intent(this, CropActivity::class.java)
                        intent.putExtra(CropActivity.EXTRA_IMAGE_URI, imageUri.toString())
                        startActivity(intent)
                    }
                } else {
                    // Otomatik tespit başarısız, manuel kırpma ekranına git
                    android.util.Log.d("MainActivity", "Belge tespit edilemedi")
                    Toast.makeText(this, "Belge tespit edilemedi, manuel ayarlama gerekiyor", Toast.LENGTH_LONG).show()
                    val intent = Intent(this, CropActivity::class.java)
                    intent.putExtra(CropActivity.EXTRA_IMAGE_URI, imageUri.toString())
                    startActivity(intent)
                }
            } else {
                Toast.makeText(this, "Görüntü yüklenemedi", Toast.LENGTH_SHORT).show()
            }

        } catch (e: Exception) {
            Toast.makeText(this, "Hata: ${e.message}", Toast.LENGTH_SHORT).show()
            // Hata durumunda manuel kırpma ekranına git
            val intent = Intent(this, CropActivity::class.java)
            intent.putExtra(CropActivity.EXTRA_IMAGE_URI, imageUri.toString())
            startActivity(intent)
        }
    }

    private fun isGoodDetection(corners: Array<PointF>, bitmap: Bitmap): Boolean {
        try {
            val width = bitmap.width.toFloat()
            val height = bitmap.height.toFloat()

            android.util.Log.d("MainActivity", "Tespit kalitesi kontrol ediliyor...")

            // Köşelerin görüntü sınırları içinde olup olmadığını kontrol et
            val allInBounds = corners.all { corner ->
                corner.x >= -10 && corner.x <= width + 10 && corner.y >= -10 && corner.y <= height + 10
            }

            if (!allInBounds) {
                android.util.Log.d("MainActivity", "Köşeler sınır dışında")
                return false
            }

            // Alan hesapla
            val area = calculateQuadrilateralArea(corners)
            val imageArea = width * height
            val areaRatio = area / imageArea

            android.util.Log.d("MainActivity", "Alan oranı: $areaRatio")

            // Daha esnek alan kontrolü - %10-%90 arası
            if (areaRatio < 0.1f || areaRatio > 0.9f) {
                android.util.Log.d("MainActivity", "Alan oranı uygun değil")
                return false
            }

            // Köşeler arası minimum mesafe kontrolü (daha esnek)
            val minDistance = minOf(width, height) * 0.05f
            for (i in corners.indices) {
                for (j in i + 1 until corners.size) {
                    val distance = calculateDistance(corners[i], corners[j])
                    if (distance < minDistance) {
                        android.util.Log.d("MainActivity", "Köşeler çok yakın")
                        return false
                    }
                }
            }

            android.util.Log.d("MainActivity", "Tespit kalitesi iyi!")
            return true

        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Kalite kontrolü hatası", e)
            return false
        }
    }

    private fun calculateQuadrilateralArea(corners: Array<PointF>): Float {
        if (corners.size != 4) return 0f

        // Shoelace formula
        var area = 0f
        for (i in corners.indices) {
            val j = (i + 1) % corners.size
            area += corners[i].x * corners[j].y
            area -= corners[j].x * corners[i].y
        }
        return kotlin.math.abs(area) / 2f
    }

    private fun calculateDistance(p1: PointF, p2: PointF): Float {
        return kotlin.math.sqrt((p1.x - p2.x) * (p1.x - p2.x) + (p1.y - p2.y) * (p1.y - p2.y))
    }

    private fun processAutomaticDetection(bitmap: Bitmap, corners: Array<PointF>, originalUri: Uri? = null) {
        try {
            Toast.makeText(this, "✅ Otomatik tespit başarılı! İşleniyor...", Toast.LENGTH_SHORT).show()

            val processor = DocumentProcessor()
            val croppedBitmap = processor.cropAndCorrectPerspective(bitmap, corners)

            if (croppedBitmap != null) {
                // Kırpılmış görüntüyü kaydet
                val fileName = "auto_cropped_${System.currentTimeMillis()}.jpg"
                val file = File(cacheDir, fileName)

                FileOutputStream(file).use { out ->
                    croppedBitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                }

                // ResultActivity'ye git
                val intent = Intent(this, ResultActivity::class.java)
                intent.putExtra(ResultActivity.EXTRA_IMAGE_URI, Uri.fromFile(file).toString())
                // Orijinal görüntü URI'sini de gönder (tekrar kırpma için)
                originalUri?.let {
                    intent.putExtra(ResultActivity.EXTRA_ORIGINAL_IMAGE_URI, it.toString())
                }
                startActivity(intent)

                Toast.makeText(this, "🎉 Belge otomatik olarak işlendi!", Toast.LENGTH_LONG).show()

            } else {
                Toast.makeText(this, "Kırpma başarısız, manuel ayarlama gerekiyor", Toast.LENGTH_SHORT).show()
                // Manuel kırpma ekranına git
                fallbackToManualCrop(bitmap)
            }

        } catch (e: Exception) {
            Toast.makeText(this, "İşleme hatası: ${e.message}", Toast.LENGTH_SHORT).show()
            fallbackToManualCrop(bitmap)
        }
    }

    private fun fallbackToManualCrop(bitmap: Bitmap) {
        try {
            // Bitmap'i geçici dosyaya kaydet
            val fileName = "temp_${System.currentTimeMillis()}.jpg"
            val file = File(cacheDir, fileName)

            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }

            // CropActivity'ye git
            val intent = Intent(this, CropActivity::class.java)
            intent.putExtra(CropActivity.EXTRA_IMAGE_URI, Uri.fromFile(file).toString())
            startActivity(intent)

        } catch (e: Exception) {
            Toast.makeText(this, "Geçici dosya oluşturulamadı", Toast.LENGTH_SHORT).show()
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
                ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
                else -> return bitmap
            }

            return Bitmap.createBitmap(
                bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true
            )

        } catch (e: Exception) {
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
}

@Composable
fun MainScreen(
    modifier: Modifier = Modifier,
    onScanDocument: () -> Unit,
    onSelectFromGallery: () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Belge Tarayıcı",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        Text(
            text = "Google ML Kit ile güçlendirilmiş profesyonel belge tarayıcı",
            fontSize = 16.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 48.dp)
        )

        Button(
            onClick = onScanDocument,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            )
        ) {
            Text(
                text = "📷 Kamera ile Tara",
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedButton(
            onClick = onSelectFromGallery,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            Text(
                text = "🖼️ Galeriden Seç",
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "Özellikler:",
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Text("• Kamera ile canlı belge tarama")
                Text("• Galeriden otomatik belge tespiti")
                Text("• Google ML Kit ile AI destekli tespit")
                Text("• Akıllı perspektif düzeltme")
                Text("• Manuel ayarlama seçeneği")
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Geliştirici bilgisi
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "👨‍💻 Geliştirici",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Text(
                    text = "Melih Yeşilyurt",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Android Geliştirici",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun MainScreenPreview() {
    DocumentScanDemoTheme {
        MainScreen(
            onScanDocument = {},
            onSelectFromGallery = {}
        )
    }
}