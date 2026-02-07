package com.example.tvnotif

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import kotlin.concurrent.thread

class AppListActivity : AppCompatActivity() {

    private lateinit var listView: ListView
    private val selectedPackages = mutableSetOf<String>()
    private lateinit var prefs: android.content.SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        listView = ListView(this)
        setContentView(listView)
        title = "Select Apps"

        prefs = getSharedPreferences("SELECTED_APPS", Context.MODE_PRIVATE)

        // ---- LOAD PREFS SAFELY ----
        val saved = prefs.getStringSet("packages", emptySet()) ?: emptySet()
        selectedPackages.addAll(saved)

        // ---- LOAD APPS OFF UI THREAD ----
        thread {
            val apps = loadLaunchableApps()
            runOnUiThread {
                listView.adapter = AppAdapter(apps)
            }
        }
    }

    private fun loadLaunchableApps(): List<AppInfo> {
        val pm = packageManager
        val result = mutableListOf<AppInfo>()

        for (pkg in pm.getInstalledPackages(0)) {
            // Skip non-launchable apps
            if (pm.getLaunchIntentForPackage(pkg.packageName) == null) continue

            // ---- FIX: SAFE applicationInfo ACCESS ----
            val appInfo = pkg.applicationInfo ?: continue
            val label = appInfo.loadLabel(pm)?.toString() ?: continue

            result.add(
                AppInfo(
                    name = label,
                    packageName = pkg.packageName,
                    isSelected = selectedPackages.contains(pkg.packageName)
                )
            )
        }

        return result.sortedBy { it.name.lowercase() }
    }

    data class AppInfo(
        val name: String,
        val packageName: String,
        var isSelected: Boolean
    )

    inner class AppAdapter(private val apps: List<AppInfo>) : BaseAdapter() {

        override fun getCount() = apps.size
        override fun getItem(position: Int) = apps[position]
        override fun getItemId(position: Int) = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = convertView ?: LayoutInflater.from(this@AppListActivity)
                .inflate(R.layout.item_app, parent, false)

            val app = apps[position]

            val icon = view.findViewById<ImageView>(R.id.imgIcon)
            val name = view.findViewById<TextView>(R.id.txtName)
            val checkbox = view.findViewById<CheckBox>(R.id.chkSelected)

            name.text = app.name
            checkbox.setOnCheckedChangeListener(null)
            checkbox.isChecked = app.isSelected

            // ---- SAFE ICON LOAD (BACKGROUND) ----
            thread {
                val drawable = try {
                    packageManager.getApplicationIcon(app.packageName)
                } catch (e: Exception) {
                    getDrawable(android.R.drawable.sym_def_app_icon)
                }

                runOnUiThread {
                    icon.setImageDrawable(drawable)
                }
            }

            checkbox.setOnCheckedChangeListener { _, checked ->
                app.isSelected = checked
                if (checked) selectedPackages.add(app.packageName)
                else selectedPackages.remove(app.packageName)

                // ALWAYS store a COPY
                prefs.edit()
                    .putStringSet("packages", HashSet(selectedPackages))
                    .apply()
            }

            view.setOnClickListener {
                checkbox.isChecked = !checkbox.isChecked
            }

            return view
        }
    }
}
