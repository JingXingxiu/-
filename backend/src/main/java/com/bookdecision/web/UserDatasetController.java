package com.bookdecision.web;

import com.bookdecision.application.userdataset.StoredUserDataset;
import com.bookdecision.application.userdataset.UserDatasetErrorCode;
import com.bookdecision.application.userdataset.UserDatasetException;
import com.bookdecision.application.userdataset.UserDatasetService;
import com.bookdecision.application.userdataset.UserDatasetUploadResult;
import com.bookdecision.web.dto.response.UserDatasetDetailsResponse;
import com.bookdecision.web.dto.response.UserDatasetUploadResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.NotBlank;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

@RestController
@Validated
@RequestMapping("/api/v1/user-datasets")
@Tag(name = "Private user datasets")
public class UserDatasetController {

    public static final String ACCESS_TOKEN_HEADER = "X-Upload-Token";
    private static final MediaType CSV = MediaType.parseMediaType("text/csv;charset=UTF-8");

    private final ObjectProvider<UserDatasetService> serviceProvider;

    public UserDatasetController(ObjectProvider<UserDatasetService> serviceProvider) {
        this.serviceProvider = serviceProvider;
    }

    @GetMapping(path = "/template.csv", produces = "text/csv;charset=UTF-8")
    @Operation(summary = "Download the strict eight-column Chinese v2 blank CSV template")
    public ResponseEntity<byte[]> template() throws IOException {
        return classpathCsv("user-datasets/user-offer-template.csv", "user-offer-template.csv");
    }

    @GetMapping(path = "/example.csv", produces = "text/csv;charset=UTF-8")
    @Operation(summary = "Download a filled Chinese v2 CSV example covering all five demo platforms")
    public ResponseEntity<byte[]> example() throws IOException {
        return classpathCsv("user-datasets/user-offer-example.csv", "user-offer-example.csv");
    }

    @PostMapping(path = "/uploads", consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Validate Chinese v2 or legacy English v1 CSV and retain it privately for 30 days")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "CSV accepted and retained privately"),
            @ApiResponse(
                    responseCode = "413",
                    description = "Multipart request exceeds the configured upload-size limit",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))
            ),
            @ApiResponse(responseCode = "422", description = "CSV or base-dataset semantics rejected"),
            @ApiResponse(responseCode = "429", description = "Per-remote-address upload window exhausted"),
            @ApiResponse(responseCode = "507", description = "Global retained-upload count or byte quota exhausted")
    })
    public ResponseEntity<UserDatasetUploadResponse> upload(
            HttpServletRequest request,
            @RequestPart("file") MultipartFile file,
            @RequestParam @NotBlank String baseDatasetVersion,
            @RequestParam(defaultValue = "false") boolean reuseConsent
    ) throws IOException {
        UserDatasetUploadResult result = requireService().upload(
                request.getRemoteAddr(),
                baseDatasetVersion,
                file.getOriginalFilename(),
                file.getBytes(),
                reuseConsent
        );
        return ResponseEntity.status(HttpStatus.CREATED)
                .cacheControl(CacheControl.noStore())
                .body(UserDatasetUploadResponse.from(result));
    }

    @GetMapping(path = "/uploads/{uploadId}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Read private upload metadata; the raw token is never returned again")
    public ResponseEntity<UserDatasetDetailsResponse> details(
            @PathVariable UUID uploadId,
            @RequestHeader(ACCESS_TOKEN_HEADER)
            @Parameter(description = "Capability token returned only in the upload response; reusable until expiry or deletion")
            String token
    ) {
        StoredUserDataset dataset = requireService().requireAuthorized(uploadId, token);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(UserDatasetDetailsResponse.from(dataset));
    }

    @GetMapping(path = "/uploads/{uploadId}/source.csv", produces = "text/csv;charset=UTF-8")
    @Operation(summary = "Download the authenticated user's original private CSV")
    public ResponseEntity<byte[]> source(
            @PathVariable UUID uploadId,
            @RequestHeader(ACCESS_TOKEN_HEADER) String token
    ) {
        byte[] content = requireService().readOriginal(uploadId, token);
        return ResponseEntity.ok()
                .contentType(CSV)
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("user-dataset.csv", StandardCharsets.UTF_8)
                        .build()
                        .toString())
                .body(content);
    }

    @DeleteMapping("/uploads/{uploadId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete the private raw object and normalized rows before automatic expiry")
    public void delete(
            @PathVariable UUID uploadId,
            @RequestHeader(ACCESS_TOKEN_HEADER) String token
    ) {
        requireService().delete(uploadId, token);
    }

    private UserDatasetService requireService() {
        UserDatasetService service = serviceProvider.getIfAvailable();
        if (service == null) {
            throw new UserDatasetException(UserDatasetErrorCode.FEATURE_DISABLED);
        }
        return service;
    }

    private static ResponseEntity<byte[]> classpathCsv(String path, String filename) throws IOException {
        ClassPathResource resource = new ClassPathResource(path);
        if (!resource.exists()) {
            throw new IllegalStateException("required CSV resource is missing: " + path);
        }
        byte[] content = withUtf8Bom(resource.getContentAsByteArray());
        return ResponseEntity.ok()
                .contentType(CSV)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(filename, StandardCharsets.UTF_8)
                        .build()
                        .toString())
                .body(content);
    }

    private static byte[] withUtf8Bom(byte[] content) {
        if (content.length >= 3
                && content[0] == (byte) 0xef
                && content[1] == (byte) 0xbb
                && content[2] == (byte) 0xbf) {
            return content;
        }
        byte[] result = new byte[content.length + 3];
        result[0] = (byte) 0xef;
        result[1] = (byte) 0xbb;
        result[2] = (byte) 0xbf;
        System.arraycopy(content, 0, result, 3, content.length);
        return result;
    }
}
