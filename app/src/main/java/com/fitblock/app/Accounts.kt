package com.fitblock.app

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import android.util.Base64
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Locale
import java.util.UUID
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

data class Account(val id: String, val name: String, val email: String)
class AccountException(message: String) : IllegalArgumentException(message)

class Accounts private constructor(private val context: Context) {
    private val prefs = context.getSharedPreferences("fitblock_accounts", Context.MODE_PRIVATE)

    fun current(): Account? {
        val id = prefs.getString("current", null) ?: return null
        return users().optJSONObject(id)?.let { account(id, it) }
    }

    @Synchronized fun register(name: String, email: String, password: CharArray): Account {
        val cleanName = name.trim()
        val cleanEmail = email.trim().lowercase(Locale.ROOT)
        if (cleanName.length !in 1..40) throw AccountException("Escribe un nombre de hasta 40 caracteres.")
        if (cleanEmail.length > 254 || !EMAIL.matches(cleanEmail)) throw AccountException("Revisa tu correo electrónico.")
        if (password.size !in 8..128 || password.all { it.isWhitespace() }) {
            throw AccountException("Usa una contraseña de entre 8 y 128 caracteres.")
        }
        val all = users()
        if (all.keys().asSequence().any { all.getJSONObject(it).getString("email") == cleanEmail }) {
            throw AccountException("Ya existe una cuenta con ese correo en este teléfono.")
        }
        val salt = ByteArray(24).also { SecureRandom().nextBytes(it) }
        val id = UUID.randomUUID().toString()
        val user = JSONObject().put("name", cleanName).put("email", cleanEmail)
            .put("salt", encode(salt)).put("hash", encode(derive(password, salt)))
            .put("iterations", 210_000)
            .put("created", System.currentTimeMillis())
        all.put(id, user)
        if (!prefs.edit().putString("users", all.toString()).commit()) {
            throw AccountException("No se pudo guardar la cuenta. Revisa el espacio del teléfono.")
        }
        return Account(id, cleanName, cleanEmail)
    }

    @Synchronized fun authenticate(email: String, password: CharArray): Account {
        val now = SystemClock.elapsedRealtime()
        val boot = Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1)
        if (prefs.getInt("failed_boot", boot) == boot && now < prefs.getLong("retry_after", 0)) {
            throw AccountException("Espera unos segundos antes de intentarlo de nuevo.")
        }
        val cleanEmail = email.trim().lowercase(Locale.ROOT)
        val all = users()
        val id = all.keys().asSequence().find { all.getJSONObject(it).getString("email") == cleanEmail }
        val user = id?.let { all.getJSONObject(it) }
        val salt = user?.let { decode(it.getString("salt")) } ?: ByteArray(24)
        val calculated = derive(password, salt, user?.optInt("iterations", 210_000) ?: 210_000)
        if (user == null || !MessageDigest.isEqual(calculated, decode(user.getString("hash")))) {
            val attempts = prefs.getInt("failures", 0) + 1
            prefs.edit().putInt("failures", attempts % 5).putInt("failed_boot", boot)
                .putLong("retry_after", if (attempts >= 5) now + 30_000 else 0).commit()
            throw AccountException("Correo o contraseña incorrectos.")
        }
        prefs.edit().remove("failures").remove("retry_after").apply()
        return account(id, user)
    }

    @Synchronized fun activate(account: Account) {
        if (!users().has(account.id)) throw AccountException("La cuenta ya no está disponible.")
        requireIdle()
        val firstAccount = users().length() == 1
        if (current() == null && firstAccount) Store.get(context).claimGuestHistory(account.id)
        if (!prefs.edit().putString("current", account.id).commit()) {
            throw AccountException("No se pudo abrir la sesión.")
        }
    }

    @Synchronized fun logout() {
        requireIdle()
        prefs.edit().remove("current").commit()
    }

    @Synchronized fun rename(name: String) {
        val user = current() ?: return
        val clean = name.trim()
        if (clean.length !in 1..40) throw AccountException("Escribe un nombre de hasta 40 caracteres.")
        val all = users()
        all.getJSONObject(user.id).put("name", clean)
        prefs.edit().putString("users", all.toString()).commit()
    }

    @Synchronized internal fun clearAll() {
        users().keys().asSequence().toList().forEach { id ->
            context.getSharedPreferences("fitblock_user_$id", Context.MODE_PRIVATE).edit().clear().commit()
        }
        prefs.edit().clear().commit()
    }

    private fun requireIdle() {
        if (Store.get(context).hasWorkout() || SportTimer.get(context).current() != null) {
            throw AccountException("Termina o descarta la actividad pendiente antes de cambiar de cuenta.")
        }
    }

    private fun users() = JSONObject(prefs.getString("users", "{}")!!)
    private fun account(id: String, user: JSONObject) = Account(id, user.getString("name"), user.getString("email"))

    private fun derive(password: CharArray, salt: ByteArray, iterations: Int = 210_000): ByteArray {
        if (password.size > 128) throw AccountException("La contraseña admite hasta 128 caracteres.")
        val spec = PBEKeySpec(password, salt, iterations, 256)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    private fun encode(value: ByteArray) = Base64.encodeToString(value, Base64.NO_WRAP)
    private fun decode(value: String) = Base64.decode(value, Base64.NO_WRAP)

    companion object {
        private val EMAIL = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]{2,}$")
        @Volatile private var instance: Accounts? = null
        fun get(context: Context): Accounts = instance ?: synchronized(this) {
            instance ?: Accounts(context.applicationContext).also { instance = it }
        }
    }
}
