package com.dsh.dayin;

import android.app.Activity;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * 最小可运行的 Android 示例：一个计数器。
 * 目的只是把 "GitHub Actions -> APK" 这条链路跑通，没有引入任何第三方依赖。
 */
public class MainActivity extends Activity {

    private int count = 0;
    private TextView counterText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        int pad = (int) (24 * getResources().getDisplayMetrics().density);
        root.setPadding(pad, pad, pad, pad);

        TextView title = new TextView(this);
        title.setText("GitHub Actions 构建成功");
        title.setTextSize(20);
        title.setGravity(Gravity.CENTER);
        root.addView(title);

        counterText = new TextView(this);
        counterText.setText("点击次数：0");
        counterText.setTextSize(32);
        counterText.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        tlp.setMargins(0, pad, 0, pad);
        root.addView(counterText, tlp);

        Button button = new Button(this);
        button.setText("点我 +1");
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                count++;
                counterText.setText("点击次数：" + count);
            }
        });
        root.addView(button);

        setContentView(root);
    }
}
