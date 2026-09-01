package com.bookdecision.web.dto.response;

import com.bookdecision.application.CatalogResult;
import com.bookdecision.application.DecisionPolicy;
import com.bookdecision.application.dataset.SourceKind;
import com.bookdecision.application.dataset.PlatformDisplayMode;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

public record CatalogResponse(
        String datasetVersion,
        String objectivePolicyVersion,
        String engineVersion,
        SourceKind sourceKind,
        PlatformDisplayMode platformDisplayMode,
        @Schema(description = "Required disclosures about observed or generated data")
        List<DisclaimerResponse> disclaimers,
        List<CatalogBookResponse> books,
        List<PlatformResponse> platforms,
        List<SuggestedInventoryItemResponse> suggestedInventory
) {

    public static CatalogResponse from(CatalogResult result) {
        return new CatalogResponse(
                result.datasetVersion(),
                result.objectivePolicyVersion(),
                DecisionPolicy.ENGINE_VERSION,
                result.sourceKind(),
                result.platformDisplayMode(),
                result.disclaimers().stream().map(DisclaimerResponse::from).toList(),
                result.books().stream()
                        .map(book -> new CatalogBookResponse(
                                book.isbn(),
                                book.title(),
                                book.acceptedPlatformCount()
                        ))
                        .toList(),
                result.platforms().stream()
                        .map(platform -> new PlatformResponse(
                                platform.platformCode(),
                                platform.displayName(),
                                platform.ruleSummary(),
                                platform.rejectionConditions(),
                                platform.repeatPolicyDescription(),
                                platform.collectedAt(),
                                platform.sourceDescription(),
                                platform.sourceReference()
                        ))
                        .toList(),
                result.suggestedInventory().stream()
                        .map(item -> new SuggestedInventoryItemResponse(item.isbn(), item.quantity()))
                        .toList()
        );
    }
}
