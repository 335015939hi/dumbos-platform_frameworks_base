package com.android.internal.os;

import android.app.LoadedApk;
import android.app.ZygotePreload;
import android.content.ComponentName;
import android.content.pm.ServiceInfo;
import android.os.ParcelFileDescriptor;
import android.system.ErrnoException;
import android.system.Os;
import android.util.Log;

import java.io.IOException;
import java.util.Arrays;

import static com.android.internal.util.Preconditions.checkState;
import static java.util.Objects.requireNonNull;

public class ExecSpawning {
    static final String TAG = "ExecSpawning";

    // Zygote argument for passing commands from zygote to exec-spawned app process
    static final String COMMAND_FD_ARG = "--command-fd=";

    private static boolean isExecSpawnedProcess;
    private static ZygoteArguments[] commandsToReplay;

    public static boolean isExecSpawnedProcess() {
        return isExecSpawnedProcess;
    }

    public static boolean isReplayingZygoteCommands() {
        return commandsToReplay != null;
    }

    static void init(String[] argv) {
        boolean logv = Log.isLoggable(TAG, Log.VERBOSE);
        if (logv) Log.v(TAG, "argv: " + Arrays.toString(argv));

        String arg = argv[argv.length - 1];
        if (!arg.startsWith(COMMAND_FD_ARG)) {
            if (arg.equals("--socket-name=zygote")) {
                // we're in the primary 64-bit zygote
                ZygoteCommandRecorder.enable(true);
            } else if (arg.equals("--enable-lazy-preload") && "--socket-name=zygote_secondary".equals(argv[argv.length - 2])) {
                // we're in the 32-bit zygote
                ZygoteCommandRecorder.enable(false);
            }
            return;
        }

        if (logv) Log.v(TAG, "init");

        try {
            Os.setenv("IS_EXEC_SPAWNED_APP_PROCESS", "1", true);
        } catch (ErrnoException e) {
            throw new IllegalStateException(e);
        }

        isExecSpawnedProcess = true;

        byte[] serializedCmds;
        {
            int separator = arg.indexOf('_', COMMAND_FD_ARG.length());
            int fd = Integer.parseInt(arg, COMMAND_FD_ARG.length(), separator, 10);
            int fdSize = Integer.parseInt(arg, separator + 1, arg.length(), 10);
            try (var stream = new ParcelFileDescriptor.AutoCloseInputStream(ParcelFileDescriptor.adoptFd(fd))) {
                serializedCmds = stream.readNBytes(fdSize);
            } catch (IOException e) {
                Log.e(TAG, "", e);
                // fd is a memfd, using it should never fail
                throw new IllegalStateException(e);
            }
            checkState(serializedCmds.length == fdSize);
        }
        checkState(commandsToReplay == null);
        commandsToReplay = ZygoteCommandRecorder.deserializeCommands(serializedCmds);;
    }

    static Runnable replayCommands(ZygoteServer zygoteServer) {
        ZygoteArguments[] commandsToReplay = ExecSpawning.commandsToReplay;
        requireNonNull(commandsToReplay);

        ZygoteConnection pseudoConnection;
        try {
            pseudoConnection = new ZygoteConnection(null, null);
        } catch (IOException e) {
            // pseudo ZygoteConnection doesn't perform any IO
            throw new IllegalStateException(e);
        }

        boolean logv = Log.isLoggable(TAG, Log.VERBOSE);

        for (int i = 0; i < commandsToReplay.length; ++i) {
            ZygoteArguments cmd = commandsToReplay[i];
            if (logv) Log.v(TAG, "replaying cmd " + (i + 1) + " / " + commandsToReplay.length + ": " + cmd);
            Runnable r = pseudoConnection.processCommand(zygoteServer, false, cmd);
            if (i == commandsToReplay.length - 1) {
                checkState(r != null);
                ExecSpawning.commandsToReplay = null;
                return r;
            }
            if (r != null) { // some commands run in-place, without a Runnable
                r.run();
            }
        }
        throw new IllegalStateException("unreachable");
    }

    private static boolean handledAppZygotePreload;

    // App zygote preloading is pointless when exec spawning is used but apps might depend on it
    public static void handleAppZygotePreload(ServiceInfo serviceInfo, LoadedApk loadedApk) {
        if (!isExecSpawnedProcess()) {
            return;
        }
        if ((serviceInfo.flags & ServiceInfo.FLAG_USE_APP_ZYGOTE) == 0) {
            return;
        }

        String zygotePreloadName = serviceInfo.applicationInfo.zygotePreloadName;
        if (zygotePreloadName == null) {
            Log.e(TAG, "maybePerformAppZygotePreload: FLAG_USE_APP_ZYGOTE is set but zygotePreloadName is null");
            return;
        }

        switch (zygotePreloadName) {
            case "org.chromium.chrome.app.TrichromeZygotePreload":
            case "org.chromium.content_public.app.ZygotePreload":
            case "org.mozilla.gecko.process.ZygotePreload":
                Log.i(TAG, "skipping ZygotePreload that is known to be optional: " + zygotePreloadName);
                return;
        }

        // Note that the process is in the isolated_app SELinux domain at this point. When zygote
        // spawning is used, app zygote is running in the app_zygote SELinux domain when it performs
        // preloading. This shouldn't cause issues in practice since app_zygote and isolated_app
        // domains have similar level of isolation.
        synchronized (ExecSpawning.class) {
            if (handledAppZygotePreload) {
                throw new IllegalStateException("app zygote preloading was already handled");
            }
            handledAppZygotePreload = true;

            String className = ComponentName.createRelative(
                    serviceInfo.applicationInfo.packageName, zygotePreloadName).getClassName();

            // copied from AppZygoteInit.handlePreloadApp()
            try {
                Class cls = Class.forName(className, true, loadedApk.getClassLoader());
                if (!ZygotePreload.class.isAssignableFrom(cls)) {
                    Log.e(TAG, className + " does not implement "
                            + ZygotePreload.class.getName());
                    return;
                }
                var preloadObject = (ZygotePreload) cls.getConstructor().newInstance();
                Log.i(TAG, "handleAppZygotePreload: starting preload via " + zygotePreloadName);
                preloadObject.doPreload(serviceInfo.applicationInfo);
                Log.i(TAG, "handleAppZygotePreload: finished preload");
            } catch (ReflectiveOperationException e) {
                Log.e(TAG, "preload failed for " + zygotePreloadName, e);
            }
        }
    }
}
