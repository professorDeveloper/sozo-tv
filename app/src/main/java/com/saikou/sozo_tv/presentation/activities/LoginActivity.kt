package com.saikou.sozo_tv.presentation.activities

import android.os.Bundle
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.app.AppCompatActivity
import com.saikou.sozo_tv.databinding.ActivityLoginBinding

// AppCompatActivity: the splash kicks off the first-launch plugin install, and plugins cast the
// context passed to `Plugin.load()` to AppCompatActivity. See MainActivity.
class LoginActivity : AppCompatActivity() {

    private lateinit var viewBinding: ActivityLoginBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewBinding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(viewBinding.root)
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
    }
}