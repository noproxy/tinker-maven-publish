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

import com.android.build.gradle.api.ApplicationVariant;
import io.github.noproxy.plugin.tinker.api.VariantArtifactsLocator;
import io.github.noproxy.plugin.tinker.api.VariantArtifactsLocatorFactory;
import org.jetbrains.annotations.NotNull;

public class DefaultVariantArtifactsLocatorFactory implements VariantArtifactsLocatorFactory {
    @NotNull
    @Override
    public VariantArtifactsLocator createLocator(@NotNull ApplicationVariant variant, @NotNull TinkerMavenPublishExtensionInternal extension, String resolveVersion) {
        return new DefaultVariantArtifactsLocator(variant, extension.getGroupId(), extension.getArtifactId(), resolveVersion);
    }
}
