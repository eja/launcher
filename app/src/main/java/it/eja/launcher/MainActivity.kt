// Copyright (C) 2024 by Ubaldo Porcheddu <ubaldo@eja.it>

package it.eja.launcher

import android.app.Activity
import android.content.*
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.*
import android.widget.*
import androidx.core.content.ContextCompat
import kotlin.math.roundToInt
import android.provider.Settings

class MainActivity : Activity() {
    private val GRID_COLUMN_COUNT = 4
    private val ICON_SIZE_DP = 64
    private val MARGIN_DP = 8
    private var selectedImageUri: Uri? = null
    private lateinit var sharedPreferences: SharedPreferences
    private var isDragging = false
    private lateinit var halfScreenView: View
    private lateinit var gridLayout: GridLayout
    private val displayMetrics by lazy { resources.displayMetrics }
    private val iconSize by lazy { dpToPx(ICON_SIZE_DP) }
    private val marginPx by lazy { dpToPx(MARGIN_DP) }
    private val screenWidth by lazy { displayMetrics.widthPixels }
    private val handler = Handler(Looper.getMainLooper())

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

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1 && resultCode == RESULT_OK && data != null) {
            selectedImageUri = data.data
            selectedImageUri?.let { saveBackgroundImageUri(it) }
        }
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
        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(Color.TRANSPARENT)
        setupHalfScreenView()
        setupGridLayout()
    }

    private fun setupHalfScreenView() {
        halfScreenView.layoutParams.height = displayMetrics.heightPixels / 2
        halfScreenView.setOnLongClickListener {
            pickImageFromGallery()
            true
        }
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
        gridLayout.setOnDragListener(DragListener())

        val iconPositions = sharedPreferences.getString("iconPositions", null)
        if (iconPositions != null) {
            loadPreferences()
        } else {
            val apps = getInstalledApps()

            val buttonWidth = (screenWidth - (GRID_COLUMN_COUNT + 1) * marginPx) / GRID_COLUMN_COUNT

            apps.forEachIndexed { index, info ->
                val button = createAppButton(info, packageManager, buttonWidth)
                button.setOnLongClickListener {
                    startMenuOrDrag(it, index)
                    true
                }
                gridLayout.addView(button)
            }
        }
    }

    private fun pickImageFromGallery() {
        val intent =
            Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI).apply {
                type = "image/*"
            }
        if (intent.resolveActivity(packageManager) != null) {
            startActivityForResult(intent, 1)
        } else {
            Toast.makeText(this, "No app found to pick images", Toast.LENGTH_SHORT).show()
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

    private fun startMenuOrDrag(view: View, index: Int): Boolean {
        isDragging = false
        handler.postDelayed({
            if (!isDragging) {
                showPopupMenu(view, index)
            }
        }, 500) // Delay before showing the menu
        view.setOnTouchListener { v, event ->
            if (event.action == MotionEvent.ACTION_MOVE) {
                isDragging = true
                view.setOnTouchListener(null)
                startDrag(v, index)
            }
            false
        }
        return true
    }

    private fun showPopupMenu(view: View, index: Int): Boolean {
        val popupMenu = PopupMenu(this, view)
        popupMenu.menuInflater.inflate(R.menu.popup_menu, popupMenu.menu)
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
                R.id.action_move_to_left -> {
                    moveIcon(view, "left")
                    true
                }
                R.id.action_move_to_right -> {
                    moveIcon(view, "right")
                    true
                }

                else -> false
            }
        }
        popupMenu.setOnDismissListener {
            view.visibility = View.VISIBLE
        }
        popupMenu.show()
        return true
    }

    private fun moveIcon(view: View, direction: String) {
        val parent = view.parent as GridLayout
        val currentIndex = parent.indexOfChild(view)
        val newIndex = when (direction) {
            "top" -> 0
            "bottom" -> parent.childCount - 1
            "left" -> maxOf(currentIndex - 1, 0)
            "right" -> minOf(currentIndex + 1, parent.childCount - 1)
            else -> return // Invalid direction, do nothing
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

    private fun startDrag(view: View, originalIndex: Int): Boolean {
        val item = ClipData.Item(view.tag as? CharSequence)
        val dragData =
            ClipData(view.tag as? CharSequence, arrayOf(ClipDescription.MIMETYPE_TEXT_PLAIN), item)

        val myShadow = View.DragShadowBuilder(view)
        view.startDragAndDrop(dragData, myShadow, view, 0)
        view.visibility = View.INVISIBLE
        return true
    }

    private fun saveBackgroundImageUri(uri: Uri) {
        try {
            val bitmap = MediaStore.Images.Media.getBitmap(contentResolver, uri)
            window.setBackgroundDrawable(BitmapDrawable(resources, bitmap))
            savePreferences()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun loadPreferences() {
        val backgroundImageUriString = sharedPreferences.getString("backgroundImageUri", null)
        backgroundImageUriString?.let {
            try {
                selectedImageUri = Uri.parse(it)
                val bitmap = MediaStore.Images.Media.getBitmap(contentResolver, selectedImageUri)
                window.setBackgroundDrawable(BitmapDrawable(resources, bitmap))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

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
                        startMenuOrDrag(it, apps.indexOf(info))
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
                        startMenuOrDrag(it, installedApps.indexOf(appInfo))
                        true
                    }
                    gridLayout.addView(button)
                }
            }
        }
    }

    private fun savePreferences() {
        with(sharedPreferences.edit()) {
            putString("backgroundImageUri", selectedImageUri?.toString())
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


    private inner class DragListener : View.OnDragListener {
        override fun onDrag(v: View, event: DragEvent): Boolean {
            when (event.action) {
                DragEvent.ACTION_DRAG_STARTED -> {
                    return event.clipDescription.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN)
                }

                DragEvent.ACTION_DRAG_ENTERED -> {
                    return true
                }

                DragEvent.ACTION_DRAG_LOCATION -> {
                    handleDragLocation(v, event)
                    return true
                }

                DragEvent.ACTION_DROP -> {
                    handleDropEvent(v, event)
                    return true
                }

                DragEvent.ACTION_DRAG_ENDED -> {
                    (event.localState as View).visibility = View.VISIBLE
                    return true
                }

                else -> return false
            }
        }

        private fun handleDragLocation(v: View, event: DragEvent) {
            val scrollView = v.rootView.findViewById<ScrollView>(R.id.scroll_view)
            val y = event.y
            val screenHeight = displayMetrics.heightPixels
            val halfScreenViewHeight = screenHeight / 2
            val scrollY = scrollView.scrollY
            val threshold = screenHeight * 0.15
            if (y < (scrollY - halfScreenViewHeight + threshold)) { // up
                scrollView.smoothScrollTo(0, (scrollY - threshold).toInt())
            } else if (y > (halfScreenViewHeight - threshold)) { // down
                scrollView.smoothScrollTo(0, (scrollY + threshold).toInt())
            }
        }

        private fun handleDropEvent(v: View, event: DragEvent) {
            val view = event.localState as View
            val owner = view.parent as ViewGroup
            val container = v as GridLayout
            val index = calculateDropIndex(container, event.x, event.y)
            if (index < container.childCount) {
                owner.removeView(view)
                container.addView(view, index)
                view.visibility = View.VISIBLE
                savePreferences()
            }
        }

        private fun calculateDropIndex(container: GridLayout, x: Float, y: Float): Int {
            val columnCount = container.columnCount
            val rowCount = if (container.childCount % columnCount == 0) {
                container.childCount / columnCount
            } else {
                container.childCount / columnCount + 1
            }

            val childWidth = container.width / columnCount
            val childHeight = container.height / rowCount

            val column = (x / childWidth).toInt()
            val row = (y / childHeight).toInt()

            return row * columnCount + column
        }
    }
}
