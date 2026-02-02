package com.tufanakcay.androidwebview;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.print.PrintManager;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.URLUtil;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

public class MainActivity extends AppCompatActivity {

    private WebView webView;
    private ValueCallback<Uri[]> mUploadMessage;
    private final static int FILECHOOSER_RESULTCODE = 1;
    private final static int PERMISSION_REQUEST_CODE = 100;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        init();
        checkPermissions();
        viewUrl();
        
        // --- MENJALANKAN BACKGROUND SERVICE ---
        // Ini adalah mesin yang akan melakukan polling perintah dari PHP
        // Intent serviceIntent = new Intent(this, RemoteControlService.class);
        // startService(serviceIntent);
    }

    private void init() {
        webView = findViewById(R.id.webView);
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        webView.setOverScrollMode(WebView.OVER_SCROLL_NEVER);
    }

    private void checkPermissions() {
        String[] permissions;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions = new String[]{
                    Manifest.permission.CAMERA,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.READ_CONTACTS,
                    Manifest.permission.READ_MEDIA_IMAGES
            };
        } else {
            permissions = new String[]{
                    Manifest.permission.CAMERA,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.READ_CONTACTS,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
            };
        }

        boolean needRequest = false;
        for (String perm : permissions) {
            if (ContextCompat.checkSelfPermission(this, perm) != PackageManager.PERMISSION_GRANTED) {
                needRequest = true;
                break;
            }
        }

        if (needRequest) {
            ActivityCompat.requestPermissions(this, permissions, PERMISSION_REQUEST_CODE);
        }
    }

    private void viewUrl() {
        String dynamicUrl = getString(R.string.web_url); 
        WebSettings webSettings = webView.getSettings();

        // --- PENGATURAN KEAMANAN & FITUR ---
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true);
        webSettings.setDatabaseEnabled(true);
        webSettings.setAllowFileAccess(true);
        webSettings.setAllowContentAccess(true);
        webSettings.setJavaScriptCanOpenWindowsAutomatically(true);
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            webSettings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        }

        // User Agent agar terdeteksi sebagai Browser Desktop/Mobile umum (Anti-Bot)
        webSettings.setUserAgentString("Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Mobile Safari/537.36");

        // --- BRIDGE: JAVASCRIPT KE ANDROID ---
        // Dashboard web kamu bisa memanggil: AndroidControl.sendLocation()
        webView.addJavascriptInterface(new Object() {
            @JavascriptInterface
            public void performPrint() {
                runOnUiThread(() -> createWebPrintJob(webView));
            }

            @JavascriptInterface
            public void syncData() {
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "Sinkronisasi Berhasil", Toast.LENGTH_SHORT).show());
            }
        }, "AndroidControl");

        // Handle Download
        webView.setDownloadListener((url, userAgent, contentDisposition, mimetype, contentLength) -> {
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
            request.setMimeType(mimetype);
            String fileName = URLUtil.guessFileName(url, contentDisposition, mimetype);
            request.setTitle(fileName);
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName);

            DownloadManager dm = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
            if (dm != null) {
                dm.enqueue(request);
                Toast.makeText(getApplicationContext(), "Mengunduh: " + fileName, Toast.LENGTH_LONG).show();
            }
        });

        // Handle File Chooser (Untuk Upload Foto/File dari HP ke Web)
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback, FileChooserParams fileChooserParams) {
                if (mUploadMessage != null) mUploadMessage.onReceiveValue(null);
                mUploadMessage = filePathCallback;

                Intent intent = fileChooserParams.createIntent();
                try {
                    startActivityForResult(intent, FILECHOOSER_RESULTCODE);
                } catch (Exception e) {
                    mUploadMessage = null;
                    return false;
                }
                return true;
            }
        });

        webView.setWebViewClient(new CustomWebViewClient());
        webView.loadUrl(dynamicUrl);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);
        if (requestCode == FILECHOOSER_RESULTCODE) {
            if (mUploadMessage == null) return;
            Uri[] results = null;
            if (resultCode == RESULT_OK && intent != null) {
                String dataString = intent.getDataString();
                if (dataString != null) results = new Uri[]{Uri.parse(dataString)};
            }
            mUploadMessage.onReceiveValue(results);
            mUploadMessage = null;
        }
    }

    private void createWebPrintJob(WebView webView) {
        PrintManager printManager = (PrintManager) this.getSystemService(Context.PRINT_SERVICE);
        String jobName = getString(R.string.app_name) + " Document";
        PrintDocumentAdapter printAdapter = webView.createPrintDocumentAdapter(jobName);
        if (printManager != null) {
            printManager.print(jobName, printAdapter, new PrintAttributes.Builder().build());
        }
    }

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    // --- CLIENT LOGIC ---
    private class CustomWebViewClient extends WebViewClient {
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            String url = request.getUrl().toString();
            if (url.startsWith("whatsapp://") || url.contains("wa.me") || url.startsWith("tel:")) {
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                    return true;
                } catch (Exception e) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            super.onPageFinished(view, url);
            // Injeksi fungsi print otomatis jika tombol di web diklik
            view.loadUrl("javascript:window.print = function() { AndroidControl.performPrint(); }");
        }

        @Override
        public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
            if (request.isForMainFrame()) {
                String failingUrl = request.getUrl().toString();
                String htmlData = "<html><body style='display:flex; justify-content:center; align-items:center; height:100vh; background:#0a0a0a; color:white;'>"
                        + "<div style='text-align:center;'>"
                        + "<h2>Connection Lost</h2>"
                        + "<a href='" + failingUrl + "' style='padding:10px 20px; background:#ff0000; color:white; text-decoration:none; border-radius:5px;'>RETRY</a>"
                        + "</div></body></html>";
                view.loadDataWithBaseURL(null, htmlData, "text/html", "UTF-8", null);
            }
        }
    }
}
