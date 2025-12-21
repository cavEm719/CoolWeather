package com.mzh.coolweather.util;

import okhttp3.OkHttpClient;
import okhttp3.Request;

public class HttpUtil {
    /*
    一个用于发送HTTP请求的静态方法
    address表示请求的URL地址
    callback是一个回调接口，用于处理异步请求的结果
     */
    public static void sendOkHttpRequest(String address,okhttp3.Callback callback){
        //okhttp通过回调机制将请求的结果返回给调用者
        OkHttpClient client = new OkHttpClient();
        //OkHttpClient是OkHttp的核心类，用于管理HTTP请求和响应，这里创建了一个默认配置的OkHttpClient
        //更改配置可以通过OkHttpClient.Builder来实现
        Request request = new Request.Builder().url(address).build();
        //Request.Builder()构建一个HTTP请求，之后设置属性，build()方法完成请求的构建
        client.newCall(request).enqueue(callback);
        //将request传递给OkHttpClient，准备发起请求
        //.enqueue(callback),以异步方式执行请求，并将结果通过回调接口返回
    }
}
