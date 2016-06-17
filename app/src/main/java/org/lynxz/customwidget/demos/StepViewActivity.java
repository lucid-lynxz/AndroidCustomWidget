package org.lynxz.customwidget.demos;

import android.os.Bundle;
import android.view.View;

import org.lynxz.customstepviewlibrary.StepView;
import org.lynxz.customwidget.BaseActivity;
import org.lynxz.customwidget.R;

public class StepViewActivity extends BaseActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_step_view);

        final StepView sv = findView(R.id.sv);
        findView(R.id.btn_forward).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sv.setCurrentProgress(sv.getCurrentProgress() + 1);
            }
        });
    }
}
