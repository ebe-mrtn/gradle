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

import com.google.common.collect.Lists;
import org.gradle.api.UncheckedIOException;
import org.gradle.internal.process.ArgWriter;
import org.gradle.nativeplatform.toolchain.internal.OptionsFileArgsWriter;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Uses an option file for arguments passed to GCC if possible.
 * Certain GCC options do not function correctly when included in an option file, so include these directly on the command line as well.
 */
class IarOptionsFileArgsWriter extends OptionsFileArgsWriter {
    private static final List<String> CLI_ONLY_ARGS = Arrays.asList("");

    public IarOptionsFileArgsWriter(File tempDir) {
        super(tempDir);
    }

    @Override
    public List<String> transformArgs(List<String> originalArgs, File tempDir) {
        List<String> commandLineOnlyArgs = getCommandLineOnlyArgs(originalArgs);
        List<String> finalArgs = Lists.newArrayList();

        finalArgs.addAll(commandLineOnlyArgs);

        //finalArgs.addAll(ArgWriter.argsFileGenerator(new File(tempDir, "options.txt"), ArgWriter.unixStyleFactory()).transform(originalArgs));

        File argsFile = new File(tempDir, "options.txt");
        argsFile.getParentFile().mkdirs();
        try {
            PrintWriter writer = new PrintWriter(argsFile);
            try {
                for (String arg: originalArgs)
                    writer.println(arg);
            } finally {
                writer.close();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(String.format("Could not write options file '%s'.", argsFile.getAbsolutePath()), e);
        }

        finalArgs.add("-f"); // use command line options file
        finalArgs.add(argsFile.getAbsolutePath());

        return finalArgs;
    }

    private List<String> getCommandLineOnlyArgs(List<String> allArgs) {
        List<String> commandLineOnlyArgs = new ArrayList<String>(allArgs);
        commandLineOnlyArgs.retainAll(CLI_ONLY_ARGS);
        return commandLineOnlyArgs;
    }
}
