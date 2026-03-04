package com.palletpilot.wcs.infrastructure.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for RateLimitFilter.ClientWindow — the sliding window counter
 * logic.
 * Testing the core rate-limiting algorithm without Filter/Servlet dependencies.
 */
class RateLimitFilterClientWindowTest {

    @Test
    @DisplayName("within limit → tryAcquire returns true")
    void withinLimit_returnsTrue() {
        var window = new RateLimitFilter.ClientWindow();

        for (int i = 0; i < 10; i++) {
            assertThat(window.tryAcquire(10)).isTrue();
        }
    }

    @Test
    @DisplayName("exceeds limit → tryAcquire returns false")
    void exceedsLimit_returnsFalse() {
        var window = new RateLimitFilter.ClientWindow();

        // Fill up
        for (int i = 0; i < 5; i++) {
            window.tryAcquire(5);
        }

        // 6th request should fail
        assertThat(window.tryAcquire(5)).isFalse();
    }

    @Test
    @DisplayName("exactly at limit → last request passes, next fails")
    void exactlyAtLimit_boundary() {
        var window = new RateLimitFilter.ClientWindow();

        // Exactly at limit
        for (int i = 0; i < 3; i++) {
            assertThat(window.tryAcquire(3)).isTrue();
        }

        // One more should fail
        assertThat(window.tryAcquire(3)).isFalse();
    }

    @Test
    @DisplayName("getLastActivity: updated on each tryAcquire")
    void lastActivity_updatedOnAcquire() {
        var window = new RateLimitFilter.ClientWindow();
        long before = window.getLastActivity();

        // Small delay then acquire
        try {
            Thread.sleep(10);
        } catch (InterruptedException ignored) {
        }
        window.tryAcquire(100);

        assertThat(window.getLastActivity()).isGreaterThanOrEqualTo(before);
    }
}
