package com.tideandtea.owner

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.tideandtea.owner.data.Booking
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
    private var listenJob: Job? = null

    init {
        viewModelScope.launch {
            authState = if (repo.isOwner()) AuthState.SignedIn.also { listen() } else AuthState.SignedOut
        }
    }

    private fun listen() {
        listenJob?.cancel()
        listenJob = viewModelScope.launch {
            repo.bookings()
                .catch { message = "Couldn't load bookings: ${it.message}" }
                .collect { _bookings.value = it }
        }
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
            } catch (e: FirebaseAuthInvalidCredentialsException) {
                loginError = "That email and password don't match."
            } catch (e: FirebaseAuthInvalidUserException) {
                loginError = "No account with that email."
            } catch (e: FirebaseNetworkException) {
                loginError = "No internet connection."
            } catch (e: Exception) {
                loginError = e.message ?: "Couldn't sign in."
            } finally {
                busy = false
            }
        }
    }

    fun signOut() {
        listenJob?.cancel()
        _bookings.value = null
        repo.signOut()
        authState = AuthState.SignedOut
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
                "Price list published. Edit it any time in Firestore at config/main."
            } else {
                "A price list already exists in Firestore (config/main), so nothing was changed."
            }
        } catch (e: Exception) {
            message = "Couldn't publish: ${e.message}"
        }
    }

    val ownerEmail: String? get() = repo.signedInEmail
}
