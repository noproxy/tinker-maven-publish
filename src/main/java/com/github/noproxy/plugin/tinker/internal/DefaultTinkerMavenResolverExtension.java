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

import com.github.noproxy.plugin.tinker.api.TinkerMavenResolver;

import org.jetbrains.annotations.NotNull;

public class DefaultTinkerMavenResolverExtension implements TinkerMavenResolverExtensionInternal {
    private String versionOfBaseApk;
    private final TinkerMavenResolver resolver;

    public DefaultTinkerMavenResolverExtension(TinkerMavenResolver resolver) {
        this.resolver = resolver;
    }

    @Override

    public String getVersion() {
        return versionOfBaseApk;
    }

    @Override
    public void setVersion(String version) {
        this.versionOfBaseApk = version;
    }

    @NotNull
    @Override
    public TinkerMavenResolver resolver() {
        return resolver;
    }
}
