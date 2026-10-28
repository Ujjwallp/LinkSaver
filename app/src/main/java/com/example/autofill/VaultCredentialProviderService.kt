package com.example.autofill

import android.credentials.ClearCredentialStateException
import android.credentials.CreateCredentialException
import android.credentials.GetCredentialException
import android.os.Build
import android.os.CancellationSignal
import android.os.OutcomeReceiver
import android.service.credentials.BeginCreateCredentialRequest
import android.service.credentials.BeginCreateCredentialResponse
import android.service.credentials.BeginGetCredentialRequest
import android.service.credentials.BeginGetCredentialResponse
import android.service.credentials.ClearCredentialStateRequest
import android.service.credentials.CredentialProviderService
import androidx.annotation.RequiresApi
import com.example.util.SecureLog as Log

/**
 * Modern Android 14+ Credential Provider Service for Credential Manager integration.
 */
@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
class VaultCredentialProviderService : CredentialProviderService() {

    override fun onBeginGetCredential(
        request: BeginGetCredentialRequest,
        cancellationSignal: CancellationSignal,
        callback: OutcomeReceiver<BeginGetCredentialResponse, GetCredentialException>
    ) {
        try {
            val responseBuilder = BeginGetCredentialResponse.Builder()
            callback.onResult(responseBuilder.build())
        } catch (e: Exception) {
            Log.e("VaultCredentialProvider", "Error in onBeginGetCredential", e)
            callback.onError(
                GetCredentialException(
                    GetCredentialException.TYPE_UNKNOWN,
                    e.message
                )
            )
        }
    }

    override fun onBeginCreateCredential(
        request: BeginCreateCredentialRequest,
        cancellationSignal: CancellationSignal,
        callback: OutcomeReceiver<BeginCreateCredentialResponse, CreateCredentialException>
    ) {
        try {
            val responseBuilder = BeginCreateCredentialResponse.Builder()
            callback.onResult(responseBuilder.build())
        } catch (e: Exception) {
            Log.e("VaultCredentialProvider", "Error in onBeginCreateCredential", e)
            callback.onError(
                CreateCredentialException(
                    CreateCredentialException.TYPE_UNKNOWN,
                    e.message
                )
            )
        }
    }

    override fun onClearCredentialState(
        request: ClearCredentialStateRequest,
        cancellationSignal: CancellationSignal,
        callback: OutcomeReceiver<Void?, ClearCredentialStateException>
    ) {
        try {
            callback.onResult(null)
        } catch (e: Exception) {
            Log.e("VaultCredentialProvider", "Error in onClearCredentialState", e)
            callback.onError(
                ClearCredentialStateException(
                    ClearCredentialStateException.TYPE_UNKNOWN,
                    e.message
                )
            )
        }
    }
}
