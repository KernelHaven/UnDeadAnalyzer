package net.ssehub.kernel_haven.default_analyses;

import java.io.File;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;

/**
 * The Class AllTests.
 */
@RunWith(Suite.class)
@SuiteClasses({
    DeadCodeAnalysisTest.class,
    })
public class AllTests {
    // runs tests defined in SuiteClasses
    
    public static final File TESTDATA_DIR = new File("testdata");
}
