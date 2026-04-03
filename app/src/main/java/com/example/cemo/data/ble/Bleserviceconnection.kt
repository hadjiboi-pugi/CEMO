package com.example.cemo.data.ble

import android.content.*
import android.os.IBinder

/**
 * Reusable ServiceConnection that exposes the bound [BleForegroundService].
 *
 * Usage in a ViewModel (or Activity):
 *
 *   private val conn = BleServiceConnection { service ->
 *       // service is ready – collect service.bleManager.status etc.
 *   }
 *
 *   // Bind:
 *   context.bindService(
 *       Intent(context, BleForegroundService::class.java),
 *       conn, Context.BIND_AUTO_CREATE
 *   )
 *
 *   // Unbind when done:
 *   context.unbindService(conn)
 */
class BleServiceConnection(
    private val onConnected: (BleForegroundService) -> Unit
) : ServiceConnection {

    var service: BleForegroundService? = null
        private set

    override fun onServiceConnected(name: ComponentName, binder: IBinder) {
        service = (binder as BleForegroundService.LocalBinder).getService()
        service?.let { onConnected(it) }
    }

    override fun onServiceDisconnected(name: ComponentName) {
        service = null
    }
}