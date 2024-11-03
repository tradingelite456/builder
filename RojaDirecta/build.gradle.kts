// use an integer for version numbers
version = 0


cloudstream {
    // All of these properties are optional, you can safely remove them

    description = "WORK IN PROGRESS | Sports Streams from RojaDirecta.eu (You might need a VPN)"
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

    requiresResources = true
    language = "en"

    // random cc logo i found
    iconUrl = "http://www.rojadirecta.eu/static/favicon.ico"
}
