// SPDX-License-Identifier: GPL-3.0-or-later
package com.osfans.trime.cloudsync;
import android.app.*;
import android.os.Bundle;
import android.content.Intent;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import androidx.work.*;
import java.io.*;

import java.util.concurrent.TimeUnit;
public class CloudSettingsActivity extends Activity {
    private TextView status;private EditText access,secret;private Button save,sync;private CheckBox automatic;private CloudCredentials credentials;
    @Override public void onCreate(Bundle saved){super.onCreate(saved);credentials=new CloudCredentials(this);
        ScrollView scroll=new ScrollView(this);LinearLayout content=new LinearLayout(this);content.setOrientation(1);int p=(int)(22*getResources().getDisplayMetrics().density);content.setPadding(p,p,p,p);scroll.addView(content);setContentView(scroll);scroll.setOnApplyWindowInsetsListener((v,insets)->{content.setPadding(p,p+insets.getSystemWindowInsetTop(),p,p+insets.getSystemWindowInsetBottom());return insets;});
        TextView title=new TextView(this);title.setText("同文 · 云同步");title.setTextSize(28);content.addView(title);
        TextView guide=new TextView(this);guide.setText("首次先完成输入法启用，再填写 S3 密钥并点一键同步。方案、词库会从电脑云备份导入；手机键盘和皮肤单独保留。初次约 296 MB，请保持页面打开。云同步使用应用专属存储，无需文件夹授权。");guide.setTextSize(16);content.addView(guide);
        Button keyboard=new Button(this);keyboard.setText("输入法设置与启用");content.addView(keyboard);keyboard.setOnClickListener(v->startActivity(new Intent(Intent.ACTION_MAIN).setClassName(this,"com.osfans.trime.ui.main.MainActivity")));
        access=new EditText(this);access.setHint("S3 Access Key");access.setSingleLine(true);access.setText(credentials.prefs.getString("access",""));content.addView(access);
        secret=new EditText(this);secret.setHint("S3 Secret Key（本机加密保存）");secret.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);secret.setSingleLine(true);secret.setSaveEnabled(false);secret.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);content.addView(secret);
        save=new Button(this);save.setText("保存密钥");content.addView(save);save.setOnClickListener(v->{try{credentials.save(access.getText().toString(),secret.getText().toString());secret.setText("");status.setText("密钥已加密保存");}catch(Exception e){status.setText(e.getMessage());}});
        sync=new Button(this);sync.setText("一键拉取、合并并上传");content.addView(sync);sync.setOnClickListener(v->{sync.setEnabled(false);save.setEnabled(false);automatic.setEnabled(false);CloudSync.start(this,new CloudSync.Callback(){public void onMessage(String message){if(!isDestroyed())status.setText(message);}public void onDone(boolean success){if(!isDestroyed()){sync.setEnabled(true);save.setEnabled(true);automatic.setEnabled(true);}}});});
        automatic=new CheckBox(this);automatic.setText("每小时自动同步（省电策略可能延迟）");automatic.setChecked(credentials.prefs.getBoolean("automatic",false));content.addView(automatic);
        automatic.setOnCheckedChangeListener((button,checked)->{if(checked&&credentials.prefs.getString("last_success","").isEmpty()){automatic.setChecked(false);status.setText("请先完成一次手动同步，再启用后台同步。");return;}credentials.prefs.edit().putBoolean("automatic",checked).apply();WorkManager wm=WorkManager.getInstance(this);if(checked){Constraints constraints=new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build();PeriodicWorkRequest request=new PeriodicWorkRequest.Builder(CloudSyncWorker.class,1,TimeUnit.HOURS).setConstraints(constraints).build();wm.enqueueUniquePeriodicWork("rime-s3-hourly",ExistingPeriodicWorkPolicy.KEEP,request);}else wm.cancelUniqueWork("rime-s3-hourly");});
        status=new TextView(this);status.setTextSize(16);status.setText("S3：s3.loto2.top / sync\n最近成功："+credentials.prefs.getString("last_success","尚未同步")+"\n"+credentials.prefs.getString("last_error",""));content.addView(status);
        EditText github=new EditText(this);github.setHint("GitHub 主题仓库网址");github.setSingleLine(true);github.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_URI);github.setText(getSharedPreferences("theme-import",MODE_PRIVATE).contains("repository")?"https://github.com/"+getSharedPreferences("theme-import",MODE_PRIVATE).getString("repository",""):"");content.addView(github);
        Button importTheme=new Button(this);importTheme.setText("从 GitHub 导入并替换主题");content.addView(importTheme);
        importTheme.setOnClickListener(v->{importTheme.setEnabled(false);GitHubTheme.start(this,github.getText().toString(),new GitHubTheme.Callback(){
            public void onMessage(String message){if(!isDestroyed())status.setText(message);}
            public void onChoices(String[] names,GitHubTheme.Choice choice){if(isDestroyed()){choice.choose(-1);return;}new AlertDialog.Builder(CloudSettingsActivity.this).setTitle("选择主题布局").setItems(names,(dialog,which)->choice.choose(which)).setOnCancelListener(dialog->choice.choose(-1)).show();}
            public void onDone(boolean success,String message){if(!isDestroyed()){importTheme.setEnabled(true);status.setText(message);}}
        });});
        TextView themeGuide=new TextView(this);themeGuide.setText("已内置经典·顺手版，默认微信暗黑配色。支持公开 GitHub 仓库根网址；导入校验后选择布局，将替换上次导入的主题。词库和同步密钥保留。");content.addView(themeGuide);
        GitHubTheme.initialize(this);
        EditText test=new EditText(this);test.setHint("在这里试用输入法");content.addView(test);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
    }

}
