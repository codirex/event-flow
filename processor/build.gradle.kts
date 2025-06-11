plugins { `java-library` }

version = "1.0.0"

dependencies {
  implementation(projects.eventFlow.core)
  implementation("com.google.auto.service:auto-service:1.1.1")
  annotationProcessor("com.google.auto.service:auto-service:1.1.1")
  implementation("com.squareup:javapoet:1.13.0")
}
