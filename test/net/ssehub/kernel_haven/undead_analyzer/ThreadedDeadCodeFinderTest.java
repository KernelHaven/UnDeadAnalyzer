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

import java.io.File;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;

import org.junit.Assert;
import org.junit.Test;

import net.ssehub.kernel_haven.SetUpException;
import net.ssehub.kernel_haven.analysis.AnalysisComponent;
import net.ssehub.kernel_haven.build_model.BuildModel;
import net.ssehub.kernel_haven.code_model.CodeElement;
import net.ssehub.kernel_haven.code_model.SourceFile;
import net.ssehub.kernel_haven.config.DefaultSettings;
import net.ssehub.kernel_haven.test_utils.TestAnalysisComponentProvider;
import net.ssehub.kernel_haven.test_utils.TestConfiguration;
import net.ssehub.kernel_haven.util.logic.Formula;
import net.ssehub.kernel_haven.util.null_checks.NonNull;
import net.ssehub.kernel_haven.variability_model.VariabilityModel;
import net.ssehub.kernel_haven.variability_model.VariabilityModelDescriptor.ConstraintFileType;
import net.ssehub.kernel_haven.variability_model.VariabilityVariable;

/**
 * Tests the {@link ThreadedDeadCodeFinder}.
 * 
 * @author Adam
 */
public class ThreadedDeadCodeFinderTest extends DeadCodeFinderTest {

    @Override
    @SuppressWarnings("null")
    public DeadCodeFinder createComponent(CodeElement<?> element, @NonNull Formula filePc, boolean considerVmVarsOnly,
            boolean detailedAnalysis) throws SetUpException {
        // Generate configuration
        @NonNull TestConfiguration tConfig = null;
        Properties config = new Properties();
        try {
            tConfig = new TestConfiguration(config);
        } catch (SetUpException e) {
            Assert.fail("Could not generate test configuration: " + e.getMessage());
        }
        tConfig.setValue(DefaultSettings.ANALYSIS_USE_VARMODEL_VARIABLES_ONLY, considerVmVarsOnly);
        tConfig.registerSetting(DeadCodeFinder.DETAILED_SETTING);
        tConfig.setValue(DeadCodeFinder.DETAILED_SETTING, detailedAnalysis);
        
        // Load variability model
        Set<VariabilityVariable> variables = new HashSet<>();
        VariabilityVariable alpha = new VariabilityVariable("ALPHA", "bool", 1);
        variables.add(alpha);
        variables.add(new VariabilityVariable("BETA", "bool", 2));
        variables.add(new VariabilityVariable("GAMMA", "bool", 3));
        VariabilityModel vm = new VariabilityModel(VM_FILE, variables);
        vm.getDescriptor().setConstraintFileType(ConstraintFileType.DIMACS);
        Assert.assertNotNull("Error: VariabilityModel not initialized.", vm);
        AnalysisComponent<VariabilityModel> vmComponent = new TestAnalysisComponentProvider<VariabilityModel>(vm);
        
        // Create virtual files
        File file1 = new File(TESTDATA_DIR, "file1.c");
        SourceFile<CodeElement<?>> sourceFile1 = new SourceFile<>(file1);
        if (element != null) {
            sourceFile1.addElement(element);
        }
        AnalysisComponent<SourceFile<?>> cmComponent = new TestAnalysisComponentProvider<SourceFile<?>>(sourceFile1);
        
        // Create virtual build model
        BuildModel bm = new BuildModel();
        bm.add(file1, filePc);
        AnalysisComponent<BuildModel> bmComponent = new TestAnalysisComponentProvider<BuildModel>(bm);
        
        
        // Create fresh analysis instance
        DeadCodeFinder analyser = new ThreadedDeadCodeFinder(tConfig, vmComponent, bmComponent, cmComponent);
        Assert.assertNotNull("Error: DeadCodeAnalysis not initialized.", analyser);
        
        return analyser;
    }
    
    /**
     * Tests that setting an invalid number of threads throws an exception.
     * 
     * @throws SetUpException wanted.
     */
    @Test(expected = SetUpException.class)
    @SuppressWarnings("null")
    public void testInvalidNumberOfThreads() throws SetUpException {
        TestConfiguration config = new TestConfiguration(new Properties());
        config.registerSetting(ThreadedDeadCodeFinder.NUMBER_OF_OF_THREADS);
        config.setValue(ThreadedDeadCodeFinder.NUMBER_OF_OF_THREADS, 0);
        
        new ThreadedDeadCodeFinder(config, null, null, null);
    }
    
}
