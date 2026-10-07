package com.example.goldalarm

import android.Manifest
import android.content.*
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var priceView: TextView
    private lateinit var listBox: LinearLayout
    private val prefs by lazy { getSharedPreferences("gold", Context.MODE_PRIVATE) }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) {
            priceView.text = "قیمت انس: " + i?.getDoubleExtra("p", 0.0)
            refresh()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.layoutDirection = View.LAYOUT_DIRECTION_RTL

        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 48, 48, 48)
        }

        priceView = TextView(this).apply { textSize = 22f; text = "قیمت انس: ---" }
        root.addView(priceView)

        val input = EditText(this).apply {
            hint = "قیمت هدف (مثلاً 2650.5)"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        }
        root.addView(input)

        root.addView(Button(this).apply {
            text = "افزودن قیمت"
            setOnClickListener {
                val v = input.text.toString().toDoubleOrNull()
                if (v != null) {
                    val list = load().toMutableList().apply { add(v) }
                    save(list)
                    input.text.clear()
                    refresh()
                }
            }
        })

        listBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(listBox)

        root.addView(Button(this).apply {
            text = "شروع پایش و آلارم"
            setOnClickListener {
                ContextCompat.startForegroundService(
                    this@MainActivity, Intent(this@MainActivity, PriceService::class.java)
                )
            }
        })
        root.addView(Button(this).apply {
            text = "توقف"
            setOnClickListener {
                startService(
                    Intent(this@MainActivity, PriceService::class.java).setAction("STOP_SERVICE")
                )
            }
        })

        setContentView(ScrollView(this).apply { addView(root) })
        refresh()
    }

    private fun load(): List<Double> =
        prefs.getString("targets", "")!!.split(",").filter { it.isNotBlank() }.map { it.toDouble() }

    private fun save(list: List<Double>) =
        prefs.edit().putString("targets", list.joinToString(",")).apply()

    private fun refresh() {
        listBox.removeAllViews()
        load().forEach { t ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            row.addView(TextView(this).apply {
                text = t.toString(); textSize = 18f
                layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
            })
            row.addView(Button(this).apply {
                text = "حذف"
                setOnClickListener { save(load().filter { it != t }); refresh() }
            })
            listBox.addView(row)
        }
    }

    override fun onStart() {
        super.onStart()
        ContextCompat.registerReceiver(
            this, receiver, IntentFilter("GOLD_PRICE"), ContextCompat.RECEIVER_NOT_EXPORTED
        )
        refresh()
    }

    override fun onStop() {
        unregisterReceiver(receiver)
        super.onStop()
    }
}
