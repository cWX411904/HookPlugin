package com.ck.hooklogin;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Message;
import android.os.Parcelable;
import android.util.Log;
import android.view.View;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

public class HookUtils {

    private Context mContext;

    private static final String TAG = "wsj";
    private static final boolean DEBUG = BuildConfig.DEBUG;

    public void hookHookMh(Context context) {

        try {
            //反射找到ActivityThread类
            Class<?> forName = Class.forName("android.app.ActivityThread");
            //反射找到ActivityThead类中的静态成员变量：sCurrentActivityThread
            Field sCurrentActivityThread = forName.getDeclaredField("sCurrentActivityThread");
            sCurrentActivityThread.setAccessible(true);
            //静态成员变量是针对类本身的，所以可以通过get（null）方法获取ActivityThread类实例
            Object activityThreadObj = sCurrentActivityThread.get(null);

            //反射找到ActivityThread的mH对象
            Field handleField = forName.getDeclaredField("mH");
            handleField.setAccessible(true);
            //对象可以通过get方法获取本身的实体类，因为mH不是静态的，所以get方法必须传入ActivityThread类对象
            Handler mH = (Handler) handleField.get(activityThreadObj);
            //Hook点是Handler的handlerMessage，那么怎样拉到我们自己的代码里执行呢？
            //有两个方法，要么是动态代理，要么是设置接口
            //因为Handler这个类本身就提供了CallBack这个接口，我么可以复用它，然后执行我们自己的逻辑
            Field callbackField = Handler.class.getDeclaredField("mCallback");
            callbackField.setAccessible(true);
            //将重写后的callback接口，重写替换系统的callback，这样在系统执行Handler的callback时候，就是执行我们本地重写的callback
            callbackField.set(mH, new ActivityMH(mH));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    class ActivityMH implements android.os.Handler.Callback {

        private android.os.Handler mH;

        public ActivityMH(android.os.Handler mH) {
            this.mH = mH;
        }

        @Override
        public boolean handleMessage(Message msg) {
            if (msg.what == 100) {
                //代表是系统要执行LaunchActivity这个方法了，我们就在这里给它进行还原
                reserLaunchActivity(msg);
            }
            mH.handleMessage(msg);
            return true;
        }

        private void reserLaunchActivity(Message msg) {
            //还原
            Object obj = msg.obj;
            try {
                Field intentField = obj.getClass().getDeclaredField("intent");
                intentField.setAccessible(true);
                //这个是ProxyActivity
                Intent realIntent = (Intent) intentField.get(obj);

                //这个是SecondActivity
                Intent oldIntent = realIntent.getParcelableExtra("oldIntent");

                if (oldIntent != null) {
                    //这里做集中式登录
                    SharedPreferences sharedPreferences = mContext.getSharedPreferences("ck", Context.MODE_PRIVATE);
                    if (sharedPreferences.getBoolean("login", false)) {
                        //如果是已经登录过了，就把原有的意图放到realyIntent
                        realIntent.setComponent(oldIntent.getComponent());
                    } else {
                        //没有登录过，统一调整到LoginActivity进行登录
                        ComponentName componentName = new ComponentName(mContext, LoginActivity.class);
                        realIntent.putExtra("extraIntent", oldIntent.getComponent().getClassName());
                        realIntent.setComponent(componentName);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public void hookStartActivity(Context context) {
        //还原gDefault也就是getDefault成员变量

        this.mContext = context;
        Log.d(TAG, "hookStartActivity: ");
        try {
            //根据class路径反射找到ActivityManagerNative类
            Class<?> ActivityManagerNativeCls = Class.forName("android.app.ActivityManagerNative");
            //找到ActivityManagerNative的成员变量：gDefault
            //注意：这里只是针对23版本，因为26版本的源码是getDefault，以后要写的时候要增加判断兼容
            Field gDefault = ActivityManagerNativeCls.getDeclaredField("gDefault");
            gDefault.setAccessible(true);
            //因为是静态变量，所以获取得到的是系统值(默认值是null)
            Object defaultValue = gDefault.get(null);

            //再反射找到Singleton对象，为什么要找这个需要看源码
            Class<?> SingletonClass = Class.forName("android.util.Singleton");
            //找到mInstance对象
            Field mInstance = SingletonClass.getDeclaredField("mInstance");
            mInstance.setAccessible(true);
            //还原IActivityManager对象
            Object iActivityManagerObject = mInstance.get(defaultValue);

            //通过动态代理将IActivityManager的startActivity方法拉到我们自己写的逻辑代码中来
            Class<?> IActivityManagerIntercept = Class.forName("android.app.IActivityManager");

            /**
             * 第一个参数：classLoader
             * 第二个参数：即将返回的对象，需要实现哪些接口
             * 第三个参数：是即将实现的接口，比如说此刻不会调用startActivity方法而是走InvocationHandler里面的invoke方法，
             *              同时invoke参数的method就是startActivity
             */
            Object oldIActivityManager = Proxy.newProxyInstance(Thread.currentThread().getContextClassLoader(),
                    new Class[]{IActivityManagerIntercept, View.OnClickListener.class},
                    new startActivity(iActivityManagerObject));

            /**
             * 将系统的IActivityManager替换成自己通过动态代理实现的对象
             */
            Log.d(TAG, "hookStartActivity: 将系统的IActivityManager替换成自己通过动态代理实现的对象");
            mInstance.set(defaultValue, oldIActivityManager);

        } catch (Exception e) {
            Log.e("wsj", "HookUtils hookStartActivity: " + "e" + e.getMessage());
            e.printStackTrace();
        }

    }

    class startActivity implements InvocationHandler {

        private Object iActivityManagerObject;

        public startActivity(Object iActivityManagerObject) {
            this.iActivityManagerObject = iActivityManagerObject;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            Log.d(TAG, "invoke: ");
            if ("startActivity".equals(method.getName())) {
                Log.d(TAG, "invoke: ---->startActivity动态代理调用");
                //开始瞒天过海
                //寻找传进来的Intent
                Intent intent = null;
                int index = 0;
                for (int i = 0; i < args.length; i++) {
                    Object arg = args[i];
                    if (arg instanceof Intent) {
                        index = i;
                        intent = (Intent) arg;
                    }
                }
                Intent newIntent = new Intent();

                //原本是未注册的activity，这里将未注册的activity的Intent转换为已注册的代理Activity：ProxyActivity
                ComponentName componentName = new ComponentName(mContext, ProxyActivity.class);
                newIntent.setComponent(componentName);
                //真实的意图，被隐藏到了键值对里面
                newIntent.putExtra("oldIntent", intent);

                args[index] = newIntent;
            }
            return method.invoke(iActivityManagerObject, args);
        }
    }
}
