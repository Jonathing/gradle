/*
 * Copyright 2017 the original author or authors.
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
package org.gradle.plugins.ide.internal.resolver;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Iterables;
import com.google.common.collect.MultimapBuilder;
import com.google.common.collect.SetMultimap;
import com.google.common.collect.Table;
import org.gradle.api.Action;
import org.gradle.api.artifacts.ArtifactCollection;
import org.gradle.api.artifacts.ArtifactView;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.component.ComponentArtifactIdentifier;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.ComponentSelector;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.artifacts.result.ArtifactResolutionResult;
import org.gradle.api.artifacts.result.ArtifactResult;
import org.gradle.api.artifacts.result.ComponentArtifactsResult;
import org.gradle.api.artifacts.result.ResolvedArtifactResult;
import org.gradle.api.artifacts.result.UnresolvedDependencyResult;
import org.gradle.api.attributes.DocsType;
import org.gradle.api.component.Artifact;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.specs.Spec;
import org.gradle.api.specs.Specs;
import org.gradle.internal.jvm.JavaModuleDetector;
import org.gradle.jvm.JvmLibrary;
import org.gradle.language.base.artifact.SourcesArtifact;
import org.gradle.language.java.artifact.JavadocArtifact;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.gradle.api.internal.artifacts.dsl.dependencies.DependencyFactoryInternal.ClassPathNotation.GRADLE_API;
import static org.gradle.api.internal.artifacts.dsl.dependencies.DependencyFactoryInternal.ClassPathNotation.GRADLE_TEST_KIT;
import static org.gradle.api.internal.artifacts.dsl.dependencies.DependencyFactoryInternal.ClassPathNotation.LOCAL_GROOVY;

/**
 * Adapts Gradle's dependency resolution engine to the special needs of the IDE plugins.
 * Allows adding and subtracting {@link Configuration}s, working in offline mode and downloading sources/javadoc.
 */
public class IdeDependencySet {
    private static final Logger LOGGER = Logging.getLogger(IdeDependencySet.class);

    private final ObjectFactory objectFactory;
    private final DependencyHandler dependencyHandler;
    private final JavaModuleDetector javaModuleDetector;
    private final Collection<Configuration> plusConfigurations;
    private final Collection<Configuration> minusConfigurations;
    private final boolean inferModulePath;
    private final GradleApiSourcesResolver gradleApiSourcesResolver;
    private final Collection<Configuration> testConfigurations;

    public IdeDependencySet(ObjectFactory objectFactory, DependencyHandler dependencyHandler, JavaModuleDetector javaModuleDetector, Collection<Configuration> plusConfigurations, Collection<Configuration> minusConfigurations, boolean inferModulePath, GradleApiSourcesResolver gradleApiSourcesResolver) {
        this(objectFactory, dependencyHandler, javaModuleDetector, plusConfigurations, minusConfigurations, inferModulePath, gradleApiSourcesResolver, Collections.emptySet());
    }

    public IdeDependencySet(ObjectFactory objectFactory, DependencyHandler dependencyHandler, JavaModuleDetector javaModuleDetector, Collection<Configuration> plusConfigurations, Collection<Configuration> minusConfigurations, boolean inferModulePath, GradleApiSourcesResolver gradleApiSourcesResolver, Collection<Configuration> testConfigurations) {
        this.objectFactory = objectFactory;
        this.dependencyHandler = dependencyHandler;
        this.javaModuleDetector = javaModuleDetector;
        this.plusConfigurations = plusConfigurations;
        this.minusConfigurations = minusConfigurations;
        this.inferModulePath = inferModulePath;
        this.gradleApiSourcesResolver = gradleApiSourcesResolver;
        this.testConfigurations = testConfigurations;
    }

    public void visit(IdeDependencyVisitor visitor) {
        if (plusConfigurations.isEmpty()) {
            return;
        }
        new IdeDependencyResult().visit(visitor);
    }

    /*
     * Tries to minimize the number of requests to the resolution engine by batching up requests
     * for sources/javadoc.
     *
     * There is still some inefficiency because the ArtifactCollection interface does not provide
     * detailed failure results, so we have to fall back to the more expensive ResolutionResult API.
     * We should fix this, as other IDE vendors will face the same problem.
     */
    private class IdeDependencyResult {
        private final Map<ComponentArtifactIdentifier, ResolvedArtifactResult> resolvedArtifacts = new LinkedHashMap<>();
        private final SetMultimap<ComponentArtifactIdentifier, Configuration> configurations = MultimapBuilder.hashKeys().linkedHashSetValues().build();
        private final SetMultimap<ComponentIdentifier, Configuration> configurationsById = MultimapBuilder.hashKeys().linkedHashSetValues().build();
        private final Map<ComponentSelector, UnresolvedDependencyResult> unresolvedDependencies = new LinkedHashMap<>();
        private final Table<ModuleComponentIdentifier, Class<? extends Artifact>, Set<ResolvedArtifactResult>> auxiliaryArtifacts = HashBasedTable.create();

