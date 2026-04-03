package com.android.calendar.alerts;

import static com.android.calendar.alerts.AlertService.FOREGROUND_CHANNEL_ID;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import com.android.calendar.AllInOneActivity;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Iterator;
import java.util.List;

import ws.xsoh.etar.R;

public class FiveMinuteService extends Service {
    private static final String TAG = "TaskNotification";
    private static final int NOTIFICATION_ID = 1001;
    private static final String CHANNEL_ID = "FiveMinuteChannel";

    // 独立通知相关（和常驻区分）
    private static final int INDEPENDENT_NOTIFICATION_ID = 1002;
    private static final String INDEPENDENT_CHANNEL_ID = "提前布置任务通知";

    private static final long POLL_INTERVAL = 50 * 1000; // 50 秒轮询一次
    private Handler handler;
    private Runnable checkRunnable;

    private int notificationCount = 10;

    @Override
    public void onCreate() {
        super.onCreate();
        // 1. 创建通知渠道（Android 8.0+ 必须）
        createNotificationChannel();
        // 2. 启动前台服务（避免被系统杀死）
        startForeground(NOTIFICATION_ID, createNotification());
        // 3. 初始化轮询任务
        initPolling();
//        DeleteCountPref();
    }

//    临时清除数据用
    private void DeleteCountPref(){
        String key = "all_task_time";
        SharedPreferences sp =this.getSharedPreferences(key, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sp.edit();
        editor.putString(key, "[]");
        editor.apply();
        Log.d("TaskNotification", "DeleteCountPref ");
    }


    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY; // 被杀死尽量重启
    }

    // 初始化轮询逻辑
    private void initPolling() {
        Log.d(TAG, "initPolling");
        handler = new Handler(Looper.getMainLooper());
        checkRunnable = new Runnable() {
            @Override
            public void run() {
                if (isFiveMinuteMultiple()) {
                    // 执行核心业务逻辑
                    Log.d(TAG, "后台触发 5 分钟倍数逻辑：" + System.currentTimeMillis());
                    clearExpiredTaskTime();
                    checkAndSendIndependentNotification();
                }
                // 继续轮询
                handler.postDelayed(this, POLL_INTERVAL);
            }
        };
        // 启动轮询
        handler.postDelayed(checkRunnable, 0);
    }

    /**
     * 核心逻辑：检查当前+30分钟是否不在任何任务时间段内，是则发送独立通知
     */
    private void checkAndSendIndependentNotification() {
        String key = "all_task_time";
        SharedPreferences sp = this.getSharedPreferences(key, Context.MODE_PRIVATE);
        String allTaskTime = sp.getString(key, "[]");

        Gson gson = new Gson();
        TypeToken<List<List<Long>>> typeToken = new TypeToken<List<List<Long>>>() {};
        List<List<Long>> taskTimeList = gson.fromJson(allTaskTime, typeToken.getType());

        // 兜底：空列表处理
        if (taskTimeList == null) {
            taskTimeList = new ArrayList<>();
        }

        // 1. 计算目标时间：当前时间 + 30分钟（毫秒）
        long currentTime = System.currentTimeMillis();
        long targetTime1 = currentTime + 58 * 60 * 1000;
        long targetTime2 = currentTime + 28 * 60 * 1000;
        long targetTime3 = currentTime + 18 * 60 * 1000;
        long targetTime4 = currentTime + 8 * 60 * 1000;

        Long[] targetTimes = {targetTime1, targetTime2, targetTime3, targetTime4};
        //        提前60,30,20,10都准备好任务了
        boolean findOne = true;
        for (Long targetTime : targetTimes) {
            findOne = false;
            for (List<Long> taskTime : taskTimeList) {
                if (taskTime != null && taskTime.size() >= 2) {
                    long startTime = taskTime.get(0);
                    long endTime = taskTime.get(1);
                    if (endTime - startTime == 24 * 60 * 60 * 1000) {
//                        忽略全天任务
                        continue;
                    }
                    if (targetTime >= startTime && targetTime <= endTime) {
                        Log.d(TAG, "目标时间落在时间段内：[" + startTime + ", " + endTime + "]");
                        findOne = true;
                        break; // 找到一个就停止遍历
                    }
                }
            }
            //如果一个都没找到,那就是没有提前布置任务
            if (!findOne) {
                break;
            }
        }

        if (!findOne) {
            Log.d(TAG, "没有全部提前准备好任务，发送独立通知");
            sendIndependentNotification();
        }
    }

    /**
     * 发送独立通知（和常驻通知区分）
     */
    private void sendIndependentNotification() {
        // 1. 检查通知权限（Android 13+）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            boolean hasPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    == PackageManager.PERMISSION_GRANTED;
            Log.d(TAG, "是否有通知权限：" + hasPermission);
        }

