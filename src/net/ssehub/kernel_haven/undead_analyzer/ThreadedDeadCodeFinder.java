package net.ssehub.kernel_haven.undead_analyzer;

import static net.ssehub.kernel_haven.util.null_checks.NullHelpers.notNull;

import java.util.List;

import net.ssehub.kernel_haven.analysis.AnalysisComponent;
import net.ssehub.kernel_haven.build_model.BuildModel;
import net.ssehub.kernel_haven.cnf.VmToCnfConverter;
import net.ssehub.kernel_haven.code_model.SourceFile;
import net.ssehub.kernel_haven.config.Configuration;
import net.ssehub.kernel_haven.util.FormatException;
import net.ssehub.kernel_haven.util.OrderPreservingParallelizer;
import net.ssehub.kernel_haven.util.null_checks.NonNull;
import net.ssehub.kernel_haven.variability_model.VariabilityModel;

/**
 * A dead code finder that uses parallelization for faster analysis.
 * 
 * @author Adam
 */
public class ThreadedDeadCodeFinder extends DeadCodeFinder {

    private static final int NUM_THREADS = 8;
    
    /**
     * Creates a dead code analysis.
     *  
     * @param config The user configuration; not used.
     * @param vmComponent The component to provide the variability model.
     * @param bmComponent The component to provide the build model.
     * @param cmComponent The component to provide the code model.
     */
    public ThreadedDeadCodeFinder(@NonNull Configuration config,
            @NonNull AnalysisComponent<VariabilityModel> vmComponent,
            @NonNull AnalysisComponent<BuildModel> bmComponent, @NonNull AnalysisComponent<SourceFile> cmComponent) {
        
        super(config, vmComponent, bmComponent, cmComponent);
        
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
            
            OrderPreservingParallelizer<SourceFile, List<@NonNull DeadCodeBlock>> parallelizer
                = new OrderPreservingParallelizer<>(this::findDeadCodeBlocks, (deadBlocks) -> {
                    for (DeadCodeBlock block : deadBlocks) {
                        addResult(block);
                    }
                }, NUM_THREADS);
            
            SourceFile file;
            while ((file = cmComponent.getNextResult()) != null) {
                parallelizer.add(file);
            }
            

            parallelizer.end();
            parallelizer.join();
            
        } catch (FormatException e) {
            LOGGER.logException("Invalid variability model", e);
        }
        
    }

    @Override
    public @NonNull String getResultName() {
        return "Dead Code Blocks";
    }
    
}
