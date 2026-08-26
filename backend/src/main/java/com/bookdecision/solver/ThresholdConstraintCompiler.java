package com.bookdecision.solver;

import com.bookdecision.domain.OrderThreshold;
import com.google.ortools.sat.BoolVar;
import com.google.ortools.sat.CpModel;
import com.google.ortools.sat.IntVar;
import com.google.ortools.sat.LinearExpr;
import com.google.ortools.sat.LinearExprBuilder;

final class ThresholdConstraintCompiler {

    private final CpModel model;

    ThresholdConstraintCompiler(CpModel model) {
        this.model = model;
    }

    void addThreshold(
            OrderThreshold threshold,
            BoolVar orderActive,
            IntVar bookCount,
            IntVar amountCents,
            String variablePrefix
    ) {
        compile(threshold, orderActive, bookCount, amountCents, variablePrefix);
    }

    private void compile(
            OrderThreshold threshold,
            BoolVar enabled,
            IntVar bookCount,
            IntVar amountCents,
            String prefix
    ) {
        switch (threshold) {
            case OrderThreshold.AmountAtLeast amount ->
                    model.addGreaterOrEqual(amountCents, amount.amountCents()).onlyEnforceIf(enabled);
            case OrderThreshold.BookCountAtLeast count ->
                    model.addGreaterOrEqual(bookCount, count.bookCount()).onlyEnforceIf(enabled);
            case OrderThreshold.AveragePriceAtLeast average -> {
                LinearExprBuilder averageExpression = LinearExpr.newBuilder();
                averageExpression.addTerm(amountCents, 1);
                averageExpression.addTerm(bookCount, -average.averagePriceCents());
                model.addGreaterOrEqual(averageExpression, 0).onlyEnforceIf(enabled);
            }
            case OrderThreshold.AllOf all -> {
                for (int index = 0; index < all.children().size(); index++) {
                    compile(all.children().get(index), enabled, bookCount, amountCents, prefix + "_all_" + index);
                }
            }
            case OrderThreshold.AnyOf any -> compileAnyOf(any, enabled, bookCount, amountCents, prefix);
        }
    }

    private void compileAnyOf(
            OrderThreshold.AnyOf any,
            BoolVar enabled,
            IntVar bookCount,
            IntVar amountCents,
            String prefix
    ) {
        LinearExprBuilder selectedBranchCount = LinearExpr.newBuilder();
        selectedBranchCount.addTerm(enabled, -1);
        for (int index = 0; index < any.children().size(); index++) {
            BoolVar branch = model.newBoolVar(prefix + "_any_" + index);
            LinearExprBuilder branchRequiresOrder = LinearExpr.newBuilder();
            branchRequiresOrder.addTerm(branch, 1);
            branchRequiresOrder.addTerm(enabled, -1);
            model.addLessOrEqual(branchRequiresOrder, 0);
            selectedBranchCount.addTerm(branch, 1);
            compile(any.children().get(index), branch, bookCount, amountCents, prefix + "_any_" + index);
        }
        model.addGreaterOrEqual(selectedBranchCount, 0);
    }
}
