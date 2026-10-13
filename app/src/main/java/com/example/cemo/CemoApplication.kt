package com.example.cemo

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache

class CemoApplication : Application(), ImageLoaderFactory {
    override fun onCreate() {
        super.onCreate()
        AppState.init(this)
        com.example.cemo.data.notification.NotificationHelper.createNotificationChannels(this)
    }

    /**
     * App-wide Coil loader with an explicit on-disk cache, so a profile photo
     * survives process death and offline launches instead of being refetched.
     *
     * `respectCacheHeaders(false)` matters most here: Firebase Storage serves
     * objects with `Cache-Control: private, max-age=0` unless the upload sets
     * otherwise, which would stop Coil from persisting the avatar at all. We
     * handle freshness ourselves by keying the request on the profile's
     * updatedAt, so ignoring the server headers is safe.
     */
    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(50L * 1024 * 1024)
                    .build()
            }
            .respectCacheHeaders(false)
            .crossfade(true)
            .build()
}
