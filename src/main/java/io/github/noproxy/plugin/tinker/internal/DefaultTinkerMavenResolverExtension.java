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

package io.github.noproxy.plugin.tinker.internal;

import io.github.noproxy.plugin.tinker.api.VariantArtifactsLocatorFactory;
import org.gradle.api.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.inject.Inject;
import java.io.File;

public class DefaultTinkerMavenResolverExtension implements TinkerMavenResolverExtensionInternal {
    private final Project project;
    private String versionOfBaseApk;
    private VariantArtifactsLocatorFactory locatorFactory;
    private File apk;
    private File mapping;
    private File symbol;

    @Inject
    public DefaultTinkerMavenResolverExtension(Project project) {
        this.project = project;
    }

    @Override
    public String getVersion() {
        return versionOfBaseApk;
    }

    @Override
    public void setVersion(@NotNull String version) {
        this.versionOfBaseApk = version;
    }

    @NotNull
    @Override
    public VariantArtifactsLocatorFactory getLocatorFactory() {
        if (locatorFactory == null) {
            return new DefaultVariantArtifactsLocatorFactory();
        }

        return locatorFactory;
    }

    @Override
    public void setLocatorFactory(@Nullable VariantArtifactsLocatorFactory factory) {
        this.locatorFactory = factory;
    }

    @Override
    public File getSymbol() {
        return symbol;
    }

    @Override
    public void setSymbol(Object symbol) {
        this.symbol = project.file(symbol);
    }

    @Override
    public File getMapping() {
        return mapping;
    }

    @Override
    public void setMapping(Object mapping) {
        this.mapping = project.file(mapping);
    }

    @Override
    public File getApk() {
        return apk;
    }

    @Override
    public void setApk(@NotNull Object apk) {
        this.apk = project.file(apk);
    }
}
