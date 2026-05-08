package com.company.bodycam

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.company.bodycam.databinding.ActivityMainBinding
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels {
        MainViewModel.Factory(application)
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val denied = grants.entries.filterNot { it.value }
        if (denied.isNotEmpty()) {
            Toast.makeText(
                this,
                "Camera and microphone permissions are required to stream",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        viewModel.bindPreview(binding.previewView, this)
        requestNeededPermissions()
        bindActions()
        observeState()
    }

    override fun onDestroy() {
        super.onDestroy()
        viewModel.release()
    }

    private fun bindActions() {
        binding.loginButton.setOnClickListener {
            viewModel.login(
                username = binding.usernameInput.text?.toString().orEmpty(),
                password = binding.passwordInput.text?.toString().orEmpty()
            )
        }

        binding.startSessionButton.setOnClickListener {
            if (!hasAllPermissions()) {
                requestNeededPermissions()
                return@setOnClickListener
            }
            viewModel.startSession()
        }

        binding.stopSessionButton.setOnClickListener {
            viewModel.stopSession()
        }

        binding.logoutButton.setOnClickListener {
            viewModel.logout()
        }
    }

    private fun observeState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    render(state)
                }
            }
        }
    }

    private fun render(state: MainUiState) {
        if (binding.usernameInput.text?.toString() != state.username) {
            binding.usernameInput.setText(state.username)
        }
        if (binding.passwordInput.text?.toString() != state.password) {
            binding.passwordInput.setText(state.password)
        }

        binding.environmentValue.text = if (state.backendUrl.isBlank()) {
            "Backend configuration missing"
        } else {
            "Managed by deployed environment"
        }
        binding.userSummary.text = state.user?.let {
            "${it.displayName} (${it.role})"
        } ?: "Not logged in"
        binding.sessionSummary.text = state.sessionSummary.ifBlank { "No active session" }
        binding.streamStatus.text = state.streamStatus
        binding.syncStatus.text = state.syncStatus
        binding.previewContainer.visibility = View.VISIBLE
        binding.previewPlaceholder.visibility = if (state.isStreaming) View.GONE else View.VISIBLE
        binding.loginButton.isEnabled = !state.loginInFlight
        binding.startSessionButton.isEnabled = state.user != null && !state.isStreaming && !state.actionInFlight
        binding.stopSessionButton.isEnabled = state.isStreaming && !state.actionInFlight
        binding.logoutButton.isEnabled = state.user != null && !state.actionInFlight
        binding.progressBar.visibility = if (state.loginInFlight || state.actionInFlight) View.VISIBLE else View.GONE
        binding.messageBanner.visibility = if (state.message.isNullOrBlank()) View.GONE else View.VISIBLE
        binding.messageBanner.text = state.message
        binding.loginStatusChip.text = if (state.user != null) "Authenticated" else "Sign in required"
        binding.streamStatusChip.text = if (state.isStreaming) "Live" else "Offline"
    }

    private fun hasAllPermissions(): Boolean {
        val permissions = listOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO
        )
        return permissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestNeededPermissions() {
        val permissions = listOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO
        ).filter {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_DENIED
        }
        if (permissions.isNotEmpty()) {
            permissionLauncher.launch(permissions.toTypedArray())
        }
    }
}
