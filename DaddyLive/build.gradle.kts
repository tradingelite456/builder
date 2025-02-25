// use an integer for version numbers
version = 1


cloudstream {
    // All of these properties are optional, you can safely remove them

    description = "Live TV from DaddyLive. You might need a VPN"
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
    language = "un"

    iconUrl = "https://github.com/doGior/doGiorsHadEnough/blob/master/DaddyLive/live-streaming.png"
}
