package dev.opluskeycontrol.input

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import dev.opluskeycontrol.KeyControlApp
import dev.opluskeycontrol.R

class DisableTile : TileService() {
    override fun onStartListening() {
        qsTile?.apply { state = Tile.STATE_ACTIVE; label = getString(R.string.emergency_disable); updateTile() }
    }
    override fun onClick() {
        (application as KeyControlApp).store.disable()
        qsTile?.apply { state = Tile.STATE_INACTIVE; updateTile() }
    }
}
