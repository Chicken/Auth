plugins {
    `java-library`
    auth.java
}

group = "codes.antti.auth.common"
version = "0.0.0"

dependencies {
    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)
    implementation(libs.gson)
}
