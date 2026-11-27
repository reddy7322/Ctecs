package com.example.model

/**
 * Authoritative chainage calculation source according to priority:
 * 1. MANUAL - Active manual chainage override
 * 2. CUSTOM_CORRECTION - Manual reference calibrated KMZ chainage within ±2 km coverage
 * 3. KMZ - Original imported KMZ alignment chainage
 * 4. GPS - Unmatched GPS-derived chainage
 */
enum class ChainageSource(val label: String) {
    MANUAL("MANUAL"),
    CUSTOM_CORRECTION("KMZ CALIBRATED"),
    KMZ("KMZ"),
    GPS("GPS")
}
