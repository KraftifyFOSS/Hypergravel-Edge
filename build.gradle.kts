plugins {
    `java-library`
}

allprojects {
    group = "pdx.dev.hypergravel"
    version = "1.1.0-SNAPSHOT"
}

subprojects {
    apply(plugin = "java-library")

    
    
    
    tasks.withType<JavaCompile>().configureEach {
        options.release.set(21)
        options.encoding = "UTF-8"
        options.compilerArgs.addAll(listOf("-Xlint:all,-serial,-processing,-this-escape", "-parameters"))
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
    }

    repositories {
        mavenCentral()
        
        maven("https://repo.viaversion.com")
    }
}
