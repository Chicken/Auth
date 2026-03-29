plugins {
    `java-library`
    auth.java
}

group = "codes.antti.auth.bluemap.integration"
version = "0.3.0"

dependencies {
    compileOnly(libs.bluemap.api)
    implementation(project(":Common:Http"))
    implementation(libs.gson)
}
