package com.ck.hooklogin;

import android.app.Application;
import android.content.Context;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.os.Environment;

import java.lang.reflect.Method;

public class MyApplication extends Application {

    private static Context  instance;
    private AssetManager assetManager;
    private Resources newResource;

    public static Context getInstance() {
        return instance;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        HookUtils hookUtils = new HookUtils();
        hookUtils.hookStartActivity(this);
//        hookUtils.injectPluginClass();

        String apkPath = Environment.getExternalStorageDirectory().getAbsolutePath() + "/plugin.apk";

        try {
            assetManager = AssetManager.class.newInstance();
            Method addAssetPathMethod = assetManager.getClass().getDeclaredMethod("addAssetPath", String.class);
            addAssetPathMethod.setAccessible(true);
            addAssetPathMethod.invoke(assetManager, apkPath);

            //手动实例化
            Method ensureStringBlocks = AssetManager.class.getDeclaredMethod("ensureStringBlocks");
            ensureStringBlocks.setAccessible(true);
            ensureStringBlocks.invoke(assetManager);

            //插件的StringBlock被实例化了
            Resources supReource = getResources();
            newResource = new Resources(assetManager, supReource.getDisplayMetrics(),
                    supReource.getConfiguration());
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    public AssetManager getAssetManager() {
        return assetManager == null ? super.getAssets() : assetManager;
    }

    @Override
    public Resources getResources() {
        return newResource == null ? super.getResources() : newResource;
    }
}
