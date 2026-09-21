rootProject.name = "hypergravel"

include("hypergravel-api")
include("hypergravel-proxy")

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        maven("https://repo.viaversion.com")
        maven("https://repo.papermc.io/repository/maven-public/")
    }
}
