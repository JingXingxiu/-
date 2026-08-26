package com.bookdecision.application;

import com.bookdecision.application.dataset.DatasetSelection;

import java.util.List;

/** Input for previewing offers before the user submits an inventory for optimization. */
public record OfferLookupCommand(
        String datasetVersion,
        List<String> isbns,
        DatasetSelection datasetSelection
) {

    public OfferLookupCommand {
        datasetSelection = datasetSelection == null ? DatasetSelection.systemOnly() : datasetSelection;
    }

    public OfferLookupCommand(String datasetVersion, List<String> isbns) {
        this(datasetVersion, isbns, DatasetSelection.systemOnly());
    }
}
