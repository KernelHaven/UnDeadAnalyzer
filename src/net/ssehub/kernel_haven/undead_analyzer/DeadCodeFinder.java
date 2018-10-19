package net.ssehub.kernel_haven.undead_analyzer;

import static net.ssehub.kernel_haven.util.null_checks.NullHelpers.notNull;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.ssehub.kernel_haven.analysis.AnalysisComponent;
import net.ssehub.kernel_haven.build_model.BuildModel;
import net.ssehub.kernel_haven.cnf.Cnf;
import net.ssehub.kernel_haven.cnf.ConverterException;
import net.ssehub.kernel_haven.cnf.FormulaToCnfConverterFactory;
import net.ssehub.kernel_haven.cnf.FormulaToCnfConverterFactory.Strategy;
import net.ssehub.kernel_haven.cnf.IFormulaToCnfConverter;
import net.ssehub.kernel_haven.cnf.ISatSolver;
import net.ssehub.kernel_haven.cnf.SatSolverFactory;
import net.ssehub.kernel_haven.cnf.SolverException;
import net.ssehub.kernel_haven.cnf.VmToCnfConverter;
import net.ssehub.kernel_haven.code_model.CodeElement;
import net.ssehub.kernel_haven.code_model.SourceFile;
import net.ssehub.kernel_haven.config.Configuration;
import net.ssehub.kernel_haven.config.DefaultSettings;
import net.ssehub.kernel_haven.undead_analyzer.DeadCodeFinder.DeadCodeBlock;
import net.ssehub.kernel_haven.util.FormatException;
import net.ssehub.kernel_haven.util.ProgressLogger;
import net.ssehub.kernel_haven.util.io.TableElement;
import net.ssehub.kernel_haven.util.io.TableRow;
import net.ssehub.kernel_haven.util.logic.Conjunction;
import net.ssehub.kernel_haven.util.logic.Formula;
import net.ssehub.kernel_haven.util.null_checks.NonNull;
import net.ssehub.kernel_haven.util.null_checks.Nullable;
import net.ssehub.kernel_haven.variability_model.VariabilityModel;

/**
 * A simple implementation for dead code detection.
 * 
 * @author Adam
 */
public class DeadCodeFinder extends AnalysisComponent<DeadCodeBlock> {
    
    protected @NonNull AnalysisComponent<VariabilityModel > vmComponent;
    
    protected @NonNull AnalysisComponent<BuildModel > bmComponent;
    
    protected @NonNull AnalysisComponent<SourceFile > cmComponent;
    
    protected boolean considerVmVarsOnly;
    
    protected @Nullable FormulaRelevancyChecker relevancyChecker;
    
    protected VariabilityModel vm;
    
    protected Cnf vmCnf;
    
    protected BuildModel bm;
    
    /**
     * Creates a dead code analysis.
     *  
     * @param config The user configuration; not used.
     * @param vmComponent The component to provide the variability model.
     * @param bmComponent The component to provide the build model.
     * @param cmComponent The component to provide the code model.
     */
    public DeadCodeFinder(@NonNull Configuration config, @NonNull AnalysisComponent<VariabilityModel> vmComponent,
            @NonNull AnalysisComponent<BuildModel> bmComponent, @NonNull AnalysisComponent<SourceFile> cmComponent) {
        super(config);
        
        this.vmComponent = vmComponent;
        this.bmComponent = bmComponent;
        this.cmComponent = cmComponent;
        
        considerVmVarsOnly = config.getValue(DefaultSettings.ANALYSIS_USE_VARMODEL_VARIABLES_ONLY);
    }
    
    /**
     * A class that holds all variables relevant for solving SAT. This is created once per
     * {@link DeadCodeFinder#findDeadCodeBlocks(SourceFile))}, so that this method is thread-safe.
     */
    private static class SatUtilities {
        
        private @NonNull IFormulaToCnfConverter converter;
        
        private @NonNull ISatSolver solver;
        
        private @NonNull Map<Formula, Boolean> satCache;

