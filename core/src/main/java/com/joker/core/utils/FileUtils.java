package com.joker.core.utils;

import android.support.annotation.NonNull;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Created by joker on 2017/7/12.
 */

public class FileUtils {


    public static void copyFile(@NonNull InputStream in, @NonNull OutputStream out) {
        BufferedInputStream bin = null;
        BufferedOutputStream bout = null;
        try {
            bin = new BufferedInputStream(in);
            bout = new BufferedOutputStream(out);
            int count = 0;
            byte data[] = new byte[8192];
            while ((count = bin.read(data, 0, 8192)) != -1) {
                bout.write(data, 0, count);
            }
            bout.flush();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            closeOutputStream(bout);
            closeOutputStream(out);
            closeInputStream(bin);
            closeInputStream(in);
        }
    }


    private static void closeInputStream(InputStream in) {
        if (in != null) {
            try {
                in.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private static void closeOutputStream(OutputStream out) {
        if (out != null) {
            try {
                out.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }


}
