package com.wnafee.vector.compat.demo;

/*
 * Copyright (C) 2015 Wael Nafee
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

import android.annotation.TargetApi;
import android.os.Build;
import android.os.Bundle;
import android.support.v7.app.ActionBarActivity;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.LinearLayout;
import android.widget.LinearLayout.LayoutParams;
import android.widget.Toast;

import com.wnafee.vector.MorphButton;
import com.wnafee.vector.MorphButton.MorphState;
import com.wnafee.vector.MorphButton.OnStateChangedListener;

@TargetApi(Build.VERSION_CODES.LOLLIPOP)
public class MainActivity extends ActionBarActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);


        // Example of adding MorphButton in java
        MorphButton mb = new MorphButton(this);
        LayoutParams p = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        mb.setLayoutParams(p);

        mb.setBackgroundTintList(getResources().getColorStateList(R.color.background_tint_color));
        mb.setForegroundTintList(getResources().getColorStateList(R.color.foreground_tint_color));
        mb.setStartDrawable(R.drawable.ic_pause_to_play);
        mb.setEndDrawable(R.drawable.ic_play_to_pause);
        mb.setOnStateChangedListener(new OnStateChangedListener() {
            @Override
            public void onStateChanged(MorphState changedTo, boolean isAnimating) {
                // Do something here
                Toast.makeText(MainActivity.this, "Changed to: " + changedTo, Toast.LENGTH_SHORT).show();
            }
        });

        LinearLayout ll = (LinearLayout) findViewById(R.id.base_view);
        ll.addView(mb);
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
}
