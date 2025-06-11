plugins { id("com.android.library") }

version = "1.0.0"

android {
  namespace = "com.codirex.eventflow.android"
  compileSdk = 34
  defaultConfig { minSdk = 16 }
}

dependencies { api(projects.eventFlow.core) }
