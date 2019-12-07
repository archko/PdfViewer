
package com.github.barteksc.pdfviewer;

import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.Window;
import android.view.WindowManager;
import android.widget.RelativeLayout;

import com.github.barteksc.pdfviewer.listener.OnLoadCompleteListener;
import com.github.barteksc.pdfviewer.listener.OnPageChangeListener;
import com.github.barteksc.pdfviewer.listener.OnPageErrorListener;
import com.github.barteksc.pdfviewer.scroll.DefaultScrollHandle;

import java.io.File;

import androidx.appcompat.app.AppCompatActivity;
import cn.archko.pdf.common.Logcat;

public class PDFViewActivity extends AppCompatActivity implements OnPageChangeListener, OnLoadCompleteListener,
        OnPageErrorListener {

    private static final String TAG = PDFViewActivity.class.getSimpleName();

    PDFView pdfView;
    String path;

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        DisplayMetrics metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(metrics);

        if (savedInstanceState != null && savedInstanceState.containsKey("FileName")) {
            path = savedInstanceState.getString("FileName");
        }

        setContentView(R.layout.pdfviewer);
        pdfView = findViewById(R.id.pdfView);

        Intent intent = getIntent();

        if (Intent.ACTION_VIEW.equals(intent.getAction())) {
            Uri uri = intent.getData();
            System.out.println("URI to open is: " + uri);
            if (uri.getScheme().equals("file")) {
                path = uri.getPath();
            } else {
            }
        }
        if (path == null) {
            //requestPassword(savedInstanceState);
            return;
        }

        displayFromUri();
    }

    public void createUI(Bundle savedInstanceState) {
        pdfView = new PDFView(this, null);
        RelativeLayout layout = new RelativeLayout(this);
        layout.setBackgroundColor(Color.DKGRAY);
        layout.addView(pdfView);
        setContentView(layout);
    }

    private void displayFromUri() {
        pdfView.fromFile(new File(path))
                .defaultPage(0)
                .onPageChange(this)
                .enableAnnotationRendering(true)
                .onLoad(this)
                .scrollHandle(new DefaultScrollHandle(this))
                .spacing(10) // in dp
                .onPageError(this)
                .load();
    }

    @Override
    public void loadComplete(int nbPages) {
        Logcat.d("loadComplete:" + nbPages);
    }

    @Override
    public void onPageChanged(int page, int pageCount) {
        Logcat.d("onPageChanged:" + page + " pc:" + pageCount);
    }

    @Override
    public void onPageError(int page, Throwable t) {
        Logcat.d("onPageError:" + t);
    }
}
