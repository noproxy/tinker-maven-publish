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

package com.github.noproxy.plugin.tinker.internal;

import com.android.build.gradle.api.ApplicationVariant;
import com.android.build.gradle.api.BaseVariant;

import org.apache.commons.lang3.ObjectUtils;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.ResolvedArtifact;
import org.gradle.api.specs.Spec;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.function.Predicate;

public class DefaultVariantArtifactsLocator implements VariantArtifactsLocator {
    private final BaseVariant variant;
    private final String groupId;
    private final String artifactId;
    private final String bareVersion;

    public DefaultVariantArtifactsLocator(@NotNull ApplicationVariant variant,
                                          @Nullable String groupId,
                                          @Nullable String artifactId,
                                          @Nullable String bareVersion) {
        this.variant = variant;
        this.groupId = ObjectUtils.firstNonNull(groupId, "org.tinker.app");
        this.artifactId = ObjectUtils.firstNonNull(artifactId, variant.getApplicationId());
        this.bareVersion = Objects.requireNonNull(ObjectUtils.firstNonNull(bareVersion, variant.getVersionName()),
                "You must set a version to publish.");
    }


    public DefaultVariantArtifactsLocator(@NotNull ApplicationVariant variant, @NotNull TinkerMavenPublishExtensionInternal extension) {
        this(variant, extension, extension.getVersion());
    }

    public DefaultVariantArtifactsLocator(@NotNull ApplicationVariant variant, @NotNull TinkerMavenPublishExtensionInternal extension, String resolveVersion) {
        this(variant, extension.getGroupId(), extension.getArtifactId(), resolveVersion);
    }


    @NotNull
    @Override
    public String getGroupId() {
        return groupId;
    }

    @NotNull
    @Override
    public String getArtifactId() {
        return artifactId;
    }

    @NotNull
    @Override
    public String getVersion() {
        final StringBuilder version = new StringBuilder(bareVersion);
        final String flavorName = variant.getFlavorName();
        if (!flavorName.isEmpty()) {
            version.append("-").append(flavorName);
        }

        version.append("-").append(variant.getBuildType().getName());
        return version.toString();
    }

    @Nullable
    @Override
    public String getClassifier(ArtifactType type) {
        switch (type) {
            case APK:
                return null;
            case MAPPING:
                return "mapping";
            case SYMBOL:
                return "r";
            default:
                throw new IllegalArgumentException("Unknown ArtifactType: " + type);
        }
    }

    @Override
    @NotNull
    public String getExtension(ArtifactType type) {
        switch (type) {
            case APK:
                return "apk";
            case MAPPING:
            case SYMBOL:
                return "txt";
            default:
                throw new IllegalArgumentException("Unknown ArtifactType: " + type);
        }
    }

    @NotNull
    @Override
    public Object getDependencyNotation(ArtifactType type) {
        final String classifier = getClassifier(type);

        return getGroupId() + ":" + getArtifactId() + ":" + getVersion() +
                (classifier == null ? "" : ":" + classifier) + "@" + getExtension(type);
    }

    @NotNull
    @Override
    public Spec<Dependency> getDependencySpec(ArtifactType type) {
        return dependency -> Objects.equals(dependency.getGroup(), getGroupId())
                && dependency.getName().equals(getArtifactId())
                && Objects.equals(dependency.getVersion(), getVersion());
    }

    @Override
    @NotNull
    public Predicate<ResolvedArtifact> getResolvedArtifactSpec(ArtifactType type) {
        return element -> Objects.equals(element.getClassifier(), getClassifier(type));
    }
}
