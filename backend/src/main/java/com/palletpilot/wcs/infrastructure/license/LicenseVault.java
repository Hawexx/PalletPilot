package com.palletpilot.wcs.infrastructure.license;

/**
 * Source-level override of license-core.jar's LicenseVault.
 * Takes classpath priority over the JAR version.
 *
 * In dev mode (LICENSE_SKIP_CHECK=true), verify() always returns true.
 * In production, delegates to LicenseGuard's runtime validity state.
 */
public final class LicenseVault {

    private LicenseVault() {
        // Utility class — no instances
    }

    /**
     * Verify license validity for WCS subsystem operations.
     * Returns true if LICENSE_SKIP_CHECK=true or if LicenseGuard has validated the
     * license.
     */
    public static boolean verify() {
        String skipCheck = System.getenv("LICENSE_SKIP_CHECK");
        if ("true".equalsIgnoreCase(skipCheck)) {
            return true;
        }
        // In production, defer to LicenseGuard's runtime validation
        try {
            LicenseGuard.assertRuntimeValidity();
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
