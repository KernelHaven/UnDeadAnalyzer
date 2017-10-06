package net.ssehub.kernel_haven.default_analyses;

import net.ssehub.kernel_haven.SetUpException;
import net.ssehub.kernel_haven.analysis.AnalysisComponent;
import net.ssehub.kernel_haven.analysis.PipelineAnalysis;
import net.ssehub.kernel_haven.config.Configuration;

/**
 * Creates a pipeline for dead code analysis.
 *
 * @author Adam
 */
public class DeadCodeAnalysis extends PipelineAnalysis {

    /**
     * Creates this analysis.
     * 
     * @param config The global configuration.
     */
    public DeadCodeAnalysis(Configuration config) {
        super(config);
    }

    @Override
    protected AnalysisComponent<?> createPipeline() throws SetUpException {
        return new DeadCodeFinder(config, getVmComponent(), getBmComponent(), getCmComponent());
    }

}
