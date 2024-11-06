// use an integer for version numbers
version = 1


cloudstream {
    // All of these properties are optional, you can safely remove them

    description = "Live streams from the Free TV github repository. Forked from the Free-TV plugin in the ItalianProvider repo"
    authors = listOf("Gian-Fr","Adippe","doGior")

    /**
    * Status int as the following:
    * 0: Down
    * 1: Ok
    * 2: Slow
    * 3: Beta only
    * */
    status = 1

    tvTypes = listOf("Live")

    requiresResources = true
    language = "it"

    // random cc logo i found
    iconUrl = "https://avatars.githubusercontent.com/u/55937028?s=200&v=4"
}
