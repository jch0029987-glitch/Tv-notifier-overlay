package com.example.tvnotif

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import kotlin.concurrent.thread

class AppListActivity : AppCompatActivity() {

    private val selectedPackages = mutableSetOf<String>()
    private lateinit var listView: ListView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        listView = ListView(this)
        setContentView(listView)
        title = "Select Apps"

        // 1. Load saved preferences safely
        val prefs = getSharedPreferences("SELECTED_APPS", Context.MODE_PRIVATE)
        prefs.getStringSet("packages", null)?.let {
            selectedPackages.addAll(it)
        }

        // 2. Load apps in a background thread to prevent freezing/crashing
        thread {
            val apps = getInstalledApps()
            runOnUiThread {
                listView.adapter = AppAdapter(this, apps)
            }
        }
    }

    private fun getInstalledApps(): List<AppInfoStub> {
        val pm = packageManager
        val appList = mutableListOf<AppInfoStub>()
        // GET_META_DATA is safer for some system apps
        val packages = pm.getInstalledPackages(0)

        for (pkg in packages) {
            val ai = pkg.applicationInfo ?: continue
            // IMPORTANT: Only show apps that can be launched. 
            // This filters out 400+ background system processes that cause crashes.
            if (pm.getLaunchIntentForPackage(pkg.packageName) != null) {
                val name = ai.loadLabel(pm).toString()
                appList.add(AppInfoStub(name, pkg.packageName, selectedPackages.contains(pkg.packageName)))
            }
        }
        return appList.sortedBy { it.name }
    }

    // A lighter data class that doesn't hold the heavy Icon Drawable in memory
    data class AppInfoStub(val name: String, val packageName: String, var isSelected: Boolean)

    inner class AppAdapter(context: Context, private val apps: List<AppInfoStub>) : 
        ArrayAdapter<AppInfoStub>(context, 0, apps) {

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = convertView ?: LayoutInflater.from(context).inflate(R.layout.item_app, parent, false)
            val app = apps[position]
            
            val iconImg = view.findViewById<ImageView>(R.id.imgIcon)
            val nameTxt = view.findViewById<TextView>(R.id.txtName)
            val checkBox = view.findViewById<CheckBox>(R.id.chkSelected)

            nameTxt.text = app.name
            checkBox.isChecked = app.isSelected

            // Load the icon ONLY when the row is actually visible (Lazy Loading)
            try {
                iconImg.setImageDrawable(context.packageManager.getApplicationIcon(app.packageName))
            } catch (e: Exception) {
                iconImg.setImageResource(android.R.drawable.sym_def_app_icon)
            }

            view.setOnClickListener {
                app.isSelected = !app.isSelected
                checkBox.isChecked = app.isSelected
                
                if (app.isSelected) selectedPackages.add(app.packageName)
                else selectedPackages.remove(app.packageName)

                getSharedPreferences("SELECTED_APPS", Context.MODE_PRIVATE)
                    .edit()
                    .putStringSet("packages", selectedPackages.toSet())
                    .apply()
            }

            return view
        }
    }
}
