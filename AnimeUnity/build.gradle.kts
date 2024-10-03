@file:Suppress("UnstableApiUsage")

// use an integer for version numbers
version = 1


cloudstream {
    // All of these properties are optional, you can safely remove them

    description = "Anime from AnimeUnity"
    authors = listOf("doGior")

    /**
    * Status int as the following:
    * 0: Down
    * 1: Ok
    * 2: Slow
    * 3: Beta only
    * */
    status = 0

    language = "it"
    requiresResources = false

    iconUrl = "https://www.animeunity.to/favicon-32x32.png"
}
