package com.bookdecision.domain;

import java.util.Objects;

public record PlatformOffer(
        String isbn,
        String platformId,
        OfferStatus status,
        long unitPriceCents,
        RepeatPolicy repeatPolicy
) {

    public PlatformOffer {
        isbn = requireText(isbn, "isbn");
        platformId = requireText(platformId, "platformId");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(repeatPolicy, "repeatPolicy must not be null");
        if (status == OfferStatus.ACCEPTED && unitPriceCents <= 0) {
            throw new IllegalArgumentException("accepted offer price must be positive");
        }
        if (status != OfferStatus.ACCEPTED && unitPriceCents != 0) {
            throw new IllegalArgumentException("rejected or unknown offer price must be zero");
        }
    }

    public static PlatformOffer accepted(
            String isbn,
            String platformId,
            long unitPriceCents,
            RepeatPolicy repeatPolicy
    ) {
        return new PlatformOffer(isbn, platformId, OfferStatus.ACCEPTED, unitPriceCents, repeatPolicy);
    }

    public static PlatformOffer rejected(String isbn, String platformId) {
        return new PlatformOffer(isbn, platformId, OfferStatus.REJECTED, 0, RepeatPolicy.INHERIT_PLATFORM);
    }

    public static PlatformOffer unknown(String isbn, String platformId) {
        return new PlatformOffer(isbn, platformId, OfferStatus.UNKNOWN, 0, RepeatPolicy.INHERIT_PLATFORM);
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field + " must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
