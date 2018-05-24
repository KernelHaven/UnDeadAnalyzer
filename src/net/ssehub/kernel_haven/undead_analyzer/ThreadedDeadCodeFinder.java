package net.ssehub.kernel_haven.undead_analyzer;

import static net.ssehub.kernel_haven.util.null_checks.NullHelpers.notNull;

import java.util.List;

import net.ssehub.kernel_haven.SetUpException;
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

    
    private @NonNull Configuration config;

	/**
     * Creates a dead code analysis.
     *  
     * @param config The user configuration; not used.
     * @param vmComponent The component to provide the variability model.
     * @param bmComponent The component to provide the build model.
     * @param cmComponent The component to provide the code model.
	 * @throws SetUpException 
     */
    public ThreadedDeadCodeFinder(@NonNull Configuration config,
            @NonNull AnalysisComponent<VariabilityModel> vmComponent,
            @NonNull AnalysisComponent<BuildModel> bmComponent, @NonNull AnalysisComponent<SourceFile> cmComponent) throws SetUpException {
        super(config, vmComponent, bmComponent, cmComponent);
        
    	this.config = config;
    	UndeadSettings.registerAllSettings(config);
        
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
            
            int threadCount = config.getValue(UndeadSettings.NUMBER_OF_OF_THREADS);
            if (threadCount < 1) {
            	LOGGER.logWarning("Thread number was <1, falling back to using 1 thread.");
            	threadCount = 1;
            }
            
            OrderPreservingParallelizer<SourceFile, List<@NonNull DeadCodeBlock>> parallelizer
                = new OrderPreservingParallelizer<>(this::findDeadCodeBlocks, (deadBlocks) -> {
                    for (DeadCodeBlock block : deadBlocks) {
                        addResult(block);
                    }
                }, threadCount);
            
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
