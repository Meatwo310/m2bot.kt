import dev.kordex.gradle.plugins.docker.file.*
import dev.kordex.gradle.plugins.kordex.DataCollection

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)

    alias(libs.plugins.shadow)

    alias(libs.plugins.kordex.docker)
    alias(libs.plugins.kordex.plugin)
}

group = "io.github.meatwo310.m2bot"
version = "0.1.0"

dependencies {
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

        runShell("mkdir -p /bot/plugins")
        runShell("mkdir -p /bot/data")

        emptyLine()

        copy("build/libs/$name-*-all.jar", "/bot/bot.jar")

        emptyLine()

        // Add volumes for locations that you need to persist. This is important!
        volume("/bot/data")  // Storage for data files
        volume("/bot/plugins")  // Plugin ZIP/JAR location

        emptyLine()

        workdir("/bot")

        emptyLine()

        entryPointExec(
            "java", "-Xms2G", "-Xmx2G",
            "-jar", "/bot/bot.jar"
        )
    }
}
