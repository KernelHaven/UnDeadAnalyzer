package net.ssehub.kernel_haven.undead_analyzer;

import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.ssehub.kernel_haven.SetUpException;
import net.ssehub.kernel_haven.analysis.AnalysisComponent;
import net.ssehub.kernel_haven.build_model.BuildModel;
import net.ssehub.kernel_haven.code_model.CodeElement;
import net.ssehub.kernel_haven.code_model.SourceFile;
import net.ssehub.kernel_haven.config.Configuration;
import net.ssehub.kernel_haven.config.EnumSetting;
import net.ssehub.kernel_haven.config.Setting;
import net.ssehub.kernel_haven.util.logic.Conjunction;
import net.ssehub.kernel_haven.util.logic.Disjunction;
import net.ssehub.kernel_haven.util.logic.Formula;
import net.ssehub.kernel_haven.util.logic.Negation;
import net.ssehub.kernel_haven.util.logic.Variable;
import net.ssehub.kernel_haven.variability_model.VariabilityModel;
import net.ssehub.kernel_haven.variability_model.VariabilityVariable;

/**
 * This Analysis uses a VariabilityVariable an looks in the code and build model to see if it is used. If it is not used
 * it will write the unused variables in a file.
 * 
 * @author Johannes
 * @author Manu
 * @author Adam
 */
public class MissingVariablesFinder extends AnalysisComponent<String> {

    private static final Setting<Analysis> MISSING_TYPE =
            new EnumSetting<>("analysis.missing.type", Analysis.class, true, Analysis.DEFINED_BUT_NOT_USED, "Defines "
                    + "the type of missing analysis to execute.");
    
    private AnalysisComponent<VariabilityModel> vmComponent;

    private AnalysisComponent<BuildModel> bmComponent;

    private AnalysisComponent<SourceFile> cmComponent;

    /**
     * The different types of missing analyzes.
     */
    private enum Analysis {
        DEFINED_BUT_NOT_USED, USED_BUT_NOT_DEFINED,
    }

    private Analysis analyse;

    /**
     * Default Constructor.
     * 
     * @param config
     *            This analysis Properties are not extended. Never <code>null</code>.
     * @param vmComponent
     *            The component to provide the variability model.
     * @param bmComponent
     *            The component to provide the build model.
     * @param cmComponent
     *            The component to provide the code model.
     * 
     * @throws SetUpException
     *             if analysis.missing.type is wrong.
     */
    public MissingVariablesFinder(Configuration config, AnalysisComponent<VariabilityModel> vmComponent,
            AnalysisComponent<BuildModel> bmComponent, AnalysisComponent<SourceFile> cmComponent)
            throws SetUpException {
        super(config);
        
        config.registerSetting(MISSING_TYPE);
        analyse = config.getValue(MISSING_TYPE);
    }

    @Override
    protected void execute() {
        VariabilityModel vm = vmComponent.getNextResult();
        BuildModel bm = bmComponent.getNextResult();

        List<SourceFile> cm = new LinkedList<>();
        SourceFile file;
        while ((file = cmComponent.getNextResult()) != null) {
            cm.add(file);
        }

        Set<String> variables = null;
        switch (analyse) {
        case DEFINED_BUT_NOT_USED:
            LOGGER.logInfo("Defined but unused analysis");
            variables = definedButUnused(vm, bm, cm);
            break;

        case USED_BUT_NOT_DEFINED:
            LOGGER.logInfo("Used but not defined analysis");
            variables = usedButNotDefined(vm, bm, cm);
            break;

        default:
            LOGGER.logError("Wrong configuration: no analysis defined");
            break;
        }
        
        if (variables != null) {
            for (String variable : variables) {
                addResult(variable);
            }
        }
    }

