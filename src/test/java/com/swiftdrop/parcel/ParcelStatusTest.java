package com.swiftdrop.parcel;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/** The parcel lifecycle rules (report 5.3.3). */
class ParcelStatusTest {

    @ParameterizedTest(name = "{0} -> {1} is allowed")
    @CsvSource({
            "CREATED, PICKED_UP",
            "PICKED_UP, IN_TRANSIT",
            "IN_TRANSIT, OUT_FOR_DELIVERY",
            "OUT_FOR_DELIVERY, DELIVERED",
            "OUT_FOR_DELIVERY, FAILED"
    })
    void allowsTheNextStep(ParcelStatus from, ParcelStatus to) {
        assertThat(from.canMoveTo(to)).isTrue();
    }

    @ParameterizedTest(name = "{0} -> {1} is rejected")
    @CsvSource({
            "CREATED, DELIVERED",
            "PICKED_UP, OUT_FOR_DELIVERY",
            "IN_TRANSIT, CREATED",
            "CREATED, FAILED",
            "DELIVERED, FAILED",
            "FAILED, OUT_FOR_DELIVERY"
    })
    void rejectsSkippingOrGoingBack(ParcelStatus from, ParcelStatus to) {
        assertThat(from.canMoveTo(to)).isFalse();
    }

    @Test
    void deliveredAndFailedAreFinal() {
        assertThat(ParcelStatus.DELIVERED.isFinal()).isTrue();
        assertThat(ParcelStatus.FAILED.isFinal()).isTrue();
        assertThat(ParcelStatus.IN_TRANSIT.isFinal()).isFalse();
        assertThat(ParcelStatus.DELIVERED.allowedNext()).isEmpty();
    }
}
