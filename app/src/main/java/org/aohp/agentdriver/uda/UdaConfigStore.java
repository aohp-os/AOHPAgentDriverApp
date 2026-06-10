package org.aohp.agentdriver.uda;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

/**
 * Persists UDA LLM settings in encrypted app storage (never shipped in the system image).
 */
public final class UdaConfigStore {
    private static final String PREFS = "uda_config_secure";
    private static final String KEY_API = "uda_api_key";
    private static final String KEY_MODEL = "uda_model";
    private static final String KEY_BASE_URL = "uda_base_url";
    private static final String KEY_PROVIDER = "uda_llm_provider";

    private static final String DEFAULT_MODEL = "doubao-seed-2-0-pro-260215";
    private static final String DEFAULT_BASE_URL = "https://ark.cn-beijing.volces.com/api/v3";
    private static final String DEFAULT_PROVIDER = "openai";

    private final SharedPreferences mPrefs;

    public UdaConfigStore(@NonNull Context context) {
        mPrefs = createPrefs(context.getApplicationContext());
    }

    private static SharedPreferences createPrefs(Context appContext) {
        try {
            MasterKey masterKey =
                    new MasterKey.Builder(appContext)
                            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                            .build();
            return EncryptedSharedPreferences.create(
                    appContext,
                    PREFS,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM);
        } catch (Exception e) {
            return appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        }
    }

    @NonNull
    public UdaConfig load() {
        return new UdaConfig(
                emptyToNull(mPrefs.getString(KEY_API, null)),
                mPrefs.getString(KEY_MODEL, DEFAULT_MODEL),
                mPrefs.getString(KEY_BASE_URL, DEFAULT_BASE_URL),
                mPrefs.getString(KEY_PROVIDER, DEFAULT_PROVIDER));
    }

    public void save(@NonNull UdaConfig config) {
        mPrefs.edit()
                .putString(KEY_API, config.apiKey != null ? config.apiKey : "")
                .putString(KEY_MODEL, config.model)
                .putString(KEY_BASE_URL, config.baseUrl)
                .putString(KEY_PROVIDER, config.llmProvider)
                .apply();
    }

    private static String emptyToNull(String s) {
        if (s == null || s.trim().isEmpty()) {
            return null;
        }
        return s.trim();
    }
}
