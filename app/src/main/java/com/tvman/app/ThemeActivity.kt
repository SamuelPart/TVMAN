package com.tvman.app

import android.content.Context
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.tvman.app.databinding.ActivityThemeBinding

/** Mantiene el tema cinematográfico uniforme en todas las secciones de TVMAN. */
class ThemeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityThemeBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityThemeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // TVMAN uses one shared dark theme so screens stay visually consistent with Home.
        getSharedPreferences("iptv_pref", Context.MODE_PRIVATE)
            .edit().putString("theme_pref", "dark").apply()
        binding.checkThemeSystem.visibility = View.GONE
        binding.checkThemeLight.visibility = View.GONE
        binding.checkThemeDark.visibility = View.VISIBLE
        binding.btnThemeBack.setOnClickListener { finish() }
    }
}
