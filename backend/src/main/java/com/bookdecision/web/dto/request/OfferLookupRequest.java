package com.bookdecision.web.dto.request;

import com.bookdecision.application.dataset.DataMode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

import static com.bookdecision.application.InputLimits.MAX_LOOKUP_ISBN_COUNT;

@Schema(description = "ISBN values scanned or entered before inventory optimization")
public record OfferLookupRequest(
        @NotBlank
        @Schema(example = "mixed-demo-v1")
        String datasetVersion,

        @NotNull
        @Size(min = 1, max = MAX_LOOKUP_ISBN_COUNT)
        @Schema(
                description = "Unique ISBN-13 values with a 978 or 979 prefix and a valid check digit",
                example = "[\"9787020002207\", \"9787111544937\"]"
        )
        List<@NotBlank String> isbns,

        @Schema(description = "Defaults to SYSTEM_ONLY when omitted", example = "SYSTEM_ONLY")
        DataMode dataMode,

        @Schema(description = "Required for USER_ONLY or USER_OVERLAY")
        UUID uploadId
) {

    public OfferLookupRequest(String datasetVersion, List<String> isbns) {
        this(datasetVersion, isbns, DataMode.SYSTEM_ONLY, null);
    }
}
