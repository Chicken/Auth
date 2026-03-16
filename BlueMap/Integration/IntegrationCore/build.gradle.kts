plugins {
    `java-library`
    auth.java
}

group = "codes.antti.auth.bluemap.integration"
version = "0.2.1"

dependencies {
    compileOnly(libs.jetbrains.annotations)
    compileOnly(libs.bluemap.api)
    implementation(project(":Common:Http"))
    implementation(libs.gson)
}
