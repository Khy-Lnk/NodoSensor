package com.example.nodosensor

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

class MainActivity : AppCompatActivity() {

    private var bluetoothAdapter: BluetoothAdapter? = null
    private var socket: BluetoothSocket? = null
    // Debe ser el mismo UUID que pusimos en la tablet
    private val MY_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    private lateinit var tvEstado: TextView
    private lateinit var tvActuadores: TextView
    private var enviandoDatos = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvEstado = findViewById(R.id.tvEstado)
        tvActuadores = findViewById(R.id.tvActuadores)
        val btnConectar = findViewById<Button>(R.id.btnConectar)

        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter

        btnConectar.setOnClickListener {
            conectarATablet()
        }
    }

    private fun conectarATablet() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Faltan permisos. Acepta en los ajustes de la app.", Toast.LENGTH_SHORT).show()
            return
        }

        val dispositivosVinculados = bluetoothAdapter?.bondedDevices
        if (dispositivosVinculados.isNullOrEmpty()) {
            Toast.makeText(this, "Ve a los ajustes de Bluetooth y vincula la tablet primero.", Toast.LENGTH_LONG).show()
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) { tvEstado.text = "Estado: Buscando Estación..." }

            var conectado = false
            for (dispositivo in dispositivosVinculados) {
                try {
                    socket = dispositivo.createRfcommSocketToServiceRecord(MY_UUID)
                    socket?.connect()
                    conectado = true
                    break
                } catch (e: Exception) {
                    socket?.close()
                }
            }

            if (conectado) {
                withContext(Dispatchers.Main) { tvEstado.text = "Estado: ✅ ¡Conectado a la Tablet!" }
                enviandoDatos = true
                iniciarEnvioDeTelemetria()
                escucharComandosDeActuadores()
            } else {
                withContext(Dispatchers.Main) {
                    tvEstado.text = "Estado: ❌ Error al conectar"
                    Toast.makeText(this@MainActivity, "Abre la app Invernadero en la tablet primero", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun iniciarEnvioDeTelemetria() {
        lifecycleScope.launch(Dispatchers.IO) {
            val outputStream: OutputStream? = socket?.outputStream
            var temperatura = 22.0

            while (enviandoDatos) {
                try {
                    temperatura += 1.5
                    if (temperatura > 34.0) temperatura = 20.0

                    val humedad = (50..80).random()
                    val luminosidad = (40..100).random()

                    val mensaje = "$temperatura,$humedad,$luminosidad"
                    outputStream?.write(mensaje.toByteArray())

                    delay(3000)
                } catch (e: Exception) {
                    enviandoDatos = false
                }
            }
        }
    }

    private fun escucharComandosDeActuadores() {
        lifecycleScope.launch(Dispatchers.IO) {
            val inputStream: InputStream? = socket?.inputStream
            val buffer = ByteArray(1024)
            while (enviandoDatos) {
                try {
                    val bytes = inputStream?.read(buffer) ?: 0
                    if (bytes > 0) {
                        val comando = String(buffer, 0, bytes).trim()
                        withContext(Dispatchers.Main) {
                            tvActuadores.text = comando
                        }
                    }
                } catch (e: Exception) {
                    enviandoDatos = false
                }
            }
        }
    }
}