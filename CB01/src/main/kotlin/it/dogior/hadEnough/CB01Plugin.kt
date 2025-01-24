package it.dogior.hadEnough

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.Coroutines.ioSafe

@CloudstreamPlugin
class CB01Plugin: Plugin() {
    override fun load(context: Context) {
        // All providers should be added in this manner
        ioSafe{
            val response = app.get("https://cb01.uno")
            val newUrl = response.okhttpResponse.request.url.toString()
                .substringBeforeLast("/")
            registerMainAPI(CB01(newUrl))
        }
    }
}