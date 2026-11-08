package com.udyam.digital.security;

/**
 * Unlock state for viewing stored documents. After a fingerprint / face / PIN check the owner
 * can view documents until the app has been in the background for {@value #LOCK_AFTER_MS} ms
 * (2 minutes); then the check is asked again. Pure Java with an injected clock (unit-tested).
 */
public final class AccessSession {

    public static final long LOCK_AFTER_MS = 2 * 60 * 1000L;

    private boolean unlocked;
    private long backgroundSince = -1;

    public synchronized void unlock() {
        unlocked = true;
        backgroundSince = -1;
    }

    public synchronized void lock() { unlocked = false; }

    /** The whole app went to the background (no visible activity). */
    public synchronized void onBackground(long nowMs) {
        if (backgroundSince < 0) backgroundSince = nowMs;
    }

    /** The app came back. Locks if it was away 2 minutes or more. */
    public synchronized void onForeground(long nowMs) {
        if (backgroundSince >= 0 && nowMs - backgroundSince >= LOCK_AFTER_MS) unlocked = false;
        backgroundSince = -1;
    }

    public synchronized boolean isUnlocked(long nowMs) {
        if (backgroundSince >= 0 && nowMs - backgroundSince >= LOCK_AFTER_MS) unlocked = false;
        return unlocked;
    }
}
