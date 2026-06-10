/*
 * Copyright (C) 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * @hide
 */
package com.android.internal.aohp;

/** @hide */
interface IAohpEventStream {
    String register(String clientId, String optionsJson);
    String drain(String sessionId, String optionsJson);
    boolean unregister(String sessionId);
    String status();
    void recordHeadsUp(String type, String key, String packageName);
}
