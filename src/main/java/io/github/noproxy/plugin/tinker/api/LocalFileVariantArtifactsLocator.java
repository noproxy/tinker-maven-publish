package io.github.noproxy.plugin.tinker.api;

import io.github.noproxy.plugin.tinker.internal.ArtifactType;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.ResolvedArtifact;
import org.gradle.api.artifacts.SelfResolvingDependency;
import org.gradle.api.specs.Spec;
import org.gradle.api.specs.Specs;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Objects;
import java.util.function.Predicate;

public class LocalFileVariantArtifactsLocator implements VariantArtifactsLocator {
    private final Project project;
    @NotNull
    private final File apk;
    @Nullable
    private final File mapping;
    @Nullable
    private final File symbol;

    public LocalFileVariantArtifactsLocator(Project project, @NotNull File apk, @Nullable File mapping, @Nullable File symbol) {
        this.project = project;
        this.apk = apk;
        this.mapping = mapping;
        this.symbol = symbol;
    }

    @Nullable
    @Override
    public Object getDependencyNotation(ArtifactType type) {
        final File file = getArtifactFile(type);
        if (file == null) {
            return null;
        }

        return project.files(file);
    }

    private File getArtifactFile(ArtifactType type) {
        switch (type) {
            case APK:
                return Objects.requireNonNull(apk, "apk file is null");
            case MAPPING:
                return mapping;
            case SYMBOL:
                return symbol;
            default:
                return null;
        }
    }

    @NotNull
    @Override
    public Spec<Dependency> getDependencySpec(ArtifactType type) {
        return dependency -> (dependency instanceof SelfResolvingDependency) && ((SelfResolvingDependency) dependency).resolve().contains(getArtifactFile(type));
    }

    @NotNull
    @Override
    public Predicate<ResolvedArtifact> getResolvedArtifactSpec(ArtifactType type) {
        return resolvedArtifact -> resolvedArtifact.getFile().equals(getArtifactFile(type));
    }
}
