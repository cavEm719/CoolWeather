package com.mzh.coolweather.service;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.IBinder;
import android.os.SystemClock;
import android.preference.PreferenceManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.mzh.coolweather.gson.Weather;
import com.mzh.coolweather.util.HttpUtil;
import com.mzh.coolweather.util.Utility;

import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;

public class AutoUpdateService extends Service {
    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        //服务暂时不需要实现IBinder的通信方式
        return null;
    }

    public int onStartCommand(Intent intent, int flags, int startId){
        //调用update方法更新天气
        updateWeather();
        //获取系统中的AlarmManager对象，用于设定定时任务
        AlarmManager manager = (AlarmManager) getSystemService(ALARM_SERVICE);
        //设定8小时的毫秒数作为定时任务的时间间隔
        int anHour = 5 * 1000;//8个小时的毫秒数
        //使用SystemClock.elapsedRealtime()获取系统启动时间到现在的毫秒数
        //加上anHour构成下次的刷新触发时间
        long triggerAtTime = SystemClock.elapsedRealtime() + anHour;
        //创建一个Intent对象，用于启动AutoUpdateService
        Intent i = new Intent(this,AutoUpdateService.class);
        //创建一个pendingIntent对象，用于在定时任务触发时启动服务。
        PendingIntent pendingIntent = PendingIntent.getService(this,0,i,0);
        //取消之前设置的定时任务，确保不会重复触发
        manager.cancel(pendingIntent);
        //使用ELAPSED_REALTIME_WAKEUP类型设置定时任务，设备睡眠时也能正常唤醒
        manager.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAtTime, pendingIntent);
        //返回默认的启动命令处理方式
        return super.onStartCommand(intent,flags,startId);
    }

    //更新天气信息
    private void updateWeather(){
        //获取缓存
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        //从缓存中获取天气数据
        String weatherString = prefs.getString("weather",null);
        if(weatherString != null){
            //有缓存时直接解析天气数据
            //使用Utility类中的方法解析天气数据
            Weather weather = Utility.handleWeatherResponse(weatherString);
            //获取天气数据中的城市ID
            String weatherId = weather.basic.weatherId;
            //构造获取天气信息的URL
            String weatherUrl = "http://guolin.tech/api/weather?cityid=" + weatherId +
                    "&key=c0cb2db1acd8488fa4f25d75daef33ce";
            //使用HttpUtil工具类发送网络请求
            HttpUtil.sendOkHttpRequest(weatherUrl, new Callback() {
                @Override
                public void onFailure(@NonNull Call call, @NonNull IOException e) {
                    //异常时，打印失败信息
                    e.printStackTrace();
                }
                @Override
                public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                    //获取服务器返回的响应数据
                    String responseText = response.body().string();
                    //使用Utility类解析响应数据
                    Weather weather = Utility.handleWeatherResponse(responseText);
                    //如果解析后的天气数据不为空且状态为ok
                    if(weather != null && "ok".equals(weather.status)){
                        //获取SharedPreferences的编辑器
                        SharedPreferences.Editor editor = PreferenceManager.
                                getDefaultSharedPreferences(AutoUpdateService.this).edit();
                        //将最新的天气数据存入缓存
                        editor.putString("weather",responseText);
                        //提交编辑器，保存数据
                        editor.apply();
                    }
                }
            });
        }
    }
}
