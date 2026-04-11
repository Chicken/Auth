plugins {
    `java-library`
    auth.java
}

group = "codes.antti.auth.bluemap.privatemaps"
version = "0.3.0"

dependencies {
    compileOnly(libs.luckperms)
    implementation(project(":Common:Http"))
    implementation(libs.gson)
}
