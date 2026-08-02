package dev.leeeet.wallpapermoe.service

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.pm.PackageManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.leeeet.wallpapermoe.MoeApplication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WallpaperServiceTest {
    private val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as MoeApplication
    private val component = ComponentName(app, MoeWallpaperService::class.java)

    @Test
    fun serviceIsRegisteredForAndroidWallpaperBinding() {
        val info = app.packageManager.getServiceInfo(
            component,
            PackageManager.ComponentInfoFlags.of(PackageManager.GET_META_DATA.toLong()),
        )

        assertTrue(info.exported)
        assertEquals("android.permission.BIND_WALLPAPER", info.permission)
        assertNotEquals(0, info.metaData.getInt(WALLPAPER_SERVICE_META_DATA))
    }

    @Test
    fun activationOpensNativePreviewForThisService() {
        val intent = WallpaperRuntime.activationIntent(app)

        assertEquals(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER, intent.action)
        assertEquals(
            component,
            intent.getParcelableExtra(
                WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                ComponentName::class.java,
            ),
        )
    }

    private companion object {
        const val WALLPAPER_SERVICE_META_DATA = "android.service.wallpaper"
    }
}
