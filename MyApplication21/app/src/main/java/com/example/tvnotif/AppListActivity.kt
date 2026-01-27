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
        
        // UI Setup
        listView = ListView(this)
        listView.choiceMode = ListView.CHOICE_MODE_MULTIPLE
        setContentView(listView)
        title = "Select Apps to Sync"

        // Load saved selections
        val prefs = getSharedPreferences("SELECTED_APPS", Context.MODE_PRIVATE)
        val saved = prefs.getStringSet("packages", emptySet())
        if (saved != null) {
            selectedPackages.addAll(saved)
        }

        // Load apps safely
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
                // Only include apps the user can actually open (skips hidden system services)
                // This prevents 90% of crashes when loading app lists
                if (pm.getLaunchIntentForPackage(pkg.packageName) != null) {
                    val name = pkg.applicationInfo.loadLabel(pm).toString()
                    val icon = pkg.applicationInfo.loadIcon(pm)
                    val isSelected = selectedPackages.contains(pkg.packageName)
                    
                    appList.add(AppInfo(name, pkg.packageName, icon, isSelected))
                }
            } catch (e: Exception) {
                continue // Skip apps that are restricted or cause errors
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

    // Custom Adapter to handle the Checkboxes and Save logic
    inner class AppAdapter(context: Context, private val apps: List<AppInfo>) : 
        ArrayAdapter<AppInfo>(context, android.R.layout.simple_list_item_multiple_choice, apps) {

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = super.getView(position, convertView, parent)
            val app = apps[position]
            
            val textView = view.findViewById<View>(android.R.id.text1) as CheckedTextView
            textView.text = app.name
            
            // Set the checkmark based on our list
            val listView = parent as ListView
            listView.setItemChecked(position, app.isSelected)

            // Handle Clicks
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
