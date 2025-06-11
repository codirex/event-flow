plugins {
    `java-library`
}
version = "1.0.0"
dependencies {
    api(projects.eventFlow.core)
    implementation("org.openjfx:javafx-graphics:17.0.8:win")
}