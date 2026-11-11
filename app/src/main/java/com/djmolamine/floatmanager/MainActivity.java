package com.djmolamine.floatmanager;

import android.app.Activity;
import android.content.ClipData;
import android.content.ContentValues;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.core.content.FileProvider;
import androidx.webkit.WebViewAssetLoader;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

/** Hosts the bundled web app in a WebView. The app has no internet permission: all data stays on the phone. */
public class MainActivity extends Activity {
    private static final int REQ_FILE = 11;
    private static final String START = "https://appassets.androidplatform.net/assets/index.html";

    private WebView web;
    private ValueCallback<Uri[]> fileCallback;
    private Uri cameraUri;
    private File pendingFile;
    private OutputStream pendingOut;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        web = new WebView(this);
        setContentView(web);

        final WebViewAssetLoader loader = new WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
                .build();

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setTextZoom(100);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(true);

        web.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                return loader.shouldInterceptRequest(request.getUrl());
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return !"appassets.androidplatform.net".equals(request.getUrl().getHost());
            }
        });
        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                return pickFile(callback, params);
            }
        });
        web.addJavascriptInterface(new Bridge(), "Android");
        web.loadUrl(START);
    }

    /** Receipt photos open a chooser with camera and gallery; other inputs (backup restore) open the file picker. */
    private boolean pickFile(ValueCallback<Uri[]> callback, WebChromeClient.FileChooserParams params) {
        if (fileCallback != null) fileCallback.onReceiveValue(null);
        fileCallback = callback;
        cameraUri = null;

        boolean image = false;
        String[] types = params.getAcceptTypes();
        if (types != null) {
            for (String t : types) {
                if (t != null && t.startsWith("image")) image = true;
            }
        }
        Intent get = new Intent(Intent.ACTION_GET_CONTENT);
        get.addCategory(Intent.CATEGORY_OPENABLE);
        get.setType(image ? "image/*" : "*/*");
        Intent chooser = Intent.createChooser(get, null);

        if (image) {
            try {
                File f = new File(sharedDir(), "camera_" + System.currentTimeMillis() + ".jpg");
                cameraUri = FileProvider.getUriForFile(this, getPackageName() + ".files", f);
                Intent cam = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                cam.putExtra(MediaStore.EXTRA_OUTPUT, cameraUri);
                cam.setClipData(ClipData.newRawUri("", cameraUri));
                cam.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
                chooser.putExtra(Intent.EXTRA_INITIAL_INTENTS, new Intent[]{cam});
            } catch (Exception e) {
                cameraUri = null;
            }
        }
        try {
            startActivityForResult(chooser, REQ_FILE);
        } catch (Exception e) {
            fileCallback.onReceiveValue(null);
            fileCallback = null;
        }
        return true;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_FILE || fileCallback == null) return;
        Uri[] result = null;
        if (resultCode == RESULT_OK) {
            if (data != null && data.getData() != null) result = new Uri[]{data.getData()};
            else if (cameraUri != null) result = new Uri[]{cameraUri};
        }
        fileCallback.onReceiveValue(result);
        fileCallback = null;
    }

    @Override
    public void onBackPressed() {
        web.evaluateJavascript("window.nativeBack ? window.nativeBack() : false", value -> {
            if (!"true".equals(value)) finish();
        });
    }

    private File sharedDir() {
        File dir = new File(getCacheDir(), "shared");
        dir.mkdirs();
        return dir;
    }

    private static String mimeOf(String name) {
        String n = name.toLowerCase();
        if (n.endsWith(".pdf")) return "application/pdf";
        if (n.endsWith(".xlsx")) return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
        if (n.endsWith(".json")) return "application/json";
        return "application/octet-stream";
    }

    /** Copies the finished file into Downloads/Float Manager, then opens the share sheet (WhatsApp, email, Drive). */
    private void deliver(File f) {
        String mime = mimeOf(f.getName());
        if (Build.VERSION.SDK_INT >= 29) {
            try {
                ContentValues v = new ContentValues();
                v.put(MediaStore.Downloads.DISPLAY_NAME, f.getName());
                v.put(MediaStore.Downloads.MIME_TYPE, mime);
                v.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Float Manager");
                Uri target = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
                if (target != null) {
                    try (OutputStream out = getContentResolver().openOutputStream(target);
                         InputStream in = new FileInputStream(f)) {
                        byte[] buf = new byte[65536];
                        int n;
                        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                    }
                    Toast.makeText(this, R.string.saved_downloads, Toast.LENGTH_LONG).show();
                }
            } catch (Exception e) {
                // Sharing below still works if the copy to Downloads fails.
            }
        }
        try {
            Uri share = FileProvider.getUriForFile(this, getPackageName() + ".files", f);
            Intent send = new Intent(Intent.ACTION_SEND);
            send.setType(mime);
            send.putExtra(Intent.EXTRA_STREAM, share);
            send.setClipData(ClipData.newRawUri("", share));
            send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(send, f.getName()));
        } catch (Exception e) {
            Toast.makeText(this, f.getName(), Toast.LENGTH_SHORT).show();
        }
    }

    /** File export from the page, sent in base64 chunks so large PDFs and backups fit through the bridge. */
    private class Bridge {
        @JavascriptInterface
        public synchronized void fileBegin(String name) {
            try {
                if (pendingOut != null) pendingOut.close();
            } catch (Exception ignored) {
            }
            try {
                pendingFile = new File(sharedDir(), name.replaceAll("[\\\\/:*?\"<>|]", "_"));
                pendingOut = new FileOutputStream(pendingFile);
            } catch (Exception e) {
                pendingFile = null;
                pendingOut = null;
            }
        }

        @JavascriptInterface
        public synchronized void fileChunk(String base64) {
            try {
                if (pendingOut != null) pendingOut.write(Base64.decode(base64, Base64.DEFAULT));
            } catch (Exception ignored) {
            }
        }

        @JavascriptInterface
        public synchronized void fileEnd() {
            final File f = pendingFile;
            try {
                if (pendingOut != null) pendingOut.close();
            } catch (Exception ignored) {
            }
            pendingOut = null;
            pendingFile = null;
            if (f != null) runOnUiThread(() -> deliver(f));
        }
    }
}