    /**
     * Searching for defined but unused Variables.
     * 
     * @param vm
     *            The model to search with. Never <code>null</code>.
     * @param bm
     *            The build to search in. Never <code>null</code>.
     * @param files
     *            The files to search in. Never <code>null</code>.
     * @return variables The set of variables, flagged with true or false whether if the variable is used in the code.
     */
    public Set<String> definedButUnused(VariabilityModel vm, BuildModel bm, List<SourceFile> files) {
        // Fill a map
        Map<String, Boolean> variables = new HashMap<>();
        for (VariabilityVariable variabilityVariable : vm.getVariables()) {
            variables.put(variabilityVariable.getName(), false);
        }
        // Check in build model
        for (File file : bm) {
            Set<String> names = new HashSet<>();
            getVariableNamesInFormula(names, bm.getPc(file));
            for (String var : names) {
                variables.put(var, true);
            }
        }
        // Check in code model
        for (SourceFile file : files) {
            for (CodeElement element : file) {
                Set<String> names = new HashSet<>();
                getVariableNamesInElement(element, names);

                for (String var : names) {
                    variables.put(var, true);
                }
            }
        }
        Set<String> definedButUnused = new HashSet<>();
        for (Map.Entry<String, Boolean> entry : variables.entrySet()) {
            if (!entry.getValue()) {
                definedButUnused.add(entry.getKey());
            }
        }
        return definedButUnused;
    }

    /**
     * Searching for defined but unused Variables.
     * 
     * @param vm
     *            The model to search with. Never <code>null</code>.
     * @param bm
     *            The build to search in. Never <code>null</code>.
     * @param files
     *            The files to search in. Never <code>null</code>.
     * @return variables The set of variables, flagged with true or false whether if the variable is used in the code.
     */
    public Set<String> usedButNotDefined(VariabilityModel vm, BuildModel bm, List<SourceFile> files) {
        Map<String, Boolean> variables = new HashMap<>();
        // Fill a map with build model
        for (File file : bm) {
            Set<String> names = new HashSet<>();
            getVariableNamesInFormula(names, bm.getPc(file));
            for (String var : names) {
                variables.put(var, false);
            }
        }
        // Fill same map with code model
        for (SourceFile file : files) {
            for (CodeElement element : file) {
                Set<String> names = new HashSet<>();
                getVariableNamesInElement(element, names);
                for (String var : names) {
                    variables.put(var, false);
                }
            }
        }
        // Check with variability model
        for (VariabilityVariable variabilityVariable : vm.getVariables()) {
            variables.put(variabilityVariable.getName(), true);
            if (variabilityVariable.getType().equals("tristate")) {
                variables.put(variabilityVariable.getName() + "_MODULE", true);
            }
        }
        // Set results
        Set<String> definedButUnused = new HashSet<>();
        for (Map.Entry<String, Boolean> entry : variables.entrySet()) {
            if (!entry.getValue()) {
                definedButUnused.add(entry.getKey());
            }
        }
        return definedButUnused;
    }

    /**
     * Recursively finds all variable names that start with CONFIG_ in the presence conditions of an element and all
     * child elements.
     * 
     * @param element
     *            The element to search in.
     * @param result
     *            The resulting set of variable names
     */
    private void getVariableNamesInElement(CodeElement element, Set<String> result) {
        getVariableNamesInFormula(result, element.getPresenceCondition());

        for (CodeElement child : element.iterateNestedElements()) {
            getVariableNamesInElement(child, result);
        }
    }

    /**
     * Recursively fills a set with strings with names of variables from a Formula. Only variables that start with
     * CONFIG_ are considered.
     * 
     * @param formular
     *            The Formula to check. Never <code>null</code>.
     * @param names
     *            A set to fill with call by reference. Never <code>null</code>.
     */
    private void getVariableNamesInFormula(Set<String> names, Formula formular) {
        if (formular instanceof Variable) {
            Variable var = (Variable) formular;
            if (var.getName().startsWith("CONFIG_")) {
                names.add(var.getName());
            }
        } else if (formular instanceof Disjunction) {
            Disjunction dis = (Disjunction) formular;
            getVariableNamesInFormula(names, dis.getLeft());
            getVariableNamesInFormula(names, dis.getRight());
        } else if (formular instanceof Conjunction) {
            Conjunction con = (Conjunction) formular;
            getVariableNamesInFormula(names, con.getLeft());
            getVariableNamesInFormula(names, con.getRight());
        } else if (formular instanceof Negation) {
            Negation neg = (Negation) formular;
            getVariableNamesInFormula(names, neg.getFormula());
        }
    }

    @Override
    public String getResultName() {
        return "Missing Variables";
    }

}
