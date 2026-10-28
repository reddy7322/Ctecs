package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.overlay.LogoProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Ctecs", appName)
  }

  @Test
  fun `generate logo presets on android canvas`() {
    val nhai = LogoProvider.generateNhaiPreset(240)
    assertNotNull(nhai)
    assertEquals(240, nhai.width)
    assertTrue(nhai.height > 50)

    val ctecs = LogoProvider.generateCtecsPreset(200)
    assertNotNull(ctecs)
    assertEquals(200, ctecs.width)

    val seal = LogoProvider.generateSurveySealPreset(150)
    assertNotNull(seal)
    assertEquals(150, seal.width)
    assertEquals(150, seal.height)
  }
}
