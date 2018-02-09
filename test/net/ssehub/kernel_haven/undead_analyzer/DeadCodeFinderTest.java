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
package net.ssehub.kernel_haven.undead_analyzer;

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
import net.ssehub.kernel_haven.test_utils.TestAnalysisComponentProvider;
import net.ssehub.kernel_haven.test_utils.TestConfiguration;
import net.ssehub.kernel_haven.undead_analyzer.DeadCodeFinder.DeadCodeBlock;
import net.ssehub.kernel_haven.util.Logger;
import net.ssehub.kernel_haven.util.logic.Negation;
import net.ssehub.kernel_haven.util.logic.Variable;
import net.ssehub.kernel_haven.util.null_checks.NonNull;
import net.ssehub.kernel_haven.variability_model.VariabilityModel;
import net.ssehub.kernel_haven.variability_model.VariabilityVariable;

/**
 * Tests for {@link DeadCodeFinder}.
 * 
 * @author El-Sharkawy
 *
 */
@SuppressWarnings("null")
public class DeadCodeFinderTest {

    private static final File TESTDATA_DIR = new File(AllTests.TESTDATA_DIR, "deadCodeAnalysis");
    private static final @NonNull File VM_FILE = new File(TESTDATA_DIR, "varModel.cnf");
    
    /**
     * Initializes a new {@link DeadCodeFinder} and its resources.
     * Variability Model:
     * <pre><code>
     * NOT(ALPHA) OR BETA
     * GAMMA
     * </code></pre>
     * Build Model:
     * <pre><code>
     * file1.c -> ALPHA
     * </code></pre>
     * 
     * @param element The code element to add to the source file.
     * @return The created DeadCodeAnalysis.
     * 
     * @throws SetUpException unwanted.
     */
    public DeadCodeFinder createComponent(CodeElement element) throws SetUpException {
        // Generate configuration
        @NonNull TestConfiguration tConfig = null;
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
        VariabilityModel vm = new VariabilityModel(VM_FILE, variables);
        Assert.assertNotNull("Error: VariabilityModel not initialized.", vm);
        AnalysisComponent<VariabilityModel> vmComponent = new TestAnalysisComponentProvider<VariabilityModel>(vm);
        
        // Create virtual files
        File file1 = new File(TESTDATA_DIR, "file1.c");
        SourceFile sourceFile1 = new SourceFile(file1);
        if (element != null) {
            sourceFile1.addElement(element);
        }
        AnalysisComponent<SourceFile> cmComponent = new TestAnalysisComponentProvider<SourceFile>(sourceFile1);
        
        // Create virtual build model
        BuildModel bm = new BuildModel();
        bm.add(file1, new Variable(alpha.getName()));
        AnalysisComponent<BuildModel> bmComponent = new TestAnalysisComponentProvider<BuildModel>(bm);
        
        
        // Create fresh analysis instance
        DeadCodeFinder analyser = new DeadCodeFinder(tConfig, vmComponent, bmComponent, cmComponent);
        Assert.assertNotNull("Error: DeadCodeAnalysis not initialized.", analyser);
        
        return analyser;
    }

    /**
     * Tests a file, which has no dead elements.
     * 
     * @throws SetUpException unwanted.
     */
    @Test
    public void testNoDeadElements() throws  SetUpException {
        assertThat(createComponent(null).getNextResult(), nullValue());
    }
    
    /**
     * Tests a file, which has a dead code element.
     * 
     * @throws SetUpException unwanted.
     */
    @Test
    public void testDeadElement() throws SetUpException {
        DeadCodeFinder analyser = createComponent(new CodeBlock(12, 15, new File("file"),
                new Negation(new Variable("BETA")), new Negation(new Variable("BETA"))));
        
        DeadCodeBlock block = analyser.getNextResult();
        assertThat(block, notNullValue());
        System.out.println(block.toString());

        assertThat(analyser.getNextResult(), nullValue());
        
    }
}
