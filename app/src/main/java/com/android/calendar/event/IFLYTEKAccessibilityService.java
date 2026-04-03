package com.android.calendar.event; // 替换为你的实际包名

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Path;
import android.graphics.Rect;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.time.LocalDateTime;

/**
 * 讯飞输入法自动长按空格键唤醒语音的无障碍服务（Java版）
 */
public class IFLYTEKAccessibilityService extends AccessibilityService {
    private static final String TAG = "IFLYTEK_SERVICE";
    private boolean isTriggered = false; // 防抖标记
    private static final long DEBOUNCE_TIME = 1500L; // 1.5秒防抖
    private static final long DELAY_TIME = 800L; // 800ms延迟等待键盘加载


    private BroadcastReceiver cancelReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            // 1. 获取屏幕真实尺寸（包含导航栏，适配全面屏）
            WindowManager windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
            Display display = windowManager.getDefaultDisplay();
            DisplayMetrics metrics = new DisplayMetrics();
            display.getRealMetrics(metrics); // 关键：getRealMetrics获取真实屏幕尺寸（含导航栏）
            int screenWidth = metrics.widthPixels;
            int screenHeight = metrics.heightPixels;

            // 2. 计算讯飞空格键坐标（底部居中，适配所有手机）
            float targetX = screenWidth / 2.0f; // 水平居中
            float targetY = screenHeight * 0.95f; // 垂直底部（可调整：0.85~0.92）

            Path releasePath = new Path();
            releasePath.moveTo(targetX, targetY);


//            停止语音输入功能,让识别的语音显示到输入框中
            GestureDescription.StrokeDescription releaseStroke = new GestureDescription.StrokeDescription(
                    releasePath, 0, 50);

            GestureDescription gesture = new GestureDescription.Builder()
                    .addStroke(releaseStroke)
                    .build();
            // 4. 执行手势
            dispatchGesture(gesture, new GestureResultCallback() {
                @Override
                public void onCompleted(GestureDescription gestureDescription) {
                    super.onCompleted(gestureDescription);
                    Log.d(TAG, "讯飞语音键抬起成功");
                }

                @Override
                public void onCancelled(GestureDescription gestureDescription) {
                    super.onCancelled(gestureDescription);
                    Log.d(TAG, "讯飞语音键抬起被取消");
                }
            }, null);
        }
    };

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    @Override
    public void onCreate() {
        super.onCreate();
        IntentFilter filter = new IntentFilter("com.yourapp.CANCEL_GESTURE");

        // ===== 关键修正：Android 12+ 必须传递第 3 个参数 =====
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) { // API 31 = Android 12
            // 自定义广播优先用 NOT_EXPORTED（仅本应用可用）
            registerReceiver(
                    cancelReceiver,   // 接收器
                    filter,           // 过滤规则
                    Context.RECEIVER_NOT_EXPORTED // 必须加的标记！
            );
        } else {
            // 低版本（< Android 12）用 2 个参数的重载方法
            registerReceiver(cancelReceiver, filter);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        unregisterReceiver(cancelReceiver);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // 1. 只处理窗口状态变化事件
        if (event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            return;
        }

        // 1. 先判断当前活跃窗口是不是你的App（核心！）
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null || root.getPackageName() == null || !root.getPackageName().toString().contains("ws.xsoh.etar")) {
            return; // 不是自己的App，直接返回，0耗电
        }

        // 2. 获取窗口包名和类名
        String packageName = event.getPackageName() != null ? event.getPackageName().toString() : "";
        String className = event.getClassName() != null ? event.getClassName().toString() : "";

        // 3. 匹配输入框或讯飞输入法窗口（触发条件）
        boolean isInputWindow =  className.contains("InputMethod")
                || packageName.contains("iflytek.inputmethod");

        // 4. 防抖+触发核心逻辑
        if (isInputWindow && !isTriggered) {
            isTriggered = true;
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                simulateLongClickOnSpaceKey();
                // 重置防抖标记
                new Handler(Looper.getMainLooper()).postDelayed(() -> isTriggered = false, DEBOUNCE_TIME);
            }, DELAY_TIME);
        }
    }

    private void simulateLongClickOnSpaceKey() {
        // 1. 获取屏幕真实尺寸（包含导航栏，适配全面屏）
        WindowManager windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        Display display = windowManager.getDefaultDisplay();
        DisplayMetrics metrics = new DisplayMetrics();
        display.getRealMetrics(metrics); // 关键：getRealMetrics获取真实屏幕尺寸（含导航栏）
        int screenWidth = metrics.widthPixels;
        int screenHeight = metrics.heightPixels;

        // 2. 计算讯飞空格键坐标（底部居中，适配所有手机）
        float targetX = screenWidth / 2.0f; // 水平居中
        float targetY = screenHeight * 0.95f; // 垂直底部（可调整：0.85~0.92）

        // 3. 创建长按手势：按下→保持→抬起（长按时间1000ms）
        // 按下路径
        Path pressPath = new Path();
        pressPath.moveTo(targetX, targetY); // 移动到目标坐标
        // 抬起路径（和按下坐标一致，模拟长按）
        Path releasePath = new Path();
        releasePath.moveTo(targetX, targetY);

        // 构建手势
        GestureDescription.StrokeDescription pressStroke = new GestureDescription.StrokeDescription(
                pressPath, 0, 50);

        GestureDescription gesture = new GestureDescription.Builder()
                .addStroke(pressStroke)
                .build();

        // 4. 执行手势
        dispatchGesture(gesture, new GestureResultCallback() {
            @Override
            public void onCompleted(GestureDescription gestureDescription) {
                super.onCompleted(gestureDescription);
                Log.d(TAG, "讯飞语音输入手势执行成功");
            }

            @Override
            public void onCancelled(GestureDescription gestureDescription) {
                super.onCancelled(gestureDescription);
                Log.d(TAG, "手势被取消");
            }
        }, null);
    }

    @Override
    public void onInterrupt() {
        // 服务被中断时的空实现
    }
}
