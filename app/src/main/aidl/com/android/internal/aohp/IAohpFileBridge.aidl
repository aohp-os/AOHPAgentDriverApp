/*
 * Copyright (C) 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * @hide
 */
package com.android.internal.aohp;

/** @hide */
interface IAohpFileBridge {
    String stat(String path);
    String list(String path, String optionsJson);
    String recent(String optionsJson);
    String snapshot(String optionsJson);
    String diff(String beforeSnapshotId, String afterSnapshotId, String optionsJson);
}