        /**
         * Creates this instance.
         * 
         * @param converter the formula to CNF converter.
         * @param solver The SAT solver.
         * @param satCache The SAT cache.
         */
        SatUtilities(@NonNull IFormulaToCnfConverter converter, @NonNull ISatSolver solver,
                @NonNull Map<Formula, Boolean> satCache) {
            this.converter = converter;
            this.solver = solver;
            this.satCache = satCache;
        }
        
    }
    
    /**
     * Finds dead code blocks. This method is thread-safe.
     * 
     * @param sourceFile The source file to search in.
     * @return The list of dead code blocks.
     */
    protected @NonNull List<@NonNull DeadCodeBlock> findDeadCodeBlocks(@NonNull SourceFile sourceFile) {
        
        List<@NonNull DeadCodeBlock> result = new ArrayList<>();
        
        SatUtilities satUtils = new SatUtilities(
                FormulaToCnfConverterFactory.create(Strategy.RECURISVE_REPLACING),
                SatSolverFactory.createSolver(vmCnf, false), new HashMap<>(10000));
        
        Formula filePc = bm.getPc(sourceFile.getPath());
        
        if (filePc == null) {
            LOGGER.logInfo("Skipping " + sourceFile.getPath() + " because it has no build PC");
        } else {
            LOGGER.logInfo("Running for file " + sourceFile.getPath());
            LOGGER.logDebug("File PC: " + filePc);
            
            
            for (CodeElement element : sourceFile) {
                try {
                    checkElement(element, filePc, sourceFile, satUtils, result);
                } catch (SolverException | ConverterException e) {
                    LOGGER.logException("Exception while trying to check element", e);
                }
            }
        }
        
        return result;
    }
    
    /**
     * Checks whether the given formula is satisfiable with the variability model. Internally, this method has a cache
     * to speed up when the same formula is passed to it several times.
     * 
     * @param pc The formula to check.
     * @param satUtils The sat utils to use.
     * 
     * @return Whether the formula is satisfiable with the variability model.
     * 
     * @throws ConverterException If the conversion to CNF fails.
     * @throws SolverException If the SAT-solver fails.
     */
    private boolean isSat(@NonNull Formula pc, @NonNull SatUtilities satUtils)
            throws ConverterException, SolverException {
        
        Boolean sat = satUtils.satCache.get(pc);
        
        if (sat == null) {
            Cnf pcCnf = satUtils.converter.convert(pc);
            
            String[] cnfLines = pcCnf.toString().split("\n");
            String[] output = new String[cnfLines.length + 1];
            System.arraycopy(cnfLines, 0, output, 1, cnfLines.length);
            output[0] = "PcCnf: ";
            LOGGER.logDebug(output);
            
            sat = satUtils.solver.isSatisfiable(pcCnf);
            satUtils.satCache.put(pc, sat);
            LOGGER.logDebug("sat(" + pc + ") = " + sat);
        }

        return sat;
    }
    
    /**
     * Checks if a given element is dead. Recursively walks over each child element, too.
     * 
     * @param element The element to check.
     * @param filePc The presence condition of the file.
     * @param sourceFile The source file; used for creating the result.
     * @param satUtils The SAT utils to use.
     * @param result The list to add result {@link DeadCodeBlock}s to.
     * 
     * @throws ConverterException If converting the formula to CNF fails.
     * @throws SolverException If solving the CNF fails.
     */
    private void checkElement(@NonNull CodeElement element, @NonNull Formula filePc, @NonNull SourceFile sourceFile,
            @NonNull SatUtilities satUtils, @NonNull List<@NonNull DeadCodeBlock> result)
            throws ConverterException, SolverException {
        
        Formula pc = new Conjunction(element.getPresenceCondition(), filePc);
        FormulaRelevancyChecker checker = this.relevancyChecker;
        boolean considerBlock = checker != null ? checker.visit(element.getPresenceCondition()) : true;
        
        if (considerBlock && !isSat(pc, satUtils)) {
            DeadCodeBlock deadBlock = new DeadCodeBlock(element, filePc);
            LOGGER.logInfo("Found dead block: " + deadBlock);
            result.add(deadBlock);
        }
        
        for (CodeElement child : element.iterateNestedElements()) {
            checkElement(child, filePc, sourceFile, satUtils, result);
        }
    }

