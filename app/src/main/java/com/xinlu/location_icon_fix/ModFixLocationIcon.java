package com.xinlu.location_icon_fix;

import android.content.Context;
import android.content.Intent;
import android.os.UserHandle;
import android.text.TextUtils;

import java.util.HashMap;
import java.util.List;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

import static de.robv.android.xposed.XposedHelpers.callMethod;
import static de.robv.android.xposed.XposedHelpers.findAndHookMethod;
import static de.robv.android.xposed.XposedHelpers.findClass;
import static de.robv.android.xposed.XposedHelpers.getAdditionalInstanceField;
import static de.robv.android.xposed.XposedHelpers.getBooleanField;
import static de.robv.android.xposed.XposedHelpers.getIntField;
import static de.robv.android.xposed.XposedHelpers.getLongField;
import static de.robv.android.xposed.XposedHelpers.getObjectField;
import static de.robv.android.xposed.XposedHelpers.getSurroundingThis;
import static de.robv.android.xposed.XposedHelpers.setAdditionalInstanceField;

public class ModFixLocationIcon implements IXposedHookLoadPackage {
    // AppOps code for high power location
    public static final int OP_MONITOR_HIGH_POWER_LOCATION = 42;

    // Used to send intent about new or expired requests for high power location
    public static final String HIGH_POWER_REQUEST_CHANGE_ACTION = "android.location.HIGH_POWER_REQUEST_CHANGE";
    public static final String EXTRA_HIGH_POWER_MONITORING_ON = "high_power_monitoring_on";
    public static final String EXTRA_PACKAGE = "package";

    private static boolean debug = false;

    public static void log(String s) {
        XposedBridge.log(s);
    }
    public static void logd(String s) {
        if (debug)
            XposedBridge.log("[d] "+ s);
    }

    public static void log(Throwable throwable) {
        XposedBridge.log(throwable);
    }

