package com.swiftdrop.parcel;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;

/**
 * Creates public tracking numbers like "SD7K2M9QXA".
 * Random (not sequential) so nobody can guess other people's tracking numbers.
 * Letters that look alike (0/O, 1/I) are left out so they are easy to type.
 */
@Component
public class TrackingNumberGenerator {

    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int LENGTH = 8;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final ParcelRepository parcels;

    public TrackingNumberGenerator(ParcelRepository parcels) {
        this.parcels = parcels;
    }

    public String next() {
        for (int attempt = 0; attempt < 10; attempt++) {
            String candidate = "SD" + randomPart();
            if (!parcels.existsByTrackingNumber(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Could not generate a unique tracking number");
    }

    private static String randomPart() {
        StringBuilder sb = new StringBuilder(LENGTH);
        for (int i = 0; i < LENGTH; i++) {
            sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }
}
