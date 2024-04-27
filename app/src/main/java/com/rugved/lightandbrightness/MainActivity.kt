package com.rugved.lightandbrightness

import android.content.ContentValues.TAG
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var lightSensor: Sensor
    private lateinit var lightSensorManager: SensorManager
    private lateinit var brightnessTextView: TextView
    private lateinit var lightTextView: TextView
    private var isRecording = false
    private val fileName = "light_and_brightness_data.csv"
    private var gammaSpaceMin: Int = 0
    private var gammaSpaceMax: Int = 65535
    private var r = 0.5f
    private var a = 0.17883277f
    private var b = 0.28466892f
    private var c = 0.55991073f

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

    }

    private val lightSensorListener:
            SensorEventListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent?) {
            if (event?.sensor?.type == Sensor.TYPE_LIGHT) {
                val lightValue = event.values[0]
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
                    writeDataToFile("$lightValue, ${getScreenBrightness()}")
                }
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        }

    }

    private fun writeDataToFile(data: String) {
        val file = File(getExternalFilesDir(null), fileName)
        val calendar = Calendar.getInstance()
        val dateFormat = SimpleDateFormat("yyyy-MM-dd")
        val timeFormat = SimpleDateFormat("HH:mm:ss")
        val date = dateFormat.format(calendar.time)
        val time = timeFormat.format(calendar.time)

        val lightValue = String.format(Locale.US, "%.3f", data.split(",")[0].toFloat())
        val brightnessValue = data.split(",")[1].toInt()

        file.appendText("$data, $time, $lightValue, $brightnessValue\n")
    }

    private fun getScreenBrightness(): Int {
        val contentResolver = applicationContext.contentResolver
        //val brightness: Float = Settings.System.getFloat(contentResolver, Settings.System.SCREEN_BRIGHTNESS)
        val brightness = Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS)
        val maxBrightness = 255.0f
        val minBrightness = 0.0f
        val linearGamma = convertLinearToGammaFloat(brightness.toFloat(), minBrightness, maxBrightness)
        val result = getPercentage(linearGamma.toDouble(), gammaSpaceMin, gammaSpaceMax)
        //val percentage = brightnessToPercentage(brightness, maxBrightness)
        return result.toInt()
    }

    private fun convertLinearToGammaFloat(`val`: Float, min: Float, max: Float): Int {
        // For some reason, HLG normalizes to the range [0, 12] rather than [0, 1]
        val normalizedVal = MathUtils.norm(min, max, `val`) * 12
        val ret: Float = if (normalizedVal <= 1f) {
            MathUtils.sqrt(normalizedVal) * r
        } else {
            a * MathUtils.log(normalizedVal - b) + c
        }

        return Math.round(MathUtils.lerp(gammaSpaceMin, gammaSpaceMax, ret))
    }

    // function getPercentage()

    private fun getPercentage(value: Double, min: Int, max: Int): Double {
        if (value > max) {
            return 1.0
        }
        if (value < min) {
            return 0.0
        }
        return (value - min) / (max - min)
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