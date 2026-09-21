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
