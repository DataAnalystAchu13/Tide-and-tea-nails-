package com.tideandtea.owner.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tideandtea.owner.R
import com.tideandtea.owner.ui.components.Bunny
import com.tideandtea.owner.ui.components.Sparkle
import com.tideandtea.owner.ui.theme.Gold
import com.tideandtea.owner.ui.theme.Pink
import com.tideandtea.owner.ui.theme.Plum
import com.tideandtea.owner.ui.theme.Royal

@Composable
fun SplashScreen() {
    Box(Modifier.fillMaxSize().background(Royal), contentAlignment = Alignment.Center) {
        Bunny(size = 110.dp, hop = true)
    }
}

@Composable
fun LoginScreen(busy: Boolean, error: String?, onSignIn: (String, String) -> Unit) {
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = Color.White, unfocusedTextColor = Color.White,
        focusedBorderColor = Pink, unfocusedBorderColor = Color.White.copy(alpha = 0.35f),
        focusedLabelColor = Pink, unfocusedLabelColor = Color.White.copy(alpha = 0.7f),
        cursorColor = Pink,
    )
    Box(Modifier.fillMaxSize().background(Royal)) {
        Column(
            Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(Modifier.size(220.dp), contentAlignment = Alignment.Center) {
                Image(
                    painterResource(R.drawable.logo), contentDescription = "Tide and Tea Nails",
                    modifier = Modifier.size(200.dp).clip(CircleShape),
                )
                Bunny(size = 76.dp, modifier = Modifier.align(Alignment.BottomStart).offset(x = (-6).dp, y = 8.dp))
                Sparkle(24.dp, Gold, Modifier.align(Alignment.TopEnd))
                Sparkle(14.dp, Pink, Modifier.align(Alignment.BottomEnd).offset(x = (-24).dp))
            }
            Text("Studio", style = MaterialTheme.typography.displaySmall, color = Color.White)
            Text(
                "Sign in to see new booking requests, inspo pics and client locations.",
                style = MaterialTheme.typography.bodyLarge, color = Color.White.copy(alpha = 0.8f), textAlign = TextAlign.Center,
            )
            OutlinedTextField(
                value = email, onValueChange = { email = it }, label = { Text("Email") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                colors = fieldColors, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = password, onValueChange = { password = it }, label = { Text("Password") }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onSignIn(email, password) }),
                colors = fieldColors, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth(),
            )
            if (error != null) Text(error, color = Pink, style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center)
            Button(
                onClick = { onSignIn(email, password) }, enabled = !busy,
                colors = ButtonDefaults.buttonColors(containerColor = Pink, contentColor = Plum),
                modifier = Modifier.fillMaxWidth().height(54.dp),
            ) {
                if (busy) CircularProgressIndicator(Modifier.size(22.dp), color = Plum, strokeWidth = 2.5.dp)
                else Text("Sign in", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}
