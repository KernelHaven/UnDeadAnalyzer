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
 * Checks whether a formula contains at least one variable defined in the {@link VariabilityModel}.
 * @author El-Sharkawy
 *
 */
public class FormulaRelevancyChecker implements IFormulaVisitor<Boolean> {
    
    private boolean considerVmVarsOnly;
    private Set<String> definedVariables;
    
    /**
     * Sole constructor of this class.
     * @param varModel The variability model, defined allowed variables must be not <tt>null</tt> if considerVmVarsOnly
     *     should be used <tt>true</tt>
     * @param considerVmVarsOnly Specification whether a formula should contain at least
     *     one variable known by the variability model. Formulas must contain at least one variable of the variability
     *     model iff this value is <tt>true</tt> <b>and</b> varModel is not <tt>null</tt>.
     */
    public FormulaRelevancyChecker(VariabilityModel varModel, boolean considerVmVarsOnly) {
        this.considerVmVarsOnly = (null != varModel) ? considerVmVarsOnly : false;
        definedVariables = this.considerVmVarsOnly ? new HashSet<>(varModel.getVariableMap().keySet()) : null;
    }
    
    /**
     * Helper function to determine which variables are relevant.
     * 
     * @param variable The variable to check.
     * @return <tt>true</tt> if the variable is relevant, <tt>false</tt> otherwise.
     */
    private boolean isRelevant(@NonNull String variable) {
        return (considerVmVarsOnly) ? definedVariables.contains(variable) : true;
    }

    @Override
    public Boolean visitFalse(@NonNull False falseConstant) {
        // Unclear if the formula is dependent on Variability Model
        // Idea is: !considerVmVarsOnly || false
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
        return !considerVmVarsOnly || this.visit(formula.getFormula());
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
