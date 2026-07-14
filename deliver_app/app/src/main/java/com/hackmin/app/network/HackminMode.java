package com.hackmin.app.network;

/**
 * Mirrors the server's HACKMIN_MODE values. Drive this from a debug-menu
 * toggle in the app so QA/demo builds can flip between modes at runtime
 * without rebuilding.
 */
public enum HackminMode {
    VULNERABLE("vulnerable"),
    SECURE("secure");

    private final String headerValue;

    HackminMode(String headerValue) {
        this.headerValue = headerValue;
    }

    public String getHeaderValue() {
        return headerValue;
    }
}
