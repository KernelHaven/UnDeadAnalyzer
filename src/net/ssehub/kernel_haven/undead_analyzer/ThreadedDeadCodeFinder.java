package net.ssehub.kernel_haven.undead_analyzer;

import static net.ssehub.kernel_haven.util.null_checks.NullHelpers.notNull;

import java.util.List;

import net.ssehub.kernel_haven.SetUpException;
import net.ssehub.kernel_haven.analysis.AnalysisComponent;
import net.ssehub.kernel_haven.build_model.BuildModel;
import net.ssehub.kernel_haven.cnf.VmToCnfConverter;
import net.ssehub.kernel_haven.code_model.SourceFile;
import net.ssehub.kernel_haven.config.Configuration;
import net.ssehub.kernel_haven.config.DefaultSettings;
import net.ssehub.kernel_haven.config.Setting;
import net.ssehub.kernel_haven.util.FormatException;
import net.ssehub.kernel_haven.util.OrderPreservingParallelizer;
import net.ssehub.kernel_haven.util.ProgressLogger;
import net.ssehub.kernel_haven.util.null_checks.NonNull;
import net.ssehub.kernel_haven.variability_model.VariabilityModel;

/**
 * A dead code finder that uses parallelization for faster analysis.
 * 
 * @author Adam
 */
public class ThreadedDeadCodeFinder extends DeadCodeFinder {

    public static final @NonNull Setting<@NonNull Integer> NUMBER_OF_OF_THREADS = new Setting<>(
            "analysis.undead.threads", Setting.Type.INTEGER, true, "2",
            "Number of threads to use for the " + ThreadedDeadCodeFinder.class.getName() + ". Must be >= 1.");

    private int numThreads;

    /**
     * Creates a dead code analysis.
     * 
     * @param config      The user configuration; not used.
     * @param vmComponent The component to provide the variability model.
     * @param bmComponent The component to provide the build model.
     * @param cmComponent The component to provide the code model.
     * @throws SetUpException if not configured correctly.
     */
    public ThreadedDeadCodeFinder(@NonNull Configuration config,
            @NonNull AnalysisComponent<VariabilityModel> vmComponent,
            @NonNull AnalysisComponent<BuildModel> bmComponent, @NonNull AnalysisComponent<SourceFile<?>> cmComponent)
            throws SetUpException {

        super(config, vmComponent, bmComponent, cmComponent);

        config.registerSetting(NUMBER_OF_OF_THREADS);
        numThreads = config.getValue(NUMBER_OF_OF_THREADS);
        if (numThreads < 1) {
            throw new SetUpException(NUMBER_OF_OF_THREADS.getKey() + " is lower than 1");
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

            if (this.usageOfVmVars != null && this.usageOfVmVars != DefaultSettings.USAGE_OF_VM_VARS.ALL_ELEMENTS) {
                relevancyChecker = new FormulaRelevancyChecker(vm, true);
            }

            ProgressLogger progress = new ProgressLogger(notNull(getClass().getSimpleName()));

            OrderPreservingParallelizer<SourceFile<?>, List<@NonNull DeadCodeBlock>> parallelizer = 
                    new OrderPreservingParallelizer<>(
                    this::findDeadCodeBlocks, (deadBlocks) -> {
                        for (DeadCodeBlock block : deadBlocks) {
                            addResult(block);
                        }

                        progress.processedOne();

                    }, numThreads);

            SourceFile<?> file;
            while ((file = cmComponent.getNextResult()) != null) {
                parallelizer.add(file);
            }

            parallelizer.end();
            parallelizer.join();

            progress.close();

        } catch (FormatException e) {
            LOGGER.logException("Invalid variability model", e);
        }

    }

    @Override
    public @NonNull String getResultName() {
        return "Dead Code Blocks";
    }

}
