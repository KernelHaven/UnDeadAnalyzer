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

import static net.ssehub.kernel_haven.util.logic.FormulaBuilder.and;
import static net.ssehub.kernel_haven.util.logic.FormulaBuilder.not;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.assertThat;

import java.io.File;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;

import org.junit.Assert;
import org.junit.Test;

import net.ssehub.kernel_haven.SetUpException;
import net.ssehub.kernel_haven.analysis.AnalysisComponent;
import net.ssehub.kernel_haven.build_model.BuildModel;
import net.ssehub.kernel_haven.code_model.CodeBlock;
import net.ssehub.kernel_haven.code_model.CodeElement;
import net.ssehub.kernel_haven.code_model.SourceFile;
import net.ssehub.kernel_haven.config.DefaultSettings;
import net.ssehub.kernel_haven.test_utils.TestAnalysisComponentProvider;
import net.ssehub.kernel_haven.test_utils.TestConfiguration;
import net.ssehub.kernel_haven.undead_analyzer.DeadCodeFinder.DeadCodeBlock;
import net.ssehub.kernel_haven.undead_analyzer.DeadCodeFinder.DetailedDeadCodeBlock;
import net.ssehub.kernel_haven.util.logic.Formula;
import net.ssehub.kernel_haven.util.logic.Variable;
import net.ssehub.kernel_haven.util.null_checks.NonNull;
import net.ssehub.kernel_haven.variability_model.VariabilityModel;
import net.ssehub.kernel_haven.variability_model.VariabilityModelDescriptor.ConstraintFileType;
import net.ssehub.kernel_haven.variability_model.VariabilityVariable;

/**
 * Tests for {@link DeadCodeFinder}.
 * 
 * @author El-Sharkawy
 *
 */
@SuppressWarnings("null")
public class DeadCodeFinderTest {

    protected static final File TESTDATA_DIR = new File(AllTests.TESTDATA_DIR, "deadCodeAnalysis");
    protected static final @NonNull File VM_FILE = new File(TESTDATA_DIR, "varModel.cnf");
    
    /**
     * Initializes a new {@link DeadCodeFinder} and its resources.
     * Variability Model:
     * <pre><code>
     * NOT(ALPHA) OR BETA
     * NOT(GAMMA)
     * </code></pre>
     * Build Model:
     * <pre>{@code 
     * file1.c -> filePc
     * }</pre>
     * 
     * @param element The code element to add to the source file.
     * @param filePc The presence condition of the file the block is in.
     * @param considerVmVarsOnly Whether to consider variables from the variability model only.
     * @param detailedAnalysis Whether detailed analysis is configured.
     * 
     * @return The created DeadCodeAnalysis.
     * 
     * @throws SetUpException unwanted.
     */
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
        DeadCodeFinder analyser = new DeadCodeFinder(tConfig, vmComponent, bmComponent, cmComponent);
        Assert.assertNotNull("Error: DeadCodeAnalysis not initialized.", analyser);
        
