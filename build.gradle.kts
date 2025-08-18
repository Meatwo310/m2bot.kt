import dev.kordex.gradle.plugins.docker.file.*
import dev.kordex.gradle.plugins.kordex.DataCollection

plugins {
    distribution

    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)

//    alias(libs.plugins.detekt)

    alias(libs.plugins.kordex.docker)
    alias(libs.plugins.kordex.plugin)
    alias(libs.plugins.ksp.plugin)

    id("java")
}

group = "io.github.meatwo310.m2bot"
version = "0.1.0"

dependencies {
//    detektPlugins(libs.detekt)

    implementation(libs.kotlin.stdlib)
    implementation(libs.kx.ser)

    // Logging dependencies
    implementation(libs.groovy)
    implementation(libs.jansi)
    implementation(libs.logback)
    implementation(libs.logback.groovy)
    implementation(libs.logging)

    testImplementation(kotlin("test"))

    implementation(kotlin("reflect"))
    implementation("com.google.genai:google-genai:1.10.0")

    implementation("org.spongepowered:configurate-hocon:4.2.0")
    implementation("org.spongepowered:configurate-extra-kotlin:4.2.0")
}

distributions {
    main {
        distributionBaseName = project.name

        contents {
            // Copy the LICENSE file into the distribution
            from("LICENSE")

            // Exclude src/main/dist/README.md
            exclude("README.md")
        }
    }
}

kordEx {
    // https://github.com/gradle/gradle/issues/31383
    kordExVersion = libs.versions.kordex.asProvider()

    bot {
        // See https://docs.kordex.dev/data-collection.html
        dataCollection(DataCollection.None)

        mainClass = "io.github.meatwo310.m2bot.AppKt"
    }

    i18n {
        classPackage = "io.github.meatwo310.m2bot.i18n"
        translationBundle = "io/github/meatwo310/m2bot.strings"
    }
}

//detekt {
//    buildUponDefaultConfig = true
//
//    config.from(rootProject.files("detekt.yml"))
//}

// Automatically generate a Dockerfile. Set `generateOnBuild` to `false` if you'd prefer to manually run the
// `createDockerfile` task instead of having it run whenever you build.
docker {
    // Create the Dockerfile in the root folder.
    file(rootProject.file("Dockerfile"))

    commands {
        // Each function (aside from comment/emptyLine) corresponds to a Dockerfile instruction.
        // See: https://docs.docker.com/reference/dockerfile/

        from("openjdk:21-jdk-slim")

        emptyLine()

        comment("Create required directories")
        runShell("mkdir -p /bot/plugins")
        runShell("mkdir -p /bot/data")
        runShell("mkdir -p /dist/out")

        emptyLine()

        // Add volumes for locations that you need to persist. This is important!
        comment("Declare required volumes")
        volume("/bot/data")  // Storage for data files
        volume("/bot/plugins")  // Plugin ZIP/JAR location

        emptyLine()

        comment("Copy the distribution files into the container")
        copy("build/distributions/${project.name}-${project.version}.tar", "/dist")

        emptyLine()

        comment("Extract the distribution files, and prepare them for use")
        runShell("tar -xf /dist/${project.name}-${project.version}.tar -C /dist/out")

        if (file("src/main/dist/plugins").isDirectory) {
            runShell("mv /dist/out/${project.name}-${project.version}/plugins/* /bot/plugins")
        }

        runShell("chmod +x /dist/out/${project.name}-${project.version}/bin/$name")

        emptyLine()

        comment("Clean up unnecessary files")
        runShell("rm /dist/${project.name}-${project.version}.tar")

        emptyLine()

        comment("Set the correct working directory")
        workdir("/bot")

        emptyLine()

        comment("Run the distribution start script")
        entryPointExec("/dist/out/${project.name}-${project.version}/bin/$name")
    }
}

tasks.named<Jar>("jar") {
    dependsOn("kspTestKotlin")
}

tasks.withType<JavaCompile> {
    options.compilerArgs.add("-parameters")
}
