package com.palletpilot.wcs.domain.model.enums;

public enum EquipmentState {
    IDLE,
    BUSY,
    ERROR,
    OFFLINE;

    public boolean canReceiveCommand() {
        return this == IDLE;
    }
}
