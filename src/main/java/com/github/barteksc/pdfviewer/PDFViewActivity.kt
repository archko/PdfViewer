package com.github.barteksc.pdfviewer;

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import android.os.SystemClock
import android.preference.PreferenceManager
import android.text.TextUtils
import android.util.SparseArray
import android.view.*
import android.widget.RelativeLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import cn.archko.pdf.activities.MuPDFRecyclerViewActivity
import cn.archko.pdf.activities.PdfOptionsActivity
import cn.archko.pdf.adapters.MuPDFReflowAdapter
import cn.archko.pdf.colorpicker.ColorPickerDialog
import cn.archko.pdf.common.*
import cn.archko.pdf.entity.APage
import cn.archko.pdf.entity.FontBean
import cn.archko.pdf.entity.MenuBean
import cn.archko.pdf.fragments.FontsFragment
import cn.archko.pdf.listeners.DataListener
import cn.archko.pdf.listeners.MenuListener
import cn.archko.pdf.listeners.OutlineListener
import cn.archko.pdf.presenter.PageViewPresenter
import cn.archko.pdf.utils.FileUtils
import cn.archko.pdf.widgets.APageSeekBarControls
import cn.archko.pdf.widgets.ViewerDividerItemDecoration
import com.github.barteksc.pdfviewer.listener.*
import com.github.barteksc.pdfviewer.scroll.DefaultScrollHandle
import org.jetbrains.anko.doAsync
import org.jetbrains.anko.uiThread
import org.vudroid.core.models.ZoomModel
import java.io.File

