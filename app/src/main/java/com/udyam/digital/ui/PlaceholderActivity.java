package com.udyam.digital.ui;

import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.udyam.digital.R;

/** Stand-in for the KYC, order tracker and chatbot screens. */
public class PlaceholderActivity extends AppCompatActivity {
    public static final String EXTRA_TITLE = "title";
    public static final String EXTRA_ICON = "icon";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_placeholder);
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setTitle(getIntent().getIntExtra(EXTRA_TITLE, R.string.app_name));
        toolbar.setNavigationOnClickListener(v -> finish());
        HelpButton.addTo(toolbar, this);
        String icon = getIntent().getStringExtra(EXTRA_ICON);
        ((TextView) findViewById(R.id.placeholder_icon)).setText(icon != null ? icon : "🚧");
    }
}
