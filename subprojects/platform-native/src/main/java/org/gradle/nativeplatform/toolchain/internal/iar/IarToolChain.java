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

import com.google.common.collect.Maps;
import org.gradle.api.NonNullApi;
import org.gradle.api.Action;
import org.gradle.internal.Actions;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.os.OperatingSystem;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.work.WorkerLeaseService;
import org.gradle.nativeplatform.internal.CompilerOutputFileNamingSchemeFactory;
import org.gradle.nativeplatform.platform.NativePlatform;
import org.gradle.nativeplatform.platform.internal.NativePlatformInternal;
import org.gradle.nativeplatform.toolchain.Iar;
import org.gradle.nativeplatform.toolchain.IarPlatformToolChain;
import org.gradle.nativeplatform.toolchain.NativePlatformToolChain;
import org.gradle.nativeplatform.toolchain.internal.metadata.CompilerMetaDataProviderFactory;
import org.gradle.nativeplatform.toolchain.internal.ExtendableToolChain;
import org.gradle.nativeplatform.toolchain.internal.NativeLanguage;
import org.gradle.nativeplatform.toolchain.internal.PlatformToolProvider;
import org.gradle.nativeplatform.toolchain.internal.ToolType;
import org.gradle.nativeplatform.toolchain.internal.UnavailablePlatformToolProvider;
import org.gradle.nativeplatform.toolchain.internal.UnsupportedPlatformToolProvider;
import org.gradle.nativeplatform.toolchain.internal.metadata.CompilerMetaDataProvider;
import org.gradle.nativeplatform.toolchain.internal.metadata.CompilerType;
import org.gradle.nativeplatform.toolchain.internal.iar.metadata.IarMetadata;
import org.gradle.nativeplatform.toolchain.internal.tools.GccCommandLineToolConfigurationInternal;
import org.gradle.nativeplatform.toolchain.internal.tools.DefaultGccCommandLineToolConfiguration;
import org.gradle.nativeplatform.toolchain.internal.tools.CommandLineToolSearchResult;
import org.gradle.nativeplatform.toolchain.internal.tools.ToolSearchPath;
import org.gradle.platform.base.internal.toolchain.ToolChainAvailability;
import org.gradle.platform.base.internal.toolchain.ToolSearchResult;
import org.gradle.platform.base.internal.toolchain.SearchResult;
import org.gradle.process.internal.ExecActionFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.File;
import java.util.Collection;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static java.util.Arrays.asList;


@NonNullApi
public class IarToolChain extends ExtendableToolChain<IarPlatformToolChain> implements Iar {
    private static final Logger LOGGER = LoggerFactory.getLogger(IarToolChain.class);
    public static final String DEFAULT_NAME = "iar";

    private final CompilerOutputFileNamingSchemeFactory compilerOutputFileNamingSchemeFactory;
    private final CompilerMetaDataProvider<IarMetadata> compilerMetaDataProvider;
    private final Instantiator instantiator;
    private final ToolSearchPath toolSearchPath;
    private final ExecActionFactory execActionFactory;
    private final WorkerLeaseService workerLeaseService;
    private final Map<NativePlatform, PlatformToolProvider> toolProviders = Maps.newHashMap();
    private final List<TargetPlatformConfiguration> platformConfigs = new ArrayList<TargetPlatformConfiguration>();

    private int configInsertLocation;

    public IarToolChain(Instantiator instantiator, String name, BuildOperationExecutor buildOperationExecutor, OperatingSystem operatingSystem, FileResolver fileResolver, ExecActionFactory execActionFactory, CompilerOutputFileNamingSchemeFactory compilerOutputFileNamingSchemeFactory, CompilerMetaDataProviderFactory metaDataProviderFactory, WorkerLeaseService workerLeaseService) {
        this(name, buildOperationExecutor, operatingSystem, fileResolver, execActionFactory, compilerOutputFileNamingSchemeFactory, new ToolSearchPath(operatingSystem), metaDataProviderFactory.iar(), instantiator, workerLeaseService);
    }

    IarToolChain(String name, BuildOperationExecutor buildOperationExecutor, OperatingSystem operatingSystem, FileResolver fileResolver, ExecActionFactory execActionFactory, CompilerOutputFileNamingSchemeFactory compilerOutputFileNamingSchemeFactory, ToolSearchPath tools, CompilerMetaDataProvider<IarMetadata> compilerMetaDataProvider, Instantiator instantiator, WorkerLeaseService workerLeaseService) {
        super(name, buildOperationExecutor, operatingSystem, fileResolver);
        this.compilerOutputFileNamingSchemeFactory = compilerOutputFileNamingSchemeFactory;
        this.compilerMetaDataProvider = compilerMetaDataProvider;
        this.instantiator = instantiator;
        this.toolSearchPath = tools;
        this.execActionFactory = execActionFactory;
        this.workerLeaseService = workerLeaseService;

        configInsertLocation = 0;
    }

