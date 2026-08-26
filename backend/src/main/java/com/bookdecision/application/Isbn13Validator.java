package com.bookdecision.application;

/**
 * Validates the ISBN-13 representation accepted by the public application use cases.
 */
public final class Isbn13Validator {

    private static final int ISBN_13_LENGTH = 13;

    private Isbn13Validator() {
    }

    public static boolean isValid(String isbn) {
        if (isbn == null
                || isbn.length() != ISBN_13_LENGTH
                || !(isbn.startsWith("978") || isbn.startsWith("979"))) {
            return false;
        }

        int weightedSum = 0;
        for (int index = 0; index < ISBN_13_LENGTH; index++) {
            char character = isbn.charAt(index);
            if (character < '0' || character > '9') {
                return false;
            }
            if (index < ISBN_13_LENGTH - 1) {
                int digit = character - '0';
                weightedSum += index % 2 == 0 ? digit : digit * 3;
            }
        }

        int expectedCheckDigit = (10 - weightedSum % 10) % 10;
        return expectedCheckDigit == isbn.charAt(ISBN_13_LENGTH - 1) - '0';
    }
}
