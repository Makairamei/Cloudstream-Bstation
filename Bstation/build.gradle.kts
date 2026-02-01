apply(plugin = "com.android.library")
apply(plugin = "kotlin-android")

android {
    compileSdk = 30

    defaultConfig {
        minSdk = 21
        targetSdk = 30
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
}

dependencies {
    implementation(project(":app"))
    implementation("org.jetbrains.kotlin:kotlin-stdlib:1.9.0")
    implementation("com.github.blopsr:ala-kotlin-extensions:2.0")
}
