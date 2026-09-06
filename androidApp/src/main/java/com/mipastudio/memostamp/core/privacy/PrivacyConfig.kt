package com.mipastudio.memostamp.core.privacy

import com.mipastudio.memostamp.BuildConfig

object PrivacyConfig {
    const val DEFAULT_PRIVACY_POLICY_URL = "https://memostamp.mipastudio.com/privacy"
    const val DEFAULT_ACCOUNT_DELETION_URL = "https://memostamp.mipastudio.com/account-deletion"

    fun getPrivacyPolicyUrl(): String {
        return try {
            val configured = BuildConfig.PRIVACY_POLICY_URL
            if (configured.isNotBlank()) configured else DEFAULT_PRIVACY_POLICY_URL
        } catch (_: Throwable) {
            DEFAULT_PRIVACY_POLICY_URL
        }
    }

    fun getAccountDeletionUrl(): String {
        return DEFAULT_ACCOUNT_DELETION_URL
    }
}
