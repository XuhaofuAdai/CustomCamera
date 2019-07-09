package com.xhf.customcamera.utils;

import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

/**
 * 权限帮助类
 */
public class PerMissionUtil {
    /**
     * @param activity    当前使用activity
     * @param permissions 需要获取的权限
     * @return
     */
    public boolean checkPermissions(Activity activity, String[] permissions) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {  //权限小于M 直接返回true 不用进行权限管理
            return true;
        }

        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(activity, permission) != PackageManager.PERMISSION_GRANTED ||
                    ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)) {
                return false;
            }

        }
        return true;
    }
}
