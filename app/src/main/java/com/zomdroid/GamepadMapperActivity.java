package com.zomdroid;

import android.os.Bundle;
import android.view.View;
import android.widget.FrameLayout;

import androidx.appcompat.app.AppCompatActivity;

import com.zomdroid.fragments.GamepadMapperFragment;

/** Special input-capture host retained outside the Compose management shell. */
public class GamepadMapperActivity extends AppCompatActivity {
    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        FrameLayout root = new FrameLayout(this);
        int containerId = View.generateViewId();
        root.setId(containerId);
        setContentView(root);
        getSupportFragmentManager().beginTransaction()
                .replace(containerId, new GamepadMapperFragment())
                .commit();
    }
}
