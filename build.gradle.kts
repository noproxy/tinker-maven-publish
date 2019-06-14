/*
 *    Copyright 2019 the original author or authors.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

plugins {
    `java-gradle-plugin`
    groovy
    id("com.gradle.plugin-publish") version "0.10.1"
}

dependencies {
    implementation(gradleApi())
    implementation("org.jetbrains:annotations:13.0")
    implementation("org.apache.commons:commons-lang3:3.9")
    implementation("com.tencent.tinker:tinker-patch-gradle-plugin:1.9.13")

    testImplementation(project(":test-kit-ext"))
}

repositories {
    google()
}

dependencies {
    implementation("com.android.tools.build:gradle:3.3.2")
}

tasks.withType(Test::class.java) {
    doFirst {
        file("build/tmp/testfiles").deleteRecursively()
    }
}
// publishing
assert("tinker-maven-publish" == name)
assert("io.github.noproxy" == group)
version = "0.0.6"

apply {
    from(rootProject.file("gradle/publishing.gradle"))
}

gradlePlugin {
    plugins {
        create("tinkerMavenPublish") {
            id = "$group.tinker-maven-publish"
            implementationClass = "com.github.noproxy.plugin.tinker.TinkerMavenPublishPlugin"
        }
    }
}

pluginBundle {
    website = "https://noproxy.github.io/tinker-maven-publish"
    vcsUrl = "https://github.com/noproxy/tinker-maven-publish"
    description = "A plugin to publish artifacts(android apk, mapping.txt and R.txt) required by Tencent Tinker."
    tags = listOf("publish", "maven", "android")

    plugins {
        getByName("tinkerMavenPublish") {
            displayName = "Tinker Maven Publish plugin"
        }
    }
}