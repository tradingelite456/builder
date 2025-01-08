package it.dogior.hadEnough.settings

import android.content.SharedPreferences
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.setPadding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.lagradost.cloudstream3.CommonActivity.showToast
import it.dogior.hadEnough.YouTubePlugin
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization

/**
 * A simple [Fragment] subclass.
 * Use the [SettingsFragment] factory method to
 * create an instance of this fragment.
 */
class SettingsFragment(private val plugin: YouTubePlugin, val sharedPref: SharedPreferences?) :
    BottomSheetDialogFragment() {

    private fun <T : View> View.findView(name: String): T {
        val id = plugin.resources!!.getIdentifier(name, "id", "it.doGior.hadEnoguh")
        return this.findViewById(id)
    }

    private fun View.makeTvCompatible() {
        this.setPadding(this.paddingLeft + 10,this.paddingTop + 10,this.paddingRight + 10,this.paddingBottom + 10)
        this.background = getDrawable("outline")
    }

    private fun getDrawable(name: String): Drawable? {
        val id =
            plugin.resources!!.getIdentifier(name, "drawable", "it.doGior.hadEnoguh")
        return ResourcesCompat.getDrawable(plugin.resources!!, id, null)
    }

    private fun getString(name: String): String? {
        val id =
            plugin.resources!!.getIdentifier(name, "string", "it.doGior.hadEnoguh")
        return plugin.resources!!.getString(id)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        // Inflate the layout for this fragment
        val id = plugin.resources!!.getIdentifier(
            "settings_fragment",
            "layout",
            "it.doGior.hadEnoguh"
        )
        val layout = plugin.resources!!.getLayout(id)
        return inflater.inflate(layout, container, false)
    }

    @RequiresApi(Build.VERSION_CODES.M)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val headerTw = view.findView<TextView>("header_tw")
        headerTw.text = getString("header_tw")

        val localizationTW = view.findView<TextView>("localization_tw")
        val homepageTW = view.findView<TextView>("homepage_tw")

        localizationTW.text = getString("localization_tw")
        homepageTW.text = getString("homepage_tw")




        val changeLocalizationButton = view.findView<ImageButton>("changeLocalization_button")
        changeLocalizationButton.setImageDrawable(getDrawable("settings_icon"))
        changeLocalizationButton.makeTvCompatible()

        changeLocalizationButton.setOnClickListener(object : View.OnClickListener {
            override fun onClick(v: View?) {
                LocalizationSettings(plugin, sharedPref).show(
                    activity?.supportFragmentManager
                        ?: throw Exception("Unable to open localization settings"),
                    ""
                )
                dismiss()
            }
        })

        val changeHomepageButton = view.findView<ImageButton>("changeHomepage_button")
        changeHomepageButton.setImageDrawable(getDrawable("settings_icon"))
        changeHomepageButton.makeTvCompatible()

        changeHomepageButton.setOnClickListener(object : View.OnClickListener {
            override fun onClick(v: View?) {
                HomepageSettings(plugin, sharedPref).show(
                    activity?.supportFragmentManager
                        ?: throw Exception("Unable to open localization settings"),
                    ""
                )
                dismiss()
            }
        })

    }
}
