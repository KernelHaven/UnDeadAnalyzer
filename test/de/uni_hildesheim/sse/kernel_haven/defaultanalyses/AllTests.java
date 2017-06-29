package de.uni_hildesheim.sse.kernel_haven.defaultanalyses;

import java.io.File;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;

/**
 * The Class AllTests.
 */
@RunWith(Suite.class)
@SuiteClasses({
    Dummy.class,
    })
public class AllTests {
    // runs tests defined in SuiteClasses
    
    public static final File TESTDATA_DIR = new File("testdata");
}
