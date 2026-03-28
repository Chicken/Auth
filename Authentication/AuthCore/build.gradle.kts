plugins {
    `java-library`
    auth.java
}

group = "codes.antti.auth.authentication"
version = "0.4.0"

dependencies {
    compileOnly(libs.jetbrains.annotations)
    implementation(project(":Common:Http"))
    implementation(project(":Common:Database"))
}
