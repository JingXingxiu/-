package com.bookdecision.web;

import com.bookdecision.application.CatalogResult;
import com.bookdecision.application.DecisionApplicationService;
import com.bookdecision.application.DecisionCommand;
import com.bookdecision.application.DecisionPolicy;
import com.bookdecision.application.DecisionResult;
import com.bookdecision.application.SolverRequestBulkhead;
import com.bookdecision.application.dataset.DatasetSelection;
import com.bookdecision.web.dto.request.DecisionRequest;
import com.bookdecision.web.dto.response.CatalogResponse;
import com.bookdecision.web.dto.response.DecisionResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api/v1", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Book recycling decisions")
public class DecisionController {

    private final DecisionApplicationService service;
    private final SolverRequestBulkhead solverRequestBulkhead;

    public DecisionController(DecisionApplicationService service) {
        this(service, new SolverRequestBulkhead());
    }

    @Autowired
    public DecisionController(
            DecisionApplicationService service,
            SolverRequestBulkhead solverRequestBulkhead
    ) {
        this.service = service;
        this.solverRequestBulkhead = solverRequestBulkhead;
    }

    @GetMapping("/demo/catalog")
    @Operation(summary = "Read the ISBN catalog exposed by an immutable demo dataset")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Catalog found"),
            @ApiResponse(
                    responseCode = "404",
                    description = "Dataset version not found",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))
            )
    })
    public CatalogResponse catalog(
            @RequestParam @NotBlank String datasetVersion
    ) {
        CatalogResult result = service.getCatalog(datasetVersion);
        return CatalogResponse.from(result);
    }

    @PostMapping(path = "/decisions", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Find a lexicographic book allocation for the supplied inventory",
            description = "Supported objectivePolicyVersion values: "
                    + DecisionPolicy.MAX_BOOKS_MONEY_PLATFORMS_ORDERS_V1 + ", "
                    + DecisionPolicy.MAX_BOOKS_PLATFORMS_MONEY_ORDERS_V1 + ", "
                    + DecisionPolicy.MAX_BOOKS_PLATFORMS_ORDERS_MONEY_V1 + ", "
                    + DecisionPolicy.BEST_SINGLE_PLATFORM_V1 + ", "
                    + DecisionPolicy.MOST_MONEY_V1 + "."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Decision produced"),
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
                    description = "Inventory violates cross-field or dataset rules",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))
            ),
            @ApiResponse(
                    responseCode = "415",
                    description = "Content-Type is not supported",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))
            ),
            @ApiResponse(
                    responseCode = "503",
                    description = "Solver request slots are busy or no usable result was available within the configured limit",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "Internal model consistency failure",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))
            )
    })
    public DecisionResponse decide(
            @Valid @RequestBody DecisionRequest request,
            @RequestHeader(value = "X-Upload-Token", required = false) String uploadAccessToken
    ) {
        DecisionCommand command = new DecisionCommand(
                request.datasetVersion(),
                request.objectivePolicyVersion(),
                request.inventory().stream()
                        .map(item -> new DecisionCommand.InventoryEntry(item.isbn(), item.quantity()))
                        .toList(),
                new DatasetSelection(request.dataMode(), request.uploadId())
        );
        return solverRequestBulkhead.executeDecision(() -> {
            DecisionResult result = uploadAccessToken == null
                    ? service.decide(command)
                    : service.decide(command, uploadAccessToken);
            return DecisionResponse.from(result);
        });
    }
}
