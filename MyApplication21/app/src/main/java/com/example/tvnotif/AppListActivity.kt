package com.example.tvnotif

import android.content.Context
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class AppListActivity : AppCompatActivity() {

    private lateinit var listView: ListView
    private val selectedPackages = mutableSetOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        listView = ListView(this)
        listView.choiceMode = ListView.CHOICE_MODE_MULTIPLE
        setContentView(listView)
        title = "Select Apps to Sync"

        val prefs = getSharedPreferences("SELECTED_APPS", Context.MODE_PRIVATE)
        val saved = prefs.getStringSet("packages", emptySet())
        if (saved != null) {
            selectedPackages.addAll(saved)
        }

        val apps = getInstalledApps()
        val adapter = AppAdapter(this, apps)
        listView.adapter = adapter
    }

    private fun getInstalledApps(): List<AppInfo> {
        val pm = packageManager
        val packages = pm.getInstalledPackages(0)
        val appList = mutableListOf<AppInfo>()

        for (pkg in packages) {
            try {
                // Fix: Check if applicationInfo is null safely
                val appInfo = pkg.applicationInfo
                if (appInfo != null && pm.getLaunchIntentForPackage(pkg.packageName) != null) {
                    
                    val name = appInfo.loadLabel(pm).toString()
                    val icon = appInfo.loadIcon(pm)
                    val isSelected = selectedPackages.contains(pkg.packageName)
                    
                    appList.add(AppInfo(name, pkg.packageName, icon, isSelected))
                }
            } catch (e: Exception) {
                continue 
            }
        }
        return appList.sortedBy { it.name }
    }

    private fun saveSelection() {
        getSharedPreferences("SELECTED_APPS", Context.MODE_PRIVATE)
            .edit()
            .putStringSet("packages", selectedPackages.toSet())
            .apply()
    }

    inner class AppAdapter(context: Context, private val apps: List<AppInfo>) : 
        ArrayAdapter<AppInfo>(context, android.R.layout.simple_list_item_multiple_choice, apps) {

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = super.getView(position, convertView, parent)
            val app = apps[position]
            
            val textView = view.findViewById<View>(android.R.id.text1) as CheckedTextView
            textView.text = app.name
            
            val listView = parent as ListView
            listView.setItemChecked(position, app.isSelected)

            view.setOnClickListener {
                app.isSelected = !app.isSelected
                if (app.isSelected) {
                    selectedPackages.add(app.packageName)
                } else {
                    selectedPackages.remove(app.packageName)
                }
                listView.setItemChecked(position, app.isSelected)
                saveSelection()
            }

            return view
        }
    }
}