    @Override
    protected String getTypeName() {
        return "IAR ANSI C/C++ Toolchain";
    }

    protected CommandLineToolSearchResult locate(GccCommandLineToolConfigurationInternal tool) {
        return toolSearchPath.locate(tool.getToolType(), tool.getExecutable());
    }

    @Override
    public List<File> getPath() {
        return toolSearchPath.getPath();
    }

    @Override
    public void path(Object... pathEntries) {
        for (Object path : pathEntries) {
            toolSearchPath.path(resolve(path));
        }
    }

    protected CompilerMetaDataProvider<IarMetadata> getMetaDataProvider() {
        return compilerMetaDataProvider;
    }

    @Override
    public void target(String platformName) {
        target(platformName, Actions.<NativePlatformToolChain>doNothing());
    }

    @Override
    public void target(String platformName, Action<? super NativePlatformToolChain> action) {
        target(new DefaultTargetPlatformConfiguration(asList(platformName), action));
    }

    public void target(List<String> platformNames, Action<? super NativePlatformToolChain> action) {
        target(new DefaultTargetPlatformConfiguration(platformNames, action));
    }

    private void target(TargetPlatformConfiguration targetPlatformConfiguration) {
        platformConfigs.add(configInsertLocation, targetPlatformConfiguration);
        configInsertLocation++;
    }

    @Override
    public void setTargets(String... platformNames) {
        platformConfigs.clear();
        configInsertLocation = 0;
        for (String platformName : platformNames) {
            target(platformName);
        }
    }

    @Override
    public PlatformToolProvider select(NativePlatformInternal targetPlatform) {
        return select(NativeLanguage.ANY, targetPlatform);
    }

    private PlatformToolProvider getProviderForPlatform(NativePlatformInternal targetPlatform) {
        PlatformToolProvider toolProvider = toolProviders.get(targetPlatform);
        if (toolProvider == null) {
            toolProvider = createPlatformToolProvider(targetPlatform);
            toolProviders.put(targetPlatform, toolProvider);
        }
        return toolProvider;
    }

    @Override
    public PlatformToolProvider select(NativeLanguage sourceLanguage, NativePlatformInternal targetMachine) {
        PlatformToolProvider toolProvider = getProviderForPlatform(targetMachine);
        switch (sourceLanguage) {
            case CPP:
                if (toolProvider instanceof UnsupportedPlatformToolProvider) {
                    return toolProvider;
                }
                ToolSearchResult cppCompiler = toolProvider.locateTool(ToolType.CPP_COMPILER);
                if (cppCompiler.isAvailable()) {
                    return toolProvider;
                }
                // No C++ compiler, complain about it
                return new UnavailablePlatformToolProvider(targetMachine.getOperatingSystem(), cppCompiler);
            case ANY:
                if (toolProvider instanceof UnsupportedPlatformToolProvider) {
                    return toolProvider;
                }
                ToolSearchResult cCompiler = toolProvider.locateTool(ToolType.C_COMPILER);
                if (cCompiler.isAvailable()) {
                    return toolProvider;
                }
                ToolSearchResult compiler = toolProvider.locateTool(ToolType.CPP_COMPILER);
                if (compiler.isAvailable()) {
                    return toolProvider;
                }
                // No compilers available, complain about the missing C compiler
                return new UnavailablePlatformToolProvider(targetMachine.getOperatingSystem(), cCompiler);
            default:
                return new UnsupportedPlatformToolProvider(targetMachine.getOperatingSystem(), String.format("Don't know how to compile language %s.", sourceLanguage));
        }
    }

    private PlatformToolProvider createPlatformToolProvider(NativePlatformInternal targetPlatform) {
        TargetPlatformConfiguration targetPlatformConfigurationConfiguration = getPlatformConfiguration(targetPlatform);
        if (targetPlatformConfigurationConfiguration == null) {
            return new UnsupportedPlatformToolProvider(targetPlatform.getOperatingSystem(), String.format("Don't know how to build for %s.", targetPlatform.getDisplayName()));
        }

        DefaultIarPlatformToolChain configurableToolChain = instantiator.newInstance(DefaultIarPlatformToolChain.class, targetPlatform);
        addDefaultTools(configurableToolChain);
        configureDefaultTools(configurableToolChain);
        targetPlatformConfigurationConfiguration.apply(configurableToolChain);
        configureActions.execute(configurableToolChain);

        ToolChainAvailability result = new ToolChainAvailability();
        initTools(configurableToolChain, result);
        if (!result.isAvailable()) {
            return new UnavailablePlatformToolProvider(targetPlatform.getOperatingSystem(), result);
        }

        return new IarPlatformToolProvider(buildOperationExecutor, targetPlatform.getOperatingSystem(), toolSearchPath, configurableToolChain, execActionFactory, compilerOutputFileNamingSchemeFactory, configurableToolChain.isCanUseCommandFile(), workerLeaseService, new CompilerMetaDataProviderWithDefaultArgs(configurableToolChain.getCompilerProbeArgs(), compilerMetaDataProvider));
    }

