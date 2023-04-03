package com.example.gyroscope

import android.content.Context.SENSOR_SERVICE
import android.hardware.Sensor
import android.hardware.Sensor.TYPE_ORIENTATION
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gyroscope.ui.theme.GyroscopeTheme
import java.math.RoundingMode
import java.util.*
import kotlin.math.*
import kotlin.system.measureTimeMillis

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            GyroscopeTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Metric()
                }
            }
        }
    }
}

@Composable
fun Metric() {
    val sensorManager = LocalContext.current.getSystemService(SENSOR_SERVICE) as SensorManager
    val gyroscopeSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    val orientationSensor: Sensor? = sensorManager.getDefaultSensor(TYPE_ORIENTATION)
    val gravitySensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY)
    val accelerometerSensor: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    var p by remember { mutableStateOf(0.0) }
    var n by remember { mutableStateOf(0.0) }
    var r by remember { mutableStateOf(0.0) }

    var z_angle by remember { mutableStateOf(0.0) }
    var y_angle by remember { mutableStateOf(0.0) }

    var gravity by remember { mutableStateOf(arrayOf(0.0, 0.0)) }

    var x by remember { mutableStateOf(0.0) }
    var y by remember { mutableStateOf(0.0) }
    var z by remember { mutableStateOf(0.0) }

    fun round(num: Double): Double {
        return num.toBigDecimal().setScale(3, RoundingMode.UP).toDouble()
    }

    fun number2Matrix(num: Double, r: Int, c: Int): Array<DoubleArray> {
        val matrix = Array(r) { DoubleArray(c) }
        for (i in 0 until r) {
            for (j in 0 until c) {
                matrix[i][j] = num
            }
        }

        return matrix
    }

    fun identityMatrix(r: Int, c: Int): Array<DoubleArray> {
        val matrix = Array(r) { DoubleArray(c) }
        for (i in 0 until r) {
            for (j in 0 until c) {
                if (i == j) {
                    matrix[i][j] = 1.0
                } else {
                    matrix[i][j] = 0.0
                }
            }
        }

        return matrix
    }

    var C by remember { mutableStateOf(number2Matrix(0.0, 3, 3)) }
    var prevC by remember {
        mutableStateOf(
            arrayOf(
                doubleArrayOf(1.0, 0.0, 0.0),
                doubleArrayOf(0.0, 1.0, 0.0),
                doubleArrayOf(0.0, 0.0, 1.0)
            )
        )
    }

    var V by remember {
        mutableStateOf(
            arrayOf(
                doubleArrayOf(0.0),
                doubleArrayOf(0.0),
                doubleArrayOf(0.0)
            )
        )
    }

    var prevV by remember {
        mutableStateOf(
            arrayOf(
                doubleArrayOf(0.0),
                doubleArrayOf(0.0),
                doubleArrayOf(0.0)
            )
        )
    }

    var S by remember {
        mutableStateOf(
            arrayOf(
                doubleArrayOf(0.0),
                doubleArrayOf(0.0),
                doubleArrayOf(0.0)
            )
        )
    }

    var prevS by remember {
        mutableStateOf(
            arrayOf(
                doubleArrayOf(0.0),
                doubleArrayOf(0.0),
                doubleArrayOf(0.0)
            )
        )
    }

    fun eventTimestamp2timeStamp(eventTimestamp: Long): Long {
        return Date().time + (eventTimestamp - System.nanoTime()) / 1000000000L
    }

    fun multiplyMatrixWithScalar(
        matrix: Array<DoubleArray>,
        scalar: Double,
        r: Int,
        c: Int
    ): Array<DoubleArray> {
        val resultMatrix = Array(r) { DoubleArray(c) }
        for (i in 0 until r) {
            for (j in 0 until c) {
                resultMatrix[i][j] = matrix[i][j] * scalar
            }
        }

        return resultMatrix
    }

    fun multiplyMatrices(
        firstMatrix: Array<DoubleArray>,
        secondMatrix: Array<DoubleArray>,
        r1: Int,
        c1: Int,
        c2: Int
    ): Array<DoubleArray> {
        val product = Array(r1) { DoubleArray(c2) }
        for (i in 0 until r1) {
            for (j in 0 until c2) {
                for (k in 0 until c1) {
                    product[i][j] += firstMatrix[i][k] * secondMatrix[k][j]
                }
            }
        }

        return product
    }

    fun addMatrix(
        firstMatrix: Array<DoubleArray>,
        secondMatrix: Array<DoubleArray>,
        r: Int,
        c: Int
    ): Array<DoubleArray> {
        val sum = Array(r) { DoubleArray(c) }
        for (i in 0 until r) {
            for (j in 0 until c) {
                sum[i][j] = firstMatrix[i][j] + secondMatrix[i][j]
            }
        }

        return sum
    }

    val NS2S = 1.0 / 1000000000.0
    var timestamp: Long = 0

    val sensorEventListener = object : SensorEventListener {
        override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
            // method to check accuracy changed in sensor.
        }

        // on below line we are creating a sensor on sensor changed
        override fun onSensorChanged(event: SensorEvent) {

            if (timestamp == 0L) {
                timestamp = eventTimestamp2timeStamp(event.timestamp)
                return
            }

            if (event.sensor.type == Sensor.TYPE_GYROSCOPE) {
                p = round(event.values[0]).toDouble()
                n = round(event.values[1]).toDouble()
                r = round(event.values[2]).toDouble()

                val dT = (event.timestamp - timestamp) / 1000000

                val B = arrayOf(
                    doubleArrayOf(0.0, 0.0, n * dT),
                    doubleArrayOf(0.0, 0.0, -p * dT),
                    doubleArrayOf(-n * dT, p * dT, 0.0)
                )

                val B2 = multiplyMatrices(B, B, 3, 3, 3)

                val sigma = sqrt(
                    dT.toDouble().pow(2.0) * (p.pow(2.0) + n.pow(2.0))
                )

                if (sigma == 0.0) return

                C = (
                        multiplyMatrixWithScalar(
                            B,
                            sin(sigma) / sigma,
                            3,
                            3
                        )
                        )

                C = addMatrix(
                    identityMatrix(3, 3),
                    C,
                    3,
                    3
                )

                C = addMatrix(
                    C,
                    multiplyMatrixWithScalar(
                        B2,
                        (1 - cos(sigma)) / sigma.pow(2.0),
                        3,
                        3
                    ),
                    3,
                    3
                )

                C = multiplyMatrices(prevC, C, 3, 3, 3)

                prevC = C
            }

            if (event.sensor.type == Sensor.TYPE_GRAVITY) {
                gravity[0] = event.values[0].toDouble()
                gravity[1] = event.values[1].toDouble()
            }

            if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
                val alpha = 0.5

                gravity[0] = alpha * gravity[0] + (1 - alpha) * event.values[0]
                gravity[1] = alpha * gravity[1] + (1 - alpha) * event.values[1]

                x = event.values[0] - gravity[0]
                y = event.values[1] - gravity[1]

                val dT = (eventTimestamp2timeStamp(event.timestamp) - timestamp) / 1000.0

                var a = arrayOf(
                    doubleArrayOf(x),
                    doubleArrayOf(y),
                    doubleArrayOf(0.0)
                )

                V = addMatrix(
                    prevV,
                    multiplyMatrixWithScalar(
                        a,
//                        addMatrix(a, number2Matrix(-9.8, 1, 2), 1, 2),
                        dT,
                        3,
                        1
                    ),
                    3,
                    1
                )

                Log.d("Gyroscopeee: ", "a: ${a[0][0]} - v: ${V[0][0]}")

                S = addMatrix(prevS, multiplyMatrixWithScalar(V, dT, 3, 1), 3, 1)

                prevV = V
                prevS = S
            }

            timestamp = eventTimestamp2timeStamp(event.timestamp)
        }
    }

    LaunchedEffect(key1 = null, block = {
        sensorManager.registerListener(
            sensorEventListener,
            gyroscopeSensor,
            SensorManager.SENSOR_DELAY_NORMAL
        )

        sensorManager.registerListener(
            sensorEventListener,
            gravitySensor,
            SensorManager.SENSOR_DELAY_NORMAL
        )

        sensorManager.registerListener(
            sensorEventListener,
            accelerometerSensor,
            SensorManager.SENSOR_DELAY_NORMAL
        )
    })

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "Gyroscope", color = Color.Green, fontSize = 36.sp)

        Spacer(modifier = Modifier.height(8.dp))

//        for (i in 0 until 3) {
//            for (j in 0 until 3) {
//                Text(
//                    text = "C[$i][$j]: ${C[i][j]}",
//                    fontSize = 20.sp
//                )
//            }
//        }

        Text(
            text = "z_angle: $z_angle",
            fontSize = 20.sp
        )
        Text(
            text = "y_angle $y_angle",
            fontSize = 20.sp
        )

        Text(
            text = "a_x: ${round(x)}",
            fontSize = 20.sp
        )
        Text(
            text = "a_y: ${round(y)}",
            fontSize = 20.sp
        )

        Text(
            text = "v_x: ${round(V[0][0])}",
            fontSize = 20.sp
        )
        Text(
            text = "v_y: ${round(V[1][0])}",
            fontSize = 20.sp
        )

        Text(
            text = "s_x: ${round(S[0][0])}",
            fontSize = 20.sp
        )
        Text(
            text = "s_y: ${round(S[1][0])}",
            fontSize = 20.sp
        )
    }
}