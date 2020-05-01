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
        EventBus.getDefault().post(new TestEvent());
        boolean a = false;
        boolean b = false;
        Log.d("miyan", "onCreate: " + (a | b));
    }

    @Subscribe
    public void getEvent1(Object event) {
        Log.d("miyan", "getEvent: Object");
    }

    @Subscribe
    public void getEvent2(BaseEvent event) {
        Log.d("miyan", "getEvent: BaseEvent");
    }

    @Subscribe
    public void getEvent3(TestEvent event) {
        Log.d("miyan", "getEvent: TestEvent");
    }

    @Subscribe
    public void getEvent4(IEvent event) {
        Log.d("miyan", "getEvent: IEvent");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        EventBus.getDefault().unregister(this);
    }
}
