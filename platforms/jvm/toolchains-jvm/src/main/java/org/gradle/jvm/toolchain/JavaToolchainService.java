/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.jvm.toolchain;

import org.gradle.api.Action;
import org.gradle.api.provider.Provider;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;

/**
 * Allows to query for toolchain managed tools, like {@link JavaCompiler}, {@link JavaLauncher} and {@link JavadocTool}.
 * <p>
 * An instance of this service is available for injection into tasks, plugins and other types.
 *
 * @since 6.7
 */
@ServiceScope(Scope.Project.class)
public interface JavaToolchainService {

    /**
     * Obtain a {@link JavaCompiler} matching the {@link JavaToolchainSpec}, as configured by the provided action.
     *
     * @param config The configuration of the {@code JavaToolchainSpec}
     * @return A {@code Provider<JavaCompiler>}
     */
    Provider<JavaCompiler> compilerFor(Action<? super JavaToolchainSpec> config);

    /**
     * Obtain a {@link JavaCompiler} matching the {@link JavaToolchainSpec}.
     *
     * @param spec The {@code JavaToolchainSpec}
     * @return A {@code Provider<JavaCompiler>}
     */
    Provider<JavaCompiler> compilerFor(JavaToolchainSpec spec);

    /**
     * Attempt to obtain a {@link JavaCompiler} matching the {@link JavaToolchainSpec}, as configured by the provided action.
     * <p>
     * If a launcher cannot be found, the provider will be empty.
     *
     * @param config The configuration of the {@code JavaToolchainSpec}
     * @return A {@code Provider<JavaCompiler>}
     */
    default Provider<JavaCompiler> compilerForOptional(Action<? super JavaToolchainSpec> config) {
        return compilerFor(config);
    }

    /**
     * Attempt to obtain a {@link JavaCompiler} matching the {@link JavaToolchainSpec}.
     * <p>
     * If a launcher cannot be found, the provider will be empty.
     *
     * @param spec The {@code JavaToolchainSpec}
     * @return A {@code Provider<JavaCompiler>}
     */
    default Provider<JavaCompiler> compilerForOptional(JavaToolchainSpec spec) {
        return compilerFor(spec);
    }

    /**
     * Obtain a {@link JavaLauncher} matching the {@link JavaToolchainSpec}, as configured by the provided action.
     *
     * @param config The configuration of the {@code JavaToolchainSpec}
     * @return A {@code Provider<JavaLauncher>}
     */
    Provider<JavaLauncher> launcherFor(Action<? super JavaToolchainSpec> config);

    /**
     * Obtain a {@link JavaLauncher} matching the {@link JavaToolchainSpec}.
     *
     * @param spec The {@code JavaToolchainSpec}
     * @return A {@code Provider<JavaLauncher>}
     */
    Provider<JavaLauncher> launcherFor(JavaToolchainSpec spec);

    /**
     * Attempt to obtain a {@link JavaLauncher} matching the {@link JavaToolchainSpec}, as configured by the provided action.
     * <p>
     * If a launcher cannot be found, the provider will be empty.
     *
     * @param config The configuration of the {@code JavaToolchainSpec}
     * @return A {@code Provider<JavaLauncher>}
     */
    default Provider<JavaLauncher> launcherForOptional(Action<? super JavaToolchainSpec> config) {
        return launcherFor(config);
    }

    /**
     * Attempt to obtain a {@link JavaLauncher} matching the {@link JavaToolchainSpec}.
     * <p>
     * If a launcher cannot be found, the provider will be empty.
     *
     * @param spec The {@code JavaToolchainSpec}
     * @return A {@code Provider<JavaLauncher>}
     */
    default Provider<JavaLauncher> launcherForOptional(JavaToolchainSpec spec) {
        return launcherFor(spec);
    }

    /**
     * Obtain a {@link JavadocTool} matching the {@link JavaToolchainSpec}, as configured by the provided action.
     *
     * @param config The configuration of the {@code JavaToolchainSpec}
     * @return A {@code Provider<JavadocTool>}
     */
    Provider<JavadocTool> javadocToolFor(Action<? super JavaToolchainSpec> config);

    /**
     * Obtain a {@link JavadocTool} matching the {@link JavaToolchainSpec}.
     *
     * @param spec The {@code JavaToolchainSpec}
     * @return A {@code Provider<JavadocTool>}
     */
    Provider<JavadocTool> javadocToolFor(JavaToolchainSpec spec);

    /**
     * Attempt to obtain a {@link JavadocTool} matching the {@link JavaToolchainSpec}, as configured by the provided action.
     * <p>
     * If a javadoc cannot be found, the provider will be empty.
     *
     * @param config The configuration of the {@code JavaToolchainSpec}
     * @return A {@code Provider<JavadocTool>}
     */
    default Provider<JavadocTool> javadocToolForOptional(Action<? super JavaToolchainSpec> config) {
        return javadocToolFor(config);
    }

    /**
     * Attempt to obtain a {@link JavadocTool} matching the {@link JavaToolchainSpec}.
     * <p>
     * If a javadoc cannot be found, the provider will be empty.
     *
     * @param spec The {@code JavaToolchainSpec}
     * @return A {@code Provider<JavadocTool>}
     */
    default Provider<JavadocTool> javadocToolForOptional(JavaToolchainSpec spec) {
        return javadocToolFor(spec);
    }
}
