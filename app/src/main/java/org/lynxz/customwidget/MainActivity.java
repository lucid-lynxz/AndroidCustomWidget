package org.lynxz.customwidget;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import org.lynxz.customwidget.demos.CircleIndexActivity;
import org.lynxz.customwidget.demos.StepViewActivity;

public class MainActivity extends BaseActivity implements View.OnClickListener {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        findView(R.id.btn1).setOnClickListener(this);
        findView(R.id.btn2).setOnClickListener(this);
    }

    @Override
    public void onClick(View v) {
        int id = v.getId();
        Intent intent = null;
        switch (id) {
            case R.id.btn1:
                intent = new Intent(this, CircleIndexActivity.class);
                break;
            case R.id.btn2:
                intent = new Intent(this, StepViewActivity.class);
                break;
        }

        if (intent != null) {
            startActivity(intent);
        }
    }
}
