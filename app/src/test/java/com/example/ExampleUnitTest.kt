package com.example

import org.junit.Assert.*
import org.junit.Test

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {
  @Test
  fun addition_isCorrect() {
    assertEquals(4, 2 + 2)
  }

  @Test
  fun testStableVersionComparison() {
    assertTrue(com.example.update.AppUpdateManager.isNewerVersion("1.0.1", "1.0.0"))
    assertTrue(com.example.update.AppUpdateManager.isNewerVersion("v1.1.0", "v1.0.9"))
    assertTrue(com.example.update.AppUpdateManager.isNewerVersion("2.0.0", "1.9.9"))
    assertFalse(com.example.update.AppUpdateManager.isNewerVersion("1.0.0", "1.0.0"))
    assertFalse(com.example.update.AppUpdateManager.isNewerVersion("1.0.0", "1.0.1"))
  }

  @Test
  fun testBetaVersionComparison() {
    assertTrue(com.example.update.AppUpdateManager.isNewerVersion("1.0.1-beta.2", "1.0.1-beta.1"))
    assertTrue(com.example.update.AppUpdateManager.isNewerVersion("v1.0.2-beta.1", "v1.0.1-beta.9"))
    assertTrue(com.example.update.AppUpdateManager.isNewerVersion("1.0.1-beta2", "1.0.1-beta1"))
    assertFalse(com.example.update.AppUpdateManager.isNewerVersion("1.0.1-beta.1", "1.0.1-beta.1"))
    assertFalse(com.example.update.AppUpdateManager.isNewerVersion("1.0.1-beta.1", "1.0.1-beta.2"))
  }

  @Test
  fun testCrossChannelVersionComparison() {
    // Stable release is newer than the same base beta
    assertTrue(com.example.update.AppUpdateManager.isNewerVersion("1.0.1", "1.0.1-beta.2"))
    assertFalse(com.example.update.AppUpdateManager.isNewerVersion("1.0.1-beta.2", "1.0.1"))
  }
}
