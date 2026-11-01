package io.github.mehdieskandari.airplaytv.service

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.CompletableFuture

data class TrustedDevice(val id: String, val name: String, val model: String, val addedAt: Long)

// a device waiting for the user to allow or deny it on the TV
class ApprovalRequest(val id: String, val name: String, val model: String, val source: String) {
    val result = CompletableFuture<Boolean>()
}

// devices the user approved once; they connect without asking again
class TrustedDevices(context: Context) {
    private val prefs = context.getSharedPreferences("trusted_devices", Context.MODE_PRIVATE)
    private val _devices = MutableStateFlow(load())
    val devices: StateFlow<List<TrustedDevice>> = _devices.asStateFlow()

    fun isTrusted(id: String): Boolean = id.isNotEmpty() && _devices.value.any { it.id == id }

    @Synchronized
    fun add(id: String, name: String, model: String) {
        if (id.isEmpty()) return
        val list = _devices.value.filterNot { it.id == id } + TrustedDevice(id, name, model, System.currentTimeMillis())
        save(list)
    }

    @Synchronized
    fun remove(id: String) = save(_devices.value.filterNot { it.id == id })

    @Synchronized
    fun clear() = save(emptyList())

    private fun save(list: List<TrustedDevice>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(JSONObject().put("id", it.id).put("name", it.name).put("model", it.model).put("addedAt", it.addedAt))
        }
        prefs.edit().putString(KEY, arr.toString()).apply()
        _devices.value = list
    }

    private fun load(): List<TrustedDevice> = runCatching {
        val arr = JSONArray(prefs.getString(KEY, "[]"))
        (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            TrustedDevice(o.getString("id"), o.optString("name"), o.optString("model"), o.optLong("addedAt"))
        }
    }.getOrDefault(emptyList())

    companion object {
        private const val KEY = "devices"
    }
}
