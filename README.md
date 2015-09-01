#[Xposed] Sticky Location Icon Fix
Xposed module to fix the sticky location icon even when there is no app requesting location.

The location notification icon in the status bar is designed only to show when an app is accessing the GPS (a.k.a. high powered location); however, this is broken on some devices where the icon is shown all the time, whether or not there is actually an app requesting high powered location. 

Some technical details of the fix:

The icon change is trigger by an intent from the `LocationManagerService`, but whether the icon is visible or not is determined by whether any app is currently using the high powered location permission in the AppOps package. I spent a long time tracking down if LocationManagerService is sloppy in forgeting to tell AppOps that a location request is no longer active, but the problem seems to stem from other packages requesting the location permission directly from AppOps without shutting it down properly. So, in essense, whenever the status bar is checking for high powered location requests, there are always active requests in the AppOps package which did not come from `LocationManagerService`. 

The fix, without figuring out where those AppOps requests come from (they seem to come from close source Google services), is to keep track of the intent sent from `LocationManagerService` without invoking AppOps; to do that, I modified the intent sending code to send also package name and the on/off status. The side effect is that the  `android.location.HIGH_POWER_REQUEST_CHANGE` broadcast is now sent twice, once without and once with the extra information. This side effect can easily be fixed by changing the intent action, but no other packages seem to use it (and it is a system level broadcast, so no user app should be using it). 
