package com.mzh.coolweather.gson;

import com.google.gson.annotations.SerializedName;

//由于JSON中一些字段可能不适合直接作为JAVA字段来命名
// 所以这里使用@SerializedName注解的方式让JSON和Java字段之间建立映射关系
//JSON字段映射为JAVA实体
public class Basic {
    @SerializedName("city")
    public String cityName;
    @SerializedName("id")
    public String weatherId;
    public Update update;
    public class Update{
        @SerializedName("loc")
        public String updateTime;
    }
}
