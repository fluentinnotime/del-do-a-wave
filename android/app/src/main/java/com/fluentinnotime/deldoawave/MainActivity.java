package com.fluentinnotime.deldoawave;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.media.midi.MidiDevice;
import android.media.midi.MidiDeviceInfo;
import android.media.midi.MidiInputPort;
import android.media.midi.MidiManager;
import android.media.midi.MidiOutputPort;
import android.media.midi.MidiReceiver;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.webkit.WebViewAssetLoader;
import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Del Do a Wave para Android: las páginas de la app van dentro del APK y se abren en un WebView.
 * El WebView no trae Web MIDI, así que esta actividad lee el piano por USB con android.media.midi
 * y se lo pasa a la página (ver assets/midi-shim.js).
 */
public class MainActivity extends Activity {
    private static final String HOST = "appassets.androidplatform.net";
    private static final String START = "https://" + HOST + "/assets/www/wave.html";
    private static final int PICK_FILE = 42;

    private WebView web;
    private ValueCallback<Uri[]> fileCallback;
    private String shim = "";
    private boolean docStartScript = false;

    private MidiManager midi;
    private boolean midiWanted = false;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final Map<Integer, MidiDevice> devices = new HashMap<>();
    private final Map<Integer, MidiOutputPort> fromPiano = new HashMap<>();
    private final Map<Integer, MidiInputPort> toPiano = new HashMap<>();

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        web = new WebView(this);
        setContentView(web);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(true);

