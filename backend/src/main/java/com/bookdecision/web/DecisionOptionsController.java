package com.bookdecision.web;

import com.bookdecision.application.DecisionCommand;
import com.bookdecision.application.DecisionOptionsApplicationService;
import com.bookdecision.application.DecisionOptionsResult;
import com.bookdecision.application.SolverRequestBulkhead;
import com.bookdecision.application.dataset.DatasetSelection;
import com.bookdecision.web.dto.request.DecisionOptionsRequest;
import com.bookdecision.web.dto.response.DecisionOptionsResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api/v1", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Book recycling decisions")
public class DecisionOptionsController {

    private final DecisionOptionsApplicationService service;
    private final SolverRequestBulkhead solverRequestBulkhead;

    public DecisionOptionsController(DecisionOptionsApplicationService service) {
        this(service, new SolverRequestBulkhead());
    }

    @Autowired
    public DecisionOptionsController(
            DecisionOptionsApplicationService service,
            SolverRequestBulkhead solverRequestBulkhead
    ) {
        this.service = service;
        this.solverRequestBulkhead = solverRequestBulkhead;
    }

    @PostMapping(path = "/decision-options", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Produce distinct recommendation, convenience, single-platform, and maximum-amount plans")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Decision options produced"),
            @ApiResponse(
                    responseCode = "503",
                    description = "Solver request slots are busy or the strategy-boundary total budget was exceeded",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))
            )
    })
    public DecisionOptionsResponse decideOptions(
            @Valid @RequestBody DecisionOptionsRequest request,
            @RequestHeader(value = "X-Upload-Token", required = false) String uploadAccessToken
    ) {
        return solverRequestBulkhead.executeDecisionOptions(timeBudget -> {
            DecisionOptionsResult result = service.decide(
                    request.datasetVersion(),
                    request.inventory().stream()
                            .map(item -> new DecisionCommand.InventoryEntry(item.isbn(), item.quantity()))
                            .toList(),
                    new DatasetSelection(request.dataMode(), request.uploadId()),
                    uploadAccessToken,
                    timeBudget
            );
            return DecisionOptionsResponse.from(result);
        });
    }
}
