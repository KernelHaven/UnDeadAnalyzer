package net.ssehub.kernel_haven.undead_analyzer;

import java.io.File;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;

/**
 * The Class AllTests.
 */
@RunWith(Suite.class)
@SuiteClasses({
    DeadCodeFinderTest.class,
    })
public class AllTests {
    // runs tests defined in SuiteClasses
    
    public static final File TESTDATA_DIR = new File("testdata");
}
