// Copyright (C) by Ubaldo Porcheddu <ubaldo@eja.it>

package it.eja.launcher

import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.*
import android.widget.*
import androidx.core.content.ContextCompat
import kotlin.math.roundToInt

class MainActivity : Activity() {
    private val GRID_COLUMN_COUNT = 4
    private val ICON_SIZE_DP = 64
    private val MARGIN_DP = 8
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var gridLayout: GridLayout
    private lateinit var halfScreenView: View
    private val displayMetrics by lazy { resources.displayMetrics }
    private val iconSize by lazy { dpToPx(ICON_SIZE_DP) }
    private val marginPx by lazy { dpToPx(MARGIN_DP) }
    private val screenWidth by lazy { displayMetrics.widthPixels }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        setContentView(R.layout.activity_main)

        sharedPreferences = getSharedPreferences("eja_preferences", MODE_PRIVATE)
        halfScreenView = findViewById(R.id.half_screen_view)
        gridLayout = findViewById(R.id.grid_layout)

        if (!isDefaultLauncher()) {
            promptToSetDefaultLauncher()
        }
        setupUI()
    }

    override fun onBackPressed() {}

    override fun onResume() {
        super.onResume()
        loadPreferences()
    }

    override fun onPause() {
        super.onPause()
        savePreferences()
    }

    private fun isDefaultLauncher(): Boolean {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val resolveInfo = packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        val currentLauncherPackageName = resolveInfo?.activityInfo?.packageName
        return currentLauncherPackageName == packageName
    }

    private fun promptToSetDefaultLauncher() {
        val intent = Intent(Settings.ACTION_HOME_SETTINGS)
        if (intent.resolveActivity(packageManager) != null) {
            startActivity(intent)
        } else {
            Toast.makeText(
                this,
                "Please set the launcher manually in the device settings.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun setupUI() {
        window.setStatusBarColor(Color.TRANSPARENT)
        window.setNavigationBarColor(Color.TRANSPARENT)
        window.decorView.setBackgroundColor(Color.WHITE)
        halfScreenView.layoutParams.height = displayMetrics.heightPixels / 2
        setupGridLayout()
    }

    private fun getInstalledApps(): List<ResolveInfo> {
        val packageManager = packageManager
        val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        return packageManager.queryIntentActivities(mainIntent, PackageManager.GET_META_DATA)
            .sortedBy { it.loadLabel(packageManager).toString() }
    }

    private fun setupGridLayout() {
        val iconPositions = sharedPreferences.getString("iconPositions", null)
        if (iconPositions != null) {
            loadPreferences()
        } else {
            val apps = getInstalledApps()
            val buttonWidth = (screenWidth - (GRID_COLUMN_COUNT + 1) * marginPx) / GRID_COLUMN_COUNT

            apps.forEachIndexed { index, info ->
                val button = createAppButton(info, packageManager, buttonWidth)
                button.setOnLongClickListener {
                    showPopupMenu(it)
                    true
                }
                gridLayout.addView(button)
            }
        }
    }

    private fun createAppButton(
        info: ResolveInfo,
        packageManager: PackageManager,
        buttonWidth: Int
    ): Button {
        val appName = info.loadLabel(packageManager).toString()
        val appIcon = info.loadIcon(packageManager)
        return Button(this).apply {
            tag = info
            layoutParams = ViewGroup.LayoutParams(buttonWidth, ViewGroup.LayoutParams.WRAP_CONTENT)
            setCompoundDrawablesWithIntrinsicBounds(
                null,
                getResizedDrawable(appIcon, iconSize),
                null,
                null
            )
            compoundDrawablePadding = marginPx
            text = appName
            gravity = Gravity.CENTER
            setPadding(marginPx, marginPx, marginPx, marginPx)
            setBackgroundColor(ContextCompat.getColor(context, android.R.color.transparent))
            setOnClickListener { launchApp(info) }
            isAllCaps = false
        }
    }

    private fun showPopupMenu(view: View): Boolean {
        val popupMenu = PopupMenu(this, view)
        popupMenu.menuInflater.inflate(R.menu.popup_menu, popupMenu.menu)
        val info = view.tag as ResolveInfo
        popupMenu.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_move_to_top -> {
                    moveIcon(view, "top")
                    true
                }
                R.id.action_move_to_bottom -> {
                    moveIcon(view, "bottom")
                    true
                }
                R.id.action_move_up -> {
                    moveIcon(view, "up")
                    true
                }
                R.id.action_move_down -> {
                    moveIcon(view, "down")
                    true
                }
                R.id.action_app_settings -> {
                    openAppSettings(info)
                    true
                }
                else -> false
            }
        }
        popupMenu.show()
        return true
    }

    private fun openAppSettings(info: ResolveInfo) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", info.activityInfo.packageName, null)
        }
        startActivity(intent)
    }

    private fun moveIcon(view: View, direction: String) {
        val parent = view.parent as GridLayout
        val currentIndex = parent.indexOfChild(view)
        val newIndex = when (direction) {
            "top" -> 0
            "bottom" -> parent.childCount - 1
            "up" -> maxOf(currentIndex - 1, 0)
            "down" -> minOf(currentIndex + 1, parent.childCount - 1)
            else -> return
        }
        parent.removeView(view)
        parent.addView(view, newIndex)
        savePreferences()
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * displayMetrics.density).roundToInt()
    }

    private fun getResizedDrawable(drawable: Drawable, size: Int): Drawable {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val paint = Paint().apply {
            isAntiAlias = true
            color = Color.WHITE
        }
        val cornerRadius = size * 0.15f
        val rectF = RectF(0f, 0f, size.toFloat(), size.toFloat())
        canvas.drawRoundRect(rectF, cornerRadius, cornerRadius, paint)

        val iconSize = (size * 0.75).toInt()
        val left = (size - iconSize) / 2
        val top = (size - iconSize) / 2
        val right = left + iconSize
        val bottom = top + iconSize

        drawable.setBounds(left, top, right, bottom)
        drawable.draw(canvas)

        return BitmapDrawable(resources, bitmap)
    }

    private fun launchApp(info: ResolveInfo) {
        val launchIntent = Intent(Intent.ACTION_MAIN).apply {
            setClassName(info.activityInfo.packageName, info.activityInfo.name)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(launchIntent)
    }

    private fun loadPreferences() {
        val iconPositionsString = sharedPreferences.getString("iconPositions", null)
        iconPositionsString?.let {
            val iconPositions = it.split(",")
            val apps = getInstalledApps()
            val buttonWidth = (screenWidth - (GRID_COLUMN_COUNT + 1) * marginPx) / GRID_COLUMN_COUNT
            gridLayout.removeAllViews()

            for (appName in iconPositions) {
                val info =
                    apps.find { appInfo -> appInfo.loadLabel(packageManager).toString() == appName }
                if (info != null) {
                    val button = createAppButton(info, packageManager, buttonWidth)
                    button.setOnLongClickListener {
                        showPopupMenu(it)
                        true
                    }
                    gridLayout.addView(button)
                }
            }

            val installedApps = getInstalledApps()
            for (appInfo in installedApps) {
                val appName = appInfo.loadLabel(packageManager).toString()
                if (!iconPositions.contains(appName)) {
                    val button = createAppButton(appInfo, packageManager, buttonWidth)
                    button.setOnLongClickListener {
                        showPopupMenu(it)
                        true
                    }
                    gridLayout.addView(button)
                }
            }
        }
    }

    private fun savePreferences() {
        with(sharedPreferences.edit()) {
            val iconPositions = mutableListOf<String>()
            for (i in 0 until gridLayout.childCount) {
                val view = gridLayout.getChildAt(i) as Button
                val appName = view.text.toString()
                iconPositions.add(appName)
            }
            val iconPositionsString = iconPositions.joinToString(",")
            putString("iconPositions", iconPositionsString)
            apply()
        }
    }
}