public class PDFViewActivity : MuPDFRecyclerViewActivity(), OnPageChangeListener, OnLoadCompleteListener,
        OnPageErrorListener, OutlineListener, OnErrorListener {

    private var pdfView: PDFView? = null

    lateinit var mLeftDrawer: RecyclerView
    lateinit var mDrawerLayout: DrawerLayout
    lateinit var mControllerLayout: RelativeLayout

    private var mPageSeekBarControls: APageSeekBarControls? = null
    private var outlineHelper: OutlineHelper? = null
    private var mStyleControls: View? = null

    private var mMenuHelper: MenuHelper? = null

    private var mFontSeekBar: SeekBar? = null
    private var mFontSizeLabel: TextView? = null
    private var mFontFaceSelected: TextView? = null
    private var mFontFaceChange: TextView? = null
    private var mLineSpaceLabel: TextView? = null
    private var mLinespaceMinus: View? = null
    private var mLinespacePlus: View? = null
    private var mColorLabel: TextView? = null
    private var mBgSetting: View? = null
    private var mFgSetting: View? = null
    private var colorPickerDialog: ColorPickerDialog? = null

    private var mStyleHelper: StyleHelper? = null
    var margin = 10

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState);
        if (TextUtils.isEmpty(mPath)) {
            return
        }
        mPageSeekBarControls?.updateTitle(mPath)

        autoCrop = PreferenceManager.getDefaultSharedPreferences(this).getBoolean(PdfOptionsActivity.PREF_AUTOCROP, true)
    }

    override fun initView() {
        window.requestFeature(Window.FEATURE_NO_TITLE)
        setContentView(R.layout.pdfviewer);

        mLeftDrawer = findViewById(R.id.left_drawer)
        mDrawerLayout = findViewById(R.id.drawerLayout)
        pdfView = findViewById(R.id.pdfView);

        mControllerLayout = findViewById(R.id.layout)

        mPageSeekBarControls = createSeekControls()

        val lp = RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.addRule(RelativeLayout.ALIGN_PARENT_TOP)
        mControllerLayout.addView(mPageSeekBarControls, lp)

        mPageSeekBarControls?.autoCropButton!!.visibility = View.VISIBLE

        with(mLeftDrawer) {
            layoutManager = LinearLayoutManager(this@PDFViewActivity, LinearLayoutManager.VERTICAL, false)
            addItemDecoration(ViewerDividerItemDecoration(this@PDFViewActivity, LinearLayoutManager.VERTICAL))
        }

        mRecyclerView = findViewById(R.id.recycler_view)//RecyclerView(this)
        with(mRecyclerView) {
            descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
            isNestedScrollingEnabled = false
            layoutManager = LinearLayoutManager(this@PDFViewActivity, LinearLayoutManager.VERTICAL, false)
            setItemViewCacheSize(0)

            addItemDecoration(ViewerDividerItemDecoration(this@PDFViewActivity, LinearLayoutManager.VERTICAL))
            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                    if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                        updateProgress(getCurrentPos())
                    }
                }

                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                }
            })
        }

        if (margin <= 0) {
            margin = ViewConfiguration.get(this).scaledTouchSlop * 2
        } else {
            margin = (margin * 0.03).toInt()
        }
        zoomModel = ZoomModel()
    }

    override fun loadDoc() {
        super.loadDoc()
    }

    override fun doLoadDoc() {
        try {
            progressDialog.setMessage("Loading menu")

            if (mReflow) {
                addGesture()
            }
            loadFromUri()

            isDocLoaded = true
        } catch (e: Exception) {
            e.printStackTrace()
            onError(e)
            finish()
        } finally {
            progressDialog.dismiss()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mPageSizes.let {
            if (it.size() < 0 || (it.size() < APageSizeLoader.PAGE_COUNT)) {
                return
            }
            APageSizeLoader.savePageSizeToFile(false, mPageSizes,
                    FileUtils.getDiskCacheDir(this@PDFViewActivity,
                            pdfBookmarkManager?.bookmarkToRestore?.name))
        }
    }

    override fun preparePageSize(cp: Int) {
        val width = mRecyclerView.width
        doAsync {
            var start = SystemClock.uptimeMillis()
            val pageSizeBean = APageSizeLoader.loadPageSizeFromFile(width,
                    FileUtils.getDiskCacheDir(this@PDFViewActivity,
                            pdfBookmarkManager?.bookmarkToRestore?.name))
            Logcat.d("open3:" + (SystemClock.uptimeMillis() - start))

            uiThread {
                var pageSizes: SparseArray<APage>? = null;
                if (pageSizeBean != null) {
                    pageSizes = pageSizeBean.sparseArray;
                }
                if (pageSizes != null && pageSizes.size() > 0 && !autoCrop) {
                    mPageSizes = pageSizes
                } else {
                    start = SystemClock.uptimeMillis()
                    super.preparePageSize(cp)
                    Logcat.d("open2:" + (SystemClock.uptimeMillis() - start))
                }
            }
        }
        //if (mReflow) {
        //    return
        //}
        //for (i in 0 until cp) {
        //    val size = getPageSize(i)
        //}
    }

    private fun loadFromUri() {
        setupView()

        autoCropModeSet(autoCrop)
        val pos = pdfBookmarkManager?.getBookmark()!!

        pdfView!!.zoomTo(pdfBookmarkManager!!.getBookmarkToRestore().zoomLevel / 1000f)
        pdfView!!.fromFile(File(mPath!!))
                .defaultPage(pos)
                .onPageChange(this)
                .enableAnnotationRendering(true)
                .onLoad(this)
                .scrollHandle(DefaultScrollHandle(this))
                .spacing(2) // in dp
                .onPageError(this)
                .onTap(onTapListener)
                .crop(autoCrop)
                .onError(this)
                .setPageSizes(mPageSizes)
                .setDocument(mDocument)
                .load();
    }

    override fun onError(t: Throwable?) {
        progressDialog.dismiss()
        Toast.makeText(this@PDFViewActivity, "open file error:$t", Toast.LENGTH_LONG).show()
        this@PDFViewActivity.finish()
    }

    override fun onPageChanged(page: Int, pageCount: Int) {
        Logcat.d("onPageChanged:$page pc:$pageCount");

        updateProgress(page)
    }

    override fun loadComplete(nbPages: Int) {
        Logcat.d("loadComplete:" + nbPages);
        try {
            progressDialog.setMessage("Loading menu")

            isDocLoaded = true

            mDocument = pdfView?.document

            if (mReflow) {
                if (null == mStyleHelper) {
                    mStyleHelper = StyleHelper()
                }
                mRecyclerView.adapter = MuPDFReflowAdapter(this, mDocument, mStyleHelper)
                mPageSeekBarControls?.reflowButton!!.setColorFilter(Color.argb(0xFF, 172, 114, 37))

            } else {
                mPageSeekBarControls?.reflowButton!!.setColorFilter(Color.argb(0xFF, 255, 255, 255))
            }

            mPageSeekBarControls?.showReflow(true)

            outlineHelper = OutlineHelper(mDocument, this);

            val pos = pdfBookmarkManager?.restoreBookmark(mDocument!!.countPages())!!
            mMenuHelper = MenuHelper(mLeftDrawer, outlineHelper, supportFragmentManager)
            mMenuHelper?.setupMenu(mPath, this@PDFViewActivity, menuListener)
            mMenuHelper?.setupOutline(pos)

            isDocLoaded = true

            val sp = getSharedPreferences(PREF_READER, Context.MODE_PRIVATE)
            val isFirst = sp.getBoolean(PREF_READER_KEY_FIRST, true)
            if (isFirst) {
                mDrawerLayout.openDrawer(mLeftDrawer)
                showOutline()

                sp.edit()
                        .putBoolean(PREF_READER_KEY_FIRST, false)
                        .apply()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            finish()
        } finally {
            progressDialog.dismiss()
        }
    }

    override fun onPageError(page: Int, t: Throwable?) {
        Logcat.d("onPageError:$t");
        Toast.makeText(this@PDFViewActivity, "onPageError:$t", Toast.LENGTH_SHORT).show()
    }

    //======================= ui =======================
    private fun setupView() {
        if (mReflow) {
            mRecyclerView.visibility = View.VISIBLE
            pdfView?.visibility = View.GONE
        } else {
            mRecyclerView.visibility = View.GONE
            pdfView?.visibility = View.VISIBLE
        }
    }

    private fun getViewHeight(): Int {
        if (mReflow) {
            return mRecyclerView.height
        } else {
            return pdfView!!.height
        }
    }

    private fun getScrollY(): Int {
        if (mReflow) {
            return mRecyclerView.scrollY
        } else {
            return pdfView!!.scrollY
        }
    }

    private fun scrollBy(scrollX: Int, scrollY: Int) {
        if (mReflow) {
            return mRecyclerView.scrollBy(scrollX, scrollY)
        } else {
            return pdfView!!.moveRelativeTo(-scrollX.toFloat(), -scrollY.toFloat(), false)
        }
    }

    private var onTapListener = object : OnTapListener {
        override fun onTap(e: MotionEvent): Boolean {
            val height = getViewHeight()
            var scrollY = getScrollY()
            val top = height / 4
            val bottom = height * 3 / 4

            if (e.y.toInt() < top) {
                scrollY = scrollY - height + margin
                scrollBy(0, scrollY)
                return true
            } else if (e.y.toInt() > bottom) {
                scrollY = scrollY + height - margin
                scrollBy(0, scrollY)
                return true
            } else {
                onSingleTap()
            }
            return true
        }

        override fun onDoubleTap(e: MotionEvent?): Boolean {
            onDoubleTap()
            return true
        }
    }

    override fun addGesture() {
        if (null == gestureDetector) {
            initTouchParams()
        }
        mRecyclerView.setOnTouchListener { v, event ->
            gestureDetector!!.onTouchEvent(event)
            false
        }
    }

    private fun toggleReflow() {
        reflowModeSet(!mReflow)
        Toast.makeText(this, if (mReflow) getString(R.string.entering_reflow_mode) else getString(R.string.leaving_reflow_mode), Toast.LENGTH_SHORT).show()
    }

    private fun reflowModeSet(reflow: Boolean) {
        val pos = getCurrentPos();
        mReflow = reflow
        setupView()
        if (mReflow) {
            if (null == mStyleHelper) {
                mStyleHelper = StyleHelper()
            }
            mRecyclerView.adapter = MuPDFReflowAdapter(this, mDocument, mStyleHelper)
            mPageSeekBarControls?.reflowButton!!.setColorFilter(Color.argb(0xFF, 172, 114, 37))

            addGesture()
        } else {
            mPageSeekBarControls?.reflowButton!!.setColorFilter(Color.argb(0xFF, 255, 255, 255))
        }
        if (pos > 0) {
            mRecyclerView.scrollToPosition(pos)
        }
    }

    override fun onSingleTap() {
        if (mPageSeekBarControls?.visibility == View.VISIBLE) {
            mPageSeekBarControls?.hide()
            return
        }
        showReflowConfigMenu()
    }

    override fun onDoubleTap() {
        super.onDoubleTap()
        if (!isDocLoaded) {
            return
        }
        mPageSeekBarControls?.hide()
        mStyleControls?.visibility = View.GONE
        if (!mDrawerLayout.isDrawerOpen(mLeftDrawer)) {
            mDrawerLayout.openDrawer(mLeftDrawer)
        } else {
            mDrawerLayout.closeDrawer(mLeftDrawer)
        }
        showOutline()
    }

    private fun createSeekControls(): APageSeekBarControls {
        mPageSeekBarControls = APageSeekBarControls(this, object : PageViewPresenter {
            override fun reflow() {
                toggleReflow()
            }

            override fun getPageCount(): Int {
                return mDocument!!.countPages()
            }

            override fun getCurrentPageIndex(): Int {
                return getCurrentPos();
            }

            override fun goToPageIndex(page: Int) {
                if (mReflow) {
                    mRecyclerView.layoutManager?.scrollToPosition(page)
                } else {
                    pdfView?.jumpTo(page)
                }
            }

            override fun showOutline() {
                this@PDFViewActivity.showOutline()
            }

            override fun back() {
                mPageSeekBarControls?.hide()
            }

            override fun getTitle(): String {
                return mPath!!
            }

            override fun autoCrop() {
                toggleAutoCrop();
            }
        })
        return mPageSeekBarControls!!
    }

    private fun showOutline() {
        outlineHelper?.let {
            if (it.hasOutline()) {
                val frameLayout = mPageSeekBarControls?.getLayoutOutline()

                if (frameLayout?.visibility == View.GONE) {
                    frameLayout.visibility = View.VISIBLE
                    mMenuHelper?.updateSelection(getCurrentPos())
                } else {
                    frameLayout?.visibility = View.GONE
                }
            } else {
                mPageSeekBarControls?.getLayoutOutline()?.visibility = View.GONE
            }
        }
    }

    private fun toggleAutoCrop() {
        val flag = autoCropModeSet(!autoCrop)
        if (flag) {
            autoCrop = !autoCrop;
        }
        pdfView?.setAutoCrop(autoCrop)
    }

    private fun autoCropModeSet(autoCrop: Boolean): Boolean {
        if (mReflow) {
            mPageSeekBarControls?.autoCropButton!!.setColorFilter(Color.argb(0xFF, 255, 255, 255))
            return false
        } else {
            if (autoCrop) {
                mPageSeekBarControls?.autoCropButton!!.setColorFilter(Color.argb(0xFF, 172, 114, 37))
            } else {
                mPageSeekBarControls?.autoCropButton!!.setColorFilter(Color.argb(0xFF, 255, 255, 255))
            }
            pdfView?.invalidate()
            return true
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when (requestCode) {
            OUTLINE_REQUEST -> {
                onSelectedOutline(resultCode)
            }
        }

        super.onActivityResult(requestCode, resultCode, data)
    }

    override fun onSelectedOutline(resultCode: Int) {
        if (mReflow) {
            mRecyclerView.layoutManager?.scrollToPosition(resultCode - RESULT_FIRST_USER)
        } else {
            pdfView?.jumpTo(resultCode - RESULT_FIRST_USER)
        }
        updateProgress(resultCode - RESULT_FIRST_USER)
    }

    override fun commitZoom() {
        mRecyclerView.adapter?.notifyItemChanged(getCurrentPos())
    }

    override fun updateProgress(index: Int) {
        if (isDocLoaded && mPageSeekBarControls?.visibility == View.VISIBLE) {
            mPageSeekBarControls?.updatePageProgress(index)
        }
    }
    //--------------------------------------

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
    }

    override fun getCurrentPos(): Int {
        var position = 0
        if (mReflow) {
            position = (mRecyclerView.layoutManager as LinearLayoutManager).findFirstVisibleItemPosition()
        } else {
            position = pdfView?.currentPage!!
        }
        if (position < 0) {
            position = 0
        }
        return position;
    }

    override fun onResume() {
        super.onResume()

        mPageSeekBarControls?.hide()
        //mZoomControls?.hide()
        mStyleControls?.visibility = View.GONE
        mDrawerLayout.closeDrawers()
    }

    override fun onPause() {
        super.onPause()
        if (autoCrop) {
            pdfBookmarkManager?.bookmarkToRestore?.autoCrop = 0
        } else {
            pdfBookmarkManager?.bookmarkToRestore?.autoCrop = 1
        }
        var zoom = 1000.0f
        if (mReflow) {
            /*if (zoomModel != null) {
                zoom = zoomModel!!.zoom * 1000
            }*/
            pdfBookmarkManager?.bookmarkToRestore?.reflow = 1
        } else {
            zoom = pdfView?.zoom!! * 1000.0f
            pdfBookmarkManager?.bookmarkToRestore?.reflow = 0
        }
        val position = getCurrentPos()

        if (mDocument != null) {
            pdfBookmarkManager?.saveCurrentPage(mPath, mDocument!!.countPages(), position, zoom, -1, 0)
        }
        if (mReflow && null != mRecyclerView.adapter && mRecyclerView.adapter is MuPDFReflowAdapter) {
            (mRecyclerView.adapter as MuPDFReflowAdapter).clearCacheViews()
        }
    }

    //===========================================

    private fun showReflowConfigMenu() {
        if (mReflow) {
            if (null == mStyleControls) {
                initStyleControls()
            } else {
                if (mStyleControls?.visibility == View.VISIBLE) {
                    mStyleControls?.visibility = View.GONE
                    super.onSingleTap()
                } else {
                    showStyleFragment()
                }
            }
        } else {
            super.onSingleTap()
        }
    }

    private fun initStyleControls() {
        mPageSeekBarControls?.hide()
        if (null == mStyleControls) {
            mStyleControls = layoutInflater.inflate(R.layout.text_style, mDrawerLayout, false)

            val lp = RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            lp.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM)
            mControllerLayout.addView(mStyleControls, lp)
        }
        mStyleControls?.visibility = View.VISIBLE

        mFontSeekBar = mStyleControls?.findViewById(R.id.font_seek_bar)
        mFontSizeLabel = mStyleControls?.findViewById(R.id.font_size_label)
        mFontFaceSelected = mStyleControls?.findViewById(R.id.font_face_selected)
        mFontFaceChange = mStyleControls?.findViewById(R.id.font_face_change)
        mLineSpaceLabel = mStyleControls?.findViewById(R.id.line_space_label)
        mLinespaceMinus = mStyleControls?.findViewById(R.id.linespace_minus)
        mLinespacePlus = mStyleControls?.findViewById(R.id.linespace_plus)
        mColorLabel = mStyleControls?.findViewById(R.id.color_label)
        mBgSetting = mStyleControls?.findViewById(R.id.bg_setting)
        mFgSetting = mStyleControls?.findViewById(R.id.fg_setting)

        mStyleHelper?.let {
            val progress = (it.styleBean?.textSize!! - START_PROGRESS).toInt()
            mFontSeekBar?.progress = progress
            mFontSizeLabel?.text = String.format("%s", progress + START_PROGRESS)
            mFontSeekBar?.max = 10
            mFontSeekBar?.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    val index = (progress + START_PROGRESS)
                    mFontSizeLabel?.text = String.format("%s", index)
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) {
                }

                override fun onStopTrackingTouch(seekBar: SeekBar?) {
                    it.styleBean?.textSize = (seekBar?.progress!! + START_PROGRESS).toFloat()
                    it.saveStyleToSP(it.styleBean)
                    updateReflowAdapter()
                }
            });
            mFontFaceSelected?.text = it.fontHelper?.fontBean?.fontName

            mLineSpaceLabel?.text = String.format("%s倍", it.styleBean?.lineSpacingMult)
            mColorLabel?.setBackgroundColor(it.styleBean?.bgColor!!)
            mColorLabel?.setTextColor(it.styleBean?.fgColor!!)
        }

        mFontFaceChange?.setOnClickListener {
            FontsFragment.showFontsDialog(this, mStyleHelper,
                    object : DataListener {
                        override fun onSuccess(vararg args: Any?) {
                            updateReflowAdapter()
                            val fBean = args[0] as FontBean
                            mFontFaceSelected?.text = fBean.fontName
                        }

                        override fun onFailed(vararg args: Any?) {
                        }
                    })
        }

        mLinespaceMinus?.setOnClickListener {
            var old = mStyleHelper?.styleBean?.lineSpacingMult
            if (old!! < 0.8f) {
                return@setOnClickListener
            }
            old = old.minus(0.1f)
            applyLineSpace(old)
        }
        mLinespacePlus?.setOnClickListener {
            var old = mStyleHelper?.styleBean?.lineSpacingMult
            if (old!! > 2.2f) {
                return@setOnClickListener
            }
            old = old?.plus(0.1f)
            applyLineSpace(old)
        }
        mBgSetting?.setOnClickListener {
            pickerColor(mStyleHelper?.styleBean?.bgColor!!, ColorPickerDialog.OnColorSelectedListener { color ->
                mColorLabel?.setBackgroundColor(color)
                mStyleHelper?.styleBean?.bgColor = color
                mStyleHelper?.saveStyleToSP(mStyleHelper?.styleBean)
                updateReflowAdapter()
            })
        }
        mFgSetting?.setOnClickListener {
            pickerColor(mStyleHelper?.styleBean?.fgColor!!, ColorPickerDialog.OnColorSelectedListener { color ->
                mColorLabel?.setTextColor(color)
                mStyleHelper?.styleBean?.fgColor = color
                mStyleHelper?.saveStyleToSP(mStyleHelper?.styleBean)
                updateReflowAdapter()
            })
        }
    }

    private fun updateReflowAdapter() {
        mRecyclerView.adapter?.run {
            this.notifyDataSetChanged()
        }
    }

    private fun applyLineSpace(old: Float?) {
        mLineSpaceLabel?.text = String.format("%s倍", old)
        mStyleHelper?.styleBean?.lineSpacingMult = old!!
        mStyleHelper?.saveStyleToSP(mStyleHelper?.styleBean)
        updateReflowAdapter()
    }

    private fun pickerColor(initialColor: Int, selectedListener: ColorPickerDialog.OnColorSelectedListener) {
        if (null == colorPickerDialog) {
            colorPickerDialog = ColorPickerDialog(this, initialColor, selectedListener);
        } else {
            colorPickerDialog?.updateColor(initialColor)
            colorPickerDialog?.setOnColorSelectedListener(selectedListener)
        }
        colorPickerDialog?.show();
    }

    private fun showStyleFragment() {
        mStyleControls?.visibility = View.VISIBLE
    }

    //===========================================

    companion object {

        private const val TAG = "PDFViewActivity"
        const val PREF_READER = "pref_reader_barteksc"
        const val PREF_READER_KEY_FIRST = "pref_reader_key_first"
    }

    //===========================================

    private var menuListener = object : MenuListener {

        override fun onMenuSelected(data: MenuBean?, position: Int) {
            when (data?.type) {
                TYPE_PROGRESS -> {
                    mDrawerLayout.closeDrawer(mLeftDrawer)
                    mPageSeekBarControls?.show()
                }
                TYPE_ZOOM -> {
                    mDrawerLayout.closeDrawer(mLeftDrawer)
                    mStyleControls?.visibility = View.VISIBLE
                }
                TYPE_CLOSE -> {
                    this@PDFViewActivity.finish()
                }
                TYPE_SETTINGS -> {
                    PdfOptionsActivity.start(this@PDFViewActivity)
                }
                else -> {
                }
            }
        }

    }
}
