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

import java.util.logging.Level;

import edu.illinois.starts.helpers.Writer;
import edu.illinois.starts.util.Logger;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.BuildPluginManager;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Execute;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

/**
 * Invoked after after running selected tests (see lifecycle.xml for details).
 */
@Mojo(name = "starts", requiresDirectInvocation = true, requiresDependencyResolution = ResolutionScope.TEST)
@Execute(phase = LifecyclePhase.TEST, lifecycle = "starts")
public class StartsMojo extends RunMojo {
    private Logger logger;

    public void execute() throws MojoExecutionException {
        long endOfRunMojo = Long.parseLong(System.getProperty("[PROFILE] END-OF-RUN-MOJO: "));
        Logger.getGlobal().setLoggingLevel(Level.parse(loggingLevel));
        logger = Logger.getGlobal();
        long end = System.currentTimeMillis();
        logger.log(Level.FINE, "[PROFILE] TEST-RUNNING-TIME: " + Writer.millsToSeconds(end - endOfRunMojo));

        if (enableMojoExecutor && updateRunChecksums) {
            try {
                logger.log(Level.FINE, "available Semaphore permits: " + UpdateMojoRunnable.mutex.availablePermits());
                UpdateMojoRunnable.mutex.acquire();
            } catch (InterruptedException ie) {
                ie.printStackTrace();
            }
        }

        end = System.currentTimeMillis();
        logger.log(Level.FINE, "[PROFILE] STARTS-MOJO-TOTAL: " + Writer.millsToSeconds(end - endOfRunMojo));
    }
}
