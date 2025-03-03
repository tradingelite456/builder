// use an integer for version numbers
version = 1


cloudstream {
    // All of these properties are optional, you can safely remove them

    description = "Test. This plugin may or may not be supported in the future"
    authors = listOf("doGior")

    /**
    * Status int as the following:
    * 0: Down
    * 1: Ok
    * 2: Slow
    * 3: Beta only
    * */
    status = 1

    tvTypes = listOf("Live")

    requiresResources = false

    iconUrl = "https://github.githubassets.com/favicons/favicon-dark.svg"
}
