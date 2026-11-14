package dev.leeeet.wallpapermoe.service

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import dev.leeeet.wallpapermoe.MoeApp

class NextWallpaperTile : TileService() {
    override fun onStartListening() {
        val tile = qsTile ?: return
        tile.state = if (MoeWallpaperService.isActive(this)) Tile.STATE_INACTIVE else Tile.STATE_UNAVAILABLE
        tile.updateTile()
    }

    override fun onClick() {
        if (MoeWallpaperService.isActive(this)) (application as MoeApp).graph.changer.changeAll()
    }
}
