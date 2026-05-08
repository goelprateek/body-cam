package com.company.bodycam

import androidx.camera.lifecycle.ProcessCameraProvider
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

suspend fun <T> ListenableFuture<T>.await(): T = suspendCancellableCoroutine { continuation ->
    addListener(
        {
            try {
                continuation.resume(get())
            } catch (exception: Exception) {
                continuation.resumeWithException(exception)
            }
        },
        Runnable::run
    )
}
