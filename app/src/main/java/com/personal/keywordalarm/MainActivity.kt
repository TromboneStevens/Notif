package com.personal.keywordalarm

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.personal.keywordalarm.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.keywordInput.setText(Prefs.getKeywordsRaw(this))
        binding.switchEnabled.isChecked = Prefs.isEnabled(this)
        binding.switchKeepAwake.isChecked = Prefs.isKeepAwake(this)
        binding.switchCaseSensitive.isChecked = Prefs.isCaseSensitive(this)
        binding.cooldownInput.setText(Prefs.getCooldownSeconds(this).toString())

        binding.saveButton.setOnClickListener { saveSettings() }

        binding.switchEnabled.setOnCheckedChangeListener { _, checked ->
            Prefs.setEnabled(this, checked)
            if (checked) {
                MonitoringService.start(this)
                if (!isAccessibilityServiceEnabled()) {
                    Toast.makeText(
                        this,
                        getString(R.string.enable_service_prompt),
                        Toast.LENGTH_LONG
                    ).show()
                }
            } else {
                MonitoringService.stop(this)
            }
        }

        // If monitoring was left on, make sure the banner is showing.
        if (Prefs.isEnabled(this)) {
            MonitoringService.start(this)
        }

        binding.accessibilityButton.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        binding.testButton.setOnClickListener {
            val intent = Intent(this, AlarmService::class.java).apply {
                action = AlarmService.ACTION_START
                putExtra(AlarmService.EXTRA_KEYWORD, "TEST")
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        }

        binding.stopButton.setOnClickListener {
            val intent = Intent(this, AlarmService::class.java).apply {
                action = AlarmService.ACTION_STOP
            }
            startService(intent)
        }

        maybeRequestNotificationPermission()
    }

    override fun onResume() {
        super.onResume()
        // Reflect the real monitoring state in case it was turned off from the
        // notification banner while the app was in the background.
        val enabled = Prefs.isEnabled(this)
        if (binding.switchEnabled.isChecked != enabled) {
            binding.switchEnabled.isChecked = enabled
        }
        updateStatusLabel()
    }

    private fun saveSettings() {
        Prefs.setKeywords(this, binding.keywordInput.text.toString())
        val secs = binding.cooldownInput.text.toString().toIntOrNull() ?: 10
        androidx.preference.PreferenceManager.getDefaultSharedPreferences(this)
            .edit()
            .putInt(Prefs.KEY_COOLDOWN, secs.coerceAtLeast(0))
            .putBoolean(Prefs.KEY_KEEP_AWAKE, binding.switchKeepAwake.isChecked)
            .putBoolean(Prefs.KEY_CASE_SENSITIVE, binding.switchCaseSensitive.isChecked)
            .apply()
        Toast.makeText(this, getString(R.string.saved), Toast.LENGTH_SHORT).show()
        updateStatusLabel()
    }

    private fun updateStatusLabel() {
        val a11y = isAccessibilityServiceEnabled()
        binding.statusLabel.text = if (a11y) {
            getString(R.string.status_ok)
        } else {
            getString(R.string.status_needs_service)
        }
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val expected = "$packageName/${KeywordAccessibilityService::class.java.name}"
        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        val splitter = TextUtils.SimpleStringSplitter(':')
        splitter.setString(enabledServices)
        while (splitter.hasNext()) {
            val name = splitter.next()
            if (name.equals(expected, ignoreCase = true)) return true
            // Some OEMs store the fully-qualified class without the package prefix.
            if (name.endsWith(KeywordAccessibilityService::class.java.name)) return true
        }
        return false
    }

    private fun maybeRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val perm = android.Manifest.permission.POST_NOTIFICATIONS
            if (checkSelfPermission(perm) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(perm), 100)
            }
        }
    }
}
