package com.mrt.jarvis.apps

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.mrt.jarvis.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AppsGridActivity : AppCompatActivity() {

    private lateinit var rvApps: RecyclerView
    private val appsList = mutableListOf<AppInfo>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_apps_grid)

        rvApps = findViewById(R.id.rvApps)
        rvApps.layoutManager = GridLayoutManager(this, 4) // 4 ستون

        findViewById<View>(R.id.btnBackApps).setOnClickListener { finish() }

        loadApps()
    }

    private fun loadApps() {
        CoroutineScope(Dispatchers.Main).launch {
            val apps = withContext(Dispatchers.IO) {
                val pm = packageManager
                val intent = Intent(Intent.ACTION_MAIN, null).addCategory(Intent.CATEGORY_LAUNCHER)
                val resolveInfos = pm.queryIntentActivities(intent, 0)
                
                val list = mutableListOf<AppInfo>()
                for (info in resolveInfos) {
                    // فیلتر کردن اپ خودمان (اختیاری)
                    if (info.activityInfo.packageName == packageName) continue
                    
                    val name = info.loadLabel(pm).toString()
                    val pkg = info.activityInfo.packageName
                    val icon = info.loadIcon(pm)
                    list.add(AppInfo(name, pkg, icon))
                }
                list.sortedBy { it.name }
            }
            
            appsList.clear()
            appsList.addAll(apps)
            rvApps.adapter = AppAdapter(appsList)
        }
    }

    private inner class AppAdapter(private val apps: List<AppInfo>) : RecyclerView.Adapter<AppAdapter.AppViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_app, parent, false)
            return AppViewHolder(view)
        }

        override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
            val app = apps[position]
            holder.ivIcon.setImageDrawable(app.icon)
            holder.tvName.text = app.name
            
            holder.itemView.setOnClickListener {
                val launchIntent = packageManager.getLaunchIntentForPackage(app.packageName)
                if (launchIntent != null) {
                    startActivity(launchIntent)
                }
            }
        }

        override fun getItemCount() = apps.size

        inner class AppViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val ivIcon: ImageView = view.findViewById(R.id.ivAppIcon)
            val tvName: TextView = view.findViewById(R.id.tvAppName)
        }
    }
}
