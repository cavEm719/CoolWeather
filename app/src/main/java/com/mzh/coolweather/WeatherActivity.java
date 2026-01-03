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


    @Override
    protected void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_weather);
        //初始化各种控件
        weatherLayout = (ScrollView) findViewById(R.id.weather_layout);
        titleCity = (TextView) findViewById(R.id.title_city);
        //titleUpdateTime = (TextView) findViewById(R.id.title_update_time);
        degreeText = (TextView) findViewById(R.id.degree_text);
        weatherInfoText =(TextView) findViewById(R.id.weather_info_text);
        forecastLayout = (LinearLayout) findViewById(R.id.forecast_layout);
        aqiText = (TextView) findViewById(R.id.aqi_text);
        pm25Text = (TextView) findViewById(R.id.pm25_text);
        comfortText = (TextView) findViewById(R.id.comfort_text);
        carWashText = (TextView) findViewById(R.id.car_wash_text);
        sportText = (TextView) findViewById(R.id.sport_text);
        //用于滑动菜单的逻辑处理
        //这两个方法是为了和对应的UI对应上
        //findViewById用于根据资源id获取布局文件中的定义的视图
        drawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);
        navButton = (Button) findViewById(R.id.nav_button);
        //
        swipeRefreshLayout = (SwipeRefreshLayout) findViewById(R.id.swipe_refresh);
        swipeRefreshLayout.setColorSchemeResources(com.google.android.material.R.color.design_default_color_primary);


        //获取SharedPreferences实例，用于读取本地缓存的天气数据
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        String weatherString = prefs.getString("weather",null);
        if(weatherString != null){
            //有缓存则直接解析缓存数据
            Weather weather = Utility.handleWeatherResponse(weatherString);
            showWeatherInfo(weather);
        }else{
            //没有缓存，走服务器
            String weatherId = getIntent().getStringExtra("weather_id");
            //如果没有数据，则把视图先隐藏，因为没有数据的视图不美观
            weatherLayout.setVisibility(View.INVISIBLE);
            requestWeather(weatherId);
        }

        //设置刷新以设置天气
        //为swipeRefreshLayout设置一个下拉监听器
        //用户通过下拉动作触发刷新时，监听器调用requestWeather(mWeatherId)方法重新请求天气数据
        //这也是回调
        swipeRefreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                requestWeather(mWeatherId);
            }
        });
        //点击时间监听器，用于切换城市打开侧边栏
        navButton.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View view){
                drawerLayout.openDrawer(GravityCompat.START);
            }
        });
    }

    //根据天气id请求城市信息
    public void requestWeather(final String weatherId){
        //构造请求天气信息的URL
        String weatherUrl = "http://guolin.tech/api/weather?cityid=" + weatherId +
                "&key=c0cb2db1acd8488fa4f25d75daef33ce";
        //使用HttpUtil发送网络请求
        HttpUtil.sendOkHttpRequest(weatherUrl, new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Message msg = mHandler.obtainMessage(UPDATE_WEATHER_FAILURE);
                mHandler.sendMessage(msg);
            }


            @Override//okhttp的回调方法，当网络请求成功返回响应时调用
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                //获取响应正文内容，并将其转换为字符串
                //这里response.body().string()只能调用一次，因为ResponseBody是一个流
                //调用后流会被关闭，所以需要将内容存储的变量中
                final String responseText = response.body().string();
                //使用handleWeatherResponse方法解析响应字符串
                //将其转换为Weather对象，这个方法通常会解析JSON数据并封装成Weather类的实例
                final Weather weather = Utility.handleWeatherResponse(responseText);
                if (weather != null && "ok".equals(weather.status)) {
                    //如果数据有效，就创建一个消息对象，用于通知主线程更新UI
                    //使用mHandler.obtainMessage方法创建消息，并指定消息类型为UPDATE_WEATHER_SUCCESS
                    //同时将解析后的Weather对象作为消息的内容
                    Message msg = mHandler.obtainMessage(UPDATE_WEATHER_SUCCESS, weather);
                    //创建一个Bundle对象，用于携带额外的数据
                    //这里将完整的响应字符串存储到Bundle中，便于后续写入缓存或进行其他处理
                    Bundle bundle = new Bundle();
                    bundle.putString("responseText", responseText);
                    //将bundle对象设置到消息中
                    msg.setData(bundle);
                    //将消息发送到主线程的Handler中，触发UI更新
                    mHandler.sendMessage(msg);
                } else {
                    //数据获取失败
                    //发送一个空消息，消息类型为 UPDATE_WEATHER_FAILURE，通知主线程处理失败情况
                    mHandler.sendEmptyMessage(UPDATE_WEATHER_FAILURE);
                }
            }

        });
    }

    //处理并展示Weather对象中的天气信息
    private void showWeatherInfo(Weather weather){
        //从Weather对象中提前并展示天气信息
        String cityName = weather.basic.cityName;
        //更新时间
        String updateTime = weather.basic.update.updateTime.split(" ")[1];
        String degree = weather.now.temperature + "℃";
        String weatherInfo = weather.now.more.info;
        //更新界面控件的文本内容
        titleCity.setText(cityName);
        degreeText.setText(degree);
        weatherInfoText.setText(weatherInfo);
        //titleUpdateTime.setText(updateTime);
        //清空未来天气预报布局
        forecastLayout.removeAllViews();
        //遍历天气预报列表，动态添加预报信息到界面
        //动态加载布局并加入相应的数据
        for (Forecast forecast : weather.forecastList) {
            View view = LayoutInflater.from(this).inflate(R.layout.forecast_item,forecastLayout,false);
            TextView dateText = (TextView) view.findViewById(R.id.date_text);
            TextView infoText = (TextView) view.findViewById(R.id.info_text);
            TextView maxText = (TextView) view.findViewById(R.id.max_text);
            TextView minText = (TextView) view.findViewById(R.id.min_text);
            dateText.setText(forecast.date);
            infoText.setText(forecast.more.info);
            maxText.setText(forecast.temperature.max);
            minText.setText(forecast.temperature.min);
            forecastLayout.addView(view);
        }
        //如果有空气质量数据，展示AQI和PM2.5值
        if(weather.aqi != null){
            aqiText.setText(weather.aqi.city.aqi);
            pm25Text.setText(weather.aqi.city.pm25);
        }
        // 展示生活指数建议
        String comfort = "舒适度：" + weather.suggestion.comfort.info;
        String carWash = "洗车指数：" + weather.suggestion.carWash.info;
        String sport = "运动建议:" + weather.suggestion.sport.info;

        comfortText.setText(comfort);
        carWashText.setText(carWash);
        sportText.setText(sport);

        //显示天气布局
        //加载完数据，重新显示视图
        weatherLayout.setVisibility(View.VISIBLE);
        //激活后台更新服务
        //一旦选中了某个天气并成功更新后，AutoUpdateService就会一直在后台运行
        //并每隔8小时更新一次天气
        Intent intent = new Intent(this, AutoUpdateService.class);
        startService(intent);
    }

    private void updateUIWithWeather(Weather weather, String responseText) {
        SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(this).edit();
        editor.putString("weather", responseText);
        editor.apply();
        mWeatherId = weather.basic.weatherId;
        showWeatherInfo(weather);
        swipeRefreshLayout.setRefreshing(false);
    }

}
