package com.bookdecision.web.dto.response;

import com.bookdecision.application.DecisionResult;
import com.bookdecision.application.dataset.SourceKind;
import com.bookdecision.application.dataset.DataMode;
import com.bookdecision.solver.SolveStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.UUID;

import static com.bookdecision.domain.AmountUnits.CNY;

public record DecisionResponse(
        String datasetVersion,
        DataMode dataMode,
        UUID uploadId,
        String objectivePolicyVersion,
        String engineVersion,
        String requestFingerprint,
        SourceKind sourceKind,
        @Schema(description = "Required disclosures about observed or generated data")
        List<DisclaimerResponse> disclaimers,
        SolveStatus solveStatus,
        String solveStatusMessage,
        int input,
        int sold,
        int unsold,
        long estimatedAmountCents,
        String currency,
        int platformCount,
        int orderCount,
        List<DecisionOrderResponse> orders,
        List<UnallocatedBookResponse> unallocated,
        List<DecisionResult.DataWarning> dataWarnings,
        long durationMs
) {

    public static DecisionResponse from(DecisionResult result) {
        return new DecisionResponse(
                result.datasetVersion(),
                result.dataMode(),
                result.uploadId(),
                result.objectivePolicyVersion(),
                result.engineVersion(),
                result.requestFingerprint(),
                result.sourceKind(),
                result.disclaimers().stream().map(DisclaimerResponse::from).toList(),
                result.status(),
                statusMessage(result.status()),
                result.input(),
                result.sold(),
                result.unsold(),
                result.estimatedAmountCents(),
                CNY,
                result.platformCount(),
                result.orderCount(),
                result.orders().stream().map(DecisionOrderResponse::from).toList(),
                result.unallocated().stream().map(UnallocatedBookResponse::from).toList(),
                result.dataWarnings(),
                result.durationMs()
        );
    }

    private static String statusMessage(SolveStatus status) {
        return switch (status) {
            case OPTIMAL -> "已找到并证明当前词典序目标下的最优方案";
            case FEASIBLE -> "已找到满足全部约束的方案，但尚未证明全局最优";
            case UNKNOWN -> "求解器未能在限制内给出可用结论";
            case INFEASIBLE -> "模型被证明无可行解";
        };
    }
}
