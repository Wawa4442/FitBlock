package com.fitblock.app

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.text.method.PasswordTransformationMethod
import android.widget.*
import androidx.activity.ComponentActivity
import java.util.concurrent.Executors

class AccountActivity : ComponentActivity() {
    private lateinit var accounts: Accounts
    private lateinit var store: Store
    private var creating = true
    private var busy = false
    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        accounts = Accounts.get(this)
        store = Store.get(this)
        if (!store.consent) { finish(); return }
        render()
    }

    private fun render(showForm: Boolean = false) {
        val body = column(22)
        body.button("‹ Volver", false) { finish() }
        body.gap(14)
        val account = accounts.current()
        if (account != null && !showForm) {
            body.text("Mi cuenta", 28f, bold = true)
            body.gap(16)
            body.card {
                text(account.name, 24f, C.green, true)
                gap(6)
                text(account.email, 14f, C.muted)
                gap(12)
                text("Cuenta local en este teléfono", 13f, C.cyan)
            }
            body.card {
                text(quantity(store.totalActivities(), "actividad completada", "actividades completadas"), 20f, bold = true)
                gap(6)
                text("${quantity(store.streak(), "día", "días")} de racha · mejor racha de ${quantity(store.bestStreak(), "día", "días")}", 14f, C.muted)
                gap(12)
                button("Ver mi historial", false) { community("history") }
                button("Rachas y premios", false) { community("streaks") }
            }
            body.button("Cambiar cuenta", false) { creating = false; render(true) }
            body.button("Cerrar sesión", false) {
                try { accounts.logout(); finish() }
                catch (error: AccountException) { Toast.makeText(this, error.message, Toast.LENGTH_LONG).show() }
            }
            body.gap(18)
            body.text("Sincronización entre dispositivos y recuperación por correo", 15f, bold = true)
            body.text("Muy pronto", 13f, C.cyan, true)
            setFitContent(body)
            return
        }

        body.text(if (creating) "Crea tu cuenta" else "Inicia sesión", 28f, bold = true)
        body.gap(8)
        body.text("Tu cuenta y tu historial se guardan en este teléfono.", 14f, C.muted)
        body.gap(18)
        val name = input("Nombre de usuario")
        if (creating) { body.addView(name); body.gap(10) }
        val email = input("Correo electrónico").apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
            contentDescription = "Correo electrónico"
        }
        body.addView(email)
        body.gap(10)
        val password = input("Contraseña").apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            transformationMethod = PasswordTransformationMethod.getInstance()
            contentDescription = "Contraseña"
        }
        body.addView(password)
        val confirmation = input("Repite la contraseña").apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            transformationMethod = PasswordTransformationMethod.getInstance()
            contentDescription = "Repite la contraseña"
        }
        if (creating) { body.gap(10); body.addView(confirmation) }
        body.gap(6)
        val show = CheckBox(this).apply {
            text = "Mostrar contraseña"
            setTextColor(C.muted)
            setOnCheckedChangeListener { _, checked ->
                password.transformationMethod = if (checked) null else PasswordTransformationMethod.getInstance()
                password.setSelection(password.text.length)
                confirmation.transformationMethod = password.transformationMethod
            }
        }
        body.addView(show)
        if (creating) body.text("Usa al menos 8 caracteres. Tu primer registro asociará el historial de invitado a la cuenta.", 12f, C.muted)
        body.gap(12)
        val message = body.text("", 13f, C.amber)
        body.gap(8)
        lateinit var submit: Button
        lateinit var switch: Button
        submit = body.button(if (creating) "Crear cuenta" else "Entrar") {
            if (busy) return@button
            if (creating && password.text.toString() != confirmation.text.toString()) {
                message.text = "Las contraseñas deben coincidir."
                return@button
            }
            if (store.hasWorkout() || SportTimer.get(this).current() != null) {
                message.text = "Termina o descarta la actividad pendiente antes de cambiar de cuenta."
                return@button
            }
            val inputName = name.text.toString()
            val inputEmail = email.text.toString()
            val secret = CharArray(password.text.length) { password.text[it] }
            password.text.clear()
            confirmation.text.clear()
            val registering = creating
            busy = true
            submit.isEnabled = false
            switch.isEnabled = false
            message.text = "Guardando tu cuenta…"
            worker.execute {
                val result = try {
                    Result.success(if (registering) accounts.register(inputName, inputEmail, secret)
                        else accounts.authenticate(inputEmail, secret))
                } catch (error: Exception) {
                    Result.failure<Account>(error)
                } finally {
                    secret.fill('\u0000')
                }
                main.post {
                    if (isFinishing || isDestroyed) return@post
                    busy = false
                    submit.isEnabled = true
                    switch.isEnabled = true
                    result.fold(onSuccess = { user ->
                        try {
                            accounts.activate(user)
                            render()
                        } catch (error: AccountException) {
                            message.text = error.message
                        }
                    }, onFailure = { error ->
                        message.text = if (error is AccountException) error.message else "No se pudo abrir la cuenta. Intenta de nuevo."
                    })
                }
            }
        }
        switch = body.button(if (creating) "Ya tengo cuenta" else "Crear una cuenta", false) {
            if (!busy) { creating = !creating; render(true) }
        }
        body.gap(18)
        body.text("Sincronización y acceso desde otros dispositivos", 14f, bold = true)
        body.text("Muy pronto", 13f, C.cyan, true)
        setFitContent(body)
    }

    private fun community(tab: String) {
        startActivity(Intent(this, CommunityActivity::class.java).putExtra("tab", tab))
    }

    override fun onDestroy() {
        worker.shutdown()
        super.onDestroy()
    }
}
