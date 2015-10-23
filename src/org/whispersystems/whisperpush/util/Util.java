package org.whispersystems.whisperpush.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Collection;
import java.util.List;

import org.whispersystems.libaxolotl.IdentityKey;
import org.whispersystems.libaxolotl.InvalidKeyException;
import org.whispersystems.textsecure.internal.util.Base64;
import org.whispersystems.whisperpush.exception.IllegalUriException;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Looper;
import android.util.Log;
import android.widget.EditText;

import com.google.android.mms.pdu.CharacterSets;

public class Util {

    private static final String TAG = "Util";

    private static final boolean ASSERT_MAIN_THREAD = /*BuildConfig.DEBUG*/true;

    private static class MainThreadReferenceHolder { // Lazy Holder idiom class
        static final Thread MAIN_THREAD = getMainThread();

        private static Thread getMainThread() {
            try {
                return Looper.getMainLooper().getThread();
            }
            catch (RuntimeException ex) {
                if (ex.getMessage() != null && ex.getMessage().contains("not mocked")) {
                    return null;
                }
                throw ex;
            }
        }
    }

    public static final boolean isRunningOnMainThread() {
        return Thread.currentThread() == MainThreadReferenceHolder.MAIN_THREAD;
    }

    public static final void preventRunningOnMainThread() {
        if (ASSERT_MAIN_THREAD && Thread.currentThread() == MainThreadReferenceHolder.MAIN_THREAD) {
            throw new RuntimeException("Possibly long operation has been running in the main thread");
        }
    }

    public static final void ensureRunningOnMainThread() {
        if (ASSERT_MAIN_THREAD && Thread.currentThread() != MainThreadReferenceHolder.MAIN_THREAD) {
            throw new RuntimeException("Should be running in the main thread");
        }
    }

    public static boolean isEmpty(String value) {
        return value == null || value.trim().length() == 0;
    }

    public static boolean isEmpty(EditText value) {
        return value == null || value.getText() == null
                || isEmpty(value.getText().toString());
    }

    public static boolean isEmpty(Collection<?> collection) {
        return collection == null || collection.size() == 0;
    }

    public static int getSize(Collection<?> collection) {
        return collection != null ? collection.size() : 0;
    }

    public static boolean isNotEmpty(Collection<?> collection) {
        return collection != null && collection.size() > 0;
    }

    public static void showAlertDialog(Context context, String title, String message) {
        AlertDialog.Builder dialog = new AlertDialog.Builder(context);
        dialog.setTitle(title);
        dialog.setMessage(message);
        dialog.setIcon(android.R.drawable.ic_dialog_alert);
        dialog.setPositiveButton(android.R.string.ok, null);
        dialog.show();
    }

    @SuppressLint("TrulyRandom") // we are not running on old Androids
    public static SecureRandom getSecureRandom() {
        try {
            return SecureRandom.getInstance("SHA1PRNG");
        } catch (NoSuchAlgorithmException e) {
            throw new AssertionError(e);
        }
    }

    public static String getSecret(int size) {
        return Base64.encodeBytes(
            org.whispersystems.textsecure.internal.util.Util.getSecretBytes(size));
    }

    public static IdentityKey deserializeIdentityKey(Intent intent) {
        byte[] key = intent.getByteArrayExtra("identity_key");
        if(key == null) { return null; } // no key passed
        try {
            return new IdentityKey(key, 0);
        } catch (InvalidKeyException e) {
            Log.e("WPUtil", "intent passed bad identity key");
            return null;
        }
    }

    public static String toIsoString(byte[] bytes) {
        try {
            return new String(bytes, CharacterSets.MIMENAME_ISO_8859_1);
        } catch (UnsupportedEncodingException e) {
            throw new AssertionError("ISO_8859_1 must be supported!");
        }
    }

    public static byte[] toIsoBytes(String isoString) {
        try {
            return isoString.getBytes(CharacterSets.MIMENAME_ISO_8859_1);
        } catch (UnsupportedEncodingException e) {
            throw new AssertionError("ISO_8859_1 must be supported!");
        }
    }

    public static byte[] readBytes(InputStream inputStream) throws IOException {
        ByteArrayOutputStream byteBuffer = new ByteArrayOutputStream();

        try {
            byte[] buffer = new byte[4096];
            int len;
            while ((len = inputStream.read(buffer)) != -1) {
                byteBuffer.write(buffer, 0, len);
            }
            byteBuffer.flush();

            return byteBuffer.toByteArray();
        } finally {
            byteBuffer.close();
        }

    }

    public static long extractMessageId(Uri uri) throws IllegalUriException {
        if (uri == null) {
            throw  new IllegalUriException("uri == null");
        }
        String authority = uri.getAuthority();
        if ("sms".equals(authority) || "mms".equals(authority) || "mms-sms".equals(authority)) {
            String lastPathSegment = uri.getLastPathSegment();
            try {
                return Long.parseLong(lastPathSegment);
            } catch (NumberFormatException ex) {
                throw  new IllegalUriException(ex);
            }
        } else {
            throw new IllegalUriException("Unsupported authority");
        }
    }

}