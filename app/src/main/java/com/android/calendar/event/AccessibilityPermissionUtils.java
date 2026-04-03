package com.android.calendar.event;

import android.content.Context;
import android.content.Intent;
import android.provider.Settings;
import android.view.accessibility.AccessibilityManager;

/**
 * 无障碍权限检测与引导工具类
 */
public class AccessibilityPermissionUtils {

    /**
     * 检查无障碍服务是否已开启
     * @param context 上下文
     * @param serviceClass 无障碍服务类（如IFLYTEKAccessibilityService.class）
     * @return true=已开启，false=未开启
     */
    public static boolean isAccessibilityServiceEnabled(Context context, Class<?> serviceClass) {
        AccessibilityManager am = (AccessibilityManager) context.getSystemService(Context.ACCESSIBILITY_SERVICE);
        // 获取当前已开启的所有无障碍服务
        String enabledServices = Settings.Secure.getString(context.getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (enabledServices == null) {
            return false;
        }

        // 拼接当前服务的完整名称（包名/类名）
        String serviceName = context.getPackageName() + "/" + serviceClass.getName();
        // 检查是否包含当前服务
        return enabledServices.contains(serviceName);
    }

    /**
     * 跳转到无障碍服务设置页面
     * @param context 上下文
     */
    public static void goToAccessibilitySettings(Context context) {
        Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }
}
