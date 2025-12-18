package com.mzh.coolweather.gson;
//引入一个总的实例类来引用刚刚创建的各个实体类
import com.google.gson.annotations.SerializedName;
import java.util.List;

public class Weather {
    //status，成功返回ok，失败返回原因
    public String status;
    public Basic basic;
    public AQI aqi;
    public Now now;
    public Suggestion suggestion;
    //daily_forecast包含的是数组，所以用List集合来引用Forecast类
    @SerializedName("daily_forecast")
    public List<Forecast> forecastList;
}
