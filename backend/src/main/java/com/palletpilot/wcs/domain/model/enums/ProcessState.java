package com.palletpilot.wcs.domain.model.enums;

public enum ProcessState {
    CREATED,
    PLANNED,
    RUNNING,
    PAUSED,
    PICKING,
    DEPALLETIZING,
    REPALLETIZING,
    WRAPPING,
    LOADING,
    COMPLETED,
    FAILED,
    PALLETIZED,
    READY_FOR_STORAGE,
    MOVING_TO_STORAGE,
    ERROR,
    CANCELLED,
    CANCEL_REQUESTED;

    /**
     * Checks if the transition to the target state is generally valid.
     * Note: Detailed flow validation should check the database configuration.
     */
    public boolean canTransitionTo(ProcessState nextState) {
        if (this == ERROR)
            return true; // Can retry from error
        if (nextState == ERROR)
            return true; // Can fail anytime

        // Any non-terminal state can be cancelled
        if (nextState == CANCELLED || nextState == CANCEL_REQUESTED) {
            return this != COMPLETED && this != CANCELLED;
        }

        // CANCEL_REQUESTED can only go to CANCELLED
        if (this == CANCEL_REQUESTED) {
            return nextState == CANCELLED;
        }

        return switch (this) {
            case CREATED -> nextState == PLANNED;
            case PLANNED -> nextState == PICKING;
            case PICKING -> nextState == DEPALLETIZING;
            case DEPALLETIZING -> nextState == REPALLETIZING;
            case REPALLETIZING -> nextState == WRAPPING;
            case WRAPPING -> nextState == LOADING;
            case LOADING -> nextState == COMPLETED;
            case COMPLETED -> false;
            default -> false;
        };
    }
}
