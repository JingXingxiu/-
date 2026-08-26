package com.bookdecision.application;

/**
 * Use-case input bounds that protect both request validation and solver resource usage.
 */
public final class InputLimits {

    public static final int MAX_LOOKUP_ISBN_COUNT = 100;
    public static final int MAX_INVENTORY_ENTRY_COUNT = 100;
    public static final int MAX_QUANTITY_PER_INVENTORY_ENTRY = 100;
    public static final int MAX_TOTAL_INVENTORY_QUANTITY = 100;

    private InputLimits() {
    }
}
