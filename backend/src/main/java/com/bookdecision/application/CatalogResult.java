package com.bookdecision.application;

import com.bookdecision.application.dataset.DatasetDisclaimer;
import com.bookdecision.application.dataset.SourceKind;

import java.util.List;

public record CatalogResult(
        String datasetVersion,
        String objectivePolicyVersion,
        SourceKind sourceKind,
        List<DatasetDisclaimer> disclaimers,
        List<Book> books,
        List<Platform> platforms,
        List<SuggestedInventoryItem> suggestedInventory
) {

    public record Book(String isbn, String title, int acceptedPlatformCount) {
    }

    public record Platform(String platformCode, String displayName, String ruleSummary) {
    }

    public record SuggestedInventoryItem(String isbn, int quantity) {
    }
}
