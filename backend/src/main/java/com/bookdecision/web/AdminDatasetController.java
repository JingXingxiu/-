package com.bookdecision.web;

import com.bookdecision.application.admin.AdminDatasetService;
import com.bookdecision.application.admin.PublishedDataset;
import com.bookdecision.web.dto.request.PublishDatasetRequest;
import com.bookdecision.web.dto.request.RejectDatasetCandidateRequest;
import com.bookdecision.web.dto.response.AdminDatasetCandidateDetailsResponse;
import com.bookdecision.web.dto.response.AdminDatasetCandidateResponse;
import com.bookdecision.web.dto.response.AdminDatasetPublicationResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/user-datasets/candidates")
@ConditionalOnProperty(prefix = "book-decision.admin", name = "enabled", havingValue = "true")
@Tag(name = "Dataset review administration")
@SecurityRequirement(name = "adminBasic")
public class AdminDatasetController {

    private final AdminDatasetService service;

    public AdminDatasetController(AdminDatasetService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "List unexpired, consented candidates awaiting manual review")
    public List<AdminDatasetCandidateResponse> pendingCandidates() {
        return service.pendingCandidates().stream()
                .map(AdminDatasetCandidateResponse::from)
                .toList();
    }

    @GetMapping("/{uploadId}")
    @Operation(summary = "Inspect one unexpired candidate awaiting manual review")
    public AdminDatasetCandidateDetailsResponse candidateDetails(@PathVariable UUID uploadId) {
        return AdminDatasetCandidateDetailsResponse.from(service.candidateDetails(uploadId));
    }

    @PostMapping("/{uploadId}/publish")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Atomically publish a candidate as a new immutable overlay version")
    public AdminDatasetPublicationResponse publish(
            @PathVariable UUID uploadId,
            @Valid @RequestBody PublishDatasetRequest request,
            Authentication authentication
    ) {
        PublishedDataset result = service.publish(
                uploadId,
                request.datasetVersion(),
                authentication.getName()
        );
        return AdminDatasetPublicationResponse.from(result);
    }

    @PostMapping("/{uploadId}/reject")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Reject a pending candidate without publishing any public dataset")
    public void reject(
            @PathVariable UUID uploadId,
            @Valid @RequestBody RejectDatasetCandidateRequest request,
            Authentication authentication
    ) {
        service.reject(uploadId, request.reason(), authentication.getName());
    }
}
