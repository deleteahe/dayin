package com.dsh.dayin;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.ViewGroup;
import android.webkit.HttpAuthHandler;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.ValueCallback;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

/**
 * 站点套壳：WebView + HTTP Basic 认证处理。
 *
 * 账号密码不写死在代码里（这个仓库是公开的），第一次打开时在弹窗里输入，
 * 勾选"记住"后存进应用私有 SharedPreferences，之后自动带上。
 */
public class MainActivity extends Activity {

    private static final String PREFS = "auth";
    private static final String KEY_USER = "user";
    private static final String KEY_PASS = "pass";

    private WebView webView;
    private ProgressBar progress;
    private LinearLayout errorView;
    private TextView errorText;

    private SharedPreferences prefs;
    /** 同一次页面加载内，避免用错密码无限重试 */
    private boolean credentialTried = false;
    /** 防止连击返回导致连退两级 */
    private boolean backPending = false;
    private final Handler handler = new Handler(Looper.getMainLooper());

    /** 点击站内自己的返回按钮；返回 clicked / hidden / no / err */
    private static final String JS_CLICK_SITE_BACK =
            "(function(){try{"
                    + "var b=document.getElementById('backBtn');"
                    + "if(!b)return 'no';"
                    + "var r=b.getBoundingClientRect();"
                    + "var s=window.getComputedStyle(b);"
                    + "if(r.width<=0||r.height<=0||s.display==='none'||s.visibility==='hidden')return 'hidden';"
                    + "b.click();return 'clicked';"
                    + "}catch(e){return 'err';}})()";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);

        FrameLayout root = new FrameLayout(this);

        webView = new WebView(this);
        root.addView(webView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        progress.setVisibility(View.GONE);
        FrameLayout.LayoutParams plp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(3));
        plp.gravity = Gravity.TOP;
        root.addView(progress, plp);

        errorView = buildErrorView();
        errorView.setVisibility(View.GONE);
        root.addView(errorView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        setContentView(root);
        enableEdgeToEdge(root);

        // 关掉系统状态栏本身（内容已经 edge-to-edge 铺开了）。
        applyImmersive();

        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);
        s.setMediaPlaybackRequiresUserGesture(true);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        s.setUserAgentString(s.getUserAgentString() + " DayinWebShell/1.2");

        webView.setWebViewClient(new WebViewClient() {

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return handleUrl(request.getUrl());
            }

            @SuppressWarnings("deprecation")
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return handleUrl(Uri.parse(url));
            }

            @Override
            public void onReceivedHttpAuthRequest(WebView view, HttpAuthHandler handler,
                                                  String host, String realm) {
                String user = prefs.getString(KEY_USER, null);
                String pass = prefs.getString(KEY_PASS, null);
                if (user != null && pass != null && !credentialTried) {
                    credentialTried = true;
                    handler.proceed(user, pass);
                    return;
                }
                // 没有保存过凭据，或者保存的凭据被服务器拒了。
                // 注意：这里不能先 cancel 再 proceed —— HttpAuthHandler 只允许用一次，
                // 所以把决定权完全交给弹窗（用户点取消时才 cancel）。
                showCredentialDialog(host, handler);
            }

            @Override
            public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                progress.setVisibility(View.VISIBLE);
                progress.setProgress(0);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                progress.setVisibility(View.GONE);
                if (view.getProgress() >= 100) {
                    errorView.setVisibility(View.GONE);
                }
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request,
                                        WebResourceError error) {
                if (request.isForMainFrame()
                        && error.getErrorCode() != ERROR_AUTHENTICATION) {
                    showError("加载失败：" + error.getDescription());
                }
            }

            @SuppressWarnings("deprecation")
            @Override
            public void onReceivedError(WebView view, int errorCode, String description,
                                        String failingUrl) {
                if (errorCode != ERROR_AUTHENTICATION) {
                    showError("加载失败：" + description);
                }
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                progress.setProgress(newProgress);
                progress.setVisibility(newProgress >= 100 ? View.GONE : View.VISIBLE);
            }
        });

        if (savedInstanceState != null) {
            webView.restoreState(savedInstanceState);
        } else {
            webView.loadUrl(getString(R.string.start_url));
        }
    }

    /** 同一个 host 的链接留在 WebView 里，其余交给系统处理 */
    private boolean handleUrl(Uri uri) {
        String scheme = uri.getScheme() == null ? "" : uri.getScheme();
        if (!("http".equals(scheme) || "https".equals(scheme))) {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, uri));
            } catch (Exception ignored) {
            }
            return true;
        }
        String targetHost = uri.getHost();
        String ownHost = Uri.parse(getString(R.string.start_url)).getHost();
        if (targetHost != null && targetHost.equalsIgnoreCase(ownHost)) {
            return false;
        }
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, uri));
        } catch (Exception ignored) {
        }
        return true;
    }

    private void showCredentialDialog(String host, final HttpAuthHandler handler) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(20);
        box.setPadding(pad, pad, pad, 0);

        final EditText userInput = new EditText(this);
        userInput.setHint("用户名");
        userInput.setSingleLine(true);
        userInput.setInputType(InputType.TYPE_CLASS_TEXT);
        String savedUser = prefs.getString(KEY_USER, "");
        if (savedUser != null) {
            userInput.setText(savedUser);
        }
        box.addView(userInput);

        final EditText passInput = new EditText(this);
        passInput.setHint("密码");
        passInput.setSingleLine(true);
        passInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        box.addView(passInput);

        final CheckBox remember = new CheckBox(this);
        remember.setText("记住账号（只存在本机）");
        remember.setChecked(true);
        box.addView(remember);

        new AlertDialog.Builder(this)
                .setTitle("登录 " + host)
                .setView(box)
                .setCancelable(false)
                .setPositiveButton("登录", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String u = userInput.getText().toString().trim();
                        String p = passInput.getText().toString();
                        if (u.length() == 0) {
                            toast("用户名不能为空");
                            return;
                        }
                        SharedPreferences.Editor e = prefs.edit();
                        if (remember.isChecked()) {
                            e.putString(KEY_USER, u).putString(KEY_PASS, p);
                        } else {
                            e.remove(KEY_USER).remove(KEY_PASS);
                        }
                        e.apply();
                        credentialTried = true;
                        handler.proceed(u, p);
                    }
                })
                .setNeutralButton("清除已存账号", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        prefs.edit().clear().apply();
                        toast("已清除，重新加载后可重新输入");
                    }
                })
                .setNegativeButton("取消", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        handler.cancel();
                    }
                })
                .show();
    }

    private LinearLayout buildErrorView() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setBackgroundColor(Color.WHITE);
        int pad = dp(28);
        box.setPadding(pad, pad, pad, pad);

        errorText = new TextView(this);
        errorText.setText("网络好像不通");
        errorText.setTextSize(16);
        errorText.setGravity(Gravity.CENTER);
        box.addView(errorText);

        Button retry = new Button(this);
        retry.setText("重试");
        retry.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                errorView.setVisibility(View.GONE);
                credentialTried = false;
                webView.reload();
            }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(16);
        box.addView(retry, lp);
        return box;
    }

    private void showError(String message) {
        if (errorText != null) {
            errorText.setText(message + "\n\n请检查网络，或稍后重试。");
        }
        errorView.setVisibility(View.VISIBLE);
        progress.setVisibility(View.GONE);
    }

    /**
     * 统一的"返回"动作。
     *
     * 这个站点是纯 JS 切换视图的 SPA，不写 history，所以 WebView 的 canGoBack()
     * 永远是 false。真正的"上一级"是站内自己的 #backBtn，因此顺序是：
     *   1. 站内 #backBtn 可见 -> 点它；
     *   2. 否则 WebView 有历史 -> goBack()；
     *   3. 都没有 -> 退出。
     */
    private void goBackSmart() {
        if (webView == null) {
            finish();
            return;
        }
        if (backPending) {
            return;
        }
        backPending = true;
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                backPending = false;
            }
        }, 500);

        webView.evaluateJavascript(JS_CLICK_SITE_BACK, new ValueCallback<String>() {
            @Override
            public void onReceiveValue(String value) {
                if (value != null && value.contains("clicked")) {
                    return;
                }
                if (webView != null && webView.canGoBack()) {
                    webView.goBack();
                    return;
                }
                finish();
            }
        });
    }

    @Override
    public void onBackPressed() {
        goBackSmart();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (event.getRepeatCount() == 0) {
                goBackSmart();
            }
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (webView != null) {
            webView.saveState(outState);
        }
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.setWebChromeClient(null);
            webView.setWebViewClient(null);
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }


    /**
     * 沉浸式：隐藏系统状态栏，保留底部导航条（否则返回手势会消失）。
     * 状态栏被隐藏后从屏幕顶部下滑仍能临时唤出，所以看时间/通知不受影响。
     * 来电、输入法、弹窗等会让系统重新显示状态栏，因此在 onWindowFocusChanged 里再收一次。
     */
    /**
     * 真正的沉浸式：内容铺满整屏，包括刘海/挖孔那一段。
     *
     * - `setDecorFitsSystemWindows(false)`：窗口不再为系统栏预留空间；
     * - `layoutInDisplayCutoutMode = ALWAYS`：允许内容进入挖孔区域（不做 letterbox 留黑边）；
     * - 顶部**故意不补** padding，网页直接顶到前置摄像头底下；
     * - 只给底部补 padding（导航条与输入法取并集），免得正文被手势条和键盘盖住。
     */
    private void enableEdgeToEdge(final View root) {
        Window window = getWindow();
        WindowManager.LayoutParams lp = window.getAttributes();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false);
            lp.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            lp.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }
        window.setAttributes(lp);

        root.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            @Override
            public WindowInsets onApplyWindowInsets(View v, WindowInsets insets) {
                int bottom;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    bottom = insets.getInsets(
                            WindowInsets.Type.navigationBars() | WindowInsets.Type.ime()).bottom;
                } else {
                    bottom = insets.getSystemWindowInsetBottom();
                }
                if (v.getPaddingBottom() != bottom) {
                    v.setPadding(0, 0, 0, bottom);
                }
                return insets;
            }
        });
    }

    private void applyImmersive() {
        Window window = getWindow();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowInsetsController controller = window.getInsetsController();
            if (controller != null) {
                controller.setSystemBarsBehavior(
                        WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
                controller.hide(WindowInsets.Type.statusBars());
            }
        } else {
            window.getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            applyImmersive();
        }
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void toast(String text) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
    }
}
