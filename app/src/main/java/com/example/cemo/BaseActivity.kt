package com.example.cemo

import android.content.Context
import androidx.appcompat.app.AppCompatActivity

open class BaseActivity : AppCompatActivity() {
    // Locale is applied in-place in Compose via ProvideLocalizedResources, not
    // through AppCompatDelegate — that recreates the Activity and flickers.
}
