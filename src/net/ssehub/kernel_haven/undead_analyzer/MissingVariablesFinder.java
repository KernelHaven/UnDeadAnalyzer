/*
 * Copyright 2017-2019 University of Hildesheim, Software Systems Engineering
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.ssehub.kernel_haven.undead_analyzer;

import static net.ssehub.kernel_haven.util.null_checks.NullHelpers.notNull;

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
import net.ssehub.kernel_haven.util.null_checks.NonNull;
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

    public static final @NonNull Setting<@NonNull Analysis> MISSING_TYPE =
            new EnumSetting<>("analysis.missing.type", Analysis.class, true, Analysis.DEFINED_BUT_NOT_USED, "Defines "
                    + "the type of missing analysis to execute.");
    
    private @NonNull AnalysisComponent<VariabilityModel> vmComponent;

    private @NonNull AnalysisComponent<BuildModel> bmComponent;

    private @NonNull AnalysisComponent<SourceFile<?>> cmComponent;

    /**
     * The different types of missing analyzes.
     */
    public enum Analysis {
        DEFINED_BUT_NOT_USED, USED_BUT_NOT_DEFINED,
    }

    private @NonNull Analysis analyse;

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
    public MissingVariablesFinder(@NonNull Configuration config,
            @NonNull AnalysisComponent<VariabilityModel> vmComponent,
            @NonNull AnalysisComponent<BuildModel> bmComponent, @NonNull AnalysisComponent<SourceFile<?>> cmComponent)
            throws SetUpException {
        super(config);
        
        config.registerSetting(MISSING_TYPE);
        analyse = config.getValue(MISSING_TYPE);
        this.vmComponent = vmComponent;
        this.bmComponent = bmComponent;
        this.cmComponent = cmComponent;
    }

    @Override
    protected void execute() {
        VariabilityModel vm = vmComponent.getNextResult();
        BuildModel bm = bmComponent.getNextResult();

        if (bm == null || vm == null) {
            LOGGER.logError("Couldn't get models");
            return;
        }
        
        List<@NonNull SourceFile<?>> cm = new LinkedList<>();
        SourceFile<?> file;
        while ((file = cmComponent.getNextResult()) != null) {
            cm.add(file);
        }

        Set<@NonNull String> variables = null;
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
    public @NonNull Set<@NonNull String> definedButUnused(@NonNull VariabilityModel vm, @NonNull BuildModel bm,
            @NonNull List<@NonNull SourceFile<?>> files) {
        // Fill a map
        Map<@NonNull String, Boolean> variables = new HashMap<>();
        for (VariabilityVariable variabilityVariable : vm.getVariables()) {
            variables.put(variabilityVariable.getName(), false);
        }
        // Check in build model
        for (File file : bm) {
            Set<@NonNull String> names = new HashSet<>();
            getVariableNamesInFormula(names, notNull(bm.getPc(file)));
            for (String var : names) {
                if (var.endsWith("_MODULE")) {
                    variables.put(notNull(var.substring(0, var.length() - "_MODULE".length())), true);
                } else {
                    variables.put(var, true);
                }
            }
        }
        // Check in code model
        for (SourceFile<?> file : files) {
            for (CodeElement<?> element : file) {
                Set<@NonNull String> names = new HashSet<>();
                getVariableNamesInElement(element, names);

                for (String var : names) {
                    if (var.endsWith("_MODULE")) {
                        variables.put(notNull(var.substring(0, var.length() - "_MODULE".length())), true);
                    } else {
                        variables.put(var, true);
                    }
                }
            }
        }
        Set<@NonNull String> definedButUnused = new HashSet<>();
        for (Map.Entry<@NonNull String, Boolean> entry : variables.entrySet()) {
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
    public @NonNull Set<@NonNull String> usedButNotDefined(@NonNull VariabilityModel vm, @NonNull BuildModel bm,
            @NonNull List<@NonNull SourceFile<?>> files) {
        Map<@NonNull String, Boolean> variables = new HashMap<>();
        // Fill a map with build model
        for (File file : bm) {
            Set<@NonNull String> names = new HashSet<>();
            getVariableNamesInFormula(names, notNull(bm.getPc(file)));
            for (String var : names) {
                variables.put(var, false);
            }
        }
        // Fill same map with code model
        for (SourceFile<?> file : files) {
            for (CodeElement<?> element : file) {
                Set<@NonNull String> names = new HashSet<>();
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
        Set<@NonNull String> definedButUnused = new HashSet<>();
        for (Map.Entry<@NonNull String, Boolean> entry : variables.entrySet()) {
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
    private void getVariableNamesInElement(@NonNull CodeElement<?> element, @NonNull Set<@NonNull String> result) {
        getVariableNamesInFormula(result, element.getPresenceCondition());

        for (CodeElement<?> child : element) {
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
    private void getVariableNamesInFormula(@NonNull Set<@NonNull String> names, @NonNull Formula formular) {
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
    public @NonNull String getResultName() {
        return "Missing Variables";
    }

}
