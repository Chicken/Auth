plugins {
    `java-library`
    auth.java
}

group = "codes.antti.auth.bluemap.privatelocation"
version = "0.4.0"

dependencies {
    compileOnly(libs.bluemap.api)
    compileOnly(libs.luckperms)
    implementation(project(":Common:Http"))
    implementation(libs.gson)
}