    public static void sendBroadcast(Context context, Intent intent) {
        context.sendBroadcastAsUser(intent,
                (UserHandle) XposedHelpers.getStaticObjectField(UserHandle.class, "ALL"));
    }

    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (lpparam.packageName.equals("com.android.systemui")) {
            try {
                final Class<?> controllerClass = XposedHelpers.findClass("com.android.systemui.statusbar.policy.LocationControllerImpl", lpparam.classLoader);
                findAndHookMethod(controllerClass, "onReceive", Context.class, Intent.class, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        Intent intent = (Intent) param.args[1];
                        logd("LocationController onReceive: " + intent.getAction());
                        if (!intent.getAction().equals(HIGH_POWER_REQUEST_CHANGE_ACTION))
                            return;

                        if (intent.hasExtra(EXTRA_PACKAGE) && intent.hasExtra(EXTRA_HIGH_POWER_MONITORING_ON)) {
                            HashMap<String, Integer> highPowerAppsCache = (HashMap<String, Integer>)
                                    getAdditionalInstanceField(param.thisObject, "highPowerAppsCache");
                            if (highPowerAppsCache == null) {
                                highPowerAppsCache = new HashMap<>();
                                setAdditionalInstanceField(param.thisObject, "highPowerAppsCache", highPowerAppsCache);
                            }
                            String pac = intent.getStringExtra(EXTRA_PACKAGE);
                            if (intent.getBooleanExtra(EXTRA_HIGH_POWER_MONITORING_ON, false)) {
                                if (highPowerAppsCache.containsKey(pac))
                                    highPowerAppsCache.put(pac, highPowerAppsCache.get(pac) + 1);
                                else
                                    highPowerAppsCache.put(pac, 1);
                            } else if (highPowerAppsCache.containsKey(pac)) { // remove package; ignore if package doens't exist
                                if (highPowerAppsCache.get(pac) == 1)
                                    highPowerAppsCache.remove(pac);
                                else
                                    highPowerAppsCache.put(pac, highPowerAppsCache.get(pac) - 1);
                            }
                            log("Changing: " + pac + "; current high power location packages: " + TextUtils.join(", ", highPowerAppsCache.keySet()));
                        } else {
                            // location icon change is triggered by intent sent from
                            // http://androidxref.com/5.0.0_r2/xref/frameworks/base/services/core/java/com/android/server/LocationManagerService.java#708
                            logd("Blocking original intent for high power location change");
                            param.setResult(null);
                            if (debug) {
                                try {
                                    // the original way to determine if there are requests for high power location
                                    List<?> packages = (List<?>) callMethod(getObjectField(param.thisObject, "mAppOpsManager"), "getPackagesForOps",
                                            getObjectField(param.thisObject, "mHighPowerRequestAppOpArray"));
                                    if (packages == null) return;
                                    for (Object p : packages) {
                                        //List<AppOpsManager.OpEntry> opEntries = packageOp.getOps();
                                        List<?> ops = (List<?>) callMethod(p, "getOps");
                                        for (Object op : ops) {
                                            if ((boolean) callMethod(op, "isRunning"))
                                                logd("Location isRunning starting=" + getLongField(op, "mTime") + " duration=" + getIntField(op, "mDuration") + " " + callMethod(p, "getPackageName"));
                                        }
                                    }
                                } catch (Throwable t) {
                                    XposedBridge.log(t);
                                }
                            }
                        }
                    }
                });
                findAndHookMethod(controllerClass, "areActiveHighPowerLocationRequests", new XC_MethodReplacement() {
                    @Override
                    protected Object replaceHookedMethod(MethodHookParam methodHookParam) throws Throwable {
                        try {
                            HashMap<String, Integer> highPowerAppsCache = (HashMap<String, Integer>)
                                    getAdditionalInstanceField(methodHookParam.thisObject, "highPowerAppsCache");
                            if (highPowerAppsCache == null)
                                return false;
                            logd("Current high power location packages: " + TextUtils.join(", ", highPowerAppsCache.keySet()));
                            return (highPowerAppsCache.keySet().size() > 0);
                        } catch (Throwable t) {
                            XposedBridge.log(t);
                            XposedBridge.log("areActiveHighPowerLocationRequests failed, defaulting to original one");
                            return methodHookParam.getResult();
                        }
                    }
                });
                logd("Hooked LocationController");
            } catch (Throwable t) {
                XposedBridge.log(t);
            }

        } else if (lpparam.packageName.equals("android") && lpparam.processName.equals("android")) {
            try {
                final Class<?> lmsReceiver = findClass("com.android.server.LocationManagerService$Receiver", lpparam.classLoader);

                findAndHookMethod(lmsReceiver, "updateMonitoring", boolean.class, boolean.class, int.class, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        //private boolean updateMonitoring(boolean allowMonitoring, boolean currentlyMonitoring,int op)
                        if ((int) param.args[2] == OP_MONITOR_HIGH_POWER_LOCATION)
                            logd("updateMonitoring(allowed=" + param.args[0] + ",currentlyMon=" + param.args[1] + ") = " + param.getResult() + " " + " " + getObjectField(param.thisObject, "mPackageName"));
                    }
                });
                findAndHookMethod(lmsReceiver, "updateMonitoring", boolean.class, new XC_MethodHook() {
                    boolean highPowerMon;

                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        highPowerMon = getBooleanField(param.thisObject, "mOpHighPowerMonitoring");
                    }

                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        if (highPowerMon != getBooleanField(param.thisObject, "mOpHighPowerMonitoring")) {
                            logd("Sending high power change intent for package " + getObjectField(param.thisObject, "mPackageName"));
                            Intent intent = new Intent(HIGH_POWER_REQUEST_CHANGE_ACTION);
                            intent.putExtra(EXTRA_PACKAGE, (String) getObjectField(param.thisObject, "mPackageName"));
                            intent.putExtra(EXTRA_HIGH_POWER_MONITORING_ON, !highPowerMon);
                            sendBroadcast((Context) getObjectField(getSurroundingThis(param.thisObject), "mContext"), intent);
                        }
                        logd("updateMonitoring(" + param.args[0] + ")" + " " + getObjectField(param.thisObject, "mPackageName"));
                    }
                });
                logd("Hooked LocationManagerService");
            } catch (Throwable t) {
                XposedBridge.log(t);
            }
        }
        if (debug)
            ModDebugAppOps.handleLoadPackage(lpparam);

    }
}