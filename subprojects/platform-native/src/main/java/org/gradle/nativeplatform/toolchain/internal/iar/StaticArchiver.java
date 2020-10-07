/*
 * Copyright 2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.nativeplatform.toolchain.internal.iar;

import org.gradle.api.Action;
import org.gradle.api.GradleException;
import org.gradle.api.tasks.WorkResult;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.operations.BuildOperationQueue;
import org.gradle.internal.work.WorkerLeaseService;
import org.gradle.nativeplatform.internal.StaticLibraryArchiverSpec;
import org.gradle.nativeplatform.toolchain.internal.AbstractCompiler;
import org.gradle.nativeplatform.toolchain.internal.ArgsTransformer;
import org.gradle.nativeplatform.toolchain.internal.CommandLineToolContext;
import org.gradle.nativeplatform.toolchain.internal.CommandLineToolInvocation;
import org.gradle.nativeplatform.toolchain.internal.CommandLineToolInvocationWorker;
import org.gradle.nativeplatform.toolchain.internal.tools.CommonPath;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * A static library archiver based on the GNU 'ar' utility
 */
public class StaticArchiver extends AbstractCompiler<StaticLibraryArchiverSpec> {
    public StaticArchiver(BuildOperationExecutor buildOperationExecutor, CommandLineToolInvocationWorker commandLineToolInvocationWorker, CommandLineToolContext invocationContext, WorkerLeaseService workerLeaseService) {
        super(buildOperationExecutor, commandLineToolInvocationWorker, invocationContext, new ArchiverSpecToArguments(), false, workerLeaseService);

    }

    @Override
    public WorkResult execute(final StaticLibraryArchiverSpec spec) {
        deletePreviousOutput(spec);

        return super.execute(spec);
    }

    private void deletePreviousOutput(StaticLibraryArchiverSpec spec) {
        // Need to delete the previous archive, otherwise stale object files will remain
        if (!spec.getOutputFile().isFile()) {
            return;
        }
        if (!(spec.getOutputFile().delete())) {
            throw new GradleException("Create static archive failed: could not delete previous archive");
        }
    }

    @Override
    protected Action<BuildOperationQueue<CommandLineToolInvocation>> newInvocationAction(final StaticLibraryArchiverSpec spec, List<String> args) {

        File workDir = CommonPath.commonPath(spec.getObjectFiles());

        final CommandLineToolInvocation invocation = newInvocation(
            "archiving " + spec.getOutputFile().getName(),      // name
            // spec.getOutputFile().getParentFile(),                  // working dir
            workDir,
            args,                                                     // arguments
            spec.getOperationLogger());                               // logger


        return new Action<BuildOperationQueue<CommandLineToolInvocation>>() {
            @Override
            public void execute(BuildOperationQueue<CommandLineToolInvocation> buildQueue) {
                buildQueue.setLogLocation(spec.getOperationLogger().getLogLocation());
                buildQueue.add(invocation);
            }
        };
    }

    @Override
    protected void addOptionsFileArgs(List<String> args, File tempDir) {
        // No support for command file
    }

    private static class ArchiverSpecToArguments implements ArgsTransformer<StaticLibraryArchiverSpec> {
        @Override
        public List<String> transform(StaticLibraryArchiverSpec spec) {
            List<String> args = new ArrayList<String>();
            // --create: create archive
            args.add("--create");
            args.addAll(spec.getAllArgs());
            // with following objects
            // Path outfilePath = Paths.get(spec.getOutputFile().getAbsolutePath());
            File workDir = CommonPath.commonPath(spec.getObjectFiles());
            Path outfilePath = Paths.get(workDir.getAbsolutePath());
            for (File file : spec.getObjectFiles()) {
                Path filePath = Paths.get(file.getAbsolutePath());
                args.add(outfilePath.relativize(filePath).toString());
            }
            // -o: to following lib
            args.add("-o");
            args.add(spec.getOutputFile().getAbsolutePath());
            return args;
        }
    }
}
