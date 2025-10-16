package com.example.downloadmanagerlab7;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    EditText edtUrl;
    Button btnDownload;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 🟢 Xin quyền thông báo (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
            }
        }

        edtUrl = findViewById(R.id.edtUrl);
        btnDownload = findViewById(R.id.btnDownload);

        btnDownload.setOnClickListener(v -> {
            String url = edtUrl.getText().toString().trim();

            if (url.isEmpty()) {
                Toast.makeText(this, "Vui lòng nhập link tải!", Toast.LENGTH_SHORT).show();
                return;
            }

            Intent intent = new Intent(this, DownloadService.class);
            intent.putExtra("url", url);
            startService(intent);
        });
    }
}
