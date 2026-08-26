package com.bookdecision.web.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RejectDatasetCandidateRequest(
        @NotBlank @Size(max = 500) String reason
) {
}

