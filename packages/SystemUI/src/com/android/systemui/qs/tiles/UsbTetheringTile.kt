package com.android.systemui.qs.tiles

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbManager
import android.net.TetheringManager
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import android.provider.Settings
import android.service.quicksettings.Tile
import com.android.internal.logging.MetricsLogger
import com.android.systemui.animation.Expandable
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.dagger.qualifiers.Main
import com.android.systemui.plugins.ActivityStarter
import com.android.systemui.plugins.FalsingManager
import com.android.systemui.plugins.qs.QSTile
import com.android.systemui.plugins.statusbar.StatusBarStateController
import com.android.systemui.qs.QSHost
import com.android.systemui.qs.QsEventLogger
import com.android.systemui.qs.logging.QSLogger
import com.android.systemui.qs.tileimpl.QSTileImpl
import com.android.systemui.res.R
import com.android.settingslib.RestrictedLockUtilsInternal.checkIfUsbDataSignalingIsDisabled
import java.util.concurrent.Executor
import javax.inject.Inject

class UsbTetheringTile
@Inject
constructor(
    host: QSHost,
    uiEventLogger: QsEventLogger,
    @Background backgroundLooper: Looper,
    @Main mainHandler: Handler,
    falsingManager: FalsingManager,
    metricsLogger: MetricsLogger,
    statusBarStateController: StatusBarStateController,
    activityStarter: ActivityStarter,
    qsLogger: QSLogger,
) : QSTileImpl<QSTile.State>(
    host,
    uiEventLogger,
    backgroundLooper,
    mainHandler,
    falsingManager,
    metricsLogger,
    statusBarStateController,
    activityStarter,
    qsLogger,
) {
    private val mIcon = ResourceIcon.get(com.android.internal.R.drawable.stat_sys_data_usb)
    private val tetheringManager: TetheringManager =
        mContext.getSystemService(TetheringManager::class.java)

    private val usbRegexs: Array<String> = tetheringManager.getTetherableUsbRegexs()

    private var usbTetheringActive = false
    private var usbConnected = false
    private var massStorageActive = false

    private val mainExecutor = Executor { command -> mainHandler.post(command) }

    private val tetheringEventCallback = object : TetheringManager.TetheringEventCallback {
        override fun onTetheredInterfacesChanged(interfaces: List<String>) {
            usbTetheringActive = interfaces.any { iface ->
                usbRegexs.any { regex -> iface.matches(Regex(regex)) }
            }
            refreshState()
        }
    }

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                UsbManager.ACTION_USB_STATE -> {
                    usbConnected = intent.getBooleanExtra(UsbManager.USB_CONNECTED, false)
                    refreshState()
                }

                Intent.ACTION_MEDIA_SHARED -> {
                    massStorageActive = true
                    refreshState()
                }

                Intent.ACTION_MEDIA_UNSHARED -> {
                    massStorageActive = false
                    refreshState()
                }
            }
        }
    }

    override fun getMetricsCategory(): Int = 0

    override fun handleSetListening(listening: Boolean) {
        super.handleSetListening(listening)
        if (listening) {
            massStorageActive = Environment.MEDIA_SHARED == Environment.getExternalStorageState()
            tetheringManager.registerTetheringEventCallback(mainExecutor, tetheringEventCallback)

            val usbFilter = IntentFilter(UsbManager.ACTION_USB_STATE)
            val stickyUsbIntent = mContext.registerReceiver(usbReceiver, usbFilter)
            stickyUsbIntent?.let { usbReceiver.onReceive(mContext, it) }

            val mediaFilter = IntentFilter().apply {
                addAction(Intent.ACTION_MEDIA_SHARED)
                addAction(Intent.ACTION_MEDIA_UNSHARED)
                addDataScheme("file")
            }
            mContext.registerReceiver(usbReceiver, mediaFilter)

        } else {
            tetheringManager.unregisterTetheringEventCallback(tetheringEventCallback)
            mContext.unregisterReceiver(usbReceiver)
        }
    }

    override fun handleClick(expandable: Expandable?) {
        toggleUsbTethering(!usbTetheringActive)
    }

    override fun newTileState(): QSTile.State {
        return QSTile.State().apply {
            icon = mIcon
            label = getTileLabel()
            state = Tile.STATE_INACTIVE
        }
    }

    override fun handleUpdateState(state: QSTile.State, arg: Any?) {
        state.icon = mIcon
        state.label = getTileLabel()

        when {
            // Device doesn't support USB tethering at all
            usbRegexs.isEmpty() -> {
                state.state = Tile.STATE_UNAVAILABLE
            }
            // USB tethering is active
            usbTetheringActive -> {
                state.state = Tile.STATE_ACTIVE
            }

            else -> {
                val enforcedAdmin = checkIfUsbDataSignalingIsDisabled(
                    mContext,
                    UserHandle.myUserId()
                )
                val usbAvailable = usbConnected && !massStorageActive

                state.state = when {
                    enforcedAdmin != null -> Tile.STATE_UNAVAILABLE // disabled by admin policy
                    usbAvailable -> Tile.STATE_INACTIVE     // ready, USB plugged in
                    else -> Tile.STATE_UNAVAILABLE  // USB not connected
                }
            }
        }
    }

    fun toggleUsbTethering(enable: Boolean) {
        if (enable) {
            tetheringManager.startTethering(
                TetheringManager.TETHERING_USB,
                mainExecutor,
                object : TetheringManager.StartTetheringCallback {
                    override fun onTetheringStarted() {
                        refreshState()
                    }

                    override fun onTetheringFailed(error: Int) {
                        refreshState()
                    }
                }
            )
        } else {
            tetheringManager.stopTethering(TetheringManager.TETHERING_USB)
        }
    }

    override fun getTileLabel(): CharSequence =
        mContext.getString(R.string.quick_settings_usb_tether_label)

    override fun getLongClickIntent(): Intent =
        Intent(Settings.ACTION_TETHER_SETTINGS)


    companion object {
        const val TILE_SPEC = "usbTethering"
    }
}
