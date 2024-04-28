package com.rugved.lightandbrightness

import android.content.ContentValues.TAG
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import android.widget.Button
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.Timer
import java.util.TimerTask

class MainActivity : AppCompatActivity() {

    private lateinit var lightSensor: Sensor
    private lateinit var lightSensorManager: SensorManager
    private lateinit var brightnessTextView: TextView
    private lateinit var lightTextView: TextView
    private lateinit var recordButton: Button
    private lateinit var shareButton: Button
    private lateinit var clearButton: Button
    private var isRecording = false
    private val fileName = "light_and_brightness_data.csv"
    private var gammaSpaceMin: Int = 0
    private var gammaSpaceMax: Int = 65535
    private var timer: Timer? = null
    private var r = 0.5f
    private var a = 0.17883277f
    private var b = 0.28466892f
    private var c = 0.55991073f
    private var lightSensorValue: Float = 0.0f
    private var brightnessValue: Int = 0

    //0.55991073f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        enableEdgeToEdge()
//        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
//            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
//            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
//            insets
//        }

        lightSensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        lightSensor = lightSensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)!!

        brightnessTextView = findViewById(R.id.brightness_text_view)
        lightTextView = findViewById(R.id.light_text_view)

        recordButton = findViewById(R.id.record_button)
        shareButton = findViewById(R.id.clear_button)
        clearButton = findViewById(R.id.clear_button)

        recordButton.setOnClickListener {
            if (isRecording) {
                stopRecording()
            } else {
                startRecording()
            }
        }

        shareButton.setOnClickListener {
            shareFile()
        }

        clearButton.setOnClickListener {
            clearFile()
        }
    }

    private val lightSensorListener: SensorEventListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent?) {
            if (event?.sensor?.type == Sensor.TYPE_LIGHT) {
                val lightValue = event.values[0]
                lightSensorValue = lightValue
                lightTextView.text = buildString {
                    append(lightValue)
                    append(" lux")
                }
                Log.d(TAG, "onSensorChanged Light: $lightValue")
                val currentBrightness = getScreenBrightness()
                brightnessTextView.text = buildString {
                    append((currentBrightness).toString())
                    append("%")
                }
                Log.d(TAG, "onSensorChanged Brightness: $currentBrightness")
                if(isRecording){
                    saveData()
                }
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        }

    }

    private fun startRecording() {
        isRecording = true
        recordButton.text = "Stop Recording"

        timer = Timer()
        timer!!.scheduleAtFixedRate(object : TimerTask() {
            override fun run() {
                try {
                    saveData()
                } catch (e: Exception) {
                    // Handle the exception gracefully
                    e.printStackTrace()
                }
            }
        }, 0, 1000)
    }

    private fun stopRecording() {
        isRecording = false
        recordButton.text = "Start Recording"
        timer?.cancel()
    }

    private fun shareFile() {
        val file = File(Environment.getExternalStorageDirectory(), "light_and_brightness_data.csv")
        val intent = Intent(Intent.ACTION_SEND)
        intent.type = "text/csv"
        intent.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(file))
        startActivity(Intent.createChooser(intent, "Share CSV File"))
    }


    @RequiresApi(Build.VERSION_CODES.Q)
    private fun saveData() {
        val file = File(Environment.DIRECTORY_DOWNLOADS, fileName)
        //val file = File(Environment.getExternalStorageDirectory(), "light_and_brightness_data.csv")
        val calendar = Calendar.getInstance()
        val dateFormat = SimpleDateFormat("yyyy-MM-dd")
        val timeFormat = SimpleDateFormat("HH:mm:ss")
        val date = dateFormat.format(calendar.time)
        val time = timeFormat.format(calendar.time)
        val fos = FileOutputStream(file, true)

        val finalLightValue = String.format(Locale.US, "%.3f", lightSensorValue)
        val finalBrightnessValue = String.format(Locale.US, "%d", brightnessValue)

        val csvData = "$date,$time,$finalLightValue,$finalBrightnessValue\n"

        if (file.length() == 0L) {
            fos.write("Date,Time,Light Value (lux),Brightness Value (%)\n".toByteArray())
        }

        fos.write(csvData.toByteArray())
        fos.close()
        // Insert the file into the MediaProvider
        //val fileUri = Uri.fromFile(file)
        //val insertUri: Uri = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        //contentResolver.insert(insertUri, fileUri)
    }

    private fun clearFile() {
        val file = File(Environment.getExternalStorageDirectory(), "light_and_brightness_data.csv")
        file.delete()
    }

    private fun getScreenBrightness(): Int {
        val contentResolver = applicationContext.contentResolver
        val brightness = Settings.System.getFloat(contentResolver, Settings.System.SCREEN_BRIGHTNESS)
        val maxBrightness = 255.0f
        val minBrightness = 0.0f
        val linearGamma = convertLinearToGammaFloat(brightness, minBrightness, maxBrightness)
        val result = getPercentage(linearGamma.toDouble(), gammaSpaceMin, gammaSpaceMax)
        //val percentage = brightnessToPercentage(brightness, maxBrightness)
        brightnessValue = result.toInt()
        return brightnessValue
    }

    private fun convertLinearToGammaFloat(`val`: Float, min: Float, max: Float): Double {
        // For some reason, HLG normalizes to the range [0, 12] rather than [0, 1]
        val normalizedVal = MathUtils.norm(min, max, `val`) * 12
        val ret: Float = if (normalizedVal <= 1f) {
            MathUtils.sqrt(normalizedVal) * r
        } else {
            a * MathUtils.log(normalizedVal - b) + c
        }

        return Math.round(MathUtils.lerp(gammaSpaceMin, gammaSpaceMax, ret)).toDouble()
    }

    // function getPercentage()

    private fun getPercentage(value: Double, min: Int, max: Int): Float {
        if (value > max) {
            return 1.0f
        }
        if (value <= min) {
            return 0.0f
        }
        return ((value - min) / (max - min) * 100).toFloat()
    }

//    fun brightnessToPercentage(brightness: Int, maxBrightness: Int): Int {
//        return (100 * (Math.log10(brightness + 1.0) / Math.log10(maxBrightness + 1.0))).toInt()
//    }

    override fun onResume() {
        super.onResume()
        lightSensorManager.registerListener(lightSensorListener, lightSensor, SensorManager.SENSOR_DELAY_NORMAL)
    }

    override fun onPause() {
        super.onPause()
        lightSensorManager.unregisterListener(lightSensorListener)
    }


}