        // 2. 检查通知渠道是否存在
        NotificationManager manager = (NotificationManager) this.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            NotificationChannel channel = manager.getNotificationChannel(INDEPENDENT_CHANNEL_ID);
            if (channel == null) {
                Log.e(TAG, "独立通知渠道不存在！");
            } else {
                Log.d(TAG, "独立通知渠道优先级：" + channel.getImportance());
            }
        }
        NotificationManager notificationManager = (NotificationManager) this.getSystemService(Context.NOTIFICATION_SERVICE);

        // 你想跳转到的页面（自己改类名）
        Intent intent = new Intent(this, AllInOneActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        // 构建 PendingIntent
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
        );

        // 构建独立通知
        Notification notification = new NotificationCompat.Builder(this, INDEPENDENT_CHANNEL_ID)
                .setContentTitle("未提前布置任务")
                .setContentText("请提前布置全部任务并准备")
                .setSmallIcon(R.mipmap.ic_launcher) // 替换为你的图标
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true) // 点击后自动取消
                .setContentIntent(pendingIntent) // ✅ 点击跳转
                .setOnlyAlertOnce(true) // 只提醒一次，避免重复弹窗
                .build();
        // 发送通知
        notificationManager.notify(INDEPENDENT_NOTIFICATION_ID + notificationCount++, notification);
    }


    /**
     * 清理过期任务记录：删除 all_task_time 中结束时间 < 当前时间戳的记录
     */
    private void clearExpiredTaskTime() {
        // 1. 定义常量（和保存逻辑保持一致）
        String key = "all_task_time";
        SharedPreferences sp = this.getSharedPreferences(key, Context.MODE_PRIVATE);
        Gson gson = new Gson();

        // 2. 获取当前时间戳（毫秒）
        long currentTime = System.currentTimeMillis();

        // 3. 读取 SP 中的任务记录（和保存逻辑一致）
        String allTaskTime = sp.getString(key, "[]");
        TypeToken<List<List<Long>>> typeToken = new TypeToken<List<List<Long>>>() {};
        List<List<Long>> taskTimeList = gson.fromJson(allTaskTime, typeToken.getType());

        // 4. 防止解析返回 null（兜底处理）
        if (taskTimeList == null) {
            taskTimeList = new ArrayList<>();
        }

        // 5. 遍历删除过期记录（用 Iterator 安全删除，避免索引错位）
        Iterator<List<Long>> iterator = taskTimeList.iterator();
        while (iterator.hasNext()) {
            List<Long> taskTime = iterator.next();
            // 校验子列表长度（避免空列表/长度不足导致下标越界）
            if (taskTime != null && taskTime.size() >= 2) {
                long endTime = taskTime.get(1); // 第二个元素是结束时间
                // 判断：结束时间 < 当前时间 → 移除该记录
                if (endTime < currentTime) {
                    iterator.remove(); // 安全删除，不会导致遍历异常
                    Log.d("TaskNotification", "清理过期任务：startTime=" + taskTime.get(0) + ", endTime=" + endTime);
                }
            } else {
                // 移除格式异常的无效记录（比如长度不足2的列表）
                iterator.remove();
                Log.d("TaskNotification", "清理格式异常的任务记录：" + taskTime);
            }
        }
        // 6. 将筛选后的结果重新保存到 SP
        SharedPreferences.Editor editor = sp.edit();
        String newJsonStr = gson.toJson(taskTimeList);
        editor.putString(key, newJsonStr);
        editor.apply();

        Log.d("TaskNotification", "清理完成后的数据：" + newJsonStr);
    }
    // 判断是否为 5 分钟倍数
    private boolean isFiveMinuteMultiple() {
        Calendar calendar = Calendar.getInstance();
        int currentHour = calendar.get(Calendar.HOUR_OF_DAY);
        int currentMinute = calendar.get(Calendar.MINUTE);
        int currentSecond = calendar.get(Calendar.SECOND);

        if( currentHour >=22 || currentHour < 7){
//            夜间时间，不触发，从7点开始触发
            return false;
        }
        return (currentMinute % 5 == 0);
    }

    // 创建前台服务通知
    private Notification createNotification() {
        Intent intent = new Intent(this, AllInOneActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        // 构建 PendingIntent
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
        );

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("检查任务中...")
                .setContentText("检查任务中...")
                .setOngoing(true)
                .setContentIntent(pendingIntent) // ✅ 点击跳转
                .setSmallIcon(R.mipmap.ic_launcher)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }

    // 创建通知渠道
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "5分钟检查服务",
                    NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(channel);

            // 2. 创建独立通知渠道（重点！）
            NotificationChannel independentChannel = new NotificationChannel(
                    INDEPENDENT_CHANNEL_ID,
                    INDEPENDENT_CHANNEL_ID,
                    NotificationManager.IMPORTANCE_DEFAULT // 必须是DEFAULT/HIGH才能弹窗
            );
            // 可选：增加震动/声音，提升提醒效果
            independentChannel.enableVibration(true);
            independentChannel.setVibrationPattern(new long[]{0, 500});
            manager.createNotificationChannel(independentChannel);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        // 停止轮询，释放资源
        if (handler != null && checkRunnable != null) {
            handler.removeCallbacks(checkRunnable);
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    // 启动 Service 的静态方法（外部调用）
    public static void start(Context context) {
        Intent intent = new Intent(context, FiveMinuteService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
    }
}
