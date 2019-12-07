//
//package com.github.barteksc.pdfviewer;
//
//import android.content.Intent;
//import android.graphics.Color;
//import android.net.Uri;
//import android.os.Bundle;
//import android.preference.PreferenceManager;
//import android.text.TextUtils;
//import android.util.DisplayMetrics;
//import android.view.ViewGroup;
//import android.view.Window;
//import android.view.WindowManager;
//import android.widget.RelativeLayout;
//
//import com.github.barteksc.pdfviewer.listener.OnLoadCompleteListener;
//import com.github.barteksc.pdfviewer.listener.OnPageChangeListener;
//import com.github.barteksc.pdfviewer.listener.OnPageErrorListener;
//import com.github.barteksc.pdfviewer.scroll.DefaultScrollHandle;
//
//import org.vudroid.core.views.APageSeekBarControls;
//
//import java.io.File;
//
//import androidx.annotation.NonNull;
//import androidx.appcompat.app.AppCompatActivity;
//import androidx.recyclerview.widget.LinearLayoutManager;
//import androidx.recyclerview.widget.RecyclerView;
//import cn.archko.pdf.activities.MuPDFRecyclerViewActivity;
//import cn.archko.pdf.activities.PdfOptionsActivity;
//import cn.archko.pdf.common.Logcat;
//import cn.archko.pdf.widgets.ViewerDividerItemDecoration;
//
//public class PDFViewActivity extends MuPDFRecyclerViewActivity implements OnPageChangeListener, OnLoadCompleteListener,
//        OnPageErrorListener {
//
//    private static final String TAG = PDFViewActivity.class.getSimpleName();
//
//    private APageSeekBarControls mPageSeekBarControls;
//    PDFView pdfView;
//
//    @Override
//    public void onCreate(final Bundle savedInstanceState) {
//        super.onCreate(savedInstanceState);
//
//
//        if (TextUtils.isEmpty(mPath)) {
//            return;
//        }
//        mPageSeekBarControls.updateTitle(mPath);
//
//        autoCrop = PreferenceManager.getDefaultSharedPreferences(this).getBoolean(PdfOptionsActivity.PREF_AUTOCROP, true);
//        setContentView(R.layout.pdfviewer);
//        pdfView = findViewById(R.id.pdfView);
//
//        displayFromUri();
//    }
//
//    void initView() {
//        getWindow().requestFeature(Window.FEATURE_NO_TITLE);
//        setContentView(R.layout.reader);
//
//        mRecyclerView = findViewById(R.id.recycler_view)//RecyclerView(this)
//
//        mRecyclerView.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
//        mRecyclerView.setNestedScrollingEnabled(false);
//        mRecyclerView.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false));
//        mRecyclerView.setItemViewCacheSize(0);
//
//        mRecyclerView.addItemDecoration(new ViewerDividerItemDecoration(this, LinearLayoutManager.VERTICAL));
//        mRecyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
//            @Override
//            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
//                super.onScrollStateChanged(recyclerView, newState);
//                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
//                    updateProgress(getCurrentPos());
//                }
//            }
//
//            @Override
//            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
//                super.onScrolled(recyclerView, dx, dy);
//            }
//        });
//
//        initTouchParams();
//    }
//
//    public void createUI(Bundle savedInstanceState) {
//        pdfView = new PDFView(this, null);
//        RelativeLayout layout = new RelativeLayout(this);
//        layout.setBackgroundColor(Color.DKGRAY);
//        layout.addView(pdfView);
//        setContentView(layout);
//    }
//
//    private void displayFromUri() {
//        pdfView.fromFile(new File(mPath))
//                .defaultPage(0)
//                .onPageChange(this)
//                .enableAnnotationRendering(true)
//                .onLoad(this)
//                .scrollHandle(new DefaultScrollHandle(this))
//                .spacing(10) // in dp
//                .onPageError(this)
//                .load();
//    }
//
//    @Override
//    public void loadComplete(int nbPages) {
//        Logcat.d("loadComplete:" + nbPages);
//    }
//
//    @Override
//    public void onPageChanged(int page, int pageCount) {
//        Logcat.d("onPageChanged:" + page + " pc:" + pageCount);
//    }
//
//    @Override
//    public void onPageError(int page, Throwable t) {
//        Logcat.d("onPageError:" + t);
//    }
//}
