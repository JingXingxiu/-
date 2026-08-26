package com.bookdecision.web.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

public record InventoryItemRequest(
        @Schema(
                description = "ISBN-13 with a 978 or 979 prefix and a valid check digit",
                example = "9787111544937"
        )
        String isbn,

        @Schema(example = "1")
        int quantity
) {
}
