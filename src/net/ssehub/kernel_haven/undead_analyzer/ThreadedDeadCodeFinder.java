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

import java.util.List;

import net.ssehub.kernel_haven.SetUpException;
import net.ssehub.kernel_haven.analysis.AnalysisComponent;
import net.ssehub.kernel_haven.build_model.BuildModel;
import net.ssehub.kernel_haven.cnf.VmToCnfConverter;
import net.ssehub.kernel_haven.code_model.SourceFile;
import net.ssehub.kernel_haven.config.Configuration;
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

            if (considerVmVarsOnly) {
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
