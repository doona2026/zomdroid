package com.zomdroid.workshop.auth

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import com.google.common.truth.Truth.assertThat
import java.lang.reflect.Proxy
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Test

class SteamAuthRepositoryTest {
    @Test
    fun loadsMultipleAccountsAndSwitchesActiveAccount() {
        val preferences = fakePreferences()
        preferences.edit().putString(
            "accounts_json",
            """{"accounts":[
                {"accountId":"a","accountName":"Alice","steamId":1,"refreshToken":"refresh-a"},
                {"accountId":"b","accountName":"Bob","steamId":2,"refreshToken":"refresh-b"}
            ],"activeAccountId":"a"}""".replace("\n", ""),
        ).apply()
        val repository = SteamAuthRepository(TestContext(preferences))

        repository.setActiveAccount("b")

        val snapshot = repository.loadSnapshot()
        assertThat(snapshot.activeAccountId).isEqualTo("b")
        assertThat(snapshot.accounts.map { it.accountName }).containsExactly("Alice", "Bob").inOrder()
        assertThat(snapshot.activeAccount?.accountName).isEqualTo("Bob")
    }

    @Test
    fun removesAccountWithoutTouchingOtherAccounts() {
        val preferences = fakePreferences()
        preferences.edit().putString(
            "accounts_json",
            """{"accounts":[{"accountId":"a","accountName":"Alice","steamId":1,"refreshToken":"refresh-a"}],"activeAccountId":"a"}""",
        ).apply()
        val repository = SteamAuthRepository(TestContext(preferences))

        repository.removeAccount("a")

        assertThat(repository.loadSnapshot().accounts).isEmpty()
        assertThat(repository.activeAccountId()).isNull()
    }

    @Test
    fun projectsWebCookiesFromStoredTokenWithoutPassword() {
        val preferences = fakePreferences()
        preferences.edit().putString(
            "accounts_json",
            """{"accounts":[{"accountId":"a","accountName":"Alice","steamId":1,"refreshToken":"refresh-a","webAccessToken":"access-a","webAccessTokenExpEpochSeconds":4102444800,"webSessionId":"session-a"}],"activeAccountId":"a"}""",
        ).apply()
        val repository = SteamAuthRepository(TestContext(preferences))

        val cookies = repository.blockingCookieHeaderFor("https://steamcommunity.com/sharedfiles".toHttpUrl(), "a")

        assertThat(cookies).isEqualTo("steamLoginSecure=1||access-a; sessionid=session-a")
    }

    private class TestContext(private val preferences: SharedPreferences) : ContextWrapper(null) {
        override fun getApplicationContext(): Context = this
        override fun getPackageName(): String = "com.zomdroid.test"
        override fun getSharedPreferences(name: String, mode: Int): SharedPreferences = preferences
    }

    private fun fakePreferences(): SharedPreferences {
        val values = mutableMapOf<String, Any?>()
        lateinit var editor: SharedPreferences.Editor
        editor = Proxy.newProxyInstance(
            SharedPreferences.Editor::class.java.classLoader,
            arrayOf(SharedPreferences.Editor::class.java),
        ) { _, method, args ->
            when (method.name) {
                "putString", "putBoolean", "putInt", "putLong", "putFloat" -> {
                    values[args!![0] as String] = args[1]
                    editor
                }
                "remove" -> { values.remove(args!![0] as String); editor }
                "clear" -> { values.clear(); editor }
                "apply" -> null
                "commit" -> true
                else -> null
            }
        } as SharedPreferences.Editor
        return Proxy.newProxyInstance(
            SharedPreferences::class.java.classLoader,
            arrayOf(SharedPreferences::class.java),
        ) { _, method, args ->
            when (method.name) {
                "getString" -> values[args!![0] as String] as String?
                "getBoolean" -> values[args!![0] as String] as? Boolean ?: args[1] as Boolean
                "getInt" -> values[args!![0] as String] as? Int ?: args[1] as Int
                "getLong" -> values[args!![0] as String] as? Long ?: args[1] as Long
                "getFloat" -> values[args!![0] as String] as? Float ?: args[1] as Float
                "contains" -> values.containsKey(args!![0] as String)
                "getAll" -> values.toMap()
                "edit" -> editor
                "registerOnSharedPreferenceChangeListener", "unregisterOnSharedPreferenceChangeListener" -> null
                else -> null
            }
        } as SharedPreferences
    }
}
