package com.quyunshuo.eventbuscodeparsing;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;

import com.quyunshuo.eventbus.EventBus;
import com.quyunshuo.eventbus.Subscribe;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        EventBus.getDefault().register(this);
        EventBus.getDefault().post("111");
    }

    @Subscribe
    public void getEvent(String event) {
        Log.d("miyan", "getEvent: ");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        EventBus.getDefault().unregister(this);
    }
}
