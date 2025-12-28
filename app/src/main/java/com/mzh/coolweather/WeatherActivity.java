package com.mzh.coolweather;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.mzh.coolweather.gson.Forecast;
import com.mzh.coolweather.gson.Weather;
import com.mzh.coolweather.service.AutoUpdateService;
import com.mzh.coolweather.util.HttpUtil;
import com.mzh.coolweather.util.Utility;

import java.io.IOException;
import java.lang.ref.WeakReference;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;
import okhttp3.internal.Util;
import android.graphics.Color;

//在活动中请求天气数据
public class WeatherActivity extends AppCompatActivity {
    private ScrollView weatherLayout;
    private TextView titleCity;
    //private TextView titleUpdateTime;
    private TextView degreeText;
    private TextView weatherInfoText;
    private LinearLayout forecastLayout;
    private TextView aqiText;
    private TextView pm25Text;
    private TextView comfortText;
    private TextView carWashText;
    private TextView sportText;

    public SwipeRefreshLayout swipeRefresh;

    public SwipeRefreshLayout swipeRefreshLayout;
    private String mWeatherId;

    public DrawerLayout drawerLayout;
    private Button navButton;
    private static final int UPDATE_WEATHER_SUCCESS = 1;
    private static final int UPDATE_WEATHER_FAILURE = 2;

    // 集中式 Handler 实例
    private final UIHandler mHandler = new UIHandler(this);

    //静态内部类，统一处理UI更新
    //静态内部类可以独立于外部类实例化，避免外部类实例的生命周期对内部类的影响
    private static class UIHandler extends Handler {
        //使用WeakReference持有WeatherActivity的引用
        //WeakReference的作用是避免内存泄漏。如果WeatherActivity被回收
        //WeakReference不会阻止其被垃圾回收器回收
        private final WeakReference<WeatherActivity> mActivityRef;

        //构造函数，初始化UIHandler并关连WeatherActivity
        //使用Looper.getMainLooper()确保Handler在主线程的Looper上执行
        //因为UI操作必须在主线程中进行
        UIHandler(WeatherActivity activity) {
            super(Looper.getMainLooper());//指定主线程Looper，确保在主线程执行
            mActivityRef = new WeakReference<>(activity);//初始化WeakReference
        }

        @Override//重写handleMessage方法，根据消息类型处理不同的UI更新逻辑
        public void handleMessage(@NonNull Message msg) {
            //通过WeakReference获取WeatherActivity的实例
            WeatherActivity activity = mActivityRef.get();
            //如果WeatherActivity已经被回收，则直接返回，避免空指针异常
            if (activity == null) return;
            //根据消息的what字段判断消息类型并执行相应的逻辑
            switch (msg.what) {
                //成功消息
                case UPDATE_WEATHER_SUCCESS:
                    //msg.obj中保存了解析后的Weather对象
                    //Bundle中携带完整的响应字符串，便于后续写入缓存
                    Weather weather = (Weather) msg.obj;//获取Weather对象
                    //获取响应字符串
                    String responseText = msg.getData().getString("responseText");
                    //调用activity的方法更新UI
                    activity.updateUIWithWeather(weather, responseText);
                    break;//
                //失败信息
                case UPDATE_WEATHER_FAILURE:
                    Toast.makeText(activity, "获取天气信息失败", Toast.LENGTH_SHORT).show();
                    activity.swipeRefreshLayout.setRefreshing(false);//停止下拉刷新动画
                    break;
            }
        }
    }
