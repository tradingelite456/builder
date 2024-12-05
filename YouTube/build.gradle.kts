
// use an integer for version numbers
version = 0

cloudstream {
    // All of these properties are optional, you can safely remove them

    description = "Work In Progress | Videos and playlists from YouTube"
    authors = listOf("doGior")

    /**
     * Status int as the following:
     * 0: Down
     * 1: Ok
     * 2: Slow
     * 3: Beta only
     * */
    status = 2

    tvTypes = listOf("Others")

    requiresResources = true

    iconUrl = "https://www.youtube.com/s/desktop/711fd789/img/logos/favicon_144x144.png"
}

android{
    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {
    implementation("com.google.android.material:material:1.12.0")
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.3")
    implementation("com.github.teamnewpipe:NewPipeExtractor:v0.24.3")
}