package net.ssehub.kernel_haven.undead_analyzer;

import static net.ssehub.kernel_haven.util.logic.FormulaBuilder.and;
import static net.ssehub.kernel_haven.util.logic.FormulaBuilder.not;
import static net.ssehub.kernel_haven.util.logic.FormulaBuilder.or;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import java.io.File;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import org.junit.Test;

import net.ssehub.kernel_haven.SetUpException;
import net.ssehub.kernel_haven.build_model.BuildModel;
import net.ssehub.kernel_haven.code_model.CodeBlock;
import net.ssehub.kernel_haven.code_model.SourceFile;
import net.ssehub.kernel_haven.test_utils.AnalysisComponentExecuter;
import net.ssehub.kernel_haven.test_utils.TestConfiguration;
import net.ssehub.kernel_haven.undead_analyzer.MissingVariablesFinder.Analysis;
import net.ssehub.kernel_haven.util.logic.Variable;
import net.ssehub.kernel_haven.util.null_checks.NonNull;
import net.ssehub.kernel_haven.variability_model.VariabilityModel;
import net.ssehub.kernel_haven.variability_model.VariabilityVariable;

/**
 * Tests the {@link MissingVariablesFinder}.
 * 
 * @author Adam
 */
public class MissingVariablesFinderTest {

    /**
     * A {@link VariabilityModel} with three variables:
     * <ul>
     *  <li>CONFIG_A (bool)</li>
     *  <li>CONFIG_B (bool)</li>
     *  <li>CONFIG_C (tristate)</li>
     * </ul>
     * .
     */
    private static final @NonNull VariabilityModel VAR_MODEL;
   
    static {
        Set<@NonNull VariabilityVariable> vars = new HashSet<>();
        vars.add(new VariabilityVariable("CONFIG_A", "bool"));
        vars.add(new VariabilityVariable("CONFIG_B", "bool"));
        vars.add(new VariabilityVariable("CONFIG_C", "tristate"));
        VAR_MODEL = new VariabilityModel(new File(""), vars);
    }
    
    /**
     * Runs the {@link MissingVariablesFinder} component.
     * 
     * @param type The type of analysis to run.
     * @param varModel The {@link VariabilityModel} to use.
     * @param buildModel The {@link BuildModel} to use.
     * @param codeElements The code model to use.
     * 
     * @return The result created by the component.
     * 
     * @throws SetUpException If setting up fails.
     */
    private @NonNull List<String> run(MissingVariablesFinder.@NonNull Analysis type,
            @NonNull VariabilityModel varModel, @NonNull BuildModel buildModel, SourceFile<?> ... codeElements)
            throws SetUpException {

        TestConfiguration config = new TestConfiguration(new Properties());
        config.registerSetting(MissingVariablesFinder.MISSING_TYPE);
        config.setValue(MissingVariablesFinder.MISSING_TYPE, type);
        
        List<String> result = AnalysisComponentExecuter.executeComponent(MissingVariablesFinder.class, config,
                new VariabilityModel[] {varModel}, new BuildModel[] {buildModel}, codeElements);
        
        return result;
    }
    
    /**
     * Tests the {@link Analysis#USED_BUT_NOT_DEFINED} analysis.
     * 
     * @throws SetUpException unwanted.
     */
    @Test
    public void testUsedButNotDefined() throws SetUpException {
        BuildModel bm = new BuildModel();
        bm.add(new File("test.c"), and("CONFIG_B", "CONFIG_D"));
        
        CodeBlock cb = new CodeBlock(or("CONFIG_A", "CONFIG_E"));
        cb.addNestedElement(new CodeBlock(and("CONFIG_D", not("CONFIG_F"))));
        
        SourceFile<CodeBlock> sf = new SourceFile<>(new File("test.c"));
        sf.addElement(cb);
        
        List<String> result = run(Analysis.USED_BUT_NOT_DEFINED, VAR_MODEL, bm, sf);
        
        assertThat(result, is(Arrays.asList("CONFIG_D", "CONFIG_F", "CONFIG_E")));
    }
    
    /**
     * Tests the {@link Analysis#USED_BUT_NOT_DEFINED} analysis with a tristate variable.
     * 
     * @throws SetUpException unwanted.
     */
    @Test
    public void testUsedButNotDefinedWithTristate() throws SetUpException {
        BuildModel bm = new BuildModel();
        
        CodeBlock cb = new CodeBlock(new Variable("CONFIG_C_MODULE"));
        
        SourceFile<CodeBlock> sf = new SourceFile<>(new File("test.c"));
        sf.addElement(cb);
        
        List<String> result = run(Analysis.USED_BUT_NOT_DEFINED, VAR_MODEL, bm, sf);
        
        assertThat(result, is(Arrays.asList()));
    }
    
    /**
     * Tests the {@link Analysis#USED_BUT_NOT_DEFINED} analysis with a non-"CONFIG_" variable.
     * 
     * @throws SetUpException unwanted.
     */
    @Test
    public void testUsedButNotDefinedWithNonConfig() throws SetUpException {
        BuildModel bm = new BuildModel();
        
        CodeBlock cb = new CodeBlock(new Variable("NOT_A_CONFIG"));
        
        SourceFile<CodeBlock> sf = new SourceFile<>(new File("test.c"));
        sf.addElement(cb);
        
        List<String> result = run(Analysis.USED_BUT_NOT_DEFINED, VAR_MODEL, bm, sf);
        
        assertThat(result, is(Arrays.asList()));
    }
    
    /**
     * Tests the {@link Analysis#DEFINED_BUT_NOT_USED} analysis.
     * 
     * @throws SetUpException unwanted.
     */
    @Test
    public void testDefinedButNotUsed() throws SetUpException {
        BuildModel bm = new BuildModel();
        bm.add(new File("test.c"), and("CONFIG_A", "CONFIG_D"));
        
        CodeBlock cb = new CodeBlock(or("CONFIG_A", "CONFIG_E"));
        cb.addNestedElement(new CodeBlock(and("CONFIG_D", not("CONFIG_C"))));
        
        SourceFile<CodeBlock> sf = new SourceFile<>(new File("test.c"));
        sf.addElement(cb);
        
        List<String> result = run(Analysis.DEFINED_BUT_NOT_USED, VAR_MODEL, bm, sf);
        
        assertThat(result, is(Arrays.asList("CONFIG_B")));
    }
    
    /**
     * Tests the {@link Analysis#DEFINED_BUT_NOT_USED} analysis.
     * 
     * @throws SetUpException unwanted.
     */
    @Test
    public void testDefinedButNotUsedWithTristate() throws SetUpException {
        BuildModel bm = new BuildModel();
        bm.add(new File("test.c"), new Variable("CONFIG_E_MODULE"));
        
        CodeBlock cb = new CodeBlock(or("CONFIG_A", "CONFIG_C_MODULE"));
        
        SourceFile<CodeBlock> sf = new SourceFile<>(new File("test.c"));
        sf.addElement(cb);
        
        List<String> result = run(Analysis.DEFINED_BUT_NOT_USED, VAR_MODEL, bm, sf);
        
        assertThat(result, is(Arrays.asList("CONFIG_B")));
    }
    
}
