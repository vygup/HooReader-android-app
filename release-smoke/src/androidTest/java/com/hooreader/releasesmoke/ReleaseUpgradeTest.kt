package com.hooreader.releasesmoke

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.StaleObjectException
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.regex.Pattern

/** Drives signed v1 and v2 APKs in separate invocations without clearing their shared data. */
@RunWith(AndroidJUnit4::class)
class ReleaseUpgradeTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val device = UiDevice.getInstance(instrumentation)

    @Test
    fun seedUpgrade() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("upgradePhase") == "seed")
        assumeTrue(InstrumentationRegistry.getArguments().getString("disposableReleaseEmulator") == "true")
        assertEquals("1.0.0", instrumentation.context.packageManager.getPackageInfo(APP, 0).versionName)
        launch()
        listOf("hooreader-release-epub.epub", "hooreader-release-fb2.fb2").forEach { file ->
            importFile(file)
            click("Следующая глава")
            node("Абзац для восстановления позиции.")
            click("Настройки чтения")
            click("Тёмная")
            click("Увеличить текст")
            click("Готово")
            click("В библиотеку")
            node("Импортировать книгу")
        }
        captureUpgrade("seed")
    }

    @Test
    fun verifyUpgrade() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("upgradePhase") == "verify")
        assumeTrue(InstrumentationRegistry.getArguments().getString("disposableReleaseEmulator") == "true")
        assertEquals("2.0.0", instrumentation.context.packageManager.getPackageInfo(APP, 0).versionName)
        assertEquals("1", device.executeShellCommand("settings get global airplane_mode_on").trim())
        launch()
        listOf("Тестовая книга — Café", "Тестовая книга").forEach { title ->
            click(title)
            node("Абзац для восстановления позиции.")
            controls()
            click("Настройки чтения")
            node("Размер текста: 150%")
            val dark = node("Тёмная")
            assertTrue(
                dark.isChecked || dark.isSelected || dark.parent?.isChecked == true || dark.parent?.isSelected == true
            )
            val vertical = node("Вертикальная прокрутка")
            assertTrue(
                vertical.isChecked || vertical.isSelected || vertical.parent?.isChecked == true ||
                    vertical.parent?.isSelected == true
            )
            click("Постранично")
            device.pressBack()
            assertTrue(device.wait(Until.hasObject(By.text(Pattern.compile("[1-9][0-9]*"))), TIMEOUT))
            controls()
            click("Настройки чтения")
            click("Вертикальная прокрутка")
            device.pressBack()
            node("Абзац для восстановления позиции.")
            captureUpgrade(if (title.contains("Café")) "epub" else "fb2")
            exitReader()
        }
        captureUpgrade("verify")
    }

    private fun captureUpgrade(phase: String) {
        val output = instrumentation.targetContext.filesDir
        device.dumpWindowHierarchy(File(output, "upgrade-$phase.xml"))
        device.takeScreenshot(File(output, "upgrade-$phase.png"))
    }

    private fun importFile(fileName: String) {
        click("Импортировать книгу")
        assertTrue(device.wait(Until.hasObject(By.pkg(PICKER)), TIMEOUT))
        if (!device.hasObject(By.text(fileName))) {
            click(By.desc(Pattern.compile("Show roots|Показать корни")))
            click(By.text(Pattern.compile("Downloads|Загрузки")))
        }
        click(fileName)
        node("Первая глава")
    }

    private fun controls() {
        if (!device.hasObject(By.text("В библиотеку"))) {
            device.click(device.displayWidth / 2, device.displayHeight / 2)
        }
        node("Настройки чтения")
    }

    private fun exitReader() {
        controls()
        click("В библиотеку")
        click("Выйти")
        node("Импортировать книгу")
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
