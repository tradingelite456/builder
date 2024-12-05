package it.dogior.hadEnough

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import com.lagradost.cloudstream3.CommonActivity.activity
import com.lagradost.cloudstream3.DownloaderTestImpl
import com.lagradost.cloudstream3.ErrorLoadingException
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization

@CloudstreamPlugin
class YouTubePlugin : Plugin() {
    private val sharedPref = activity?.getSharedPreferences("Youtube", Context.MODE_PRIVATE)

    override fun load(context: Context) {
        val language = sharedPref?.getString("language", "it") ?: "it"
        val country = sharedPref?.getString("country", "it") ?: "IT"
        NewPipe.init(DownloaderTestImpl.getInstance())
        NewPipe.setupLocalization(Localization(language), ContentCountry(country))

        // All providers should be added in this manner
        registerMainAPI(YouTubeProvider())
//        registerMainAPI(YouTubePlaylistsProvider())
        registerMainAPI(YouTubeChannelProvider())
        registerExtractorAPI(YouTubeExtractor())

        val activity = context as AppCompatActivity
        openSettings = {
            val frag = SettingsFragment(this, sharedPref)
            frag.show(activity.supportFragmentManager, "Frag")
        }
    }
}