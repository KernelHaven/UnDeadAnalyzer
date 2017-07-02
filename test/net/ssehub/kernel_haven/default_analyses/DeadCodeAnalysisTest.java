/*
 * Copyright 2017 University of Hildesheim, Software Systems Engineering
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.ssehub.kernel_haven.default_analyses;

import java.io.File;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import net.ssehub.kernel_haven.SetUpException;
import net.ssehub.kernel_haven.TestConfiguration;
import net.ssehub.kernel_haven.build_model.BuildModel;
import net.ssehub.kernel_haven.cnf.ConverterException;
import net.ssehub.kernel_haven.cnf.SolverException;
import net.ssehub.kernel_haven.code_model.CodeModelCacheTest;
import net.ssehub.kernel_haven.code_model.SourceFile;
import net.ssehub.kernel_haven.default_analyses.DeadCodeAnalysis.DeadCodeBlock;
import net.ssehub.kernel_haven.util.BlockingQueue;
import net.ssehub.kernel_haven.util.ExtractorException;
import net.ssehub.kernel_haven.util.FormatException;
import net.ssehub.kernel_haven.util.Logger;
import net.ssehub.kernel_haven.util.logic.Negation;
import net.ssehub.kernel_haven.util.logic.Variable;
import net.ssehub.kernel_haven.variability_model.VariabilityModel;
import net.ssehub.kernel_haven.variability_model.VariabilityVariable;

/**
 * Tests for {@link DeadCodeAnalysis}.
 * 
 * @author El-Sharkawy
 *
 */
public class DeadCodeAnalysisTest {

    private static final File TESTDATA_DIR = new File(AllTests.TESTDATA_DIR, "deadCodeAnalysis");
    private static final File VM_FILE = new File(TESTDATA_DIR, "varModel.cnf");
    
    private DeadCodeAnalysis analyser;
    private VariabilityModel vm;
    private BlockingQueue<SourceFile> cm;
    private SourceFile sFile1;
    private BuildModel bm;

    /**
     * Pre-condition for all tests in this test class: Initializes a new {@link DeadCodeAnalysis} and its resources.
     * Variability Model:
     * <pre><code>
     * NOT(ALPHA) OR BETA
     * GAMMA
     * </code></pre>
     * Build Model:
     * <pre><code>
     * file1.c -> ALPHA
     * </code></pre>
     */
    @Before
    public void setUp() {
        // Generate configuration
        TestConfiguration tConfig = null;
        Properties config = new Properties();
        try {
            tConfig = new TestConfiguration(config);
        } catch (SetUpException e) {
            Assert.fail("Could not generate test configuration: " + e.getMessage());
        }
        
        Logger.init();
        
        // Load variability model
        Set<VariabilityVariable> variables = new HashSet<>();
        VariabilityVariable alpha = new VariabilityVariable("ALPHA", "bool", 1);
        variables.add(alpha);
        variables.add(new VariabilityVariable("BETA", "bool", 2));
        variables.add(new VariabilityVariable("GAMMA", "bool", 3));
        vm = new VariabilityModel(VM_FILE, variables);
        Assert.assertNotNull("Error: VariabilityModel not initialized.", vm);
        
        // Create virtual files
        cm = new BlockingQueue<>();
        File file1 = new File(TESTDATA_DIR, "file1.c");
        sFile1 = new SourceFile(file1);
        cm.add(sFile1);
        cm.end();
        
        // Create virtual build model
        bm = new BuildModel();
        bm.add(file1, new Variable(alpha.getName()));
        
        // Create fresh analysis instance
        analyser = new DeadCodeAnalysis(tConfig);
        Assert.assertNotNull("Error: DeadCodeAnalysis not initialized.", analyser);
    }

    /**
     * Tests a file, which has no dead blocks.
     * 
     * @throws FormatException unwanted.
     * @throws ConverterException unwanted.
     * @throws SolverException unwanted.
     * @throws ExtractorException unwanted.
     */
    @Test
    public void testNoDeadBlock() throws FormatException, ConverterException, SolverException, ExtractorException {
        List<DeadCodeBlock> deadBlocks = analyser.findDeadCodeBlocks(vm, bm, cm);
        Assert.assertTrue(deadBlocks.isEmpty());
    }
    
    /**
     * Tests a file, which has a dead code block.
     * 
     * @throws FormatException unwanted.
     * @throws ConverterException unwanted.
     * @throws SolverException unwanted.
     * @throws ExtractorException unwanted.
     */
    @Test
    public void testDeadBlock() throws FormatException, ConverterException, SolverException, ExtractorException {
        sFile1.addBlock(new CodeModelCacheTest.PseudoBlock(12, 15,
                new Negation(new Variable("BETA")), new Negation(new Variable("BETA"))));
        List<DeadCodeBlock> deadBlocks = analyser.findDeadCodeBlocks(vm, bm, cm);
        Assert.assertFalse(deadBlocks.isEmpty());
        Assert.assertEquals(1, deadBlocks.size());
        System.out.println(deadBlocks.get(0).toString());
    }
}
