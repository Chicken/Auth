plugins {
    `java-library`
    auth.java
}

group = "codes.antti.auth.authorization"
version = "0.5.0"

dependencies {
    compileOnly(libs.luckperms)
    implementation(project(":Common:Http"))
}
