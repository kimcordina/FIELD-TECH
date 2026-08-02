plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.google.services)
    id("com.google.firebase.crashlytics")
}

android {
    namespace = "com.example.fieldtechv20kc"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.ncordina.fieldtech2"
        minSdk = 29
        targetSdk = 36
        versionCode = 9
        versionName = "9.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        multiDexEnabled = true
        
        // Company ID for Firestore path
        buildConfigField("String", "COMPANY_ID", "\"NCORDINA\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

// Custom task to copy APKs to MyApks folder in Downloads
tasks.register<Copy>("copyApksToDownloads") {
    description = "Copy APK files to MyApks folder in Downloads"
    group = "build"
    
    // Find all APK files in the build outputs
    from("${layout.buildDirectory.get()}/outputs/apk/debug") {
        include("*.apk")
        rename { fileName ->
            val timestamp = System.currentTimeMillis()
            "FieldTech_Debug_${timestamp}.apk"
        }
    }
    from("${layout.buildDirectory.get()}/outputs/apk/release") {
        include("*.apk")
        rename { fileName ->
            val timestamp = System.currentTimeMillis()
            "FieldTech_Release_${timestamp}.apk"
        }
    }
    
    // Copy to MyApks folder in Downloads
    val downloadsPath = System.getProperty("user.home") + "/Downloads/MyApks"
    into(downloadsPath)
    
    // Create directory if it doesn't exist
    doFirst {
        file(downloadsPath).mkdirs()
        println("DEBUG: Created/verified MyApks directory: $downloadsPath")
    }
    
    // Log the copy operation
    doLast {
        println("DEBUG: APK files copied to: $downloadsPath")
        file(downloadsPath).listFiles()?.forEach { file ->
            println("DEBUG: Copied APK: ${file.name} (${file.length()} bytes)")
        }
    }
}

// Make the copy task run after assemble tasks
afterEvaluate {
    tasks.named("assembleDebug") {
        finalizedBy("copyApksToDownloads")
    }

    tasks.named("assembleRelease") {
        finalizedBy("copyApksToDownloads")
    }
}

// Manual task to copy existing APKs (can be run independently)
tasks.register<Copy>("backupApks") {
    description = "Manually copy all existing APK files to MyApks folder"
    group = "build"
    
    // Find all APK files in the build outputs
    from("${layout.buildDirectory.get()}/outputs/apk/debug") {
        include("*.apk")
        rename { fileName ->
            val timestamp = System.currentTimeMillis()
            "FieldTech_Debug_${timestamp}.apk"
        }
    }
    from("${layout.buildDirectory.get()}/outputs/apk/release") {
        include("*.apk")
        rename { fileName ->
            val timestamp = System.currentTimeMillis()
            "FieldTech_Release_${timestamp}.apk"
        }
    }
    
    // Copy to MyApks folder in Downloads
    val downloadsPath = System.getProperty("user.home") + "/Downloads/MyApks"
    into(downloadsPath)
    
    // Create directory if it doesn't exist
    doFirst {
        file(downloadsPath).mkdirs()
        println("DEBUG: Created/verified MyApks directory: $downloadsPath")
    }
    
    // Log the copy operation
    doLast {
        println("DEBUG: APK backup completed!")
        println("DEBUG: APK files copied to: $downloadsPath")
        file(downloadsPath).listFiles()?.forEach { file ->
            println("DEBUG: Backed up APK: ${file.name} (${file.length()} bytes)")
        }
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation("androidx.multidex:multidex:2.0.1")
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation("androidx.compose.material:material-icons-extended:1.5.4")
    
    // Navigation
    implementation(libs.androidx.navigation.compose)
    
    // Room Database
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    
    // ViewModel
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    
    // WorkManager
    implementation("androidx.work:work-runtime-ktx:2.9.0")
    
    // PDF Generation (explicit modules — embeds images at full resolution)
    implementation("com.itextpdf:kernel:7.2.5")
    implementation("com.itextpdf:io:7.2.5")
    implementation("com.itextpdf:layout:7.2.5")
    
    // Permissions
    implementation(libs.com.google.accompanist.permissions)
    
    // Image loading
    implementation("io.coil-kt:coil-compose:2.6.0")
    
    // EXIF data handling
    implementation("androidx.exifinterface:exifinterface:1.3.6")
    
    // JSON serialization
    implementation("com.google.code.gson:gson:2.10.1")
    
    // CameraX
    implementation("androidx.camera:camera-core:1.3.1")
    implementation("androidx.camera:camera-camera2:1.3.1")
    implementation("androidx.camera:camera-lifecycle:1.3.1")
    implementation("androidx.camera:camera-view:1.3.1")
    implementation("androidx.camera:camera-extensions:1.3.1")
    
    // Guava (required by CameraX)
    implementation("com.google.guava:guava:31.1-android")
    
    // CSV parsing
    implementation("com.github.doyaaaaaken:kotlin-csv-jvm:1.9.3")
    
    // XLSX parsing
    implementation("org.apache.poi:poi:5.2.5")
    implementation("org.apache.poi:poi-ooxml:5.2.5")
    
    // Location services
    implementation("com.google.android.gms:play-services-location:21.2.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.7.3")
    
    // Firebase
    implementation(platform("com.google.firebase:firebase-bom:33.5.1"))
    implementation("com.google.firebase:firebase-auth-ktx")
    implementation("com.google.firebase:firebase-firestore-ktx")
    implementation("com.google.firebase:firebase-storage-ktx")
    implementation("com.google.firebase:firebase-messaging-ktx")
    implementation("com.google.firebase:firebase-functions-ktx")
    implementation("com.google.firebase:firebase-crashlytics-ktx")
    implementation("com.google.firebase:firebase-analytics-ktx")
    
    // Coil for image loading from URLs
    implementation("io.coil-kt:coil-compose:2.5.0")
    
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}