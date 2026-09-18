package io.github.yuhj319.meowpower;

import android.app.Application;

import io.github.libxposed.service.XposedService;
import io.github.libxposed.service.XposedServiceHelper;

/**
 * 模块 App 侧入口。负责与 LSPosed 框架建立 Binder 连接，
 * 拿到 {@link XposedService} 后即可读写 RemotePreferences 并触发热重载。
 */
public class App extends Application implements XposedServiceHelper.OnServiceListener {

    private static volatile XposedService sService;

    /** 框架未连接时返回 null。 */
    public static XposedService getService() {
        return sService;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        XposedServiceHelper.registerListener(this);
    }

    @Override
    public void onServiceBind(XposedService service) {
        sService = service;
    }

    @Override
    public void onServiceDied(XposedService service) {
        sService = null;
    }
}