    /**
     * A dead code block.
     */
    @TableRow
    public static class DeadCodeBlock {
        
        private @NonNull File sourceFile;
        
        private @Nullable Formula filePc;
        
        private int startLine;

        private int endLine;
        
        private @Nullable Formula presenceCondition;
        
        /**
         * Creates a dead code block.
         * @param sourceFile The source file.
         * @param line The line of the element.
         */
        public DeadCodeBlock(@NonNull File sourceFile, int line) {
            this.sourceFile = sourceFile;
            this.startLine = line;
            this.endLine = 0;
            this.presenceCondition = null;
            this.filePc = null;
        }
        
        /**
         * Converts a {@link CodeElement} into a {@link DeadCodeBlock}.
         * This constructor stores more information.
         * 
         * @param deadElement An element which was identified to be dead.
         * @param filePc The presence condition for the complete file, maybe <tt>null</tt>
         */
        public DeadCodeBlock(@NonNull CodeElement deadElement, @NonNull Formula filePc) {
            this(deadElement.getSourceFile(), deadElement.getLineStart());
            this.endLine = deadElement.getLineEnd();
            this.presenceCondition = deadElement.getPresenceCondition();
            this.filePc = filePc;
        }

        /**
         * Returns the source file that this block is in.
         * 
         * @return The source file.
         */
        @TableElement(name = "Source File", index = 0)
        public @NonNull File getSourceFile() {
            return sourceFile;
        }
        
        /**
         * Returns the presence condition (PC) of the file.
         * 
         * @return The PC of the file. May be <code>null</code>.
         */
        @TableElement(name = "File PC", index = 1)
        public @Nullable Formula getFilePc() {
            return filePc;
        }
        
        /**
         * The starting line of this block.
         * @return The staring line.
         */
        @TableElement(name = "Line Start", index = 2)
        public int getStartLine() {
            return startLine;
        }
        
        /**
         * The end line of this block.
         * @return The end line.
         */
        @TableElement(name = "Line End", index = 3)
        public int getEndLine() {
            return endLine;
        }
        
        /**
         * Returns the presence condition (PC) of this block.
         * 
         * @return The PC.
         */
        @TableElement(name = "Presence Condition", index = 4)
        public @Nullable Formula getPresenceCondition() {
            return presenceCondition;
        }
        
        @Override
        public @NonNull String toString() {
            char separator = ' ';
            StringBuffer result = new StringBuffer();
            result.append(sourceFile.getPath());
            result.append(separator);
            if (null != filePc) {
                result.append(filePc.toString());
            }
            result.append(separator);
            result.append(startLine);
            result.append(separator);
            if (0 != endLine) {
                result.append(endLine);
            }
            result.append(separator);
            if (null != presenceCondition) {
                result.append(presenceCondition.toString());
            }            
            
            return notNull(result.toString());
        }
        
    }

    @Override
    protected void execute() {
        vm = vmComponent.getNextResult();
        bm = bmComponent.getNextResult();
        
        if (vm == null || bm == null) {
            LOGGER.logError("Couldn't get models");
            return;
        }
        
        try {
            vmCnf = new VmToCnfConverter().convertVmToCnf(notNull(vm)); // vm was initialized in execute()
            
            if (considerVmVarsOnly) {
                relevancyChecker = new FormulaRelevancyChecker(vm, considerVmVarsOnly);
            }
            
            ProgressLogger progress = new ProgressLogger(notNull(getClass().getSimpleName()));
            
            SourceFile file;
            while ((file = cmComponent.getNextResult()) != null) {
                List<@NonNull DeadCodeBlock> deadBlocks = findDeadCodeBlocks(file);
                for (DeadCodeBlock block : deadBlocks) {
                    addResult(block);
                }
                
                progress.processedOne();
            }
            
            progress.close();
            
        } catch (FormatException e) {
            LOGGER.logException("Invalid variability model", e);
        }
    }

    @Override
    public String getResultName() {
        return "Dead Code Blocks";
    }
    
}
