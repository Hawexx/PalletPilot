package com.palletpilot.wcs.domain.model.enums;

/**
 * Internal mission status enum mapped from Movu aSTC notificationTypes.
 * Ordered by natural progression to enable regression detection.
 */
public enum MovuMissionStatus {
    CREATED(0),
    ASSIGNING(1),
    ASSIGNED(2),
    AWAITING_SOURCE_CONFIRM(3),
    PICKED(4),
    AWAITING_DEST_CONFIRM(5),
    COMPLETED(10),
    CANCELLED(11),
    ABORTED(12),
    FAILED(13),
    REQUIRES_MANUAL_INTERVENTION(14),
    UNKNOWN(99);

    private final int order;

    MovuMissionStatus(int order) {
        this.order = order;
    }

    public int getOrder() {
        return order;
    }

    /** Terminal states cannot transition further. */
    public boolean isTerminal() {
        return this == COMPLETED || this == CANCELLED || this == ABORTED
                || this == FAILED || this == REQUIRES_MANUAL_INTERVENTION;
    }

    /**
     * Maps a Movu notificationType string to an internal status.
     * Handles both camelCase (v1.2+) and mixed-case variants.
     */
    public static MovuMissionStatus fromNotificationType(String notificationType) {
        if (notificationType == null)
            return UNKNOWN;

        return switch (notificationType) {
            case "MissionCreated" -> CREATED;
            case "MissionAssigning" -> ASSIGNING;
            case "MissionAssigned" -> ASSIGNED;
            case "MissionPicked" -> PICKED;
            case "MissionDropped" -> COMPLETED;
            case "MissionCancelled" -> CANCELLED;
            case "MissionAborted" -> ABORTED;
            case "MissionError" -> FAILED;
            case "MissionAwaitingSourceEntryConfirmation" -> AWAITING_SOURCE_CONFIRM;
            case "MissionAwaitingDestinationEntryConfirmation" -> AWAITING_DEST_CONFIRM;
            // These don't change the mission status but are valid event types
            case "MissionSourceChanged", "MissionDestinationChanged" -> null;
            default -> UNKNOWN;
        };
    }
}
