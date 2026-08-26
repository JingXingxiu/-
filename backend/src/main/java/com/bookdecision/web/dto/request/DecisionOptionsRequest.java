package com.bookdecision.web.dto.request;

import com.bookdecision.application.dataset.DataMode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

import static com.bookdecision.application.InputLimits.MAX_INVENTORY_ENTRY_COUNT;

@Schema(description = "Inventory used to produce distinct, explainable decision options")
public record DecisionOptionsRequest(
        @NotBlank
        @Schema(example = "mixed-demo-v1")
        String datasetVersion,

        @NotNull
        @Size(min = 1, max = MAX_INVENTORY_ENTRY_COUNT)
        List<@NotNull @Valid InventoryItemRequest> inventory,

        @Schema(description = "Defaults to SYSTEM_ONLY when omitted", example = "SYSTEM_ONLY")
        DataMode dataMode,

        @Schema(description = "Required for USER_ONLY or USER_OVERLAY")
        UUID uploadId
) {

    public DecisionOptionsRequest(String datasetVersion, List<InventoryItemRequest> inventory) {
        this(datasetVersion, inventory, DataMode.SYSTEM_ONLY, null);
    }
}
