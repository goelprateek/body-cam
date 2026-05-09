package com.company.bodycam

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import java.util.Locale
import java.util.concurrent.TimeUnit
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

    private var sessionStartTime: Long = 0L
    private val blinkAnimation by lazy {
        AlphaAnimation(1f, 0f).apply {
            duration = 500
            repeatMode = Animation.REVERSE
            repeatCount = Animation.INFINITE
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

        binding.highQualityToggle.setOnCheckedChangeListener { _, isChecked ->
            viewModel.setHighQualityMode(isChecked)
        }

        binding.flipCameraIcon.setOnClickListener {
            viewModel.flipCamera()
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
            getString(R.string.env_missing)
        } else {
            state.backendUrl
        }
        binding.userSummary.text = state.user?.let {
            "${it.displayName} (${it.role})"
        } ?: "Not logged in"
        binding.sessionSummary.text = state.sessionSummary.ifBlank { "No active session" }
        binding.streamStatus.text = state.streamStatus
        binding.syncStatus.text = state.syncStatus
        binding.previewContainer.visibility = View.VISIBLE
        binding.previewPlaceholder.visibility = if (state.isStreaming) View.GONE else View.VISIBLE
        binding.sessionSummary.visibility = if (state.isStreaming) View.VISIBLE else View.GONE

        // Interactive Recording UI
        if (state.isStreaming) {
            if (binding.recordingIndicator.visibility != View.VISIBLE) {
                binding.recordingIndicator.visibility = View.VISIBLE
                binding.blinkingDot.startAnimation(blinkAnimation)
                sessionStartTime = System.currentTimeMillis()
                startTimer()
            }
        } else {
            binding.recordingIndicator.visibility = View.GONE
            binding.blinkingDot.clearAnimation()
        }

        // Button States & Visibility
        if (state.isStreaming) {
            binding.startSessionButton.visibility = View.GONE
            binding.stopSessionButton.visibility = View.VISIBLE
            binding.stopSessionButton.isEnabled = !state.actionInFlight
            binding.stopSessionButton.text = if (state.actionInFlight) "Ending Session..." else getString(R.string.action_stop)
        } else {
            binding.startSessionButton.visibility = View.VISIBLE
            binding.stopSessionButton.visibility = View.GONE
            binding.startSessionButton.isEnabled = state.user != null && !state.actionInFlight
            
            binding.startSessionButton.text = when {
                state.actionInFlight -> "Connecting..."
                state.user == null -> "Sign in to Stream"
                else -> getString(R.string.action_start_stream)
            }
        }

        binding.loginButton.isEnabled = !state.loginInFlight
        binding.logoutButton.isEnabled = state.user != null && !state.actionInFlight
        binding.progressBar.visibility = if (state.loginInFlight || state.actionInFlight) View.VISIBLE else View.GONE
        binding.messageBanner.visibility = if (state.message.isNullOrBlank()) View.GONE else View.VISIBLE
        binding.messageBanner.text = state.message
        binding.loginStatusChip.text = if (state.user != null) "Authenticated" else "Sign in required"
        
        if (state.thermalThrottling) {
            binding.streamStatus.setTextColor(ContextCompat.getColor(this, R.color.danger))
        } else {
            binding.streamStatus.setTextColor(ContextCompat.getColor(this, R.color.text_primary))
        }

        binding.highQualityToggle.apply {
            if (isChecked != state.highQualityMode) {
                isChecked = state.highQualityMode
            }
            isEnabled = !state.isStreaming && !state.actionInFlight
        }

        binding.flipCameraIcon.apply {
            visibility = if (state.isStreaming && state.canFlipCamera) View.VISIBLE else View.GONE
            isEnabled = !state.cameraSwitchInFlight
            alpha = if (state.cameraSwitchInFlight) 0.5f else 1.0f
        }

        binding.loginLayout.visibility = if (state.user == null) View.VISIBLE else View.GONE
        binding.sessionLayout.visibility = if (state.user != null) View.VISIBLE else View.GONE

        if (state.user != null) {
            val imm = getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            imm.hideSoftInputFromWindow(binding.root.windowToken, 0)
        }
    }

    private fun startTimer() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (binding.recordingIndicator.visibility == View.VISIBLE) {
                    val elapsed = System.currentTimeMillis() - sessionStartTime
                    val hours = TimeUnit.MILLISECONDS.toHours(elapsed)
                    val minutes = TimeUnit.MILLISECONDS.toMinutes(elapsed) % 60
                    val seconds = TimeUnit.MILLISECONDS.toSeconds(elapsed) % 60
                    
                    binding.recordingTimer.text = if (hours > 0) {
                        String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds)
                    } else {
                        String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
                    }
                    kotlinx.coroutines.delay(1000)
                }
            }
        }
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
        val permissions = mutableListOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val denied = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_DENIED
        }
        
        if (denied.isNotEmpty()) {
            permissionLauncher.launch(denied.toTypedArray())
        }
    }
}