    protected void initTools(DefaultIarPlatformToolChain platformToolChain, ToolChainAvailability availability) {
        // Attempt to determine whether the compiler is the correct implementation
        for (GccCommandLineToolConfigurationInternal tool : platformToolChain.getCompilers()) {
            CommandLineToolSearchResult compiler = locate(tool);
            if (compiler.isAvailable()) {
                SearchResult<IarMetadata> iarMetadata = getMetaDataProvider().getCompilerMetaData(toolSearchPath.getPath(), spec -> spec.executable(compiler.getTool()).args(platformToolChain.getCompilerProbeArgs()));
                availability.mustBeAvailable(iarMetadata);
                if (!iarMetadata.isAvailable()) {
                    return;
                }
                // Assume all the other compilers are ok, if they happen to be installed
                LOGGER.debug("Found {} with version {}", tool.getToolType().getToolName(), iarMetadata);
                break;
            }
        }
    }

    protected void initForImplementation(DefaultIarPlatformToolChain platformToolChain, IarMetadata versionResult) {
    }

    private void addDefaultTools(DefaultIarPlatformToolChain toolChain) {
        toolChain.add(instantiator.newInstance(DefaultGccCommandLineToolConfiguration.class, ToolType.C_COMPILER, "iccarm"));
        toolChain.add(instantiator.newInstance(DefaultGccCommandLineToolConfiguration.class, ToolType.CPP_COMPILER, "iccarm"));
        toolChain.add(instantiator.newInstance(DefaultGccCommandLineToolConfiguration.class, ToolType.LINKER, "ilinkarm"));
        toolChain.add(instantiator.newInstance(DefaultGccCommandLineToolConfiguration.class, ToolType.STATIC_LIB_ARCHIVER, "iarchive"));
        toolChain.add(instantiator.newInstance(DefaultGccCommandLineToolConfiguration.class, ToolType.ASSEMBLER, "iasmarm"));
    }

    protected void configureDefaultTools(DefaultIarPlatformToolChain toolChain) {
    }

    @Nullable
    protected TargetPlatformConfiguration getPlatformConfiguration(NativePlatformInternal targetPlatform) {
        for (TargetPlatformConfiguration platformConfig : platformConfigs) {
            if (platformConfig.supportsPlatform(targetPlatform)) {
                return platformConfig;
            }
        }
        return null;
    }


    private static class DefaultTargetPlatformConfiguration implements TargetPlatformConfiguration {
        //TODO this should be a container of platforms
        private final Collection<String> platformNames;
        private Action<? super NativePlatformToolChain> configurationAction;

        public DefaultTargetPlatformConfiguration(Collection<String> targetPlatformNames, Action<? super NativePlatformToolChain> configurationAction) {
            this.platformNames = targetPlatformNames;
            this.configurationAction = configurationAction;
        }

        @Override
        public boolean supportsPlatform(NativePlatformInternal targetPlatform) {
            return platformNames.contains(targetPlatform.getName());
        }

        @Override
        public void apply(NativePlatformToolChain platformToolChain) {
            configurationAction.execute(platformToolChain);
        }
    }

    private static class CompilerMetaDataProviderWithDefaultArgs implements CompilerMetaDataProvider<IarMetadata> {

        private final List<String> compilerProbeArgs;
        private final CompilerMetaDataProvider<IarMetadata> delegate;

        public CompilerMetaDataProviderWithDefaultArgs(List<String> compilerProbeArgs, CompilerMetaDataProvider<IarMetadata> delegate) {
            this.compilerProbeArgs = compilerProbeArgs;
            this.delegate = delegate;
        }

        @Override
        public SearchResult<IarMetadata> getCompilerMetaData(List<File> searchPath, Action<? super CompilerExecSpec> configureAction) {
            return delegate.getCompilerMetaData(searchPath, execSpec -> {
                execSpec.args(compilerProbeArgs);
                configureAction.execute(execSpec);
            });
        }

        @Override
        public CompilerType getCompilerType() {
            return delegate.getCompilerType();
        }
    }


}