        shim = readAsset("midi-shim.js");
        web.addJavascriptInterface(new Bridge(), "AndroidMidi");
        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            WebViewCompat.addDocumentStartJavaScript(web, shim, Collections.singleton("https://" + HOST));
            docStartScript = true;
        }

        final WebViewAssetLoader loader = new WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
                .build();

        web.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                return loader.shouldInterceptRequest(request.getUrl());
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri u = request.getUrl();
                if (HOST.equals(u.getHost())) return false;
                try { startActivity(new Intent(Intent.ACTION_VIEW, u)); } catch (ActivityNotFoundException ignored) { }
                return true; // los enlaces de fuera (jobim.org) se abren en el navegador
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                if (!docStartScript) view.evaluateJavascript(shim, null);
            }
        });

        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (fileCallback != null) fileCallback.onReceiveValue(null);
                fileCallback = callback;
                Intent i = new Intent(Intent.ACTION_GET_CONTENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("*/*");
                try {
                    startActivityForResult(Intent.createChooser(i, "Elige tu partitura"), PICK_FILE);
                } catch (ActivityNotFoundException e) {
                    fileCallback = null;
                    return false;
                }
                return true;
            }
        });

        midi = (MidiManager) getSystemService(MIDI_SERVICE);
        if (midi != null) {
            midi.registerDeviceCallback(new MidiManager.DeviceCallback() {
                @Override public void onDeviceAdded(MidiDeviceInfo info) { if (midiWanted) openDevice(info); notifyChanged(); }
                @Override public void onDeviceRemoved(MidiDeviceInfo info) { closeDevice(info.getId()); notifyChanged(); }
            }, ui);
        }

        web.loadUrl(START);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_FILE && fileCallback != null) {
            fileCallback.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(resultCode, data));
            fileCallback = null;
        }
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onBackPressed() {
        if (web != null && web.canGoBack()) web.goBack(); else super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        for (Integer id : devices.keySet().toArray(new Integer[0])) closeDevice(id);
        if (web != null) web.destroy();
        super.onDestroy();
    }

    /* ---------------- MIDI ---------------- */

    @SuppressWarnings("deprecation")
    private MidiDeviceInfo[] allDevices() {
        return midi == null ? new MidiDeviceInfo[0] : midi.getDevices();
    }

    private void openAll() {
        for (MidiDeviceInfo info : allDevices()) openDevice(info);
    }

    private void openDevice(final MidiDeviceInfo info) {
        final int id = info.getId();
        if (midi == null || devices.containsKey(id)) return;
        midi.openDevice(info, device -> {
            if (device == null) return;
            devices.put(id, device);
            if (info.getOutputPortCount() > 0) {
                MidiOutputPort out = device.openOutputPort(0);
                if (out != null) { out.connect(new Parser(id)); fromPiano.put(id, out); }
            }
            if (info.getInputPortCount() > 0) {
                MidiInputPort in = device.openInputPort(0);
                if (in != null) toPiano.put(id, in);
            }
            notifyChanged();
        }, ui);
    }

    private void closeDevice(int id) {
        try { MidiOutputPort o = fromPiano.remove(id); if (o != null) o.close(); } catch (IOException ignored) { }
        try { MidiInputPort i = toPiano.remove(id); if (i != null) i.close(); } catch (IOException ignored) { }
        try { MidiDevice d = devices.remove(id); if (d != null) d.close(); } catch (IOException ignored) { }
    }

    private void notifyChanged() {
        ui.post(() -> { if (web != null) web.evaluateJavascript("window.__androidMidiChanged&&window.__androidMidiChanged()", null); });
    }

    private static String deviceName(MidiDeviceInfo info) {
        Bundle p = info.getProperties();
        String n = p.getString(MidiDeviceInfo.PROPERTY_NAME);
        if (n == null || n.isEmpty()) n = p.getString(MidiDeviceInfo.PROPERTY_PRODUCT);
        return n == null || n.isEmpty() ? "Teclado MIDI" : n;
    }

    /** Separa el flujo de bytes en mensajes de nota/control; descarta sysex y mensajes de tiempo real (el FP-10 manda Active Sensing sin parar). */
    private class Parser extends MidiReceiver {
        private final int id;
        private int status = 0, need = 0, have = 0;
        private final int[] msg = new int[3];
        private boolean inSysex = false;

        Parser(int id) { this.id = id; }

        @Override
        public void onSend(byte[] data, int offset, int count, long timestamp) {
            for (int k = offset; k < offset + count; k++) {
                int b = data[k] & 0xFF;
                if (b >= 0xF8) continue;
                if (b == 0xF0) { inSysex = true; continue; }
                if (b == 0xF7) { inSysex = false; continue; }
                if (inSysex) continue;
                if ((b & 0x80) != 0) {
                    if (b >= 0xF0) { status = 0; continue; }
                    status = b; have = 0; msg[0] = b;
                    int hi = b & 0xF0;
                    need = (hi == 0xC0 || hi == 0xD0) ? 1 : 2;
                    continue;
                }
                if (status == 0) continue;
                if (have == 0) msg[0] = status;
                msg[1 + have] = b; have++;
                if (have == need) {
                    final String js = "window.__androidMidiMessage&&window.__androidMidiMessage(" + id + ",[" + msg[0] + "," + msg[1] + (need == 2 ? "," + msg[2] : "") + "])";
                    have = 0;
                    ui.post(() -> { if (web != null) web.evaluateJavascript(js, null); });
                }
            }
        }
    }

    /** Lo que la página ve como window.AndroidMidi */
    private class Bridge {
        @JavascriptInterface
        public String list() {
            JSONArray a = new JSONArray();
            try {
                for (MidiDeviceInfo info : allDevices()) {
                    JSONObject o = new JSONObject();
                    o.put("id", info.getId());
                    o.put("name", deviceName(info));
                    o.put("ins", info.getInputPortCount());
                    o.put("outs", info.getOutputPortCount());
                    a.put(o);
                }
            } catch (Exception ignored) { }
            return a.toString();
        }

        @JavascriptInterface
        public void open() {
            ui.post(() -> { midiWanted = true; openAll(); });
        }

        @JavascriptInterface
        public void send(int id, String json) {
            MidiInputPort port = toPiano.get(id);
            if (port == null) return;
            try {
                JSONArray a = new JSONArray(json);
                byte[] b = new byte[a.length()];
                for (int i = 0; i < b.length; i++) b[i] = (byte) a.getInt(i);
                port.send(b, 0, b.length);
            } catch (Exception ignored) { }
        }
    }

    private String readAsset(String name) {
        try (InputStream in = getAssets().open(name); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[8192]; int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return out.toString(StandardCharsets.UTF_8.name());
        } catch (IOException e) {
            return "";
        }
    }
}
