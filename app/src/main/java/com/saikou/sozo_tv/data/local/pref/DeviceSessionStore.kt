package com.saikou.sozo_tv.data.local.pref

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.bugsnag.android.Bugsnag
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class DeviceSession(
    val userId: String,
    val username: String,
    val email: String?,
    val displayName: String?,
    val photoUrl: String?,
    val accessToken: String,
    val refreshToken: String,
    /** Epoch ms, decoded from the access JWT's `exp` claim (the server signs access for 15m). */
    val accessExpiresAtMs: Long,
    val sessionId: String,
)

/**
 * The single source of truth for "is this TV signed in?". Encrypted token storage plus an
 * observable [session] so every login-aware surface can react instead of re-reading prefs on
 * each bind.
 *
 * Never put these tokens in the app's plaintext `app_preferences`.
 */
class DeviceSessionStore(context: Context) {

    // EncryptedSharedPreferences.create() throws on devices with a broken or absent
    // AndroidKeyStore, which is not rare on cheap TV boxes at minSdk 24. This is a Koin single,
    // so a throw here would take down graph construction; a plaintext downgrade beats a crash loop.
    private val prefs: SharedPreferences = runCatching {
        EncryptedSharedPreferences.create(
            context,
            FILE,
            MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }.getOrElse { failure ->
        runCatching { Bugsnag.notify(failure) }
        context.getSharedPreferences(FILE + "_plain", Context.MODE_PRIVATE)
    }

    private val _session = MutableStateFlow(read())
    val session: StateFlow<DeviceSession?> get() = _session

    fun current(): DeviceSession? = _session.value

    fun isSignedIn(): Boolean = _session.value != null

    /**
     * Full write after the pairing is approved. `commit()`, not `apply()`: the pairing flips to
     * `claimed` server-side before the response is sent, so these tokens arrive in exactly one
     * HTTP response ever — a lost write means re-pairing.
     */
    fun save(session: DeviceSession) {
        val durable = prefs.edit()
            .putString(K_USER_ID, session.userId)
            .putString(K_USERNAME, session.username)
            .putString(K_EMAIL, session.email)
            .putString(K_DISPLAY_NAME, session.displayName)
            .putString(K_PHOTO, session.photoUrl)
            .putString(K_ACCESS, session.accessToken)
            .putString(K_REFRESH, session.refreshToken)
            .putLong(K_ACCESS_EXP, session.accessExpiresAtMs)
            .putString(K_SESSION_ID, session.sessionId)
            .commit()
        // Publish either way: the tokens are valid for this process even if the disk write lost
        // them, and the alternative is a signed-in server session the TV pretends it never got.
        // A false here means the user will silently need to re-pair after a restart, so it is
        // worth reporting rather than swallowing.
        _session.value = session
        if (!durable) report("save() commit() returned false")
    }

    /**
     * Rotation write after a refresh. The refresh endpoint returns no user object, so identity
     * is carried over from the current session. One atomic `commit()` — the OLD refresh token is
     * already dead by the time this is called, so a lost write signs the TV out permanently.
     */
    fun updateTokens(accessToken: String, refreshToken: String, accessExpiresAtMs: Long) {
        val existing = _session.value ?: return
        save(
            existing.copy(
                accessToken = accessToken,
                refreshToken = refreshToken,
                accessExpiresAtMs = accessExpiresAtMs,
            )
        )
    }

    fun clear() {
        // Retried once and reported: this is the sign-out and terminal-401 path, so a lost write
        // leaves live credentials on disk after the user believes they are gone.
        if (!prefs.edit().clear().commit() && !prefs.edit().clear().commit()) {
            report("clear() commit() returned false — tokens may survive on disk")
        }
        _session.value = null
    }

    /** [Bugsnag.notify], not a breadcrumb: breadcrumbs only ship attached to some later crash. */
    private fun report(what: String) {
        runCatching { Bugsnag.notify(IllegalStateException("DeviceSessionStore: $what")) }
    }

    private fun read(): DeviceSession? {
        val access = prefs.getString(K_ACCESS, null) ?: return null
        val refresh = prefs.getString(K_REFRESH, null) ?: return null
        return DeviceSession(
            userId = prefs.getString(K_USER_ID, "").orEmpty(),
            username = prefs.getString(K_USERNAME, "").orEmpty(),
            email = prefs.getString(K_EMAIL, null),
            displayName = prefs.getString(K_DISPLAY_NAME, null),
            photoUrl = prefs.getString(K_PHOTO, null),
            accessToken = access,
            refreshToken = refresh,
            accessExpiresAtMs = prefs.getLong(K_ACCESS_EXP, 0L),
            sessionId = prefs.getString(K_SESSION_ID, "").orEmpty(),
        )
    }

    private companion object {
        const val FILE = "sozo_device_session"
        const val K_USER_ID = "user_id"
        const val K_USERNAME = "username"
        const val K_EMAIL = "email"
        const val K_DISPLAY_NAME = "display_name"
        const val K_PHOTO = "photo_url"
        const val K_ACCESS = "access_token"
        const val K_REFRESH = "refresh_token"
        const val K_ACCESS_EXP = "access_exp_ms"
        const val K_SESSION_ID = "session_id"
    }
}