        public void visit(IdeDependencyVisitor visitor) {
            resolvePlusConfigurations(visitor);
            resolveMinusConfigurations(visitor);
            resolveAuxiliaryArtifacts(visitor);
            visitArtifacts(visitor);
            visitUnresolvedDependencies(visitor);
        }

        private void resolvePlusConfigurations(IdeDependencyVisitor visitor) {
            for (Configuration configuration : plusConfigurations) {
                ArtifactCollection artifacts = getResolvedArtifacts(configuration, visitor);
                for (ResolvedArtifactResult resolvedArtifact : artifacts) {
                    ComponentArtifactIdentifier identifier = resolvedArtifact.getId();
                    resolvedArtifacts.put(identifier, resolvedArtifact);
                    configurations.put(identifier, configuration);
                    configurationsById.put(identifier.getComponentIdentifier(), configuration);
                }
                if (artifacts.getFailures().isEmpty()) {
                    continue;
                }
                for (UnresolvedDependencyResult unresolvedDependency : getUnresolvedDependencies(configuration, visitor)) {
                    unresolvedDependencies.put(unresolvedDependency.getAttempted(), unresolvedDependency);
                }
            }
        }

        private void resolveMinusConfigurations(IdeDependencyVisitor visitor) {
            for (Configuration configuration : minusConfigurations) {
                ArtifactCollection artifacts = getResolvedArtifacts(configuration, visitor);
                for (ResolvedArtifactResult resolvedArtifact : artifacts) {
                    resolvedArtifacts.remove(resolvedArtifact.getId());
                    configurations.removeAll(resolvedArtifact.getId());
                }
                if (artifacts.getFailures().isEmpty()) {
                    continue;
                }
                for (UnresolvedDependencyResult unresolvedDependency : getUnresolvedDependencies(configuration, visitor)) {
                    unresolvedDependencies.remove(unresolvedDependency.getAttempted());
                }
            }
        }

        private ArtifactCollection getResolvedArtifacts(Configuration configuration, final IdeDependencyVisitor visitor) {
            return configuration.getIncoming().artifactView(new Action<ArtifactView.ViewConfiguration>() {
                @Override
                public void execute(ArtifactView.ViewConfiguration viewConfiguration) {
                    viewConfiguration.lenient(true);
                    viewConfiguration.componentFilter(getComponentFilter(visitor));
                }
            }).getArtifacts();
        }

        private Spec<ComponentIdentifier> getComponentFilter(IdeDependencyVisitor visitor) {
            return visitor.isOffline() ? NOT_A_MODULE : Specs.<ComponentIdentifier>satisfyAll();
        }

        private Iterable<UnresolvedDependencyResult> getUnresolvedDependencies(Configuration configuration, IdeDependencyVisitor visitor) {
            if (visitor.isOffline()) {
                return Collections.emptySet();
            }
            return Iterables.filter(configuration.getIncoming().getResolutionResult().getRoot().getDependencies(), UnresolvedDependencyResult.class);
        }

        private void resolveAuxiliaryArtifacts(IdeDependencyVisitor visitor) {
            if (visitor.isOffline()) {
                return;
            }

            Set<ComponentArtifactIdentifier> componentIdentifiers = getModuleComponentIdentifiers();
            if (componentIdentifiers.isEmpty()) {
                return;
            }

            List<Class<? extends Artifact>> types = getAuxiliaryArtifactTypes(visitor);
            if (types.isEmpty()) {
                return;
            }

            SetMultimap<Configuration, ComponentIdentifier> cfgs = MultimapBuilder.hashKeys().linkedHashSetValues().build();
            for (ComponentArtifactIdentifier id : componentIdentifiers) {
                for (Configuration cfg : configurations.get(id)) {
                    cfgs.put(cfg, id.getComponentIdentifier());
                }
            }

            for (Class<? extends Artifact> type : types) {
                for (Map.Entry<Configuration, Collection<ComponentIdentifier>> entry : cfgs.asMap().entrySet()) {
                    ArtifactCollection artifacts = entry.getKey().getIncoming().artifactView(viewConfiguration -> {
                        viewConfiguration.withVariantReselection();
                        viewConfiguration.lenient(true);
                        Spec<ComponentIdentifier> filter = getComponentFilter(visitor);
                        viewConfiguration.componentFilter(id -> entry.getValue().contains(id) && filter.isSatisfiedBy(id));
                        if (SourcesArtifact.class.isAssignableFrom(type))
                            viewConfiguration.getAttributes().attribute(DocsType.DOCS_TYPE_ATTRIBUTE, objectFactory.named(DocsType.class, DocsType.SOURCES));
                        else if (JavadocArtifact.class.isAssignableFrom(type))
                            viewConfiguration.getAttributes().attribute(DocsType.DOCS_TYPE_ATTRIBUTE, objectFactory.named(DocsType.class, DocsType.JAVADOC));
                    }).getArtifacts();

                    Map<ComponentArtifactIdentifier, Set<ResolvedArtifactResult>> resolvedArtifactResults = artifacts.getArtifacts().stream().collect(Collectors.groupingBy(ArtifactResult::getId, Collectors.toSet()));
                    for (Map.Entry<ComponentArtifactIdentifier, Set<ResolvedArtifactResult>> resolvedArtifactResult : resolvedArtifactResults.entrySet()) {
                        LOGGER.warn("Found artifacts for {} : {}", resolvedArtifactResult.getKey().getComponentIdentifier(), resolvedArtifactResult.getValue().stream().map(Object::toString).collect(Collectors.joining(", ")));
                        auxiliaryArtifacts.put((ModuleComponentIdentifier) resolvedArtifactResult.getKey().getComponentIdentifier(), type, resolvedArtifactResult.getValue());
                    }
                }
            }
        }

