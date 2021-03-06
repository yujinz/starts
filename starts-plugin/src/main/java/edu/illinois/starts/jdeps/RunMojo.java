/*
 * Copyright (c) 2015 - Present. The STARTS Team. All Rights Reserved.
 */

package edu.illinois.starts.jdeps;

import static org.twdata.maven.mojoexecutor.MojoExecutor.artifactId;
import static org.twdata.maven.mojoexecutor.MojoExecutor.configuration;
import static org.twdata.maven.mojoexecutor.MojoExecutor.element;
import static org.twdata.maven.mojoexecutor.MojoExecutor.executeMojo;
import static org.twdata.maven.mojoexecutor.MojoExecutor.executionEnvironment;
import static org.twdata.maven.mojoexecutor.MojoExecutor.goal;
import static org.twdata.maven.mojoexecutor.MojoExecutor.groupId;
import static org.twdata.maven.mojoexecutor.MojoExecutor.name;
import static org.twdata.maven.mojoexecutor.MojoExecutor.plugin;
import static org.twdata.maven.mojoexecutor.MojoExecutor.version;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;

import edu.illinois.starts.helpers.Writer;
import edu.illinois.starts.maven.AgentLoader;
import edu.illinois.starts.util.Logger;
import edu.illinois.starts.util.Pair;
import org.apache.maven.plugin.BuildPluginManager;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;

/**
 * Prepares for test runs by writing non-affected tests in the excludesFile.
 */
@Mojo(name = "run", requiresDependencyResolution = ResolutionScope.TEST)
public class RunMojo extends DiffMojo {
    /**
     * Set this to "false" to prevent checksums from being persisted to disk. This
     * is useful for "dry runs" where one may want to see the non-affected tests that
     * STARTS writes to the Surefire excludesFile, without updating test dependencies.
     */
    @Parameter(property = "updateRunChecksums", defaultValue = "true")
    protected boolean updateRunChecksums;

    /**
     * Set this option to "true" to run all tests, not just the affected ones. This option is useful
     * in cases where one is interested to measure the time to run all tests, while at the
     * same time measuring the times for analyzing what tests to select and reporting the number of
     * tests it would select.
     * Note: Run with "-DstartsLogging=FINER" or "-DstartsLogging=FINEST" so that the "selected-tests"
     * file, which contains the list of tests that would be run if this option is set to false, will
     * be written to disk.
     */
    @Parameter(property = "retestAll", defaultValue = "false")
    protected boolean retestAll;

    /**
     * Set this to "true" to save nonAffectedTests to a file on disk. This improves the time for
     * updating test dependencies in offline mode by not running computeChangeData() twice.
     * Note: Running with "-DstartsLogging=FINEST" also writes nonAffected to a file on disk.
     */
    @Parameter(property = "writeNonAffected", defaultValue = "false")
    protected boolean writeNonAffected;

    /**
     * Set this to "true" to spawn another thread that executes UpdateMojo to update test dependencies
     * in parallel with Maven test phase execution.
     * The default value of "false" will update test dependencies before Maven test phase.
     * If updateRunChecksums is "false", this option will not affect any behaviour of "starts:starts".
     */
    @Parameter(property = "offlineMode", defaultValue = "false")
    protected boolean offlineMode;

    /**
     * The Maven BuildPluginManager component.
     */
    @Component
    protected BuildPluginManager pluginManager;

    protected Set<String> nonAffectedTests;
    protected Set<String> changedClasses;
    private Logger logger;

    public void execute() throws MojoExecutionException {
        Logger.getGlobal().setLoggingLevel(Level.parse(loggingLevel));
        logger = Logger.getGlobal();
        long start = System.currentTimeMillis();
        setChangedAndNonaffected();
        List<String> excludePaths = Writer.fqnsToExcludePath(nonAffectedTests);
        setIncludesExcludes();
        if (writeNonAffected || logger.getLoggingLevel().intValue() <= Level.FINEST.intValue()) {
            Writer.writeToFile(nonAffectedTests, "non-affected-tests", getArtifactsDir());
        }
        run(excludePaths);
        Set<String> allTests = new HashSet<>(getTestClasses("checkIfAllAffected"));
        if (allTests.equals(nonAffectedTests)) {
            logger.log(Level.INFO, "********** Run **********");
            logger.log(Level.INFO, "No tests are selected to run.");
        }
        long end = System.currentTimeMillis();
        System.setProperty("[PROFILE] END-OF-RUN-MOJO: ", Long.toString(end));
        logger.log(Level.FINE, "[PROFILE] RUN-MOJO-TOTAL: " + Writer.millsToSeconds(end - start));
    }

    protected void run(List<String> excludePaths) throws MojoExecutionException {
        if (retestAll) {
            dynamicallyUpdateExcludes(new ArrayList<String>());
        } else {
            dynamicallyUpdateExcludes(excludePaths);
        }
        if (updateRunChecksums) {
            if (offlineMode) {
                long updateStart = System.currentTimeMillis();
                System.setProperty("[PROFILE] START-OF-PARALLEL-UPDATE-MOJO: ", Long.toString(updateStart));
                try {
                    logger.log(Level.FINEST, "Starting a parallel thread for UpdateMojo. Semaphore permits: "
                            + UpdateMojoRunnable.mutex.availablePermits());
                    UpdateMojoRunnable.mutex.acquire();
                    Thread updateThread = new Thread(new UpdateMojoRunnable(getPluginDescriptor().getPlugin(),
                            getProject(), getSession(), pluginManager, writeNonAffected));
                    updateThread.start();
                } catch (InterruptedException ie) {
                    ie.printStackTrace();
                    UpdateMojoRunnable.mutex.release();
                }
            } else {
                long startUpdateTime = System.currentTimeMillis();
                updateForNextRun(nonAffectedTests);
                long endUpdateTime = System.currentTimeMillis();
                logger.log(Level.FINE, "[PROFILE] STARTS-MOJO-UPDATE-TIME: "
                        + Writer.millsToSeconds(endUpdateTime - startUpdateTime));
            }
        }
    }

    private void dynamicallyUpdateExcludes(List<String> excludePaths) throws MojoExecutionException {
        if (AgentLoader.loadDynamicAgent()) {
            logger.log(Level.FINEST, "AGENT LOADED!!!");
            System.setProperty(STARTS_EXCLUDE_PROPERTY, Arrays.toString(excludePaths.toArray(new String[0])));
        } else {
            throw new MojoExecutionException("I COULD NOT ATTACH THE AGENT");
        }
    }

    protected void setChangedAndNonaffected() throws MojoExecutionException {
        nonAffectedTests = new HashSet<>();
        changedClasses = new HashSet<>();
        Pair<Set<String>, Set<String>> data = computeChangeData();
        nonAffectedTests = data == null ? new HashSet<String>() : data.getKey();
        changedClasses  = data == null ? new HashSet<String>() : data.getValue();
    }
}
