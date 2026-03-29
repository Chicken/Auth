plugins {
    `java-library`
    auth.java
}

group = "codes.antti.auth.authentication"
version = "0.5.0"

dependencies {
    implementation(project(":Common:Http"))
    implementation(project(":Common:Database"))
}
