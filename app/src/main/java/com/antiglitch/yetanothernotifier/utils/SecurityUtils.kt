package com.antiglitch.yetanothernotifier.utils

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.IvParameterSpec

object SecurityUtils {
    private const val TAG = "SecurityUtils"
    private const val KEY_ALIAS = "YANMqttPasswordKey"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val TRANSFORMATION = "AES/CBC/PKCS7Padding"
    private const val IV_SEPARATOR = ":"
    
    /**
     * Encrypt a password using Android Keystore
     */
    fun encryptPassword(context: Context, password: String): String? {
        if (password.isEmpty()) return ""
        
        try {
            val secretKey = getOrCreateSecretKey()
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            
            val encryptedBytes = cipher.doFinal(password.toByteArray(Charsets.UTF_8))
            val iv = cipher.iv
            
            // Combine IV and encrypted data
            val encryptedData = Base64.encodeToString(encryptedBytes, Base64.DEFAULT)
            val ivData = Base64.encodeToString(iv, Base64.DEFAULT)
            
            return "$ivData$IV_SEPARATOR$encryptedData"
        } catch (e: Exception) {
            Log.e(TAG, "Error encrypting password", e)
            return null
        }
    }
    
    /**
     * Decrypt a password using Android Keystore
     */
    fun decryptPassword(context: Context, encryptedPassword: String): String? {
        if (encryptedPassword.isEmpty()) return ""
        
        try {
            val parts = encryptedPassword.split(IV_SEPARATOR)
            if (parts.size != 2) {
                Log.w(TAG, "Invalid encrypted password format")
                return null
            }
            
            val iv = Base64.decode(parts[0], Base64.DEFAULT)
            val encryptedData = Base64.decode(parts[1], Base64.DEFAULT)
            
            val secretKey = getOrCreateSecretKey()
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, IvParameterSpec(iv))
            
            val decryptedBytes = cipher.doFinal(encryptedData)
            return String(decryptedBytes, Charsets.UTF_8)
        } catch (e: Exception) {
            Log.e(TAG, "Error decrypting password", e)
            return null
        }
    }
    
    /**
     * Get or create a secret key in Android Keystore
     */
    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
        keyStore.load(null)
        
        return if (keyStore.containsAlias(KEY_ALIAS)) {
            keyStore.getKey(KEY_ALIAS, null) as SecretKey
        } else {
            createSecretKey()
        }
    }
    
    /**
     * Create a new secret key in Android Keystore
     */
    private fun createSecretKey(): SecretKey {
        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        
        val keyGenParameterSpec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_CBC)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_PKCS7)
            .setUserAuthenticationRequired(false)
            .setRandomizedEncryptionRequired(true)
            .build()
        
        keyGenerator.init(keyGenParameterSpec)
        return keyGenerator.generateKey()
    }
    
    /**
     * Check if the password appears to be encrypted
     */
    fun isPasswordEncrypted(password: String): Boolean {
        return password.contains(IV_SEPARATOR) && 
               password.split(IV_SEPARATOR).size == 2 &&
               password.isNotEmpty()
    }
    
    /**
     * Migrate plain text password to encrypted format
     */
    fun migratePasswordIfNeeded(context: Context, password: String): String? {
        return if (isPasswordEncrypted(password)) {
            password // Already encrypted
        } else {
            encryptPassword(context, password) // Encrypt plain text password
        }
    }
}
