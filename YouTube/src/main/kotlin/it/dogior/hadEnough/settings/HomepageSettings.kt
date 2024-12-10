package it.dogior.hadEnough.settings

import android.content.Context
import android.content.SharedPreferences
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.View.generateViewId
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.Switch
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.core.content.res.ResourcesCompat
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import it.dogior.hadEnough.BuildConfig
import it.dogior.hadEnough.YouTubePlugin
import android.widget.LinearLayout
import android.widget.RelativeLayout
import com.lagradost.api.Log
import com.lagradost.cloudstream3.CommonActivity.showToast
import com.lagradost.cloudstream3.amap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.channel.ChannelInfo
import org.schabi.newpipe.extractor.playlist.PlaylistInfo

/**
 * A simple [Fragment] subclass.
 * Use the [HomepageSettings] factory method to
 * create an instance of this fragment.
 */
class HomepageSettings(private val plugin: YouTubePlugin, val sharedPref: SharedPreferences?) :
    BottomSheetDialogFragment() {

    private fun <T : View> View.findView(name: String): T {
        val id = plugin.resources!!.getIdentifier(name, "id", BuildConfig.LIBRARY_PACKAGE_NAME)
        return this.findViewById(id)
    }

    private fun getDrawable(name: String): Drawable? {
        val id =
            plugin.resources!!.getIdentifier(name, "drawable", BuildConfig.LIBRARY_PACKAGE_NAME)
        return ResourcesCompat.getDrawable(plugin.resources!!, id, null)
    }

    private fun getString(name: String): String? {
        val id =
            plugin.resources!!.getIdentifier(name, "string", BuildConfig.LIBRARY_PACKAGE_NAME)
        return plugin.resources!!.getString(id)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        // Inflate the layout for this fragment
        val id = plugin.resources!!.getIdentifier(
            "homepage_settings",
            "layout",
            BuildConfig.LIBRARY_PACKAGE_NAME
        )
        val layout = plugin.resources!!.getLayout(id)
        return inflater.inflate(layout, container, false)
    }

    @RequiresApi(Build.VERSION_CODES.M)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val headerTw = view.findView<TextView>("header_tw")
        headerTw.text = getString("changeHomepageHeader_tw")

        val trendingSwitch = view.findView<Switch>("trending_switch")
        trendingSwitch.text = getString("trending_text")
        trendingSwitch.isChecked = sharedPref?.getBoolean("trending", true) ?: true

        val addPlaylistTw = view.findView<TextView>("addPlaylist_tw")
        addPlaylistTw.text = getString("addPlaylist_tw")

        val youtubeUrlEt = view.findView<TextView>("youtubeUrl_editText")
        youtubeUrlEt.hint = getString("add_playlist_hint")

        var playlistsSet = mutableSetOf<String>()
        sharedPref?.getStringSet("playlists", emptySet())?.let {
            playlistsSet.addAll(it)
            Log.d("YoutubeSettings", "Playlists: $playlistsSet")
        }
        val playlistsList = view.findView<LinearLayout>("playlists_list")

        runBlocking{
            playlistsSet.toList().amap {
                playlistsList.addView(
                    playlistsRow(it, sharedPref, playlistsSet, playlistsList)
                )
            }
        }


        val addSectionButton = view.findView<ImageButton>("addSection_button")
        addSectionButton.setImageDrawable(getDrawable("add_icon"))

        addSectionButton.setOnClickListener(object : View.OnClickListener {
            override fun onClick(v: View?) {
                sharedPref?.getStringSet("playlists", emptySet())?.let {
                    playlistsSet = mutableSetOf()
                    playlistsSet.addAll(it)
                    playlistsSet.add(youtubeUrlEt.text.toString())
                }

                with(sharedPref?.edit()) {
                    this?.putStringSet("playlists", playlistsSet)
                    this?.apply()
                }


                youtubeUrlEt.text = ""
                showToast("Playlist / channel added!\nRestart the app to see it in the homepage")
            }
        })


        val saveButton = view.findView<ImageButton>("save_button")
        saveButton.setImageDrawable(getDrawable("save_icon"))

        saveButton.setOnClickListener(object : View.OnClickListener {
            override fun onClick(v: View?) {
                with(sharedPref?.edit()) {
                    this?.putBoolean("trending", trendingSwitch.isChecked)
                    this?.apply()
                }
                dismiss()
            }
        })

    }

    private suspend fun playlistsRow(
        playlistUrl: String,
        sharedPref: SharedPreferences?,
        playlistsSet: MutableSet<String>,
        playlistList: LinearLayout,
    ): RelativeLayout {
        val isPlaylist = playlistUrl.startsWith("https://www.youtube.com/playlist?list=")
        val isChannel = playlistUrl.startsWith("https://www.youtube.com/@")

        val title = withContext(Dispatchers.IO) {
            if (isPlaylist && !isChannel) {
                val playlistInfo = PlaylistInfo.getInfo(playlistUrl)
                "${playlistInfo.uploaderName}: ${playlistInfo.name}"
            } else if (!isPlaylist && isChannel) {
                ChannelInfo.getInfo(playlistUrl).name
            } else {
                null
            }
        }

        // Create the RelativeLayout
        val relativeLayout = RelativeLayout(this@HomepageSettings.requireContext()).apply {
            layoutParams = RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.MATCH_PARENT,
                RelativeLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setPadding(
                    0,
                    0,
                    0,
                    dpToPx(this@HomepageSettings.requireContext(), 8)
                ) // Convert dp to px
            }
        }

        // Create the TextView (Label)
        val label = TextView(this.context).apply {
            text = title
            textSize = 15f
        }

        val labelParams = RelativeLayout.LayoutParams(
            RelativeLayout.LayoutParams.WRAP_CONTENT,
            RelativeLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            addRule(RelativeLayout.CENTER_VERTICAL) // Vertically center in parent
            addRule(RelativeLayout.ALIGN_PARENT_START)
            marginEnd = dpToPx(this@HomepageSettings.requireContext(), 8)
        }


        // Create the ImageButton
        val deleteButton = ImageButton(this.context).apply {
            id = generateViewId()
            setImageDrawable(getDrawable("delete_icon"))
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            isClickable = true
            isFocusable = true
        }

        val buttonParams = RelativeLayout.LayoutParams(
            RelativeLayout.LayoutParams.WRAP_CONTENT,
            RelativeLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            addRule(RelativeLayout.ALIGN_PARENT_END)
            addRule(RelativeLayout.CENTER_VERTICAL) // Vertically center in parent
            marginEnd = dpToPx(this@HomepageSettings.requireContext(), 8) // Convert dp to px
        }

        relativeLayout.addView(label, labelParams)
        relativeLayout.addView(deleteButton, buttonParams)

        val delete = relativeLayout.findViewById<ImageButton>(deleteButton.id)
        delete.setOnClickListener(object : View.OnClickListener {
            override fun onClick(v: View?) {
                val deleteSuccessfull = playlistsSet.remove(playlistUrl)
                if(deleteSuccessfull){
                    with(sharedPref?.edit()) {
                        this?.putStringSet("playlists", playlistsSet)
                        this?.apply()
                    }
                    playlistList.removeView(relativeLayout)
                    showToast("$title removed")
                }
            }
        })
        return relativeLayout
    }

    private fun dpToPx(context: Context, dp: Int): Int {
        val density = context.resources.displayMetrics.density
        return (dp * density).toInt()
    }
}
