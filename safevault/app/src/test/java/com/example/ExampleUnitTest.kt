package com.example

import com.example.security.SecureCsvHelper
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleUnitTest {
  @Test
  fun addition_isCorrect() {
    assertEquals(4, 2 + 2)
  }

  @Test
  fun testSecureCsvHelperEncryptDecrypt() {
    val sampleCsv = "entry_type,platform_or_title,username,decrypted_secret,url,category,description,category_label,category_color,requires_biometric,created_date,last_modified_date\n" +
            "\"password\",\"Google\",\"user123\",\"p@ssword\",\"https://google.com\",\"Finance\",\"Sample desc\",\"\",\"\",\"false\",\"1234567\",\"1234567\""
    val passphrase = "mySuperSecretPassphrase"
    
    try {
      val encrypted = SecureCsvHelper.encryptCsv(sampleCsv, passphrase)
      assertNotNull(encrypted)
      assertTrue(encrypted.contains(":"))
      
      val decrypted = SecureCsvHelper.decryptCsv(encrypted, passphrase)
      assertEquals(sampleCsv, decrypted)
      
      val (passwords, notes) = SecureCsvHelper.parseCsvToEntries(decrypted)
      println("DEBUG: parsed passwords count: ${passwords.size}")
      if (passwords.isNotEmpty()) {
        println("DEBUG: platformName='${passwords[0].platformName}'")
        println("DEBUG: username='${passwords[0].username}'")
        println("DEBUG: encryptedPassword='${passwords[0].encryptedPassword}'")
      }
      assertEquals(1, passwords.size)
      assertEquals(0, notes.size)
      assertEquals("Google", passwords[0].platformName)
      assertEquals("p@ssword", passwords[0].encryptedPassword)
    } catch (e: Exception) {
      e.printStackTrace()
      fail("Failed with exception: ${e.message}")
    }
  }

  @Test
  fun testSecureCsvHelper_InvalidColumnSize_ThrowsException() {
    val invalidCsv = "entry_type,platform_or_title,username\n\"password\",\"Google\",\"user123\""
    try {
      SecureCsvHelper.parseCsvToEntries(invalidCsv)
      fail("Should have thrown IllegalArgumentException due to incorrect number of columns")
    } catch (e: IllegalArgumentException) {
      assertTrue(e.message!!.contains("Expected 12 columns"))
    }
  }

  @Test
  fun testSecureCsvHelper_WrongHeaderName_ThrowsException() {
    val invalidCsv = "entry_type,platform_or_title,username,wrong_decrypted_secret,url,category,description,category_label,category_color,requires_biometric,created_date,last_modified_date\n" +
            "\"password\",\"Google\",\"user123\",\"p@ssword\",\"https://google.com\",\"Finance\",\"Sample desc\",\"\",\"\",\"false\",\"1234567\",\"1234567\""
    try {
      SecureCsvHelper.parseCsvToEntries(invalidCsv)
      fail("Should have thrown IllegalArgumentException due to invalid header name")
    } catch (e: IllegalArgumentException) {
      assertTrue(e.message!!.contains("Invalid column header name at position 4"))
    }
  }

  @Test
  fun testSecureCsvHelper_EmptyCsv_ThrowsException() {
    try {
      SecureCsvHelper.parseCsvToEntries("")
      fail("Should have thrown IllegalArgumentException for blank CSV input")
    } catch (e: IllegalArgumentException) {
      assertTrue(e.message!!.contains("empty or blank") || e.message!!.contains("empty"))
    }
  }
}
