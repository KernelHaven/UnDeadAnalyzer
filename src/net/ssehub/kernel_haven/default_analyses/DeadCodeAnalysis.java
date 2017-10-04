package net.ssehub.kernel_haven.default_analyses;

import java.io.File;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.ssehub.kernel_haven.SetUpException;
import net.ssehub.kernel_haven.analysis.AbstractAnalysis;
import net.ssehub.kernel_haven.build_model.BuildModel;
import net.ssehub.kernel_haven.cnf.Cnf;
import net.ssehub.kernel_haven.cnf.ConverterException;
import net.ssehub.kernel_haven.cnf.FormulaToCnfConverterFactory;
import net.ssehub.kernel_haven.cnf.FormulaToCnfConverterFactory.Strategy;
import net.ssehub.kernel_haven.cnf.IFormulaToCnfConverter;
import net.ssehub.kernel_haven.cnf.SatSolver;
import net.ssehub.kernel_haven.cnf.SolverException;
import net.ssehub.kernel_haven.cnf.VmToCnfConverter;
import net.ssehub.kernel_haven.code_model.CodeElement;
import net.ssehub.kernel_haven.code_model.SourceFile;
import net.ssehub.kernel_haven.config.Configuration;
import net.ssehub.kernel_haven.util.BlockingQueue;
import net.ssehub.kernel_haven.util.CodeExtractorException;
import net.ssehub.kernel_haven.util.ExtractorException;
import net.ssehub.kernel_haven.util.FormatException;
import net.ssehub.kernel_haven.util.logic.Conjunction;
import net.ssehub.kernel_haven.util.logic.Formula;
import net.ssehub.kernel_haven.variability_model.VariabilityModel;

/**
 * A simple implementation for dead code detection.
 * 
 * @author Adam
 */
public class DeadCodeAnalysis extends AbstractAnalysis {

    private SatSolver solver;
    
    private IFormulaToCnfConverter converter;
    
    private List<DeadCodeBlock> result;
    
    private Map<Formula, Boolean> satCache;
    
    /**
     * Creates a dead code analysis.
     *  
     * @param config The user configuration; not used.
     */
    public DeadCodeAnalysis(Configuration config) {
        super(config);
        satCache = new HashMap<>(10000);
    }
    
    /**
     * Finds dead code blocks.
     * 
     * @param vm The variability model.
     * @param bm The build model.
     * @param cm The the code model.
     * @return The list of dead code blocks.
     * 
     * @throws FormatException If the {@link VariabilityModel} has an invalid constraint model file.
     * @throws ConverterException If converting PCs to CNF fails.
     * @throws SolverException If the solver fails.
     * @throws ExtractorException If the code provider throws an exception.
     */
    public List<DeadCodeBlock> findDeadCodeBlocks(VariabilityModel vm, BuildModel bm, BlockingQueue<SourceFile> cm)
            throws FormatException, ConverterException, SolverException, ExtractorException {
        
        result = new ArrayList<>();
        
        Cnf vmCnf = new VmToCnfConverter().convertVmToCnf(vm);
        solver = new SatSolver(vmCnf);
        
        converter = FormulaToCnfConverterFactory.create(Strategy.RECURISVE_REPLACING);
        
        SourceFile sourceFile;
        while ((sourceFile = cm.get()) != null) {
            satCache.clear(); // clear after each source file, so that it doesn't get too big
            
            Formula filePc = bm.getPc(sourceFile.getPath());
            
            if (filePc == null) {
                LOGGER.logInfo("Skipping " + sourceFile.getPath() + " because it has no build PC");
                continue;
            }
            
            LOGGER.logInfo("Running for file " + sourceFile.getPath());
            LOGGER.logDebug("File PC: " + filePc);
            
            
            for (CodeElement element : sourceFile) {
                checkElement(element, filePc, sourceFile);
            }
        }
        
        return result;
    }
    
