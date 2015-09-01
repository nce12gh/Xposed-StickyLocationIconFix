package com.xinlu.location_icon_fix;

import android.app.PendingIntent;
import android.os.IBinder;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

import static de.robv.android.xposed.XposedHelpers.findAndHookMethod;
import static de.robv.android.xposed.XposedHelpers.findClass;
import static de.robv.android.xposed.XposedHelpers.getIntField;
import static de.robv.android.xposed.XposedHelpers.getLongField;
import static de.robv.android.xposed.XposedHelpers.getObjectField;

public class ModDebugAppOps {
    public static void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (lpparam.packageName.equals("android") && lpparam.processName.equals("android")) {
            try {
                final Class<?> appops = findClass("com.android.server.AppOpsService", lpparam.classLoader);
                findAndHookMethod(appops, "finishOperationLocked", "com.android.server.AppOpsService$Op", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        try {
                            Object op = param.args[0];
                            if (getIntField(op, "op") != ModFixLocationIcon.OP_MONITOR_HIGH_POWER_LOCATION)
                                return;
                            ModFixLocationIcon.logd("finishOperationLocked " + getObjectField(op, "packageName") + " uid=" + getIntField(op, "uid") + " time=" + getLongField(op, "time")
                                    + " duration=" + getIntField(op, "duration") + " nesting=" + getIntField(op, "nesting"));
                        } catch (Throwable t) {
                            XposedBridge.log(t);
                        }
                    }
                });
                findAndHookMethod(appops, "startOperation", IBinder.class, int.class, int.class, String.class, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        // public int startOperation(IBinder token, int code, int uid, String packageName)
                        try {
                            int opcode = (int) param.args[1];
                            if (opcode != ModFixLocationIcon.OP_MONITOR_HIGH_POWER_LOCATION) return;
                            ModFixLocationIcon.logd("startOperation" + " pkg=" + param.args[3] + " res=" + param.getResult() + " uid=" + param.args[2] + " time=" + System.currentTimeMillis());
                        } catch (Throwable t) {
                            XposedBridge.log(t);
                        }
                    }
                });

            } catch (Throwable t) {
                XposedBridge.log(t);
            }
            try {
                final Class<?> lms = findClass("com.android.server.LocationManagerService", lpparam.classLoader);
                findAndHookMethod(lms, "removeUpdates", "android.location.ILocationListener", PendingIntent.class, String.class, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        ModFixLocationIcon.logd("removeUpdates pkg=" + param.args[2] + " listener=" + param.args[0] + " intent=" + param.args[1]);
                    }
                });
                final Class<?> lmsReceiver = findClass("com.android.server.LocationManagerService$Receiver", lpparam.classLoader);
                XposedBridge.hookAllConstructors(lmsReceiver, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        ModFixLocationIcon.logd("Receiver constructed pkg=" + getObjectField(param.thisObject, "mPackageName") + " uid=" + getObjectField(param.thisObject, "mUid")
                                + " hide=" + getObjectField(param.thisObject, "mHideFromAppOps") + " listener=" + getObjectField(param.thisObject, "mListener")
                                + " intent=" + getObjectField(param.thisObject, "mPendingIntent"));
                    }
                });
            } catch (Throwable t) {
                XposedBridge.log(t);
            }
        }
    }
}
