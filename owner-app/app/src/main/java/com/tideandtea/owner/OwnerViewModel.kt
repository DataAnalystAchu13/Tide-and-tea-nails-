package com.tideandtea.owner

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.jan.supabase.auth.exception.AuthErrorCode
import io.github.jan.supabase.auth.exception.AuthRestException
import io.github.jan.supabase.exceptions.HttpRequestException
import com.tideandtea.owner.data.Booking
import com.tideandtea.owner.data.Payment
import com.tideandtea.owner.data.NotOwnerException
import com.tideandtea.owner.data.OwnerRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

enum class AuthState { Checking, SignedOut, SignedIn }

class OwnerViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = OwnerRepository()

    var authState by mutableStateOf(AuthState.Checking)
        private set
    var busy by mutableStateOf(false)
        private set
    var loginError by mutableStateOf<String?>(null)
        private set

    /** One-off messages for the snackbar. */
    var message by mutableStateOf<String?>(null)

    private val _bookings = MutableStateFlow<List<Booking>?>(null)
    val bookings: StateFlow<List<Booking>?> = _bookings.asStateFlow()
    private val _payments = MutableStateFlow<List<Payment>?>(null)
    val payments: StateFlow<List<Payment>?> = _payments.asStateFlow()
    private val listenJobs = mutableListOf<Job>()

    /** Set when the app is opened from a notification: "booking:<id>" or "order:<id>". */
    var pendingOpen by mutableStateOf<String?>(null)

    init {
        viewModelScope.launch {
            authState = if (repo.isOwner()) AuthState.SignedIn.also { listen() } else AuthState.SignedOut
        }
    }

    private fun listen() {
        listenJobs.forEach { it.cancel() }
        listenJobs.clear()
        listenJobs += viewModelScope.launch {
            repo.bookings()
                .catch { message = "Couldn't load bookings: ${it.message}" }
                .collect { _bookings.value = it }
        }
        listenJobs += viewModelScope.launch {
            repo.payments().catch { _payments.value = emptyList() }.collect { _payments.value = it }
        }
        viewModelScope.launch { runCatching { repo.registerPushToken() } }
    }

    fun signIn(email: String, password: String) {
        if (email.isBlank() || password.isBlank()) {
            loginError = "Enter your email and password."; return
        }
        viewModelScope.launch {
            busy = true; loginError = null
            try {
                repo.signIn(email, password)
                authState = AuthState.SignedIn
                listen()
            } catch (e: NotOwnerException) {
                loginError = e.message
            } catch (e: AuthRestException) {
                loginError = when (e.errorCode) {
                    AuthErrorCode.InvalidCredentials -> "That email and password don't match."
                    AuthErrorCode.EmailNotConfirmed -> "Confirm your email first (check your inbox), or mark the user as confirmed in Supabase."
                    else -> e.message ?: "Couldn't sign in."
                }
            } catch (e: HttpRequestException) {
                loginError = "No internet connection."
            } catch (e: Exception) {
                loginError = e.message ?: "Couldn't sign in."
            } finally {
                busy = false
            }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            runCatching { repo.unregisterPushToken() }
            listenJobs.forEach { it.cancel() }
            listenJobs.clear()
            _bookings.value = null
            _payments.value = null
            repo.signOut()
            authState = AuthState.SignedOut
        }
    }

    /** Runs a write, shows [done] when it succeeds, and a readable error otherwise. */
    fun act(done: String, block: suspend OwnerRepository.() -> Unit) {
        if (busy) return
        viewModelScope.launch {
            busy = true
            try {
                repo.block(); message = done
            } catch (e: Exception) {
                message = "Didn't save: ${e.message ?: "unknown error"}"
            } finally {
                busy = false
            }
        }
    }

    fun publishPriceList() = viewModelScope.launch {
        try {
            message = if (repo.publishDefaultPriceList(getApplication())) {
                "Price list published. Edit it any time in Supabase: Table Editor > config > main."
            } else {
                "A price list already exists in Supabase (config > main), so nothing was changed."
            }
        } catch (e: Exception) {
            message = "Couldn't publish: ${e.message}"
        }
    }

    val ownerEmail: String? get() = repo.signedInEmail
}
