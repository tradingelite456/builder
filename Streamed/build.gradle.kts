// use an integer for version numbers
version = 2


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
    status = 1
    tvTypes = listOf("Live")


    requiresResources = false
    language = "it"

    iconUrl = "https://streamed.su/favicon.png"
}
