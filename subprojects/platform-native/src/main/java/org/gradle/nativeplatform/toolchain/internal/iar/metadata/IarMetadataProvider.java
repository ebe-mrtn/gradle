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

package org.gradle.nativeplatform.toolchain.internal.iar.metadata;

import com.google.common.collect.ImmutableList;
import org.gradle.api.UncheckedIOException;
import org.gradle.nativeplatform.platform.internal.ArchitectureInternal;
import org.gradle.nativeplatform.platform.internal.Architectures;
import org.gradle.nativeplatform.toolchain.internal.metadata.AbstractMetadataProvider;
import org.gradle.nativeplatform.toolchain.internal.metadata.CompilerType;
import org.gradle.process.internal.ExecActionFactory;
import org.gradle.util.VersionNumber;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Given a File pointing to an (existing) gcc/g++/clang/clang++ binary, extracts the version number and default architecture by running with -dM -E -v and scraping the output.
 */
public class IarMetadataProvider extends AbstractMetadataProvider<IarMetadata> {

    private static final CompilerType IAR_COMPILER_TYPE = new CompilerType() {
        @Override
        public String getIdentifier() {
            return "iar";
        }

        @Override
        public String getDescription() {
            return "IAR Embedded Workbench";
        }
    };

    public IarMetadataProvider(ExecActionFactory execActionFactory) {
        super(execActionFactory);
    }

    @Override
    protected List<String> compilerArgs() {
        return ImmutableList.of("--version");
    }

    @Override
    public CompilerType getCompilerType() {
        return IAR_COMPILER_TYPE;
    }

    @Override
    protected IarMetadata parseCompilerOutput(String stdout, String stderr, File file, List<File> path) {
        BufferedReader reader = new BufferedReader(new StringReader(stdout));
        try {
            String line;
            Pattern p = Pattern.compile("IAR\\s(.*)\\sV(.*)\\sfor\\s(.*)");
            while ((line = reader.readLine()) != null) {
                Matcher m = p.matcher(line);
                if (m.matches()) {
                    // Assuming format: 'IAR ANSI C/C++ Compiler V7.50.1.10123/W32 for ARM'
                    VersionNumber version = VersionNumber.parse(m.group(2));
                    return new IarMetadataProvider.DefaultIarMetadata(line, version, Architectures.forInput(m.group(3)));
                }
            }
            throw new BrokenResultException(String.format("Could not determine %s metadata: %s produced unexpected output.", getCompilerType().getDescription(), file.getName()));
        } catch (IOException e) {
            // Should not happen when reading from a StringReader
            throw new UncheckedIOException(e);
        }
    }

    private class DefaultIarMetadata implements IarMetadata {
        private final String versionString;
        private final VersionNumber version;
        private final ArchitectureInternal architecture;

        DefaultIarMetadata(String versionString, VersionNumber version, ArchitectureInternal architecture) {
            this.versionString = versionString;
            this.architecture = architecture;
            this.version = version;
        }

        @Override
        public String getVendor() {
            return versionString;
        }

        @Override
        public ArchitectureInternal getDefaultArchitecture() {
            return architecture;
        }

        @Override
        public VersionNumber getVersion() {
            return version;
        }
    }
}
