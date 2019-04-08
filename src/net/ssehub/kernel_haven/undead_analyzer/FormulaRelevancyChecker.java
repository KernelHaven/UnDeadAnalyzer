package net.ssehub.kernel_haven.undead_analyzer;

import java.util.HashSet;
import java.util.Set;

import net.ssehub.kernel_haven.config.DefaultSettings.USAGE_OF_VM_VARS;
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

    private USAGE_OF_VM_VARS usageOfVmVars;
    private Set<String> definedVariables;

    /**
     * Sole constructor of this class.
     * 
     * @param varModel      The variability model, defined allowed variables must be
     *                      not <tt>null</tt> if considerVmVarsOnly should be used
     *                      <tt>true</tt>
     * @param usageOfVmVars Specification whether a formula should contain variables
     *                      known by the variability model:
     *                      <p>
     *                      - {@link USAGE_OF_VM_VARS#ALL_ELEMENTS}: Considers all
     *                      elements in the analysis, independently if used elements
     *                      are defined in the variability model.
     *                      </p>
     *                      <p>
     *                      - {@link USAGE_OF_VM_VARS#ANY_VM_USAGE}: Considers only
     *                      elements that contain at least one variable defined by
     *                      the variability model, independently where it was used.
     *                      </p>
     *                      <p>
     *                      - {@link USAGE_OF_VM_VARS#VM_USAGE_IN_CODE}: Considers
     *                      only elements that contain at least one variable defined
     *                      in the variability model, only if the element was used
     *                      in code."
     *                      </p>
     * 
     */
    public FormulaRelevancyChecker(VariabilityModel varModel, @NonNull USAGE_OF_VM_VARS usageOfVmVars) {
        this.usageOfVmVars = usageOfVmVars;
        if (varModel != null && usageOfVmVars != USAGE_OF_VM_VARS.ALL_ELEMENTS) {
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
        if (this.usageOfVmVars != USAGE_OF_VM_VARS.ALL_ELEMENTS) {
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
        return (this.usageOfVmVars == USAGE_OF_VM_VARS.ALL_ELEMENTS);
    }

    @Override
    public Boolean visitTrue(@NonNull True trueConstant) {
        // Unclear if the formula is dependent on Variability Model
        return (this.usageOfVmVars == USAGE_OF_VM_VARS.ALL_ELEMENTS);
    }

    @Override
    public Boolean visitVariable(@NonNull Variable variable) {
        // Relevance depends on variable
        return isRelevant(variable.getName());
    }

    @Override
    public Boolean visitNegation(@NonNull Negation formula) {
        return (this.usageOfVmVars == USAGE_OF_VM_VARS.ALL_ELEMENTS);
    }

    @Override
    public Boolean visitDisjunction(@NonNull Disjunction formula) {

        // Formula is relevant if either RHS or LHS is dependent on a variable
        boolean isRelevant = (this.usageOfVmVars == USAGE_OF_VM_VARS.ALL_ELEMENTS) || this.visit(formula.getLeft());
        if (!isRelevant) {
            isRelevant = this.visit(formula.getRight());
        }

        return isRelevant;
    }

    @Override
    public Boolean visitConjunction(@NonNull Conjunction formula) {
        // Formula is relevant if either RHS or LHS is dependent on a variable
        boolean isRelevant = (this.usageOfVmVars == USAGE_OF_VM_VARS.ALL_ELEMENTS) || this.visit(formula.getLeft());
        if (!isRelevant) {
            isRelevant = this.visit(formula.getRight());
        }

        return isRelevant;
    }

}
