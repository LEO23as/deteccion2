package com.example.deteccion

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.gms.tasks.OnFailureListener
import com.google.android.gms.tasks.OnSuccessListener
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.IOException

class MainActivity : AppCompatActivity(), OnSuccessListener<Text>, OnFailureListener {
    private val REQUEST_GALLERY = 1
    private val REQUEST_CAMERA = 2
    private val PERMISSION_CAMERA_CODE = 100
    private lateinit var mImageView: ImageView
    private lateinit var txtResults: TextView
    private var mSelectedImage: Bitmap? = null
    private lateinit var englishSpanishTranslator: Translator
    
    // Diccionario de respaldo para palabras comunes si falla el traductor online
    private val diccionarioManual = mapOf(
        "Chair" to "Silla",
        "Product" to "Producto",
        "Armrest" to "Reposabrazos",
        "Furniture" to "Mueble",
        "Table" to "Mesa",
        "Person" to "Persona",
        "Computer" to "Computadora",
        "Screen" to "Pantalla",
        "Clothing" to "Ropa",
        "Bottle" to "Botella"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        mImageView = findViewById(R.id.image_view)
        txtResults = findViewById(R.id.txtresults)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Configurar traductor
        val options = TranslatorOptions.Builder()
            .setSourceLanguage(TranslateLanguage.ENGLISH)
            .setTargetLanguage(TranslateLanguage.SPANISH)
            .build()
        englishSpanishTranslator = Translation.getClient(options)

        // Descargar modelo (quitamos restricciones de WiFi para asegurar descarga)
        val conditions = DownloadConditions.Builder().build() 
        englishSpanishTranslator.downloadModelIfNeeded(conditions)
            .addOnSuccessListener {
                Toast.makeText(this, "Traductor listo", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error descarga: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    fun abrirGaleria(view: View) {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        startActivityForResult(intent, REQUEST_GALLERY)
    }

    fun abrirCamara(view: View) {
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.CAMERA), PERMISSION_CAMERA_CODE)
        } else {
            lanzarCamara()
        }
    }

    private fun lanzarCamara() {
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        startActivityForResult(intent, REQUEST_CAMERA)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_CAMERA_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                lanzarCamara()
            } else {
                Toast.makeText(this, "Permiso de cámara denegado", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == RESULT_OK && data != null) {
            try {
                if (requestCode == REQUEST_CAMERA) {
                    mSelectedImage = data.extras?.get("data") as Bitmap
                } else if (requestCode == REQUEST_GALLERY) {
                    val uri = data.data
                    mSelectedImage = MediaStore.Images.Media.getBitmap(contentResolver, uri)
                }
                mImageView.setImageBitmap(mSelectedImage)
                txtResults.text = "Resultados:"
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }

    fun OCRfx(v: View) {
        if (mSelectedImage != null) {
            mImageView.setImageBitmap(mSelectedImage)
            val image = InputImage.fromBitmap(mSelectedImage!!, 0)
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            recognizer.process(image)
                .addOnSuccessListener(this)
                .addOnFailureListener(this)
        } else {
            Toast.makeText(this, "Selecciona una imagen", Toast.LENGTH_SHORT).show()
        }
    }

    fun Rostrosfx(v: View) {
        if (mSelectedImage != null) {
            val image = InputImage.fromBitmap(mSelectedImage!!, 0)
            val options = FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                .build()
            val detector = FaceDetection.getClient(options)
            detector.process(image)
                .addOnSuccessListener { faces ->
                    if (faces.isEmpty()) {
                        txtResults.text = "No hay rostros"
                        mImageView.setImageBitmap(mSelectedImage)
                    } else {
                        txtResults.text = "Rostros: ${faces.size}"
                        dibujarRectangulosRostros(faces)
                    }
                }
                .addOnFailureListener(this)
        } else {
            Toast.makeText(this, "Selecciona una imagen", Toast.LENGTH_SHORT).show()
        }
    }

    private fun dibujarRectangulosRostros(faces: List<Face>) {
        val bitmap = mSelectedImage!!.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(bitmap)
        val paint = Paint().apply {
            color = Color.RED
            style = Paint.Style.STROKE
            strokeWidth = 5f
        }
        for (face in faces) {
            canvas.drawRect(face.boundingBox, paint)
        }
        mImageView.setImageBitmap(bitmap)
    }

    fun QRfx(v: View) {
        if (mSelectedImage != null) {
            val image = InputImage.fromBitmap(mSelectedImage!!, 0)
            val scanner = BarcodeScanning.getClient()
            scanner.process(image)
                .addOnSuccessListener { barcodes ->
                    if (barcodes.isEmpty()) {
                        txtResults.text = "No hay QR"
                        mImageView.setImageBitmap(mSelectedImage)
                    } else {
                        var resultText = ""
                        for (barcode in barcodes) {
                            resultText += "QR: ${barcode.rawValue}\n"
                        }
                        txtResults.text = resultText
                        dibujarRectangulosQR(barcodes)
                    }
                }
                .addOnFailureListener(this)
        } else {
            Toast.makeText(this, "Selecciona una imagen", Toast.LENGTH_SHORT).show()
        }
    }

    private fun dibujarRectangulosQR(barcodes: List<Barcode>) {
        val bitmap = mSelectedImage!!.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(bitmap)
        val paint = Paint().apply {
            color = Color.GREEN
            style = Paint.Style.STROKE
            strokeWidth = 5f
        }
        for (barcode in barcodes) {
            barcode.boundingBox?.let { canvas.drawRect(it, paint) }
        }
        mImageView.setImageBitmap(bitmap)
    }

    fun Objetosfx(v: View) {
        if (mSelectedImage != null) {
            val image = InputImage.fromBitmap(mSelectedImage!!, 0)
            val labeler = ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS)
            
            labeler.process(image)
                .addOnSuccessListener { labels ->
                    if (labels.isEmpty()) {
                        txtResults.text = "No se detectó nada"
                        return@addOnSuccessListener
                    }
                    
                    txtResults.text = "Traduciendo..."
                    val listaFinal = mutableListOf<String>()
                    var procesados = 0
                    
                    for (label in labels) {
                        englishSpanishTranslator.translate(label.text)
                            .addOnSuccessListener { textoTraducido ->
                                listaFinal.add("$textoTraducido ${(label.confidence * 100).toInt()}%")
                                procesados++
                                if (procesados == labels.size) {
                                    txtResults.text = listaFinal.joinToString("\n")
                                }
                            }
                            .addOnFailureListener {
                                // Fallback: Usamos el diccionario manual o el original si no está en el mapa
                                val backup = diccionarioManual[label.text] ?: label.text
                                listaFinal.add("$backup ${(label.confidence * 100).toInt()}%")
                                procesados++
                                if (procesados == labels.size) {
                                    txtResults.text = listaFinal.joinToString("\n")
                                }
                            }
                    }
                }
                .addOnFailureListener(this)
        } else {
            Toast.makeText(this, "Selecciona una imagen primero", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onSuccess(text: Text) {
        txtResults.text = if (text.text.isEmpty()) "Sin texto" else text.text
    }

    override fun onFailure(e: Exception) {
        txtResults.text = "Error: ${e.message}"
    }

    override fun onDestroy() {
        super.onDestroy()
        englishSpanishTranslator.close()
    }
}
