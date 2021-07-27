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

package io.github.noproxy.plugin.tinker;

import com.android.build.gradle.AppExtension;
import com.android.build.gradle.AppPlugin;
import com.android.build.gradle.api.ApkVariant;
import com.android.build.gradle.api.ApplicationVariant;
import com.android.build.gradle.api.BaseVariantOutput;
import com.tencent.tinker.build.gradle.extension.TinkerBuildConfigExtension;
import com.tencent.tinker.build.gradle.extension.TinkerPatchExtension;
import com.tencent.tinker.build.gradle.task.TinkerPatchSchemaTask;
import com.tencent.tinker.build.gradle.task.TinkerProguardConfigTask;
import com.tencent.tinker.build.gradle.task.TinkerResourceIdTask;
import io.github.noproxy.plugin.tinker.api.Resolver;
import io.github.noproxy.plugin.tinker.api.TinkerMavenPublishExtension;
import io.github.noproxy.plugin.tinker.api.TinkerMavenResolverExtension;
import io.github.noproxy.plugin.tinker.api.VariantArtifactsLocator;
import io.github.noproxy.plugin.tinker.internal.*;
import org.gradle.api.Action;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.logging.Logger;
import org.gradle.api.plugins.ExtensionAware;
import org.gradle.api.publish.PublishingExtension;
import org.gradle.api.publish.maven.MavenArtifact;
import org.gradle.api.publish.maven.MavenPublication;
import org.gradle.api.publish.maven.plugins.MavenPublishPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

import static org.codehaus.groovy.runtime.StringGroovyMethods.capitalize;

@SuppressWarnings("unused")
public class TinkerMavenPublishPlugin implements Plugin<Project> {
    private static void withApplicationVariants(Project project, Action<? super ApplicationVariant> action) {
        project.getPlugins().withId("com.android.application", plugin -> {
            final AppPlugin appPlugin = (AppPlugin) plugin;
            project.getExtensions().getByType(AppExtension.class).getApplicationVariants().all(action);
        });
    }

    @Override
    public void apply(@NotNull Project project) {
        project.getPluginManager().apply(MavenPublishPlugin.class);

        final TinkerMavenPublishExtensionInternal publishExtension =
                (TinkerMavenPublishExtensionInternal) project.getExtensions().create(TinkerMavenPublishExtension.class,
                        "tinkerPublish", DefaultTinkerMavenPublishExtension.class);

        final TinkerMavenResolverExtensionInternal resolverExtension = (TinkerMavenResolverExtensionInternal) project.getExtensions()
                .create(TinkerMavenResolverExtension.class, "tinkerResolver",
                        DefaultTinkerMavenResolverExtension.class, project);

        configurePublishing(project, publishExtension);

        Resolver resolver = ((ExtensionAware) resolverExtension).getExtensions().create(Resolver.class, "api", DefaultResolver.class,
                project, resolverExtension, publishExtension);

        project.getPluginManager().withPlugin("com.tencent.tinker.patch", appliedPlugin -> project.afterEvaluate(ignored -> configureResolvingForTinker(project, resolver, resolverExtension)));
    }

    private void configurePublishing(Project project, TinkerMavenPublishExtensionInternal publishExtension) {
        withApplicationVariants(project, variant -> {
            final PublishingExtension publishing = project.getExtensions().getByType(PublishingExtension.class);

            final MavenVariantArtifactsLocator locator = publishExtension.getLocatorFactory().createMavenLocator(variant, publishExtension);
            variant.getOutputs().all(baseVariantOutput -> configuringAndroidArtifacts(project, variant, publishing, locator, baseVariantOutput));
        });
    }

    private File findResguardApk(Logger logger, BaseVariantOutput baseVariantOutput) {
        final File apk = baseVariantOutput.getOutputFile();
        final String basename = apk.getName().split("\\.(?=[^.]+$)")[0];

        final File resguardDir = new File(apk.getParentFile(), "AndResGuard_" + basename);
        final File primaryApk = new File(resguardDir, basename + "_aligned_unsigned" + ".apk");
        if (primaryApk.exists()) {
            logger.quiet("we found resguard apk: " + primaryApk);
            return primaryApk;
        }


        final File[] files = resguardDir.listFiles();
        if (files == null) {
            return null;
        }
        for (File maybeUsedApk : files) {
            if (maybeUsedApk.isFile() && maybeUsedApk.getName().endsWith(".apk")) {
                if (maybeUsedApk.getName().startsWith(basename)) {
                    logger.warn("we found dir " + resguardDir + ", but the primary apk not found:" + primaryApk + ", use: " + maybeUsedApk);
                    return maybeUsedApk;
                }
            }
        }
        logger.warn("we found dir " + resguardDir + ", but no apk found");
        return null;
    }

