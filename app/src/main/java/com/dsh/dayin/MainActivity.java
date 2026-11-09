package com.dsh.dayin;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.HttpAuthHandler;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
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
        s.setUserAgentString(s.getUserAgentString() + " DayinWebShell/1.1");

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

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(0, 1, 0, "清除已保存的账号");
        menu.add(0, 2, 0, "重新加载");
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == 1) {
            prefs.edit().clear().apply();
            credentialTried = false;
            toast("已清除，正在重新加载");
            webView.reload();
            return true;
        }
        if (item.getItemId() == 2) {
            credentialTried = false;
            webView.reload();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && webView != null && webView.canGoBack()) {
            webView.goBack();
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

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void toast(String text) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
    }
}
