package com.bookdecision.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;

final class RequestFingerprint {

    private RequestFingerprint() {
    }

    static String sha256(DecisionCommand command) {
        StringBuilder canonical = new StringBuilder();
        appendLengthPrefixed(canonical, command.datasetVersion());
        appendLengthPrefixed(canonical, command.objectivePolicyVersion());
        appendLengthPrefixed(canonical, command.datasetSelection().dataMode().name());
        appendLengthPrefixed(
                canonical,
                command.datasetSelection().uploadId() == null ? "" : command.datasetSelection().uploadId().toString()
        );
        command.inventory().stream()
                .sorted(Comparator.comparing(DecisionCommand.InventoryEntry::isbn))
                .forEach(item -> {
                    appendLengthPrefixed(canonical, item.isbn());
                    canonical.append(item.quantity()).append(';');
                });
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(canonical.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 must be available in every Java runtime", exception);
        }
    }

    private static void appendLengthPrefixed(StringBuilder target, String value) {
        target.append(value.length()).append(':').append(value).append('|');
    }
}
