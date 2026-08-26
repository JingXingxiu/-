package com.bookdecision.web;

import com.bookdecision.application.OfferLookupCommand;
import com.bookdecision.application.OfferLookupResult;
import com.bookdecision.application.OfferLookupService;
import com.bookdecision.application.dataset.DatasetSelection;
import com.bookdecision.web.dto.request.OfferLookupRequest;
import com.bookdecision.web.dto.response.OfferLookupResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api/v1/books", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Book offer preview")
public final class OfferLookupController {

    private final OfferLookupService service;

    public OfferLookupController(OfferLookupService service) {
        this.service = service;
    }

    @PostMapping(path = "/offers:lookup", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Preview platform offers without running the allocation solver")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Offer preview produced"),
            @ApiResponse(
                    responseCode = "400",
                    description = "Malformed request or field validation failed",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Dataset version not found",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))
            ),
            @ApiResponse(
                    responseCode = "422",
                    description = "ISBN list violates semantic rules",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))
            )
    })
    public OfferLookupResponse lookup(
            @Valid @RequestBody OfferLookupRequest request,
            @RequestHeader(value = "X-Upload-Token", required = false) String uploadAccessToken
    ) {
        OfferLookupResult result = service.lookup(new OfferLookupCommand(
                request.datasetVersion(),
                request.isbns(),
                new DatasetSelection(request.dataMode(), request.uploadId())
        ), uploadAccessToken);
        return OfferLookupResponse.from(result);
    }
}
