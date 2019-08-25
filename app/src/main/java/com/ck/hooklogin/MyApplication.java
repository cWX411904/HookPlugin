package com.ck.hooklogin;

import android.app.Application;

public class MyApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        HookUtils hookUtils = new HookUtils();
        hookUtils.hookStartActivity(this);
    }
}