    /**
     * Checks whether the given formula is satisfiable with the variability model. Internally, this method has a cache
     * to speed up when the same formula is passed to it several times.
     * 
     * @param pc The formula to check.
     * @return Whether the formula is satisfiable with the variability model.
     * 
     * @throws ConverterException If the conversion to CNF fails.
     * @throws SolverException If the SAT-solver fails.
     */
    private boolean isSat(Formula pc) throws ConverterException, SolverException {
        Boolean sat = satCache.get(pc);
        
        if (sat == null) {
            Cnf pcCnf = converter.convert(pc);
            
            String[] cnfLines = pcCnf.toString().split("\n");
            String[] output = new String[cnfLines.length + 1];
            System.arraycopy(cnfLines, 0, output, 1, cnfLines.length);
            output[0] = "PcCnf: ";
            LOGGER.logDebug(output);
            
            sat = solver.isSatisfiable(pcCnf);
            satCache.put(pc, sat);
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
     * 
     * @throws ConverterException If converting the formula to CNF fails.
     * @throws SolverException If solving the CNF fails.
     */
    private void checkElement(CodeElement element, Formula filePc, SourceFile sourceFile)
            throws ConverterException, SolverException {
        
        Formula pc = new Conjunction(element.getPresenceCondition(), filePc);

        if (!isSat(pc)) {
            DeadCodeBlock deadBlock = new DeadCodeBlock(element, filePc);
            LOGGER.logInfo("Found dead block: " + deadBlock);
            result.add(deadBlock);
        }
        
        for (CodeElement child : element.iterateNestedElements()) {
            checkElement(child, filePc, sourceFile);
        }
    }

    /**
     * A dead code block.
     */
    public static class DeadCodeBlock {
        
        private File sourceFile;
        
        private int startLine;

        private int endLinie;
        
        private Formula presenceCondition;
        private Formula filePc;
        
        /**
         * Creates a dead code block.
         * @param sourceFile The source file.
         * @param line The line of the element.
         */
        public DeadCodeBlock(File sourceFile, int line) {
            this.sourceFile = sourceFile;
            this.startLine = line;
            this.endLinie = 0;
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
        public DeadCodeBlock(CodeElement deadElement, Formula filePc) {
            this(deadElement.getSourceFile(), deadElement.getLineStart());
            this.endLinie = deadElement.getLineEnd();
            this.presenceCondition = deadElement.getPresenceCondition();
            this.filePc = filePc;
        }
        
        /**
         * Converts this block to a CSV line using the specified separator.
         * @param separator Specification how to separate lines, if unsure use &#59;
         * @return This block in CSV representation.
         */
        private String toCSVLine(String separator) {
            StringBuffer result = new StringBuffer();
            result.append(sourceFile.getPath());
            result.append(separator);
            if (null != filePc) {
                result.append(filePc.toString());
            }
            result.append(separator);
            result.append(startLine);
            result.append(separator);
            if (0 != endLinie) {
                result.append(endLinie);
            }
            result.append(separator);
            if (null != presenceCondition) {
                result.append(presenceCondition.toString());
            }            
            
            return result.toString();
        }
        
        @Override
        public String toString() {
            return toCSVLine(";");
        }
        
    }
    
    @Override
    public void run() {
        PrintStream resultStream = createResultStream("dead_blocks.csv");
        
        try {
            vmProvider.start();
            bmProvider.start();
            cmProvider.start();
            
        
            VariabilityModel vm = vmProvider.getResult();
            BuildModel bm = bmProvider.getResult();
            
            LOGGER.logInfo("Start finding dead code blocks");

            List<DeadCodeBlock> result = findDeadCodeBlocks(vm, bm, cmProvider.getResultQueue());

            LOGGER.logInfo("Found " + result.size() + " dead code blocks");
            
            // Create header
            resultStream.println("File;Presence Condition of File;Start Line;End Line;Presence Condition of Block");
            
            for (DeadCodeBlock block : result) {
                resultStream.println(block.toString());
            }
            
            CodeExtractorException exception;
            List<String> lines = new ArrayList<>();
            lines.add("Couldn't parse the following files:");
            while ((exception = (CodeExtractorException) cmProvider.getNextException()) != null) {
                String message;
                if (exception.getCause() != null) {
                    message = exception.getCause().getMessage();
                } else {
                    message = exception.getMessage();
                }
                lines.add("* " + exception.getCausingFile().getPath() + ": " + message);
            }
            lines.set(0, "Couldn't parse the following " + (lines.size() - 1) + " files:");
            LOGGER.logInfo(lines.toArray(new String[0]));
            
        } catch (ExtractorException e) {
            LOGGER.logException("Error extracting model", e);
        } catch (FormatException e) {
            LOGGER.logException("Error converting the variability model to CNF", e);
        } catch (SolverException e) {
            LOGGER.logException("Error solving variability model", e);
        } catch (ConverterException e) {
            LOGGER.logException("Error converting to CNF", e);
        } catch (SetUpException e) {
            LOGGER.logException("Error in Setup", e);
        }
        
        resultStream.close();
    }
    
}
