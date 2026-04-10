plugins {
    `java-library`
    auth.java
}

group = "codes.antti.auth.bluemap.chat"
version = "0.5.0"

dependencies {
    compileOnly(libs.bluemap.api)
    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)
    implementation(project(":Common:Http"))
    implementation(libs.gson)
}
