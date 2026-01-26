package com.example.tvnotif

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.ListView
import androidx.appcompat.app.AppCompatActivity

class AppListActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val listView = ListView(this)
        setContentView(listView)

        val pm = packageManager
        val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            .filter { (it.flags and ApplicationInfo.FLAG_SYSTEM) == 0 } // Only show downloaded apps
            .sortedBy { it.loadLabel(pm).toString() }

        val appNames = apps.map { it.loadLabel(pm).toString() }
        val packageNames = apps.map { it.packageName }

        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_multiple_choice, appNames)
        listView.adapter = adapter
        listView.choiceMode = ListView.CHOICE_MODE_MULTIPLE

        // Load previously saved apps
        val prefs = getSharedPreferences("SELECTED_APPS", Context.MODE_PRIVATE)
        val savedPackages = prefs.getStringSet("packages", emptySet()) ?: emptySet()

        packageNames.forEachIndexed { index, pkg ->
            if (savedPackages.contains(pkg)) {
                listView.setItemChecked(index, true)
            }
        }

        // Save on every click
        listView.setOnItemClickListener { _, _, _, _ ->
            val selected = mutableSetOf<String>()
            val checkedPositions = listView.checkedItemPositions
            for (i in 0 until packageNames.size) {
                if (checkedPositions.get(i)) {
                    selected.add(packageNames[i])
                }
            }
            prefs.edit().putStringSet("packages", selected).apply()
        }
    }
}
