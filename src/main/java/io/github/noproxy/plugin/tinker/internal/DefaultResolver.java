/*
 * Copyright 2020 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.github.noproxy.plugin.tinker.internal;

import com.android.build.gradle.api.ApplicationVariant;
import com.google.common.base.Preconditions;
import io.github.noproxy.plugin.tinker.api.Resolver;
import io.github.noproxy.plugin.tinker.api.VariantArtifactsLocator;
import io.github.noproxy.plugin.tinker.api.VariantArtifactsLocatorFactory;
import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ResolvedArtifact;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Set;
import java.util.stream.Collectors;

import static org.codehaus.groovy.runtime.StringGroovyMethods.capitalize;

public class DefaultResolver implements Resolver {
    private final VariantArtifactsLocatorFactory locatorFactory;
    private final TinkerMavenPublishExtensionInternal publishExtension;
    private final Project project;
    private final TinkerMavenResolverExtensionInternal resolverExtension;

    public DefaultResolver(Project project, TinkerMavenResolverExtensionInternal resolverExtension,
                           TinkerMavenPublishExtensionInternal publishExtension) {
        this.locatorFactory = resolverExtension.getLocatorFactory();
        this.publishExtension = publishExtension;
        this.resolverExtension = resolverExtension;
        this.project = project;
    }

    private static <T> T assertSingleton(Set<T> collections) {
        return assertSingleton(collections, null);
    }

    private static <T> T assertSingleton(Set<T> collections, String msg) {
        Preconditions.checkArgument(collections.size() == 1, msg);
        return collections.stream().findFirst().get();
    }

    @Override
    @Nullable
    public File resolveMapping(ApplicationVariant variant) {
        if (resolverExtension.getVersion() == null) {
            return null;
        }

        final VariantArtifactsLocator resolveLocator = locatorFactory.createLocator(variant, publishExtension, resolverExtension.getVersion());
        Configuration classpath = createResourceClasspath(variant, resolveLocator);

        final Set<ResolvedArtifact> artifacts = classpath.getResolvedConfiguration().getLenientConfiguration()
                .getArtifacts(resolveLocator.getDependencySpec(ArtifactType.MAPPING));

        final Set<File> mappings = artifacts.stream().filter(resolveLocator.getResolvedArtifactSpec(ArtifactType.MAPPING))
                .map(ResolvedArtifact::getFile).collect(Collectors.toSet());
        if (mappings.isEmpty()) {
            return null;
        }

        return assertSingleton(mappings);
    }

    // use separate configuration to resolve apk, because for other file, we use lenientConfiguration to ignore resolve error.
    // but for the apk, we want gradle throw exception
    private Configuration createResourceClasspath(ApplicationVariant variant, VariantArtifactsLocator resolveLocator) {
        final String variantName = capitalize(variant.getName());

        return maybeCreate("tinkerResolve" + variantName + "Classpath", files -> {
            files.setCanBeConsumed(false);
            files.setVisible(false);
            files.setDescription("Configuration to resolve base version of mapping.txt and R.txt files.");

            project.getDependencies().add(files.getName(), resolveLocator.getDependencyNotation(ArtifactType.SYMBOL));
            project.getDependencies().add(files.getName(), resolveLocator.getDependencyNotation(ArtifactType.MAPPING));
        });
    }

    @Override
    @Nullable
    public File resolveSymbol(ApplicationVariant variant) {
        if (resolverExtension.getVersion() == null) {
            return null;
        }
        final VariantArtifactsLocator resolveLocator = locatorFactory.createLocator(variant, publishExtension, resolverExtension.getVersion());
        Configuration classpath = createResourceClasspath(variant, resolveLocator);


        final Set<ResolvedArtifact> artifacts = classpath.getResolvedConfiguration().getLenientConfiguration()
                .getArtifacts(resolveLocator.getDependencySpec(ArtifactType.SYMBOL));
        final Set<File> symbol = artifacts.stream().filter(resolveLocator.getResolvedArtifactSpec(ArtifactType.SYMBOL))
                .map(ResolvedArtifact::getFile).collect(Collectors.toSet());

        if (symbol.isEmpty()) {
            return null;
        }

        return assertSingleton(symbol);
    }

    private Configuration maybeCreate(String name, Action<? super Configuration> action) {
        Configuration created = project.getConfigurations().findByName(name);
        if (created == null) {
            project.getConfigurations().create(name, action);
        }

        return created;
    }

    @Override
    @Nullable
    public File resolveApk(ApplicationVariant variant) {
        if (resolverExtension.getVersion() == null) {
            return null;
        }
        final VariantArtifactsLocator resolveLocator = locatorFactory.createLocator(variant, publishExtension, resolverExtension.getVersion());

        final String variantName = capitalize(variant.getName());
        final Configuration tinkerResolveApkClasspath = maybeCreate("tinkerResolve" + variantName + "ApkClasspath", files -> {
            files.setCanBeConsumed(false);
            files.setVisible(false);
            files.setDescription("Configuration to resolve base version of apk files.");

            project.getDependencies().add(files.getName(), resolveLocator.getDependencyNotation(ArtifactType.APK));
        });


        final Set<File> apk = tinkerResolveApkClasspath.getResolvedConfiguration().getFiles(resolveLocator.getDependencySpec(ArtifactType.APK));

        if (apk.isEmpty()) {
            return null;
        }

        return assertSingleton(apk, "Cannot find singleton apk file in Maven repository, we found: " + apk + ", ");
    }
}
