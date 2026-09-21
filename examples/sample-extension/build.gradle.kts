plugins {
    `java-library`
}

group = "com.example.hypergravel"
version = "1.0.0"

repositories {
    mavenCentral()
    // The API is published here with: ./gradlew :hypergravel-api:publishToMavenLocal
    mavenLocal()
}

dependencies {
    compileOnly("pdx.dev.hypergravel:hypergravel-api:1.1.0-SNAPSHOT")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(21)
    options.encoding = "UTF-8"
}

// A thin jar is all you need when your extension only uses classes the proxy
// already has (the API, Adventure, log4j). If you bundle third-party libraries,
// use the Shadow plugin (com.gradleup.shadow) so they land in the same jar.
tasks.jar {
    manifest {
        attributes("Implementation-Title" to "sample-hello",
            "Implementation-Version" to project.version)
    }
}

// Copy straight into a proxy's extension directory after build.
tasks.register<Copy>("installExtension") {
    from(tasks.jar)
    into(System.getProperty("hypergravel.extensions") ?: "build/extensions")
}