// use an integer for version numbers
version = 7


cloudstream {
    // All of these properties are optional, you can safely remove them

    description = "Live streams of different sports from streamed.su"
    authors = listOf("doGior")

    /**
    * Status int as the following:
    * 0: Down
    * 1: Ok
    * 2: Slow
    * 3: Beta only
    * */
    status = 0
    tvTypes = listOf("Live")


    requiresResources = false

    iconUrl = "https://streamed.su/favicon.png"
}

android {
    compileOptions {
        isCoreLibraryDesugaringEnabled = true
    }
}

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.3")

}
