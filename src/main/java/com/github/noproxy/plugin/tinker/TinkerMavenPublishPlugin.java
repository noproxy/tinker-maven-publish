/*
 * Copyright 2019 the original author or authors.
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

package com.github.noproxy.plugin.tinker;

import com.android.build.gradle.AppExtension;
import com.android.build.gradle.AppPlugin;
import com.android.build.gradle.api.ApplicationVariant;
import com.github.noproxy.plugin.tinker.api.TinkerMavenPublishExtension;
import com.github.noproxy.plugin.tinker.api.TinkerMavenResolver;
import com.github.noproxy.plugin.tinker.api.TinkerMavenResolverExtension;
import com.github.noproxy.plugin.tinker.internal.ArtifactType;
import com.github.noproxy.plugin.tinker.internal.DefaultTinkerMavenPublishExtension;
import com.github.noproxy.plugin.tinker.internal.DefaultTinkerMavenResolver;
import com.github.noproxy.plugin.tinker.internal.DefaultTinkerMavenResolverExtension;
import com.github.noproxy.plugin.tinker.internal.DefaultVariantArtifactsLocator;
import com.github.noproxy.plugin.tinker.internal.TinkerMavenPublishExtensionInternal;
import com.github.noproxy.plugin.tinker.internal.TinkerMavenResolverExtensionInternal;
import com.github.noproxy.plugin.tinker.internal.VariantArtifactsLocator;
import com.google.common.base.Preconditions;
import com.tencent.tinker.build.gradle.extension.TinkerBuildConfigExtension;
import com.tencent.tinker.build.gradle.extension.TinkerPatchExtension;
import com.tencent.tinker.build.gradle.task.TinkerPatchSchemaTask;
import com.tencent.tinker.build.gradle.task.TinkerProguardConfigTask;
import com.tencent.tinker.build.gradle.task.TinkerResourceIdTask;

import org.gradle.api.Action;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ResolvedArtifact;
import org.gradle.api.plugins.ExtensionAware;
import org.gradle.api.publish.PublishingExtension;
import org.gradle.api.publish.maven.MavenArtifact;
import org.gradle.api.publish.maven.MavenPublication;
import org.gradle.api.publish.maven.plugins.MavenPublishPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;

import static org.codehaus.groovy.runtime.StringGroovyMethods.capitalize;

@SuppressWarnings("unused")
public class TinkerMavenPublishPlugin implements Plugin<Project> {
    private static void withApplicationVariants(Project project, Action<? super ApplicationVariant> action) {
        project.getPlugins().withId("com.android.application", plugin -> {
            final AppPlugin appPlugin = (AppPlugin) plugin;
            project.getExtensions().getByType(AppExtension.class).getApplicationVariants().all(action);
        });
    }

    private static <T> T assertSingleton(Set<T> collections) {
        return assertSingleton(collections, null);
    }

    private static <T> T assertSingleton(Set<T> collections, String msg) {
        Preconditions.checkArgument(collections.size() == 1, msg);
        return collections.stream().findFirst().get();
    }

    @Override
    public void apply(@NotNull Project project) {
        project.getPluginManager().apply(MavenPublishPlugin.class);

        final TinkerMavenPublishExtensionInternal publishExtension =
                (TinkerMavenPublishExtensionInternal) project.getExtensions().create(TinkerMavenPublishExtension.class,
                        "tinkerPublish", DefaultTinkerMavenPublishExtension.class);

        final TinkerMavenResolver resolver = new DefaultTinkerMavenResolver();

        final TinkerMavenResolverExtensionInternal tinkerResolverExtension = (TinkerMavenResolverExtensionInternal) project.getExtensions()
                .create(TinkerMavenResolverExtension.class, "tinkerResolver",
                        DefaultTinkerMavenResolverExtension.class, resolver);

        configurePublishing(project, publishExtension);

        project.getPluginManager().withPlugin("com.tencent.tinker.patch", appliedPlugin -> {
            project.afterEvaluate(ignored -> configureResolvingForTinker(project, tinkerResolverExtension, resolver, publishExtension));
        });
    }

    private void configurePublishing(Project project, TinkerMavenPublishExtensionInternal baseExtension) {
        withApplicationVariants(project, variant -> {
            final PublishingExtension publishing = project.getExtensions().getByType(PublishingExtension.class);

            final VariantArtifactsLocator locator = new DefaultVariantArtifactsLocator(variant, baseExtension);

            variant.getOutputs().all(baseVariantOutput -> {
                final File apk = baseVariantOutput.getOutputFile();
                final File mapping = computeMappingFile(project, apk);
                final File symbol = computeSymbolFile(project, apk);

                publishing.getPublications().create("App" + capitalize(variant.getName()), MavenPublication.class, publication -> {
                    publication.setGroupId(locator.getGroupId());
                    publication.setArtifactId(locator.getArtifactId());
                    publication.setVersion(locator.getVersion());

                    publication.artifact(apk, artifact -> {
                        artifact.setExtension(locator.getExtension(ArtifactType.APK));
                        artifact.setClassifier(locator.getClassifier(ArtifactType.APK));
                    });
                    if (variant.getBuildType().isMinifyEnabled()) {
                        publication.artifact(mapping, artifact -> {
                            artifact.setExtension(locator.getExtension(ArtifactType.MAPPING));
                            artifact.setClassifier(locator.getClassifier(ArtifactType.MAPPING));
                        });
                    } else {
                        project.getLogger().info("TinkerMavenPublish: skip publish mapping.txt for '\" + variant.getName() + \"' because minifyEnabled = false");
                    }
                    baseVariantOutput.getProcessResourcesProvider().configure(processAndroidResources -> {
                        processAndroidResources.doLast(task -> {
                            if (symbol.exists()) {
                                final MavenArtifact symbolArtifact = publication.artifact(symbol, artifact -> {
                                    artifact.setExtension(locator.getExtension(ArtifactType.SYMBOL));
                                    artifact.setClassifier(locator.getClassifier(ArtifactType.SYMBOL));
                                });
                            } else {
                                project.getLogger().warn("TinkerMavenPublish: skip publish R.txt for '\" + variant.getName() + \"' because file not exists");
                            }
                        });
                    });
                });
            });
        });
    }

    private void configureResolvingForTinker(Project project,
                                             TinkerMavenResolverExtensionInternal resolverExtension,
                                             TinkerMavenResolver resolver,
                                             TinkerMavenPublishExtensionInternal publishExtension) {
        final String resolveVersion = resolverExtension.getVersion();
        if (resolveVersion == null) {
            project.getLogger().lifecycle("TinkerMavenPublish: skip setup old apk for tinker because 'tinkerResolver.version' = null");
            return;
        }

        // use separate configuration to resolve apk, because for other file, we use lenientConfiguration to ignore resolve error.
        // but for the apk, we want gradle throw exception
        final Configuration tinkerResolveApkClasspath = project.getConfigurations().create("tinkerResolveApkClasspath", files -> {
            files.setCanBeConsumed(false);
            files.setVisible(false);
            files.setDescription("Configuration to resolve base version of apk files.");
        });
        final Configuration tinkerResolveClasspath = project.getConfigurations().create("tinkerResolveClasspath", files -> {
            files.setCanBeConsumed(false);
            files.setVisible(false);
            files.setDescription("Configuration to resolve base version of mapping.txt and R.txt files.");
        });

        final TinkerPatchExtension tinkerPatch = project.getExtensions().getByType(TinkerPatchExtension.class);
        final TinkerBuildConfigExtension tinkerBuildConfig = ((ExtensionAware) tinkerPatch).getExtensions().getByType(TinkerBuildConfigExtension.class);
        withApplicationVariants(project, variant -> {
            final DefaultVariantArtifactsLocator resolveLocator = new DefaultVariantArtifactsLocator(variant, publishExtension, resolveVersion);
            project.getDependencies().add(tinkerResolveApkClasspath.getName(), resolveLocator.getDependencyNotation(ArtifactType.APK));

            project.getDependencies().add(tinkerResolveClasspath.getName(), resolveLocator.getDependencyNotation(ArtifactType.SYMBOL));
            project.getDependencies().add(tinkerResolveClasspath.getName(), resolveLocator.getDependencyNotation(ArtifactType.MAPPING));


            final String variantName = capitalize(variant.getName());
            task(project, "tinkerPatch" + variantName, TinkerPatchSchemaTask.class, tinkerPatchSchemaTask -> {
                tinkerPatchSchemaTask.doFirst(ignored -> {
                    final Set<File> apk = tinkerResolveApkClasspath.getResolvedConfiguration().getFiles(resolveLocator.getDependencySpec(ArtifactType.APK));
                    tinkerPatch.setOldApk(assertSingleton(apk, "Cannot find singleton apk file in Maven repository, we found: " + apk + ", ").getAbsolutePath());
                });
            });
            maybeTask(project, "tinkerProcess" + variantName + "Proguard", TinkerProguardConfigTask.class, tinkerProguardConfigTask -> {
                tinkerProguardConfigTask.doFirst(task -> {
                    final Set<ResolvedArtifact> artifacts = tinkerResolveClasspath.getResolvedConfiguration().getLenientConfiguration()
                            .getArtifacts(resolveLocator.getDependencySpec(ArtifactType.MAPPING));

                    final Set<File> mappings = artifacts.stream().filter(resolveLocator.getResolvedArtifactSpec(ArtifactType.MAPPING))
                            .map(ResolvedArtifact::getFile).collect(Collectors.toSet());
                    if (mappings.isEmpty()) {
                        project.getLogger().warn("Can not find the mapping.txt file in Maven Repository, continue build without mapping file.");
                        return;
                    }
                    final File mapping = assertSingleton(mappings);
                    tinkerBuildConfig.setApplyMapping(mapping.getAbsolutePath());
                    tinkerBuildConfig.setUsingResourceMapping(true);
                });
            });
            task(project, "tinkerProcess" + variantName + "ResourceId", TinkerResourceIdTask.class, tinkerResourceIdTask -> {
                tinkerResourceIdTask.doFirst(task -> {
                    final Set<ResolvedArtifact> artifacts = tinkerResolveClasspath.getResolvedConfiguration().getLenientConfiguration()
                            .getArtifacts(resolveLocator.getDependencySpec(ArtifactType.SYMBOL));
                    final Set<File> symbol = artifacts.stream().filter(resolveLocator.getResolvedArtifactSpec(ArtifactType.SYMBOL))
                            .map(ResolvedArtifact::getFile).collect(Collectors.toSet());

                    if (symbol.isEmpty()) {
                        project.getLogger().warn("Can not find the R.txt file in Maven Repository, continue build without R file.");
                        return;
                    }

                    tinkerBuildConfig.setApplyResourceMapping(assertSingleton(symbol).getAbsolutePath());
                });
            });
        });

    }

    @NotNull
    private <T extends Task> T task(Project project, String name, Class<T> type, Action<? super T> action) {
        return project.getTasks().withType(type).getByName(name, action);
    }


    @Nullable
    private <T extends Task> T maybeTask(Project project, String name, Class<T> type, Action<? super T> action) {
        final T task = project.getTasks().withType(type).findByName(name);
        if (task != null) {
            action.execute(task);
        }

        return task;
    }

    private File computeMappingFile(Project project, final File apk) {
        final Path relativizeToDir = project.file("build/outputs/apk").toPath().relativize(apk.getParentFile().toPath());
        final Path mapping = project.file("build/outputs/mapping").toPath().resolve(relativizeToDir).resolve("mapping.txt");

        return mapping.toFile();
    }

    private File computeSymbolFile(Project project, final File apk) {
        final Path relativizeToDir = project.file("build/outputs/apk").toPath().relativize(apk.getParentFile().toPath());
        final Path mapping = project.file("build/intermediates/symbols").toPath().resolve(relativizeToDir).resolve("R.txt");

        return mapping.toFile();
    }
}
