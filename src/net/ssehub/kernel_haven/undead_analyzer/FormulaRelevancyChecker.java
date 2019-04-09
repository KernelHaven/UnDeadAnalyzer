package net.ssehub.kernel_haven.undead_analyzer;

import java.util.HashSet;
import java.util.Set;

import net.ssehub.kernel_haven.util.logic.Conjunction;
import net.ssehub.kernel_haven.util.logic.Disjunction;
import net.ssehub.kernel_haven.util.logic.False;
import net.ssehub.kernel_haven.util.logic.IFormulaVisitor;
import net.ssehub.kernel_haven.util.logic.Negation;
import net.ssehub.kernel_haven.util.logic.True;
import net.ssehub.kernel_haven.util.logic.Variable;
import net.ssehub.kernel_haven.util.null_checks.NonNull;
import net.ssehub.kernel_haven.variability_model.VariabilityModel;

/**
 * Checks whether a formula contains at least one variable defined in the
 * {@link VariabilityModel}.
 * 
 * @author El-Sharkawy
 *
 */
public class FormulaRelevancyChecker implements IFormulaVisitor<Boolean> {

    private Set<String> definedVariables;
    private boolean considerVmVarsOnly;

    /**
     * Sole constructor of this class.
     * 
     * @param varModel           The variability model, defined allowed variables
     *                           must be not <tt>null</tt> if considerVmVarsOnly
     *                           should be used <tt>true</tt>
     * @param considerVmVarsOnly Specification whether a formula should only be
     *                           considered relevant if it contains variables known
     *                           by the variability model:
     *                           <p>
     *                           - false: Considers all elements in the analysis,
     *                           independently if used elements are defined in the
     *                           variability model.
     *                           </p>
     *                           <p>
     *                           - true: Considers only elements that contain at
     *                           least one variable defined by the variability
     *                           model.
     *                           </p>
     * 
     * 
     */
    public FormulaRelevancyChecker(VariabilityModel varModel, boolean considerVmVarsOnly) {

        this.considerVmVarsOnly = considerVmVarsOnly;

        if (varModel != null && considerVmVarsOnly) {
            definedVariables = new HashSet<>(varModel.getVariableMap().keySet());
        }
    }

    /**
     * Helper function to determine which variables are relevant.
     * 
     * @param variable The variable to check.
     * @return <tt>true</tt> if the variable is relevant, <tt>false</tt> otherwise.
     */
    private boolean isRelevant(@NonNull String variable) {
        boolean isRelevant;
        if (considerVmVarsOnly) {
            isRelevant = definedVariables.contains(variable);

            // Consider MODULE-variables heuristically
            if (!isRelevant && variable.endsWith("_MODULE")) {
                int endIndex = variable.length() - "_MODULE".length();
                isRelevant = definedVariables.contains(variable.substring(0, endIndex));
            }
        } else {
            isRelevant = true;
        }

        return isRelevant;
    }

    @Override
    public Boolean visitFalse(@NonNull False falseConstant) {
        // Unclear if the formula is dependent on Variability Model
        return !considerVmVarsOnly;
    }

    @Override
    public Boolean visitTrue(@NonNull True trueConstant) {
        // Unclear if the formula is dependent on Variability Model
        return !considerVmVarsOnly;
    }

    @Override
    public Boolean visitVariable(@NonNull Variable variable) {
        // Relevance depends on variable
        return isRelevant(variable.getName());
    }

    @Override
    public Boolean visitNegation(@NonNull Negation formula) {
        return !considerVmVarsOnly;
    }

    @Override
    public Boolean visitDisjunction(@NonNull Disjunction formula) {

        // Formula is relevant if either RHS or LHS is dependent on a variable
        boolean isRelevant = !considerVmVarsOnly || this.visit(formula.getLeft());
        if (!isRelevant) {
            isRelevant = this.visit(formula.getRight());
        }

        return isRelevant;
    }

    @Override
    public Boolean visitConjunction(@NonNull Conjunction formula) {
        // Formula is relevant if either RHS or LHS is dependent on a variable
        boolean isRelevant = !considerVmVarsOnly || this.visit(formula.getLeft());
        if (!isRelevant) {
            isRelevant = this.visit(formula.getRight());
        }

        return isRelevant;
    }

}