    private void configuringAndroidArtifacts(Project project, ApplicationVariant variant,
                                             PublishingExtension publishing, MavenVariantArtifactsLocator locator,
                                             BaseVariantOutput baseVariantOutput) {
        final File originApk = baseVariantOutput.getOutputFile();
        final File resguardApk = findResguardApk(project.getLogger(), baseVariantOutput);

        final File apk = Optional.ofNullable(resguardApk).orElse(originApk);

        final File mapping = computeMappingFile(project, variant, originApk);
        final File symbol = computeSymbolFile(project, variant, originApk);

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
                project.getLogger().info("TinkerMavenPublish: skip publish mapping.txt for '" + variant.getName() + "' because minifyEnabled = false");
            }
            baseVariantOutput.getProcessResourcesProvider().configure(processAndroidResources -> {
                processAndroidResources.doLast(task -> {
                    if (symbol.exists()) {
                        final MavenArtifact symbolArtifact = publication.artifact(symbol, artifact -> {
                            artifact.setExtension(locator.getExtension(ArtifactType.SYMBOL));
                            artifact.setClassifier(locator.getClassifier(ArtifactType.SYMBOL));
                        });
                    } else {
                        project.getLogger().warn("TinkerMavenPublish: skip publish R.txt for '" + variant.getName() + "' because file not exists");
                    }
                });
            });
        });
    }

    private void configureResolvingForTinker(Project project, Resolver resolver, TinkerMavenResolverExtensionInternal resolverExtension) {
        final TinkerPatchExtension tinkerPatch = project.getExtensions().getByType(TinkerPatchExtension.class);
        final TinkerBuildConfigExtension tinkerBuildConfig = ((ExtensionAware) tinkerPatch).getExtensions().getByType(TinkerBuildConfigExtension.class);
        withApplicationVariants(project, variant -> {
            if (!tinkerPatch.isTinkerEnable()) {
                return;
            }

            final String variantName = capitalize(variant.getName());
            task(project, "tinkerPatch" + variantName, TinkerPatchSchemaTask.class, tinkerPatchSchemaTask -> {
                tinkerPatchSchemaTask.doFirst(ignored -> tinkerPatch.setOldApk(Objects.requireNonNull(resolver.resolveApk(variant),
                        "Cannot find base apk file in Maven repository").getAbsolutePath()));
            });
            maybeTask(project, "tinkerProcess" + variantName + "Proguard", TinkerProguardConfigTask.class, tinkerProguardConfigTask -> {
                tinkerProguardConfigTask.doFirst(task -> {
                    if (resolverExtension.isIgnoreMapping()) {
                        project.getLogger().warn("skip resolving the mapping.txt file because ignoreMapping = true");
                        return;
                    }

                    File mapping = resolver.resolveMapping(variant);
                    if (mapping == null) {
                        project.getLogger().warn("Can not find the mapping.txt file in Maven Repository, continue build without mapping file.");
                        return;
                    }

                    tinkerBuildConfig.setApplyMapping(mapping.getAbsolutePath());
                    tinkerBuildConfig.setUsingResourceMapping(true);
                });
            });
            task(project, "tinkerProcess" + variantName + "ResourceId", TinkerResourceIdTask.class, tinkerResourceIdTask -> {
                tinkerResourceIdTask.doFirst(task -> {
                    final File symbol = resolver.resolveSymbol(variant);
                    if (symbol == null) {
                        project.getLogger().warn("Can not find the R.txt file in Maven Repository, continue build without R file.");
                        return;
                    }

                    tinkerBuildConfig.setApplyResourceMapping(symbol.getAbsolutePath());
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

    private File computeMappingFile(Project project, ApkVariant variant, final File apk) {
        return variant.getMappingFile();
    }

    private File computeSymbolFile(Project project,ApkVariant variant, final File apk) {
        return project.file("build/intermediates/runtime_symbol_list/" + variant.getName() + "/R.txt");
    }
}
