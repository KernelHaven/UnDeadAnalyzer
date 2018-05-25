package net.ssehub.kernel_haven.undead_analyzer;

import net.ssehub.kernel_haven.SetUpException;
import net.ssehub.kernel_haven.analysis.AnalysisComponent;
import net.ssehub.kernel_haven.analysis.PipelineAnalysis;
import net.ssehub.kernel_haven.config.Configuration;
import net.ssehub.kernel_haven.util.null_checks.NonNull;

/**
 * Creates a pipeline for threaded dead code analysis.
 *
 * @author Moritz
 */
public class ThreadedDeadCodeAnalysis extends PipelineAnalysis {

    /**
     * Creates this analysis.
     * 
     * @param config
     *            The global configuration.
     */
    public ThreadedDeadCodeAnalysis(@NonNull Configuration config) {
        super(config);
    }

    @Override
    protected @NonNull AnalysisComponent<?> createPipeline() throws SetUpException {
        return new ThreadedDeadCodeFinder(config, getVmComponent(), getBmComponent(), getCmComponent());
    }

}
