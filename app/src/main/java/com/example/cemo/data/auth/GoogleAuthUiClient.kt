package com.example.cemo.data.auth

import android.content.Context
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import com.example.cemo.AppState
import com.example.cemo.data.image.AvatarCompressor
import com.example.cemo.data.model.User
import com.example.cemo.data.model.UserRole
import com.example.cemo.R
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.Firebase
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import com.google.firebase.firestore.firestore
import com.google.firebase.storage.StorageMetadata
import com.google.firebase.storage.storage
import kotlinx.coroutines.tasks.await
import java.util.Date

import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GoogleAuthUiClient(
    private val context: Context
) {
    private val auth = Firebase.auth
    private val firestore = Firebase.firestore
    private val storage = Firebase.storage
    private val credentialManager = CredentialManager.create(context)
    private val TAG = "GoogleAuthUiClient"

    private fun showToast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }

    /** Mirrors the freshest profile into [AppState] so screens can render it without refetching. */
    private fun cacheUser(user: User) {
        AppState.cachedUser.value = user
    }

    suspend fun signIn(): SignInResult {
        return try {
            val serverClientId = context.getString(R.string.default_web_client_id)
            Log.d(TAG, "Starting sign in with client ID: $serverClientId")
            
            if (serverClientId == "YOUR_WEB_CLIENT_ID_HERE" || serverClientId.isBlank()) {
                Log.e(TAG, "Web Client ID not configured!")
                return SignInResult(null, "Please configure your Web Client ID in strings.xml")
            }

            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(serverClientId)
                .setAutoSelectEnabled(false) // Disable auto-select to force account picker
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            Log.d(TAG, "Requesting credentials from CredentialManager...")
            val result = try {
                credentialManager.getCredential(context, request)
            } catch (e: androidx.credentials.exceptions.GetCredentialException) {
                Log.e(TAG, "CredentialManager error: ${e.type} - ${e.message}")
                return SignInResult(null, "Google Sign-In Error: ${e.message}")
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error during getCredential", e)
                return SignInResult(null, "Unexpected error: ${e.message}")
            }

            val credential = result.credential
            Log.d(TAG, "Received credential type: ${credential.type}")

            val googleIdTokenCredential = when {
                credential is GoogleIdTokenCredential -> {
                    Log.d(TAG, "Credential is GoogleIdTokenCredential")
                    credential
                }
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL -> {
                    Log.d(TAG, "Credential type matches string, creating from bundle")
                    try {
                        GoogleIdTokenCredential.createFrom(credential.data)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error creating GoogleIdTokenCredential from bundle", e)
                        null
                    }
                }
                else -> {
                    Log.e(TAG, "No matching credential handler for type: ${credential.type}")
                    null
                }
            }

            if (googleIdTokenCredential != null) {
                Log.d(TAG, "Google ID Token received, signing into Firebase...")
                val firebaseCredential = GoogleAuthProvider.getCredential(googleIdTokenCredential.idToken, null)
                val user = auth.signInWithCredential(firebaseCredential).await().user
                Log.d(TAG, "Firebase sign in successful for user: ${user?.email}")
                
                val fullName = user?.displayName ?: ""
                val nameParts = fullName.trim().split("\\s+".toRegex())
                val firstName = if (nameParts.size > 1) nameParts.dropLast(1).joinToString(" ") else fullName
                val lastName = if (nameParts.size > 1) nameParts.last() else null

                val uid = user?.uid
                val existingData = if (uid != null) getUserData(uid) else null

                val finalData = if (existingData != null) {
                    existingData
                } else {
                    val creationDate = Date(user?.metadata?.creationTimestamp ?: System.currentTimeMillis())
                    val userData = user?.let {
                        User(
                            uid = it.uid,
                            email = it.email ?: "",
                            displayName = it.displayName,
                            firstName = firstName,
                            lastName = lastName,
                            photoUrl = it.photoUrl?.toString(),
                            role = UserRole.USER,
                            isIndividual = true,
                            establishmentName = null,
                            establishmentAddress = null,
                            createdAt = creationDate,
                            updatedAt = null
                        )
                    }

                    if (userData != null) {
                        saveUserToFirestore(userData)
                    }
                    userData
                }

                SignInResult(
                    data = finalData,
                    errorMessage = null
                )
            } else {
                Log.e(TAG, "Could not extract Google ID Token from credential: ${credential.type}")
                SignInResult(null, "Could not extract identity token")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Sign in process failed with general exception", e)
            SignInResult(null, "Sign in failed: ${e.message}")
        }
    }

    private suspend fun saveUserToFirestore(user: User) {
        try {
            Log.d(TAG, "FIRESTORE: Starting write for UID=${user.uid}")
            
            val userMap = hashMapOf<String, Any?>(
                "uid" to user.uid,
                "email" to user.email,
                "displayName" to user.displayName,
                "firstName" to user.firstName,
                "lastName" to user.lastName,
                "photoUrl" to user.photoUrl,
                "role" to user.role.name,
                "isIndividual" to user.isIndividual,
                "establishmentName" to user.establishmentName,
                "establishmentAddress" to user.establishmentAddress
            )

            user.createdAt?.let {
                userMap["createdAt"] = com.google.firebase.Timestamp(it)
            }
            if (user.updatedAt != null) {
                userMap["updatedAt"] = com.google.firebase.Timestamp(user.updatedAt)
            }

            val docRef = firestore.collection("users").document(user.uid)
            
            // We use set with merge to create OR update without losing fields
            docRef.set(userMap, com.google.firebase.firestore.SetOptions.merge()).await()

            Log.i(TAG, "FIRESTORE: Successfully wrote to server!")
            cacheUser(user)
            withContext(Dispatchers.Main) {
                // showToast("Profile synced with Cloud")
            }
        } catch (e: Exception) {
            Log.e(TAG, "FIRESTORE ERROR: ${e.message}")
            withContext(Dispatchers.Main) {
                showToast("Firestore Error: ${e.message}")
            }
            e.printStackTrace()
        }
    }

    fun getSignedInUser(): User? = auth.currentUser?.let {
        val cached = AppState.cachedUser.value
        if (cached != null && cached.uid == it.uid) {
            return cached
        }
        User(
            uid = it.uid,
            email = it.email ?: "",
            displayName = it.displayName,
            photoUrl = it.photoUrl?.toString(),
            role = UserRole.USER,
            isIndividual = true,
            createdAt = Date(it.metadata?.creationTimestamp ?: System.currentTimeMillis()),
            updatedAt = null
        )
    }

    suspend fun signOut() {
        try {
            // Drop the cached profile first so the next account never briefly sees
            // the previous user's name and avatar.
            AppState.cachedUser.value = null
            auth.signOut()
            credentialManager.clearCredentialState(ClearCredentialStateRequest())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun signInWithEmail(email: String, pass: String): SignInResult {
        return try {
            val result = auth.signInWithEmailAndPassword(email, pass).await()
            val user = result.user

            // Gate access on a verified email. Resend the link so a user who
            // hasn't verified yet has a fresh one waiting.
            if (user != null && !user.isEmailVerified) {
                try { user.sendEmailVerification().await() } catch (_: Exception) {}
                auth.signOut()
                return SignInResult(null, "Email not verified — check your inbox", errorField = AuthField.EMAIL)
            }

            val creationDate = Date(user?.metadata?.creationTimestamp ?: System.currentTimeMillis())
            val userData = user?.let {
                User(
                    uid = it.uid,
                    email = it.email ?: email,
                    displayName = it.displayName,
                    photoUrl = it.photoUrl?.toString(),
                    role = UserRole.USER,
                    isIndividual = true,
                    createdAt = creationDate,
                    updatedAt = null
                )
            }
            if (userData != null) {
                // Fetch full data from firestore to get firstName/lastName if they exist
                val fullData = getUserData(userData.uid)
                val finalData = fullData ?: run {
                    saveUserToFirestore(userData)
                    userData
                }
                cacheUser(finalData)
                SignInResult(finalData, null)
            } else {
                SignInResult(null, "User data not found")
            }
        } catch (e: Exception) {
            authError(e)
        }
    }

    suspend fun signUpWithEmail(
        email: String,
        pass: String,
        firstName: String = "",
        lastName: String = "",
        displayName: String = ""
    ): SignInResult {
        return try {
            val result = auth.createUserWithEmailAndPassword(email, pass).await()
            val user = result.user
                ?: return SignInResult(null, "Account creation failed")

            val fullName = listOf(firstName, lastName)
                .filter { it.isNotBlank() }
                .joinToString(" ")
            // Explicit display name wins; otherwise fall back to the full name.
            val finalDisplayName = displayName.ifBlank { fullName }.ifBlank { null }

            val creationDate = Date(user.metadata?.creationTimestamp ?: System.currentTimeMillis())
            val userData = User(
                uid = user.uid,
                email = user.email ?: email,
                displayName = finalDisplayName,
                firstName = firstName,
                lastName = lastName,
                photoUrl = null,
                role = UserRole.USER,
                isIndividual = true,
                createdAt = creationDate,
                updatedAt = null
            )
            // Persist the record while still authenticated, then send the
            // verification email and sign back out — the user must verify and
            // then log in explicitly, so we don't drop them into the app yet.
            saveUserToFirestore(userData)
            user.sendEmailVerification().await()
            auth.signOut()

            SignInResult(data = null, errorMessage = null, verificationSent = true)
        } catch (e: Exception) {
            authError(e)
        }
    }

    suspend fun updateProfile(user: User) {
        val updated = if (user.updatedAt == null) user.copy(updatedAt = Date()) else user
        saveUserToFirestore(updated)
    }

    /**
     * Uploads already-compressed avatar bytes to Cloud Storage under
     * `avatars/<uid>.webp` and returns the public download URL. Overwriting a
     * fixed path per user keeps one avatar per account instead of orphaning
     * every previous upload. Throws on failure so the caller can surface why.
     */
    suspend fun uploadAvatar(uid: String, bytes: ByteArray, previousPhotoUrl: String?): String {
        val ref = storage.reference.child("avatars/$uid.${AvatarCompressor.FILE_EXTENSION}")
        Log.d(TAG, "STORAGE: Uploading ${bytes.size / 1024}KB avatar to ${ref.path}")
        val metadata = StorageMetadata.Builder()
            .setContentType(AvatarCompressor.CONTENT_TYPE)
            // A long max-age is safe here: every upload mints a fresh download
            // token, so a changed avatar arrives on a new URL rather than being
            // masked by a stale cached copy.
            .setCacheControl("public, max-age=31536000")
            .build()
        ref.putBytes(bytes, metadata).await()
        val downloadUrl = ref.downloadUrl.await().toString()
        Log.i(TAG, "STORAGE: Avatar uploaded, url=$downloadUrl")

        // Only once the new avatar is safely stored, so a failed upload can never
        // leave the account with no photo at all.
        deletePreviousAvatar(previousPhotoUrl, keepPath = ref.path)
        return downloadUrl
    }

    /**
     * Removes the avatar a user was previously using. Avatars are written to one
     * path per account, so the usual case is an overwrite with nothing to clean
     * up — this exists for the leftovers, e.g. a `.jpg` from before uploads were
     * normalised to WEBP.
     *
     * Never throws: the new avatar is already live, and a stale object left in a
     * bucket isn't worth failing the user's action over.
     */
    private suspend fun deletePreviousAvatar(previousPhotoUrl: String?, keepPath: String) {
        if (previousPhotoUrl.isNullOrBlank()) return

        val previousRef = try {
            storage.getReferenceFromUrl(previousPhotoUrl)
        } catch (e: Exception) {
            // Google account photos and the old local file:// paths aren't ours to delete.
            Log.d(TAG, "STORAGE: Previous avatar isn't a Storage object, skipping cleanup")
            return
        }

        if (previousRef.path == keepPath) {
            Log.d(TAG, "STORAGE: Previous avatar was overwritten in place, nothing to delete")
            return
        }

        try {
            previousRef.delete().await()
            Log.i(TAG, "STORAGE: Deleted previous avatar ${previousRef.path}")
        } catch (e: Exception) {
            Log.w(TAG, "STORAGE: Couldn't delete previous avatar ${previousRef.path}", e)
        }
    }

    /** Sends a password-reset email. Returns null errorMessage on success. */
    suspend fun sendPasswordReset(email: String): SignInResult {
        return try {
            auth.sendPasswordResetEmail(email).await()
            SignInResult(null, null)
        } catch (e: Exception) {
            authError(e)
        }
    }

    /**
     * Maps Firebase's verbose auth exceptions to a short message and the input it
     * belongs to. Where Firebase distinguishes them, a missing account is flagged
     * on the email field and a wrong password on the password field; when email
     * enumeration protection hides which is wrong, it falls back to BOTH.
     */
    private fun authError(e: Exception): SignInResult {
        val msg = e.message ?: ""
        val (message, field) = when {
            e is com.google.firebase.auth.FirebaseAuthInvalidUserException ->
                "No account found for this email" to AuthField.EMAIL

            e is com.google.firebase.auth.FirebaseAuthUserCollisionException ->
                "Email already in use" to AuthField.EMAIL

            e is com.google.firebase.auth.FirebaseAuthInvalidCredentialsException -> when {
                msg.contains("badly formatted", true) -> "Enter a valid email address" to AuthField.EMAIL
                msg.contains("password is invalid", true) ||
                    msg.contains("WRONG_PASSWORD", true) -> "Incorrect password" to AuthField.PASSWORD
                else -> "Incorrect email or password" to AuthField.BOTH
            }

            msg.contains("already in use", true) -> "Email already in use" to AuthField.EMAIL
            msg.contains("no user record", true) -> "No account found for this email" to AuthField.EMAIL
            msg.contains("badly formatted", true) -> "Enter a valid email address" to AuthField.EMAIL
            msg.contains("password is invalid", true) -> "Incorrect password" to AuthField.PASSWORD
            msg.contains("at least 6 characters", true) -> "Use at least 6 characters" to AuthField.PASSWORD
            msg.contains("network", true) -> "Network error — check your connection" to AuthField.BOTH
            msg.contains("blocked all requests", true) -> "Too many attempts — try again later" to AuthField.BOTH
            msg.isBlank() -> "Something went wrong" to AuthField.BOTH
            else -> msg to AuthField.BOTH
        }
        return SignInResult(null, message, errorField = field)
    }

    suspend fun getUserData(uid: String): User? {
        return try {
            Log.d(TAG, "FIRESTORE: Fetching data for $uid")
            val doc = firestore.collection("users").document(uid).get().await()
            if (doc.exists()) {
                Log.d(TAG, "FIRESTORE: Document found")
                val firestoreCreatedAt = doc.getTimestamp("createdAt")?.toDate()
                val authCreatedAt = if (auth.currentUser?.uid == uid) {
                    auth.currentUser?.metadata?.creationTimestamp?.let { Date(it) }
                } else null
                val finalCreatedAt = firestoreCreatedAt ?: authCreatedAt ?: Date()

                val rawUpdatedAt = doc.getTimestamp("updatedAt")?.toDate()
                // If updatedAt in Firestore was set during initial account creation (within 5 seconds of createdAt),
                // treat it as not updated yet.
                val finalUpdatedAt = if (rawUpdatedAt != null && Math.abs(rawUpdatedAt.time - finalCreatedAt.time) <= 5000L) {
                    null
                } else {
                    rawUpdatedAt
                }

                // If createdAt was missing in Firestore, backfill it so Firestore is kept consistent
                if (firestoreCreatedAt == null) {
                    try {
                        firestore.collection("users").document(uid).update(
                            "createdAt", com.google.firebase.Timestamp(finalCreatedAt)
                        )
                    } catch (e: Exception) {
                        Log.w(TAG, "Could not backfill createdAt: ${e.message}")
                    }
                }

                User(
                    uid = doc.getString("uid") ?: uid,
                    email = doc.getString("email") ?: "",
                    displayName = doc.getString("displayName"),
                    firstName = doc.getString("firstName"),
                    lastName = doc.getString("lastName"),
                    photoUrl = doc.getString("photoUrl"),
                    role = try { UserRole.valueOf(doc.getString("role") ?: "USER") } catch(e: Exception) { UserRole.USER },
                    isIndividual = doc.getBoolean("isIndividual") ?: true,
                    establishmentName = doc.getString("establishmentName"),
                    establishmentAddress = doc.getString("establishmentAddress"),
                    createdAt = finalCreatedAt,
                    updatedAt = finalUpdatedAt
                ).also { cacheUser(it) }
            } else {
                Log.w(TAG, "FIRESTORE: Document does not exist for $uid")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "FIRESTORE ERROR: Fetch failed", e)
            withContext(Dispatchers.Main) {
                showToast("Fetch Error: ${e.message}")
            }
            null
        }
    }
}
