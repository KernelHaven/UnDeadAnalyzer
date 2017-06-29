package net.ssehub.kernel_haven.default_analyses;

import java.io.File;
import java.io.PrintStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.ssehub.kernel_haven.SetUpException;
import net.ssehub.kernel_haven.analysis.AbstractAnalysis;
import net.ssehub.kernel_haven.build_model.BuildModel;
import net.ssehub.kernel_haven.code_model.Block;
import net.ssehub.kernel_haven.code_model.SourceFile;
import net.ssehub.kernel_haven.config.Configuration;
import net.ssehub.kernel_haven.util.ExtractorException;
import net.ssehub.kernel_haven.util.logic.Conjunction;
import net.ssehub.kernel_haven.util.logic.Disjunction;
import net.ssehub.kernel_haven.util.logic.Formula;
import net.ssehub.kernel_haven.util.logic.Negation;
import net.ssehub.kernel_haven.util.logic.Variable;
import net.ssehub.kernel_haven.variability_model.VariabilityModel;
import net.ssehub.kernel_haven.variability_model.VariabilityVariable;

/**
 * This Analysis uses a VariabilityVariable an looks in the code and build model to see if it 
 * is used. If it is not used it will write the unused variables in a file.
 * 
 * @author Johannes
 * @author Manu
 * @author Adam
 */
public class MissingAnalysis extends AbstractAnalysis {

    /**
     * The different types of missing analyzes.
     */
    private enum Analysis {
        DEFINED_BUT_NOT_USED,
        USED_BUT_NOT_DEFINED,
    }
    
    private Analysis analyse;
    
    /**
     * Default Constructor.
     * @param config
     *            This analysis Properties are not extended. Never <code>null</code>.
     * @throws SetUpException if analysis.missing.type is wrong.
     */
    public MissingAnalysis(Configuration config) throws SetUpException {
        super(config);
        
        String type = config.getProperty("analysis.missing.type", "D");
        if (type.equalsIgnoreCase("D")) {
            analyse = Analysis.DEFINED_BUT_NOT_USED;
        } else if (type.equalsIgnoreCase("U")) {
            analyse = Analysis.USED_BUT_NOT_DEFINED;
        } else {
            throw new SetUpException("analysis.missing.type is wrong: " + type);
        }
        
    }

    @Override
    public void run() {
        LOGGER.logInfo("Start missing analysis");
        try {
            // Start all
            vmProvider.start(config.getVariabilityConfiguration());
            bmProvider.start(config.getBuildConfiguration());
            cmProvider.start(config.getCodeConfiguration());

            // Set all needed Models
            VariabilityModel vm = null;
            BuildModel bm = null;
            List<SourceFile> files = new LinkedList<>();
            vm = vmProvider.getResult();
            bm = bmProvider.getResult();
            SourceFile file = cmProvider.getNext();
            while (file != null) {
                files.add(file);
                file = cmProvider.getNext();
            }
            
            // Analysis
            String filename = "";
            Set<String> variables = null;
            
            switch (analyse) {
            case DEFINED_BUT_NOT_USED:
                LOGGER.logInfo("Defined but unused analysis");
                variables = definedButUnused(vm, bm, files);
                filename = "definedButUnused.variables";
                break;
                
            case USED_BUT_NOT_DEFINED:
                LOGGER.logInfo("Used but not defined analysis");
                variables = usedButNotDefined(vm, bm, files);
                filename = "usedButUndefined.variables";
                break;
                
            default:
                LOGGER.logError("Wrong configuration: no analysis defined");
                break;
            }
            
            // Put Result
            PrintStream fw = createResultStream(filename);
            for (String entry : variables) {
                fw.println(entry);
            }
            fw.close();
            
        } catch (ExtractorException | SetUpException e) {
            LOGGER.logException("Provider failed", e);
        }
        LOGGER.logInfo("Missing analysis done");
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
     * @return variables The set of variables, flagged with true or false
     *         whether if the variable is used in the code.
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
            for (Block block : file) {
                Set<String> names = new HashSet<>();
                getVariableNamesInBlock(block, names);
                
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
     * @return variables The set of variables, flagged with true or false
     *         whether if the variable is used in the code.
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
            for (Block block : file) {
                Set<String> names = new HashSet<>();
                getVariableNamesInBlock(block, names);
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
     * Recursively finds all variable names that start with CONFIG_
     * in the presence conditions of a block and all child blocks.
     *  
     * @param block The block to search in.
     * @param result The resulting set of variable names
     */
    private void getVariableNamesInBlock(Block block, Set<String> result) {
        getVariableNamesInFormula(result, block.getPresenceCondition());
        
        for (Block child : block) {
            getVariableNamesInBlock(child, result);
        }
    }

    /**
     * Recursively fills a set with strings with names of variables from a Formula. Only
     * variables that start with CONFIG_ are considered.
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
    
}
