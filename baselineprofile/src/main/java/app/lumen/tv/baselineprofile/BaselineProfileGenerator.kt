package app.lumen.tv.baselineprofile

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.UiDevice
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() = rule.collect(
        packageName = PACKAGE,
        maxIterations = 3,
        stableIterations = 2,
        includeInStartupProfile = true,
    ) {
        pressHome()
        device.executeShellCommand("am start -W -n $PACKAGE/.MainActivity")
        settle(6_000)
        browseHome()
        openDetailAndBack()
        openTab(DISCOVER)
        browseRows()
        openTab(SEARCH)
        settle(1_500)
        openTab(COLLECTIONS)
        settle(3_000)
        openTab(HOME)
        settle(1_500)
    }

    private fun MacrobenchmarkScope.browseHome() {
        repeat(5) { press { pressDPadRight() } }
        repeat(3) {
            press { pressDPadDown() }
            repeat(3) { press { pressDPadRight() } }
        }
        repeat(3) { press { pressDPadUp() } }
    }

    private fun MacrobenchmarkScope.openDetailAndBack() {
        press(2_500) { pressDPadCenter() }
        repeat(3) { press { pressDPadDown() } }
        repeat(2) { press { pressDPadRight() } }
        press(1_500) { pressBack() }
    }

    private fun MacrobenchmarkScope.browseRows() {
        repeat(2) {
            press { pressDPadDown() }
            repeat(4) { press { pressDPadRight() } }
        }
    }

    private fun MacrobenchmarkScope.openTab(index: Int) {
        repeat(6) { press(250) { pressDPadUp() } }
        repeat(NAV_ITEMS) { press(150) { pressDPadLeft() } }
        repeat(index) { press(250) { pressDPadRight() } }
        press(2_500) { pressDPadCenter() }
        press { pressDPadDown() }
    }

    private fun MacrobenchmarkScope.press(wait: Long = 600, action: UiDevice.() -> Unit) {
        device.action()
        settle(wait)
    }

    private fun settle(millis: Long) {
        Thread.sleep(millis)
    }

    private companion object {
        const val PACKAGE = "app.lumen.tv"
        const val NAV_ITEMS = 7
        const val HOME = 0
        const val COLLECTIONS = 3
        const val DISCOVER = 4
        const val SEARCH = 5
    }
}
