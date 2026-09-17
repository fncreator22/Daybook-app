package com.sr2ma.daybook

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.sr2ma.daybook.sync.SyncViewModel
import com.sr2ma.daybook.ui.DaybookApp
import com.sr2ma.daybook.ui.DaybookViewModel
import com.sr2ma.daybook.ui.theme.DaybookTheme

/**
 * The app's only Activity.
 *
 * Everything above this is Compose, so there is nothing here but wiring: create
 * the ViewModel from the container, go edge to edge, and hand over.
 */
class MainActivity : ComponentActivity() {

    private val viewModel: DaybookViewModel by viewModels {
        DaybookViewModel.factory((application as DaybookApplication).container.repository)
    }

    private val syncViewModel: SyncViewModel by viewModels {
        SyncViewModel.factory((application as DaybookApplication).container.syncManager)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Required from targetSdk 35 onwards, where the system no longer insets the
        // window for you. The theme draws behind the bars and Scaffold pads for them.
        //
        // The styles are passed explicitly because Daybook is light-only: with no
        // arguments the bars follow the system's dark/light setting, so a phone in
        // dark mode would get light bar icons over the light app theme. light() means
        // a transparent bar with dark icons; the second colour is the scrim used on
        // API levels that cannot draw dark icons, which at minSdk 26 is only the
        // navigation bar on API 26 (dark nav-bar icons arrived in API 27).
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.BLACK),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.BLACK),
        )
        super.onCreate(savedInstanceState)
        setContent {
            DaybookTheme {
                DaybookApp(viewModel, syncViewModel)
            }
        }
    }

    /**
     * The app can sit in the background across midnight, which would leave "Today"
     * showing yesterday. Re-reading the date on resume is cheaper and more reliable
     * than listening for ACTION_DATE_CHANGED.
     */
    override fun onResume() {
        super.onResume()
        viewModel.refreshToday()
    }
}
