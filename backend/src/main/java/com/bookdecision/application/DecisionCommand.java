package com.bookdecision.application;

import com.bookdecision.application.dataset.DatasetSelection;

import java.util.List;

public record DecisionCommand(
        String datasetVersion,
        String objectivePolicyVersion,
        List<InventoryEntry> inventory,
        DatasetSelection datasetSelection
) {

    public DecisionCommand {
        datasetSelection = datasetSelection == null ? DatasetSelection.systemOnly() : datasetSelection;
    }

    public DecisionCommand(String datasetVersion, String objectivePolicyVersion, List<InventoryEntry> inventory) {
        this(datasetVersion, objectivePolicyVersion, inventory, DatasetSelection.systemOnly());
    }

    public record InventoryEntry(String isbn, int quantity) {
    }
}