        private Set<ComponentArtifactIdentifier> getModuleComponentIdentifiers() {
            Set<ComponentArtifactIdentifier> componentIdentifiers = new LinkedHashSet<>();
            for (ComponentArtifactIdentifier identifier : resolvedArtifacts.keySet()) {
                if (identifier.getComponentIdentifier() instanceof ModuleComponentIdentifier) {
                    componentIdentifiers.add(identifier);
                }
            }
            return componentIdentifiers;
        }

        private List<Class<? extends Artifact>> getAuxiliaryArtifactTypes(IdeDependencyVisitor visitor) {
            List<Class<? extends Artifact>> types = new ArrayList<>(2);
            if (visitor.downloadSources()) {
                types.add(SourcesArtifact.class);
            }
            if (visitor.downloadJavaDoc()) {
                types.add(JavadocArtifact.class);
            }
            return types;
        }

        private void visitArtifacts(IdeDependencyVisitor visitor) {
            for (ResolvedArtifactResult artifact : resolvedArtifacts.values()) {
                ComponentIdentifier componentIdentifier = artifact.getId().getComponentIdentifier();
                boolean testOnly = isTestConfiguration(configurations.get(artifact.getId()));
                boolean asModule = isModule(testOnly, artifact.getFile());
                if (componentIdentifier instanceof ProjectComponentIdentifier) {
                    visitor.visitProjectDependency(artifact, testOnly, asModule);
                } else {
                    if (componentIdentifier instanceof ModuleComponentIdentifier) {
                        Set<ResolvedArtifactResult> sources = auxiliaryArtifacts.get(componentIdentifier, SourcesArtifact.class);
                        sources = sources != null ? sources : Collections.emptySet();
                        Set<ResolvedArtifactResult> javaDoc = auxiliaryArtifacts.get(componentIdentifier, JavadocArtifact.class);
                        javaDoc = javaDoc != null ? javaDoc : Collections.emptySet();
                        visitor.visitModuleDependency(artifact, sources, javaDoc, testOnly, asModule);
                    } else if (isLocalGroovyDependency(artifact)) {
                        File localGroovySources = shouldDownloadSources(visitor) ? gradleApiSourcesResolver.resolveLocalGroovySources(artifact.getFile().getName()) : null;
                        visitor.visitGradleApiDependency(artifact, localGroovySources, testOnly);
                    } else {
                        visitor.visitFileDependency(artifact, testOnly);
                    }
                }
            }
        }

        private boolean isModule(boolean testOnly, File artifact) {
            // Test code is not treated as modules, as Eclipse does not support compiling two modules in one project anyway.
            // See also: https://bugs.eclipse.org/bugs/show_bug.cgi?id=520667
            //
            // We assume that a test-only dependency is not a module, which corresponds to how Eclipse does test running for modules:
            // It patches the main module with the tests and expects test dependencies to be part of the unnamed module (classpath).
            return javaModuleDetector.isModule(inferModulePath && !testOnly, artifact);
        }

        private boolean isLocalGroovyDependency(ResolvedArtifactResult artifact) {
            String artifactFileName = artifact.getFile().getName();
            String componentIdentifier = artifact.getId().getComponentIdentifier().getDisplayName();
            return (componentIdentifier.equals(GRADLE_API.displayName)
                    || componentIdentifier.equals(GRADLE_TEST_KIT.displayName)
                    || componentIdentifier.equals(LOCAL_GROOVY.displayName))
                && artifactFileName.startsWith("groovy-");
        }

        private boolean shouldDownloadSources(IdeDependencyVisitor visitor) {
            return !visitor.isOffline() && visitor.downloadSources();
        }

        private boolean isTestConfiguration(Set<Configuration> configurations) {
            return testConfigurations.containsAll(configurations);
        }

        private void visitUnresolvedDependencies(IdeDependencyVisitor visitor) {
            for (UnresolvedDependencyResult unresolvedDependency : unresolvedDependencies.values()) {
                visitor.visitUnresolvedDependency(unresolvedDependency);
            }
        }
    }

    private static final Spec<ComponentIdentifier> NOT_A_MODULE = new Spec<ComponentIdentifier>() {
        @Override
        public boolean isSatisfiedBy(ComponentIdentifier id) {
            return !(id instanceof ModuleComponentIdentifier);
        }
    };

}
