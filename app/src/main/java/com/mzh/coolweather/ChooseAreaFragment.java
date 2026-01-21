package com.mzh.coolweather;

import android.app.ProgressDialog;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import com.mzh.coolweather.db.City;
import com.mzh.coolweather.db.County;
import com.mzh.coolweather.db.Province;
import com.mzh.coolweather.util.HttpUtil;
import com.mzh.coolweather.util.Utility;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.litepal.LitePal;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;
import android.app.Activity;

public class ChooseAreaFragment extends Fragment{
    public static final int LEVEL_PROVINCE = 0;
    public static final int LEVEL_CITY = 1;
    public static final int LEVEL_COUNTY = 2;

    //进度对话框，用于显示加载进度
    private ProgressDialog progressDialog;
    //UI
    private TextView titleText;
    private Button backButton;
    private ListView listView; //列表视图
    private ArrayAdapter<String> adapter; //列表适配器
    private List<String> dataList = new ArrayList<>(); //列表数据
    //数据模型
    private List<Province> provinceList;
    private List<City> cityList;
    private List<County> countyList;
    private Province selectedProvince;
    private City selectedCity;
    private int currentLevel;

    @Override
    //初始化Fragment视图，加载布局文件，初始化UI控件，设置适配器
    //在onCreateView获取了一些控件的实例，并初始化了ArrayAdapter，将其设置为ListView的适配器
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState){
        View view = inflater.inflate(R.layout.choose_area, container,false);//加载布局文件
        titleText = view.findViewById(R.id.title_text);//初始化标题栏文本
        backButton = view.findViewById(R.id.back_button);//初始化按钮
        listView = view.findViewById(R.id.list_view);//初始化列表视图
        adapter = new ArrayAdapter<>(getContext(), android.R.layout.simple_list_item_1,dataList);
        //初始化适配器
        listView.setAdapter(adapter);
        return view;
    }

    //Fragment创建完成后，设置点击事件并加载数据
    //此方法中，为列表项和返回按钮设置点击事件，并调用方法加载省数据
    @Override
    public void onActivityCreated(Bundle savedInstanceState){
        super.onActivityCreated(savedInstanceState);
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            //列表项点击事件，根据当前的级别，判断加载哪一级的数据
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if(currentLevel == LEVEL_PROVINCE){
                    selectedProvince = provinceList.get(position);
                    //选中省份后，加载市数据
                    queryCities();
                }else if(currentLevel == LEVEL_CITY){
                    selectedCity = cityList.get(position);
                    //选中市后，加载县区数据
                    queryCounties();
                }else if(currentLevel == LEVEL_COUNTY){
                    //如果当前是县区级，就启动WeatherActivity，把当前区域的天气id传过去
                    String weatherId = countyList.get(position).getWeatherId();
                    /*
                    instanceof关键字可以用来判断一个对象是否属于某个类的实例
                    在碎片中调用getActivity()方法，然后配合instanceof关键字
                    就能轻松判断出该碎片是在MainActivity当中，还是在WeatherActivity当中
                    如果是在MainActivity当中，那么处理逻辑不变。如果是在WeatherActivity当中
                    那么就关闭滑动菜单，显示下拉刷新进度条，然后请求新城市的天气信息
                     */
                    if(getActivity() instanceof  MainActivity){
                        Intent intent = new Intent(getActivity(), WeatherActivity.class);
                        intent.putExtra("weather_id",weatherId);
                        startActivity(intent);
                        getActivity().finish();
                    }else if(getActivity() instanceof WeatherActivity){
                        WeatherActivity activity = (WeatherActivity) getActivity();
                        activity.drawerLayout.closeDrawers();
                        activity.swipeRefreshLayout.setRefreshing(true);
                        activity.requestWeather(weatherId);
                    }
                }
            }
        });
        //设置返回键
        backButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(currentLevel == LEVEL_COUNTY){
                    //返回市列表
                    queryCities();
                }else if(currentLevel == LEVEL_CITY){
                    //返回省列表
                    queryProvince();
                }
            }
        });
        queryProvince();//首次加载省数据
    }

    //查询全国所有省份，优先从本地数据库查，如果没有数据则从服务器查询
    private void queryProvince(){
        titleText.setText("中国");
        backButton.setVisibility(View.GONE); //隐藏返回按钮
        //从数据库中查询数据
        provinceList = LitePal.findAll(Province.class);
        if(provinceList.size() > 0){
            dataList.clear();
            for(Province province : provinceList){
                //将省份的名称逐个添加到列表
                dataList.add(province.getProvinceName());
            }
            adapter.notifyDataSetChanged();//通知适配器数据更新
            listView.setSelection(0);//设置列表选中第一项
            currentLevel = LEVEL_PROVINCE;//设置当前级别为省
        }else{
            String address = "http://guolin.tech/api/china";//省份数据服务器的地址
            queryFromService(address,"province");//从服务器查询省份数据
        }
    }

    //再查城市
    private void queryCities(){
        titleText.setText(selectedProvince.getProvinceName());
        backButton.setVisibility(View.VISIBLE); //显示返回按钮
        //从数据库查询城市数据,where为指定查询条件，用？表示参数将被后续提供的值替换
        //String.valueOf(selectedProvince.getId())获取当前选定的省份的id
        //find根据获取到的省份id查询其所有城市
        cityList = LitePal.where("provinceid = ?", String.valueOf(selectedProvince.getId())).find(City.class);
        if(cityList.size() > 0){
            dataList.clear();
            for(City city : cityList){
                dataList.add(city.getCityName()); //添加城市名称到列表
            }
            adapter.notifyDataSetChanged();//通知适配器更新
            listView.setSelection(0); //设置列表选第一项
            currentLevel = LEVEL_CITY;
        }else {
            int provinceCode = selectedProvince.getProvinceCode();
            String address = "http://guolin.tech/api/china/" + provinceCode; //城市数据的URL
            queryFromService(address,"city");
        }
    }

    //区县，完全同理
    private void queryCounties() {
        titleText.setText(selectedCity.getCityName());
        backButton.setVisibility(View.VISIBLE);
        countyList = LitePal.where("cityid = ?",String.valueOf(selectedCity.getId())).find(County.class);
        if (countyList.size() > 0) {
            dataList.clear();
            for (County county : countyList) {
                dataList.add(county.getCountyName());
            }
            adapter.notifyDataSetChanged();
            listView.setSelection(0);
            currentLevel = LEVEL_COUNTY;
        } else {
            int provinceCode = selectedProvince.getProvinceCode();
            int cityCode = selectedCity.getCityCode();
            String address = "http://guolin.tech/api/china/" + provinceCode + "/" + cityCode;
            queryFromService(address,"county");
        }
    }

    //从服务器查询
    //type表示数据类型，分为省市县三种
    private void queryFromService(String address, final String type){
        showProgressDialog(); //显示进度对话框，提示用户正在加载
        //使用HttpUtil发送HTTP请求到指定地址，并设置回调处理响应
        //什么是回调？
        //CallBack是一个编程模式，将一个方法作为参数传递给另一个方法，并在适当时候调用。
        //安卓开发常用于处理异步操作的结果，如网络请求，文件操作或耗时任务
        //这些操作耗时长，主线程不能直接等待他们完成，所以需要这种机制来通知主线程已经完成。
        HttpUtil.sendOkHttpRequest(address, new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                //请求失败时的回调
                //使用Handler
                new Handler(Looper.getMainLooper()).post(new Runnable(){
                    @Override
                    public void run(){
                        closeProgressDialog();//关闭进度对话框
                        //提示用户加载失败，Toast是什么？
                        Toast.makeText(getContext(),"加载失败",Toast.LENGTH_SHORT).show();
                    }
                });
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                //获取服务器返回的JSON数据
                String responseText = response.body().string();
                boolean result = false;//用于标记数据解析是否成功
                //根据请求类型解析数据
                if("province".equals(type)){
                    result = Utility.handleProvinceResponse(responseText);
                }else if("city".equals(type)){
                    result = Utility.handleCityResponse(responseText,selectedProvince.getId());
                }else if("county".equals(type)){
                    result = Utility.handleCountyResponse(responseText, selectedCity.getId());
                }

                //如果数据解析成功，更新UI
                if(result){
                    new Handler(Looper.getMainLooper()).post(new Runnable() {
                        @Override
                        public void run(){
                            //根据请求类型重新加载对应的数据
                            closeProgressDialog();
                            if ("province".equals(type)) {
                                queryProvince();
                            } else if ("city".equals(type)) {
                                queryCities();
                            } else if ("county".equals(type)) {
                                queryCounties();
                            }
                        }
                    });
                }
            }
        });
    }

    //加载进度对话框
    private void showProgressDialog(){
        if(progressDialog == null){//如果进度框未初始化，创建一个新的
            progressDialog = new ProgressDialog(getContext());
            progressDialog.setMessage("正在加载");
            progressDialog.setCanceledOnTouchOutside(false); //设置不可通过点击外部取消
        }
        progressDialog.show();
    }

    //关闭进度对话框
    private void closeProgressDialog(){
        if(progressDialog != null){
            progressDialog.dismiss();
        }
    }


}
