package org.aust.dialer.core

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class SpeedDialEntry(val digit: Int, val number: String, val name: String?)

/**
 * The only app-specific data stored on the device: theme, language, dial-pad tones and speed dials.
 * Contacts, call history and favorites always live in Android's own providers.
 */
class Prefs(context: Context) {
    private val sp: SharedPreferences =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    private val _themeMode = MutableStateFlow(sp.getInt(K_THEME, THEME_SYSTEM))
    val themeMode: StateFlow<Int> = _themeMode.asStateFlow()

    fun setThemeMode(value: Int) {
        sp.edit().putInt(K_THEME, value).apply()
        _themeMode.value = value
    }

    private val _dynamicColor = MutableStateFlow(sp.getBoolean(K_DYNAMIC, true))
    val dynamicColor: StateFlow<Boolean> = _dynamicColor.asStateFlow()

    fun setDynamicColor(value: Boolean) {
        sp.edit().putBoolean(K_DYNAMIC, value).apply()
        _dynamicColor.value = value
    }

    private val _dtmfTones = MutableStateFlow(sp.getBoolean(K_TONES, true))
    val dtmfTones: StateFlow<Boolean> = _dtmfTones.asStateFlow()

    fun setDtmfTones(value: Boolean) {
        sp.edit().putBoolean(K_TONES, value).apply()
        _dtmfTones.value = value
    }

    /** "" = follow the system, otherwise a BCP-47 tag such as "en" or "ar". */
    var language: String
        get() = sp.getString(K_LANG, "") ?: ""
        set(value) {
            sp.edit().putString(K_LANG, value).apply()
        }

    var setupDone: Boolean
        get() = sp.getBoolean(K_SETUP_DONE, false)
        set(value) {
            sp.edit().putBoolean(K_SETUP_DONE, value).apply()
        }

    private val _speedDials = MutableStateFlow(loadSpeedDials())
    val speedDials: StateFlow<Map<Int, SpeedDialEntry>> = _speedDials.asStateFlow()

    private fun loadSpeedDials(): Map<Int, SpeedDialEntry> {
        val map = LinkedHashMap<Int, SpeedDialEntry>()
        for (digit in 1..9) {
            val raw = sp.getString("$K_SD$digit", null) ?: continue
            val parts = raw.split(SEP, limit = 2)
            val number = parts.getOrNull(0).orEmpty()
            if (number.isEmpty()) continue
            val name = parts.getOrNull(1)?.takeIf { it.isNotEmpty() }
            map[digit] = SpeedDialEntry(digit, number, name)
        }
        return map
    }

    fun setSpeedDial(digit: Int, number: String, name: String?) {
        if (digit !in 1..9 || number.isBlank()) return
        sp.edit().putString("$K_SD$digit", number + SEP + (name ?: "")).apply()
        _speedDials.value = loadSpeedDials()
    }

    fun removeSpeedDial(digit: Int) {
        sp.edit().remove("$K_SD$digit").apply()
        _speedDials.value = loadSpeedDials()
    }

    companion object {
        const val THEME_SYSTEM = 0
        const val THEME_LIGHT = 1
        const val THEME_DARK = 2

        private const val FILE = "aust_dialer_prefs"
        private const val K_THEME = "theme_mode"
        private const val K_DYNAMIC = "dynamic_color"
        private const val K_TONES = "dtmf_tones"
        private const val K_LANG = "language"
        private const val K_SETUP_DONE = "setup_done"
        private const val K_SD = "speed_dial_"
        private const val SEP = "\u001F"

        /** Read without an application instance (used from attachBaseContext). */
        fun readLanguage(context: Context): String =
            context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(K_LANG, "") ?: ""
    }
}
