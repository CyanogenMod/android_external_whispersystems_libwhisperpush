package org.whispersystems.whisperpush.util;


import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.util.Pair;

import com.google.android.gms.gcm.GoogleCloudMessaging;

import java.io.IOException;

/**
 * A helper that manages synchronous retrieval of GCM registration IDs.
 */
public class GcmHelper {

  private static final String GCM_SENDER_ID = "312334754206";

  public static String getRegistrationId(Context context) throws IOException {
    String registrationId = getCurrentRegistrationId(context);

    if (registrationId == null) {
      registrationId = GoogleCloudMessaging.getInstance(context).register(GCM_SENDER_ID);
      setCurrentRegistrationId(context, registrationId);
    }

    return registrationId;
  }

  private static void setCurrentRegistrationId(Context context, String registrationId) {
    int currentVersion = getCurrentAppVersion(context);
    WhisperPreferences.setGcmRegistrationId(context, registrationId, currentVersion);
  }

  private static String getCurrentRegistrationId(Context context) {
    int                   currentVersion          = getCurrentAppVersion(context);
    Pair<String, Integer> currentRegistrationInfo = WhisperPreferences.getGcmRegistrationId(context);

    if (currentVersion != currentRegistrationInfo.second) {
      return null;
    }

    return currentRegistrationInfo.first;
  }

  private static int getCurrentAppVersion(Context context) {
    try {
      PackageManager manager     = context.getPackageManager();
      PackageInfo    packageInfo = manager.getPackageInfo(context.getPackageName(), 0);

      return packageInfo.versionCode;
    } catch (PackageManager.NameNotFoundException e) {
      throw new AssertionError(e);
    }
  }

}
