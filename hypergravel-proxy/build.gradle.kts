plugins {
    application
}

dependencies {
    implementation(platform(libs.netty.bom))
    implementation(platform(libs.junit.bom))

    api(project(":hypergravel-api"))

    implementation(libs.bundles.netty)
    
    
    implementation(libs.netty.transport.epoll)
    runtimeOnly(libs.netty.transport.epoll.native)

    implementation(libs.log4j.core)
    runtimeOnly(libs.log4j.slf4j)

    implementation(libs.fastutil)
    implementation(libs.caffeine)

    
    
    
    implementation("com.viaversion:viaversion-common:5.12.0-SNAPSHOT")
    implementation("com.viaversion:viabackwards-common:5.12.0-SNAPSHOT")
    
    
    
    implementation(libs.guava)

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

application {
    mainClass.set("pdx.dev.hypergravel.proxy.HyperGravelBootstrap")
    applicationDefaultJvmArgs = listOf(
        "-XX:+UseZGC",
        "-XX:+ZGenerational",
        "-XX:MaxDirectMemorySize=2G",
        "-Dio.netty.allocator.type=pooled",
        "-Dio.netty.leakDetection.level=disabled",
        "-Dlog4j2.contextSelector=org.apache.logging.log4j.core.async.AsyncLoggerContextSelector",
    )
}

tasks.jar {
    manifest {
        attributes(
            "Main-Class" to "pdx.dev.hypergravel.proxy.HyperGravelBootstrap",
            "Implementation-Title" to "HyperGravel",
            "Implementation-Version" to project.version,
            "Multi-Release" to "true",
        )
    }
}


val shadowJar by tasks.registering(Jar::class) {
    archiveClassifier.set("all")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    manifest.from(tasks.jar.get().manifest)
    from(sourceSets.main.get().output)
    dependsOn(configurations.runtimeClasspath)
    from({
        configurations.runtimeClasspath.get()
            .filter { it.name.endsWith("jar") }
            .map { zipTree(it) }
    }) {
        exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA", "module-info.class")
    }
}

tasks.named("build") { dependsOn(shadowJar) }
