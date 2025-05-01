plugins {
  java
  application
}

dependencies {
  implementation(project(":common"))
  implementation(libs.jackson.databind)
  implementation(libs.jackson.parameternames)
  testImplementation(libs.junit.jupiter)
}

application {
  mainClass.set("com.example.app1.App1")
}