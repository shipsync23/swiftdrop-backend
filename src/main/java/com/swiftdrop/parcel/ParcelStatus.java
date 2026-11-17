package com.swiftdrop.parcel;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Parcel lifecycle (report 5.3.3):
 * CREATED -> PICKED_UP -> IN_TRANSIT -> OUT_FOR_DELIVERY -> DELIVERED or FAILED
 * The allowed next steps are defined in ONE place (DRY), used by StatusTransitionValidator.
 */
public enum ParcelStatus {
    CREATED,
    PICKED_UP,
    IN_TRANSIT,
    OUT_FOR_DELIVERY,
    DELIVERED,
    FAILED;

    private static final Map<ParcelStatus, Set<ParcelStatus>> NEXT = Map.of(
            CREATED, EnumSet.of(PICKED_UP),
            PICKED_UP, EnumSet.of(IN_TRANSIT),
            IN_TRANSIT, EnumSet.of(OUT_FOR_DELIVERY),
            OUT_FOR_DELIVERY, EnumSet.of(DELIVERED, FAILED),
            DELIVERED, EnumSet.noneOf(ParcelStatus.class),
            FAILED, EnumSet.noneOf(ParcelStatus.class));

    public boolean canMoveTo(ParcelStatus next) {
        return NEXT.get(this).contains(next);
    }

    public Set<ParcelStatus> allowedNext() {
        return NEXT.get(this);
    }

    public boolean isFinal() {
        return this == DELIVERED || this == FAILED;
    }
}