        return analyser;
    }
    

    /**
     * Initializes a new {@link DeadCodeFinder} and its resources.
     * Variability Model:
     * <pre><code>
     * NOT(ALPHA) OR BETA
     * NOT(GAMMA)
     * </code></pre>
     * Build Model:
     * <pre>{@code 
     * file1.c -> ALPHA
     * }</pre>
     * 
     * @param element The code element to add to the source file.
     * @param considerVmVarsOnly Whether to consider variables from the variability model only.
     * @param detailedAnalysis Whether detailed analysis is configured.
     * 
     * @return The created DeadCodeAnalysis.
     * 
     * @throws SetUpException unwanted.
     */
    public final DeadCodeFinder createComponent(CodeElement<?> element, boolean considerVmVarsOnly,
            boolean detailedAnalysis) throws SetUpException {
        return createComponent(element, new Variable("ALPHA"), considerVmVarsOnly, detailedAnalysis);
    }

    /**
     * Tests a file, which has no dead elements.
     * 
     * @throws SetUpException unwanted.
     */
    @Test
    public void testNoDeadElements() throws  SetUpException {
        assertThat(createComponent(null, false, false).getNextResult(), nullValue());
    }
    
    /**
     * Tests a file, which has a dead code element.
     * 
     * @throws SetUpException unwanted.
     */
    @Test
    public void testDeadElement() throws SetUpException {
        DeadCodeFinder analyser = createComponent(new CodeBlock(12, 15, new File("file"),
                not("BETA"), not("BETA")), false, false);
        
        DeadCodeBlock block = analyser.getNextResult();
        assertThat(block, notNullValue());

        assertThat(analyser.getNextResult(), nullValue());
    }
    
    /**
     * Tests a file, which has a dead code element.
     * 
     * @throws SetUpException unwanted.
     */
    @Test
    public void testDeadElementWithOnlyVmVars() throws SetUpException {
        DeadCodeFinder analyser = createComponent(new CodeBlock(12, 15, new File("file"),
                not("BETA"), not("BETA")), true, false);
        
        DeadCodeBlock block = analyser.getNextResult();
        assertThat(block, notNullValue());

        assertThat(analyser.getNextResult(), nullValue());
    }
    
    /**
     * Tests a file, which has no dead elements.
     * 
     * @throws SetUpException unwanted.
     */
    @Test
    public void testNoDeadElementsWithNonVmVars() throws SetUpException {
        assertThat(createComponent(new CodeBlock(12, 15, new File("file"),
                not("NON_VM"), not("NON_VM")), true, false).getNextResult(),
                nullValue());
    }
    
    /**
     * Tests the detailed analysis.
     * 
     * @throws SetUpException unwanted.
     */
    @Test
    public void testDetailedCppDead() throws SetUpException {
        /*
         * The CPP condition alone is not satisfiable: (ALPHA && !ALPHA)
         */
        DeadCodeFinder finder = createComponent(new CodeBlock(and("ALPHA", not("ALPHA"))), true, true);
        
        DetailedDeadCodeBlock result = (DetailedDeadCodeBlock) finder.getNextResult();
        
        assertThat(result, notNullValue());
        assertThat(result.getReason(), is(DeadCodeFinder.Reason.CPP_NOT_SATISFIABLE));
        
        assertThat(finder.getNextResult(), nullValue());
    }
    
    /**
     * Tests the detailed analysis.
     * 
     * @throws SetUpException unwanted.
     */
    @Test
    public void testDetailedCppAndVmDead() throws SetUpException {
        /*
         * The CPP condition and the VM are not satisfiable:
         * CPP: ALPHA && !BETA
         * VM: !ALPHA || BETA
         */
        DeadCodeFinder finder = createComponent(new CodeBlock(and("ALPHA", not("BETA"))), true, true);
        
        DetailedDeadCodeBlock result = (DetailedDeadCodeBlock) finder.getNextResult();
        
        assertThat(result, notNullValue());
        assertThat(result.getReason(), is(DeadCodeFinder.Reason.CPP_AND_VM_NOT_SATISFIABLE));
        
        assertThat(finder.getNextResult(), nullValue());
    }
    
    /**
     * Tests the detailed analysis.
     * 
     * @throws SetUpException unwanted.
     */
    @Test
    public void testDetailedCppAndFilePCDead() throws SetUpException {
        /*
         * The CPP condition and the filePc are dead:
         * CPP: !ALPHA
         * filePc: ALPHA
         */
        DeadCodeFinder finder = createComponent(new CodeBlock(not("ALPHA")), true, true);
        
        DetailedDeadCodeBlock result = (DetailedDeadCodeBlock) finder.getNextResult();
        
        assertThat(result, notNullValue());
        assertThat(result.getReason(), is(DeadCodeFinder.Reason.CPP_AND_FILE_PC_NOT_SATISFIABLE));
        
        assertThat(finder.getNextResult(), nullValue());
    }
    
    /**
     * Tests the detailed analysis.
     * 
     * @throws SetUpException unwanted.
     */
    @Test
    public void testDetailedCppAndFilePCAndVMDead() throws SetUpException {
        /*
         * The CPP condition and the filePc combined with the VM are dead:
         * CPP: !BETA
         * filePc: ALPHA
         *  -> ALPHA && !BETA
         * VM: !ALPHA || BETA
         */
        DeadCodeFinder finder = createComponent(new CodeBlock(not("BETA")), true, true);
        
        DetailedDeadCodeBlock result = (DetailedDeadCodeBlock) finder.getNextResult();
        
        assertThat(result, notNullValue());
        assertThat(result.getReason(), is(DeadCodeFinder.Reason.CPP_AND_FILE_PC_AND_VM_NOT_SATISFIABLE));
        
        assertThat(finder.getNextResult(), nullValue());
    }
    
    /**
     * Tests the detailed analysis.
     * 
     * @throws SetUpException unwanted.
     */
    @Test
    public void testDetailedFilePcDead() throws SetUpException {
        /*
         * The filePC alone is dead: BETA && !BETA
         */
        DeadCodeFinder finder = createComponent(new CodeBlock(new Variable("ALPHA")), and("BETA", not("BETA")),
                true, true);
        
        DetailedDeadCodeBlock result = (DetailedDeadCodeBlock) finder.getNextResult();
        
        assertThat(result, notNullValue());
        assertThat(result.getReason(), is(DeadCodeFinder.Reason.FILE_PC_NOT_SATISFIABLE));
        
        assertThat(finder.getNextResult(), nullValue());
    }
    
    /**
     * Tests the detailed analysis.
     * 
     * @throws SetUpException unwanted.
     */
    @Test
    public void testDetailedFilePcAndVmDead() throws SetUpException {
        /*
         * The filePC combined with the variability model is dead:
         * filePC: ALPHA && !BETA
         * VM: !ALPHA || BETA
         */
        DeadCodeFinder finder = createComponent(new CodeBlock(new Variable("ALPHA")), and("ALPHA", not("BETA")),
                true, true);
        
        DetailedDeadCodeBlock result = (DetailedDeadCodeBlock) finder.getNextResult();
        
        assertThat(result, notNullValue());
        assertThat(result.getReason(), is(DeadCodeFinder.Reason.FILE_PC_AND_VM_NOT_SATISFIABLE));
        
        assertThat(finder.getNextResult(), nullValue());
    }
    
    /**
     * Tests the detailed analysis.
     * 
     * @throws SetUpException unwanted.
     */
    @Test
    public void testDetailedNotDead() throws SetUpException {
        /*
         * The block is not dead.
         */
        DeadCodeFinder finder = createComponent(new CodeBlock(new Variable("ALPHA")), true, true);
        
        assertThat(finder.getNextResult(), nullValue());
    }
    
}
