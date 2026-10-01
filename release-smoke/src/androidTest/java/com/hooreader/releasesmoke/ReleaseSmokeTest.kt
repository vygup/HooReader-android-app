package com.hooreader.releasesmoke

import android.content.pm.ApplicationInfo
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.StaleObjectException
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.regex.Pattern

/** External process drives the actual signed, non-debuggable release through the system picker. */
@RunWith(AndroidJUnit4::class)
class ReleaseSmokeTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val device = UiDevice.getInstance(instrumentation)

    @Test
    fun signedReleaseImportsBothFormatsAndRestoresOfflineSettingsAndChapter() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("releaseSmoke") == "true")
        assumeTrue(InstrumentationRegistry.getArguments().getString("disposableReleaseEmulator") == "true")
        val info = instrumentation.context.packageManager.getPackageInfo(APP, 0)
        assertEquals("1.0.0", info.versionName)
        @Suppress("DEPRECATION") // versionCode is required on supported API 26/27.
        val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.longVersionCode
        } else {
            info.versionCode.toLong()
        }
        assertEquals(1L, code)
        assertFalse(requireNotNull(info.applicationInfo).flags and ApplicationInfo.FLAG_DEBUGGABLE != 0)
        assertEquals("Success", device.executeShellCommand("pm clear $APP").trim())
        val previousAirplane = device.executeShellCommand("settings get global airplane_mode_on").trim()
        try {
            launch()
            verifyBook("hooreader-release-epub.epub", "Тестовая книга — Café")
            verifyBook("hooreader-release-fb2.fb2", "Тестовая книга")
            node("Импортировать книгу")
        } finally {
            val output = instrumentation.targetContext.filesDir
            device.dumpWindowHierarchy(File(output, "release-smoke.xml"))
            device.takeScreenshot(File(output, "release-smoke.png"))
            if (previousAirplane != "1") device.executeShellCommand("cmd connectivity airplane-mode disable")
        }
    }

    private fun verifyBook(fileName: String, title: String) {
        click("Импортировать книгу")
        assertTrue(device.wait(Until.hasObject(By.pkg(PICKER)), TIMEOUT))
        if (!device.hasObject(By.text(fileName))) {
            click(By.desc(Pattern.compile("Show roots|Показать корни")))
            click(By.text(Pattern.compile("Downloads|Загрузки")))
        }
        click(fileName)
        node(title)
        click("Следующая глава")
        node("Абзац для восстановления позиции.")
        click("Настройки чтения")
        click("Тёмная")
        click("Увеличить текст")
        click("Готово")
        node("Абзац для восстановления позиции.")
        click("В библиотеку")
        node("Импортировать книгу")
        device.executeShellCommand("cmd connectivity airplane-mode enable")
        device.executeShellCommand("am force-stop $APP")
        launch()
        click(title)
        node("Абзац для восстановления позиции.")
        click("Настройки чтения")
        val dark = node("Тёмная")
        val choice = dark.parent
        assertTrue(dark.isChecked || dark.isSelected || choice?.isChecked == true || choice?.isSelected == true)
        node("Размер текста: ${if (fileName.endsWith("epub")) "125" else "150"}%")
        click("Готово")
        click("В библиотеку")
        assertTrue(device.wait(Until.hasObject(By.desc("Удалить книгу «$title»")), TIMEOUT))
        click(By.desc("Удалить книгу «$title»"))
        click("Удалить из библиотеки")
        assertTrue(device.wait(Until.gone(By.text(title)), TIMEOUT))
        // Source picker file still exists after the app-local book was deleted.
        val sourcePath = "/sdcard/Download/$fileName"
        assertEquals(sourcePath, device.executeShellCommand("ls $sourcePath").trim())
    }

    private fun launch() {
        device.executeShellCommand("am start -n $APP/.MainActivity")
        node("Импортировать книгу")
    }

    private fun node(text: String): UiObject2 = requireNotNull(device.wait(Until.findObject(By.text(text)), TIMEOUT)) {
        "Release UI did not show: $text"
    }

    private fun click(text: String) = click(By.text(text))

    private fun click(selector: BySelector) {
        repeat(CLICK_ATTEMPTS) {
            try {
                device.waitForIdle()
                requireNotNull(device.wait(Until.findObject(selector), TIMEOUT)).click()
                device.waitForIdle()
                return
            } catch (_: StaleObjectException) {
                // Reacquire after a system picker or Compose transition replaced its accessibility node.
            }
        }
        error("UI node remained stale: $selector")
    }

    private companion object {
        const val APP = "com.hooreader"
        const val PICKER = "com.google.android.documentsui"
        const val TIMEOUT = 15_000L
        const val CLICK_ATTEMPTS = 3
    }
}
