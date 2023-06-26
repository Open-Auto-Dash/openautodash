package com.openautodash.utilities;

import android.content.Context;
import android.os.Environment;
import android.os.Handler;
import android.util.Log;

import com.openautodash.R;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class LogApp {
    private static final String TAG = "LogApp";
    private Context context;
    private static String filename;
    private String data;
    Handler handler = new Handler();

    public LogApp(Context context){
        this.context = context;
        filename = context.getResources().getString(R.string.log_filename);
    }

    public void Log(String content){
        Date date = new Date(System.currentTimeMillis());
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy.MMM.dd',' HH:mm:ss");
        String dateString = formatter.format(date);
        content = dateString + ": "+ content + "\n";
        writeToFile(filename, content);
    }

    private void writeToFile(String fileName, String content){
        File path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS);
        File newDir = new File(path + "/OpenAutodash/" + fileName);
        try{
            if (!newDir.exists()) {
                if(!newDir.mkdir()) {
                    Log.e(TAG, "writeToFile: can't create directory" + newDir.getPath());
                    return;
                }
            }
            FileOutputStream writer = new FileOutputStream(new File(path, filename));
            writer.write(content.getBytes());
            writer.close();
            Log.d(TAG, content);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
