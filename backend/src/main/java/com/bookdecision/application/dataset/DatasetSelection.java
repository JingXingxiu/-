package com.bookdecision.application.dataset;

import java.util.UUID;

public record DatasetSelection(DataMode dataMode, UUID uploadId) {

    public DatasetSelection {
        dataMode = dataMode == null ? DataMode.SYSTEM_ONLY : dataMode;
    }

    public static DatasetSelection systemOnly() {
        return new DatasetSelection(DataMode.SYSTEM_ONLY, null);
    }
}
