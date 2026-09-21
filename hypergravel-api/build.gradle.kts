plugins {
    `java-library`
    `maven-publish`
}

dependencies {
    api(platform(libs.junit.bom))

    api(libs.bundles.adventure)
    api(libs.log4j.api)
    api(libs.gson)
    api(libs.nightconfig.core)
    api(libs.nightconfig.toml)

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

publishing {
    publications {
        create<MavenPublication>("hypergravelApi") {
            from(components["java"])
            artifactId = "hypergravel-api"
            pom {
                name.set("HyperGravel API")
                description.set("API for server-side proxy plugins (extensions) on HyperGravel Edge")
                url.set("https://github.com/KraftifyFOSS/Hypergravel-Edge")
            }
        }
    }
}
