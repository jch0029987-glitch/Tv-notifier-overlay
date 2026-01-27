package com.example.tvnotif

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class AppListActivity : AppCompatActivity() {

    private val selectedPackages = mutableSetOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Use a simple ListView as the content
        val listView = ListView(this)
        setContentView(listView)
        title = "Select Apps"

        // 1. Load saved preferences
        val prefs = getSharedPreferences("SELECTED_APPS", Context.MODE_PRIVATE)
        prefs.getStringSet("packages", null)?.let {
            selectedPackages.addAll(it)
        }

        // 2. Load apps safely
        val apps = getInstalledApps()
        
        // 3. Set Adapter
        val adapter = AppAdapter(this, apps)
        listView.adapter = adapter
    }

    private fun getInstalledApps(): List<AppInfo> {
        val pm = packageManager
        val appList = mutableListOf<AppInfo>()
        // Only get apps that actually have a launch icon (real apps)
        val packages = pm.getInstalledPackages(0)

        for (pkg in packages) {
            val ai = pkg.applicationInfo ?: continue
            // This filter stops the app from loading 500+ system services (prevents crash)
            if (pm.getLaunchIntentForPackage(pkg.packageName) != null) {
                val name = ai.loadLabel(pm).toString()
                val icon = ai.loadIcon(pm)
                val isSelected = selectedPackages.contains(pkg.packageName)
                appList.add(AppInfo(name, pkg.packageName, icon, isSelected))
            }
        }
        return appList.sortedBy { it.name }
    }

    inner class AppAdapter(context: Context, private val apps: List<AppInfo>) : 
        ArrayAdapter<AppInfo>(context, 0, apps) {

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = convertView ?: LayoutInflater.from(context)
                .inflate(R.layout.item_app, parent, false)
            
            val app = apps[position]
            
            val iconImg = view.findViewById<ImageView>(R.id.imgIcon)
            val nameTxt = view.findViewById<TextView>(R.id.txtName)
            val checkBox = view.findViewById<CheckBox>(R.id.chkSelected)

            nameTxt.text = app.name
            iconImg.setImageDrawable(app.icon)
            checkBox.isChecked = app.isSelected

            view.setOnClickListener {
                app.isSelected = !app.isSelected
                checkBox.isChecked = app.isSelected
                
                if (app.isSelected) selectedPackages.add(app.packageName)
                else selectedPackages.remove(app.packageName)

                // Save immediately on click
                getSharedPreferences("SELECTED_APPS", Context.MODE_PRIVATE)
                    .edit()
                    .putStringSet("packages", selectedPackages)
                    .apply()
            }

            return view
        }
    }
}
