package org.aohp.agentdriver.uda;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/** LLM configuration for UDAGen (not baked into the system image). */
public final class UdaConfig {
    @Nullable public final String apiKey;
    @NonNull public final String model;
    @NonNull public final String baseUrl;
    @NonNull public final String llmProvider;

    public UdaConfig(
            @Nullable String apiKey,
            @NonNull String model,
            @NonNull String baseUrl,
            @NonNull String llmProvider) {
        this.apiKey = apiKey;
        this.model = model;
        this.baseUrl = baseUrl;
        this.llmProvider = llmProvider;
    }

    public boolean isComplete() {
        return apiKey != null && !apiKey.trim().isEmpty()
                && model != null && !model.trim().isEmpty()
                && baseUrl != null && !baseUrl.trim().isEmpty();
    }

    @NonNull
    public String maskedApiKey() {
        if (apiKey == null || apiKey.length() <= 4) {
            return "****";
        }
        return "****" + apiKey.substring(apiKey.length() - 4);
    }
}
