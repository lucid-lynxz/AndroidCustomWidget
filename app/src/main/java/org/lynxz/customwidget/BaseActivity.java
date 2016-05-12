package org.lynxz.customwidget;

import android.support.v4.app.FragmentActivity;
import android.view.View;

/**
 * Created by zxz on 2016/5/12.
 */
public class BaseActivity extends FragmentActivity {

    public <T extends View> T findView(int id) {
        return (T) findViewById(id);
    }
}
