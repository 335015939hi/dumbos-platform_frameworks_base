package android.ext.dcl;

import android.annotation.NonNull;
import android.annotation.SuppressLint;
import android.annotation.SystemApi;
import android.app.ActivityManager;
import android.app.AppGlobals;
import android.app.Application;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.GosPackageState;
import android.ext.settings.app.AswRestrictMemoryDynCodeLoading;
import android.ext.settings.app.AswRestrictStorageDynCodeLoading;
import android.ext.settings.app.AswRestrictWebViewDynCodeLoading;
import android.os.RemoteException;
import android.system.Os;
import android.util.Log;

import androidx.annotation.Keep;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Objects;

import dalvik.system.DexFile;

/** @hide */
@SystemApi
public class DynCodeLoading {
    private static final String TAG = DynCodeLoading.class.getSimpleName();

    /** @hide */
    public static final int RESTRICT_MEMORY_DCL = 1;
    /** @hide */
    public static final int RESTRICT_STORAGE_DCL = 1 << 1;
    /** @hide */
    public static final int RESTRICT_WEBVIEW_DCL = 1 << 2;

    /** @hide */
    public static int getAppBindFlags(Context ctx, int userId, ApplicationInfo appInfo,
                                      GosPackageState gosPs) {
        int res = 0;

        if (AswRestrictMemoryDynCodeLoading.I.get(ctx, userId, appInfo, gosPs)) {
            res |= RESTRICT_MEMORY_DCL;
        }

        if (AswRestrictStorageDynCodeLoading.I.get(ctx, userId, appInfo, gosPs)) {
            res |= RESTRICT_STORAGE_DCL;
        }

        if (AswRestrictWebViewDynCodeLoading.I.get(ctx, userId, appInfo, gosPs)) {
            res |= RESTRICT_WEBVIEW_DCL;
        }

        return res;
    }

    private static int appBindFlags;

    /** @hide */
    public static int getAppBindFlags() {
        return appBindFlags;
    }

    /** @hide */
    public static void handleAppBindFlags(int flags) {
        appBindFlags = flags;
        if ((flags & (RESTRICT_MEMORY_DCL | RESTRICT_STORAGE_DCL)) != 0) {
            // RESTRICT_WEBVIEW_DCL is intentionally not included here since it means
            // "restrict DCL for isolated WebView processes" and shouldn't apply inside other app's
            // processes. WebView processes are being moved to the new native-only (no ART) isolated
            // processes in Android 17, which removes the need to block Java DCL there.
            DexFile.enableDynCodeLoadingChecks(flags);
        }
        var sb = new StringBuilder("AppBindFlags: ");
        if (flags == 0) {
            sb.append("0");
        } else {
            if ((flags & RESTRICT_MEMORY_DCL) != 0) {
                sb.append("RESTRICT_MEMORY_DCL, ");
            }
            if ((flags & RESTRICT_STORAGE_DCL) != 0) {
                sb.append("RESTRICT_STORAGE_DCL, ");
            }
            if ((flags & RESTRICT_WEBVIEW_DCL) != 0) {
                sb.append("RESTRICT_WEBVIEW_DCL, ");
            }
        }
        Log.d(TAG, sb.toString());
    }

    private static void showNotif(int type, String path, String denialType, Exception e) {
        String pkgName = AppGlobals.getInitialPackage();

        var reportLines = new ArrayList<String>();
        reportLines.add("process: " + Application.getProcessName());
        reportLines.add("thread: " + Thread.currentThread().getName());
        reportLines.add("");
        String stackTrace = Log.getStackTraceString(e);
        reportLines.addAll(Arrays.asList(stackTrace.split("\n")));

        try {
            ActivityManager.getService().showDynCodeLoadingNotification(type, pkgName, path,
                    reportLines, denialType);
        } catch (RemoteException re) {
            Log.d(TAG, "", re);
        }
    }

    @Keep // called from native ART code
    public static void checkInMemoryDexFileOpen(int flags) {
        var se = new SecurityException();
        showNotif(RESTRICT_MEMORY_DCL, null, "InMemoryDexFile", se);
        throw se;
    }

    @Keep
    @SuppressLint("GenericException")
    public static void checkDexFileOpen(int flags, @NonNull String rawPath) throws Exception {
        String gmscompatFdPrefix = "/gmscompat_fd_";
        if (rawPath.startsWith(gmscompatFdPrefix)) {
            int fd = Integer.parseInt(rawPath.substring(gmscompatFdPrefix.length()));
            rawPath = Os.readlink("/proc/self/fd/" + fd);
        }

        String path;
        try {
            path = new File(rawPath).getCanonicalPath();
        } catch (IOException e) {
            path = null;
        }

        boolean allow = false;
        if (path != null) {
            String[] pathParts = path.split("/");
            if (!pathParts[0].equals("")) {
                throw new IllegalStateException(path);
            }

            switch (pathParts[1]) {
                case "apex":
                case "product":
                case "system":
                case "system_ext":
                case "vendor":
                    allow = true;
                    break;
                case "data":
                    allow = path.startsWith("/data/app/") && path.endsWith(".apk");
                    break;
            }
        }


        if (allow) {
            Log.d(TAG, "allowed DexFileOpen from " + rawPath);
        } else {
            String msg = "DCL via storage, path: " + rawPath;
            if (!Objects.equals(rawPath, path)) {
                msg += ", canonicalPath: " + path;
            }
            var e = new SecurityException(msg);
            showNotif(RESTRICT_STORAGE_DCL, rawPath, "DexFileOpen", e);
            throw e;
        }
    }

    private DynCodeLoading() {}
}
