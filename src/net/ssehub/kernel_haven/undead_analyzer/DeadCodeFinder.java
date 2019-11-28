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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.ssehub.kernel_haven.SetUpException;
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
import net.ssehub.kernel_haven.config.Setting;
import net.ssehub.kernel_haven.config.Setting.Type;
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
    
    public static final @NonNull Setting<@NonNull Boolean> DETAILED_SETTING = new Setting<>(
            "analysis.undead.detailed_checks", Type.BOOLEAN, true, "false", "Whether the DeadCodeFinder should do"
                    + " a detailed analysis why a block is dead or not.");

    protected @NonNull AnalysisComponent<VariabilityModel> vmComponent;

    protected @NonNull AnalysisComponent<BuildModel> bmComponent;

    protected @NonNull AnalysisComponent<SourceFile<?>> cmComponent;

    protected boolean considerVmVarsOnly;

    protected @Nullable FormulaRelevancyChecker relevancyChecker;

    protected VariabilityModel vm;

    protected Cnf vmCnf;

    protected BuildModel bm;
    
    private boolean detailedAnalysis;

    /**
     * Creates a dead code analysis.
     * 
     * @param config      The pipeline configuration.
     * @param vmComponent The component to provide the variability model.
     * @param bmComponent The component to provide the build model.
     * @param cmComponent The component to provide the code model.
     * 
     * @throws SetUpException If reading the configuration fails.
     */
    public DeadCodeFinder(@NonNull Configuration config, @NonNull AnalysisComponent<VariabilityModel> vmComponent,
            @NonNull AnalysisComponent<BuildModel> bmComponent, @NonNull AnalysisComponent<SourceFile<?>> cmComponent)
            throws SetUpException {
        super(config);

        config.registerSetting(DETAILED_SETTING);
        detailedAnalysis = config.getValue(DETAILED_SETTING);
        
        this.vmComponent = vmComponent;
        this.bmComponent = bmComponent;
        this.cmComponent = cmComponent;
        
        this.considerVmVarsOnly = config.getValue(DefaultSettings.ANALYSIS_USE_VARMODEL_VARIABLES_ONLY);

    }

    /**
     * A class that holds all variables relevant for solving SAT. This is created
     * once per {@link DeadCodeFinder#findDeadCodeBlocks(SourceFile))}, so that this
     * method is thread-safe.
     */
    private static class SatUtilities {

        private @NonNull IFormulaToCnfConverter converter;

        private @NonNull ISatSolver vmSolver;
        
        private @NonNull ISatSolver plainSolver;

        private @NonNull Map<Formula, Boolean> vmSatCache;
        
        private @NonNull Map<Formula, Boolean> plainSatCache;

        /**
         * Creates this instances.
         * 
         * @param vmCnf The variability model as CNF:
         */
        SatUtilities(@NonNull Cnf vmCnf) {
            this.converter = FormulaToCnfConverterFactory.create(Strategy.RECURISVE_REPLACING);
            this.vmSolver = SatSolverFactory.createSolver(vmCnf, false);
            this.plainSolver = SatSolverFactory.createSolver();
            this.vmSatCache = new HashMap<>(10000);
            this.plainSatCache = new HashMap<>(10000);
        }
        
        /**
         * Checks whether the given formula is satisfiable with the variability model.
         * Internally, this method has a cache to speed up when the same formula is
         * passed to it several times.
         * 
         * @param pc The formula to check.
         * 
         * @return Whether the formula is satisfiable with the variability model.
         * 
         * @throws ConverterException If the conversion to CNF fails.
         * @throws SolverException If the SAT-solver fails.
         */
        public boolean isVmSat(@NonNull Formula pc) throws SolverException, ConverterException {
            Boolean sat = this.vmSatCache.get(pc);

            if (sat == null) {
                Cnf pcCnf = this.converter.convert(pc);

                String[] cnfLines = pcCnf.toString().split("\n");
                String[] output = new String[cnfLines.length + 1];
                System.arraycopy(cnfLines, 0, output, 1, cnfLines.length);
                output[0] = "PcCnf: ";
                LOGGER.logDebug(output);

                sat = this.vmSolver.isSatisfiable(pcCnf);
                this.vmSatCache.put(pc, sat);
                LOGGER.logDebug("sat(" + pc + ") = " + sat);
            }

            return sat;
        }
        
        /**
         * Checks whether the given formula is satisfiable (without the variability model).
         * Internally, this method has a cache to speed up when the same formula is
         * passed to it several times.
         * 
         * @param pc The formula to check.
         * 
         * @return Whether the formula is satisfiable.
         * 
         * @throws ConverterException If the conversion to CNF fails.
         * @throws SolverException If the SAT-solver fails.
         */
        public boolean isSat(@NonNull Formula pc) throws SolverException, ConverterException {
            Boolean sat = this.plainSatCache.get(pc);
            
            if (sat == null) {
                Cnf pcCnf = this.converter.convert(pc);
                
                String[] cnfLines = pcCnf.toString().split("\n");
                String[] output = new String[cnfLines.length + 1];
                System.arraycopy(cnfLines, 0, output, 1, cnfLines.length);
                output[0] = "PcCnf: ";
                LOGGER.logDebug(output);
                
                sat = this.plainSolver.isSatisfiable(pcCnf);
                this.plainSatCache.put(pc, sat);
                LOGGER.logDebug("sat(" + pc + ") = " + sat);
            }
            
            return sat;
        }
        
    }

    /**
     * Finds dead code blocks. This method is thread-safe.
     * 
     * @param sourceFile The source file to search in.
     * @return The list of dead code blocks.
     */
    protected @NonNull List<@NonNull DeadCodeBlock> findDeadCodeBlocks(@NonNull SourceFile<?> sourceFile) {

        List<@NonNull DeadCodeBlock> result = new ArrayList<>();
        
        // satUtils gets initialized lazily.
        SatUtilities satUtils = null;

        Formula filePc = bm.getPc(sourceFile.getPath());

        if (filePc == null) {
            LOGGER.logInfo("Skipping " + sourceFile.getPath() + " because it has no build PC");
        } else {
            LOGGER.logInfo("Running for file " + sourceFile.getPath());
            LOGGER.logDebug("File PC: " + filePc);
            
            boolean foundResult = false;
            
            if (this.detailedAnalysis) {
                satUtils = new SatUtilities(notNull(vmCnf));
                
                try {
                    if (!satUtils.isSat(filePc)) { // check filePC alone
                        foundResult = true;
                        DetailedDeadCodeBlock block = new DetailedDeadCodeBlock(sourceFile.getPath(), 0,
                                Reason.FILE_PC_NOT_SATISFIABLE);
                        block.setFilePc(filePc);
                        result.add(block);
                    } else if (!satUtils.isVmSat(filePc)) { // check filePC and VM
                        foundResult = true;
                        DetailedDeadCodeBlock block = new DetailedDeadCodeBlock(sourceFile.getPath(), 0,
                                Reason.FILE_PC_AND_VM_NOT_SATISFIABLE);
                        block.setFilePc(filePc);
                        result.add(block);
                    }
                } catch (SolverException | ConverterException e) {
                    LOGGER.logException("Exception while trying to check file PC", e);
                }
            }

            if (!foundResult) {
                for (CodeElement<?> element : sourceFile) {
                    // initialize satUtils lazily here, so that files with no block don't create one (if detailed=false)
                    if (satUtils == null) {
                        satUtils = new SatUtilities(notNull(vmCnf));
                    }
                    try {
                        checkElement(element, filePc, sourceFile, satUtils, result);
                    } catch (SolverException | ConverterException e) {
                        LOGGER.logException("Exception while trying to check element", e);
                    }
                }
            }
        }

        return result;
    }

    /**
     * Checks if a given element is dead. Recursively walks over each child element,
     * too.
     * 
     * @param element    The element to check.
     * @param filePc     The presence condition of the file.
     * @param sourceFile The source file; used for creating the result.
     * @param satUtils   The SAT utils to use.
     * @param result     The list to add result {@link DeadCodeBlock}s to.
     * 
     * @throws ConverterException If converting the formula to CNF fails.
     * @throws SolverException    If solving the CNF fails.
     */
    private void checkElement(@NonNull CodeElement<?> element, @NonNull Formula filePc,
            @NonNull SourceFile<?> sourceFile, @NonNull SatUtilities satUtils,
            @NonNull List<@NonNull DeadCodeBlock> result) throws ConverterException, SolverException {

        Formula pc = new Conjunction(element.getPresenceCondition(), filePc);
        FormulaRelevancyChecker checker = this.relevancyChecker;
        boolean considerBlock = checker != null ? checker.visit(element.getPresenceCondition()) : true;

        if (this.detailedAnalysis) {
            // check CPP alone
            if (!satUtils.isSat(element.getPresenceCondition())) { // check CPP alone
                result.add(new DetailedDeadCodeBlock(element, filePc, Reason.CPP_NOT_SATISFIABLE));
            } else if (!satUtils.isSat(pc)) { // check CPP and filePC
                result.add(new DetailedDeadCodeBlock(element, filePc, Reason.CPP_AND_FILE_PC_NOT_SATISFIABLE));
            } else if (!satUtils.isVmSat(element.getPresenceCondition())) { // check CPP and VM
                result.add(new DetailedDeadCodeBlock(element, filePc, Reason.CPP_AND_VM_NOT_SATISFIABLE));
            } else if (!satUtils.isVmSat(pc)) { // check CPP and filePC and VM
                result.add(new DetailedDeadCodeBlock(element, filePc, Reason.CPP_AND_FILE_PC_AND_VM_NOT_SATISFIABLE));
            }
        } else {
            if (considerBlock && !satUtils.isVmSat(pc)) {
                DeadCodeBlock deadBlock = new DeadCodeBlock(element, filePc);
                LOGGER.logInfo("Found dead block: " + deadBlock);
                result.add(deadBlock);
            }
        }

        for (CodeElement<?> child : element) {
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
         * 
         * @param sourceFile The source file.
         * @param line       The line of the element.
         */
        public DeadCodeBlock(@NonNull File sourceFile, int line) {
            this.sourceFile = sourceFile;
            this.startLine = line;
            this.endLine = 0;
            this.presenceCondition = null;
            this.filePc = null;
        }

        /**
         * Converts a {@link CodeElement} into a {@link DeadCodeBlock}. This constructor
         * stores more information.
         * 
         * @param deadElement An element which was identified to be dead.
         * @param filePc      The presence condition for the complete file.
         */
        public DeadCodeBlock(@NonNull CodeElement<?> deadElement, @Nullable Formula filePc) {
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
         * Changes the file presence condition.
         * 
         * @param filePc The new filePc.
         */
        public void setFilePc(Formula filePc) {
            this.filePc = filePc;
        }

        /**
         * The starting line of this block.
         * 
         * @return The staring line.
         */
        @TableElement(name = "Line Start", index = 2)
        public int getStartLine() {
            return startLine;
        }

        /**
         * The end line of this block.
         * 
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
            StringBuilder result = new StringBuilder();
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
    
    /**
     * Possible reasons why a code block is dead.
     */
    public enum Reason {
        
        CPP_NOT_SATISFIABLE("C-preprocessor condition alone is not satisfiable"),
        CPP_AND_VM_NOT_SATISFIABLE("C-preprocessor condition combined with VM is not satisfiable"),
        
        FILE_PC_NOT_SATISFIABLE("File PC alone is not satisfiable"),
        FILE_PC_AND_VM_NOT_SATISFIABLE("File PC combined with VM is not satisfiable"),
        
        CPP_AND_FILE_PC_NOT_SATISFIABLE("C-preprocessor condition combined with file PC is not satisfiable"),
        
        CPP_AND_FILE_PC_AND_VM_NOT_SATISFIABLE("C-preprocessor condition combined with file PC and VM is"
                + " not satisfiable");
        
        private @NonNull String description;
        
        /**
         * Creates a new reason.
         * 
         * @param description A description of this reason.
         */
        private Reason(@NonNull String description) {
            this.description = description;
        }
        
        /**
         * Describes this reason.
         * 
         * @return The description of this reason.
         */
        public @NonNull String getDescription() {
            return description;
        }
        
    }
    
    /**
     * The result of a more detailed analysis of dead code blocks.
     */
    @TableRow
    public static class DetailedDeadCodeBlock extends DeadCodeBlock {

        private @NonNull Reason reason;
        
        /**
         * Creates a dead code block.
         * 
         * @param sourceFile The source file.
         * @param line The line of the element.
         * @param reason The reason why this block is dead.
         */
        public DetailedDeadCodeBlock(@NonNull File sourceFile, int line,  @NonNull Reason reason) {
            super(sourceFile, line);
            
            this.reason = reason;
        }

        /**
         * Converts a {@link CodeElement} into a {@link DeadCodeBlock}. This constructor
         * stores more information.
         * 
         * @param deadElement An element which was identified to be dead.
         * @param filePc The presence condition for the complete file.
         * @param reason The reason why this block is dead.
         */
        public DetailedDeadCodeBlock(@NonNull CodeElement<?> deadElement, @Nullable Formula filePc,
                @NonNull Reason reason) {
            super(deadElement, filePc);
            
            this.reason = reason;
        }
        
        /**
         * Returns the reason for the deadness of this block.
         * 
         * @return The reason why this block is dead.
         */
        public @NonNull Reason getReason() {
            return reason;
        }
        
        /**
         * Returns the reason for the deadness of this block as a description string.
         * 
         * @return A description of the reason why this block is dead.
         */
        @TableElement(name = "Reason", index = 5)
        public @NonNull String getReasonDescription() {
            return reason.getDescription();
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
                relevancyChecker = new FormulaRelevancyChecker(vm, true);
            }

            ProgressLogger progress = new ProgressLogger(notNull(getClass().getSimpleName()));

            SourceFile<?> file;
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
