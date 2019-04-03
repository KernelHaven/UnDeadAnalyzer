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

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import java.io.File;
import java.util.HashSet;
import java.util.Set;

import org.junit.Test;

import net.ssehub.kernel_haven.util.logic.Conjunction;
import net.ssehub.kernel_haven.util.logic.Disjunction;
import net.ssehub.kernel_haven.util.logic.False;
import net.ssehub.kernel_haven.util.logic.Formula;
import net.ssehub.kernel_haven.util.logic.Negation;
import net.ssehub.kernel_haven.util.logic.True;
import net.ssehub.kernel_haven.util.logic.Variable;
import net.ssehub.kernel_haven.util.null_checks.NonNull;
import net.ssehub.kernel_haven.variability_model.VariabilityModel;
import net.ssehub.kernel_haven.variability_model.VariabilityVariable;

/**
 * Tests the {@link FormulaRelevancyChecker}.
 * 
 * @author Adam
 */
public class FormulaRelevancyCheckerTest {

    /**
     * Creates a test variability model with the variables ALPHA, and BETA.
     * 
     * @return A test {@link VariabilityModel}.
     */
    private static VariabilityModel createTestVariabilityModel() {
        Set<VariabilityVariable> vars = new HashSet<>();
        vars.add(new VariabilityVariable("ALPHA", "bool"));
        vars.add(new VariabilityVariable("BETA", "bool"));

        VariabilityModel varModel = new VariabilityModel(new File("not_existing"), vars);
        return varModel;
    }

    /**
     * Creates a formula using the two variable names.
     * 
     * @param var1 The first variable name.
     * @param var2 The second variable name.
     * 
     * @return A formula.
     */
    private static @NonNull Formula makeFormula(@NonNull String var1, @NonNull String var2) {
        return new Disjunction(new Conjunction(new Negation(False.INSTANCE), new Variable(var1)),
                new Disjunction(True.INSTANCE, new Variable(var2)));
    }

    /**
     * Tests a formula that contains only non-VM variables, and
     * considerVmVariablesOnly = false.
     */
    @Test
    public void testNonVmVarWithNotConsiderVmVarsOnly() {
        FormulaRelevancyChecker checker = new FormulaRelevancyChecker(createTestVariabilityModel(), false);

        assertThat(checker.visit(makeFormula("NON_VM_VAR_1", "NON_VM_VAR_2")), is(true));
    }

    /**
     * Tests a formula that contains only non-VM variables, and
     * considerVmVariablesOnly = true.
     */
    @Test
    public void testNonVmVarWithConsiderVmVarsOnly() {
        FormulaRelevancyChecker checker = new FormulaRelevancyChecker(createTestVariabilityModel(), true);

        assertThat(checker.visit(makeFormula("NON_VM_VAR_1", "NON_VM_VAR_2")), is(false));
    }

    /**
     * Tests a formula that contains only non-VM variables, and
     * considerVmVariablesOnly = false.
     */
    @Test
    public void testVmVarWithNotConsiderVmVarsOnly() {
        FormulaRelevancyChecker checker = new FormulaRelevancyChecker(createTestVariabilityModel(), false);

        assertThat(checker.visit(makeFormula("ALPHA", "BETA")), is(true));
    }

    /**
     * Tests a formula that contains only non-VM variables, and
     * considerVmVariablesOnly = true.
     */
    @Test
    public void testVmVarWithConsiderVmVarsOnly() {
        FormulaRelevancyChecker checker = new FormulaRelevancyChecker(createTestVariabilityModel(), true);

        assertThat(checker.visit(makeFormula("ALPHA", "BETA")), is(true));
    }

}
