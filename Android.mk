LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_MODULE := libwhisperpush

LOCAL_JAVA_LIBRARIES := telephony-common

LOCAL_MODULE_TAGS := optional

LOCAL_SRC_FILES := $(call all-java-files-under, src)
LOCAL_SRC_FILES += $(call find-other-aidl-files, src)
LOCAL_RESOURCE_DIR := $(LOCAL_PATH)/res

LOCAL_PACKAGE_NAME := org.whispersystems.whisperpush

LOCAL_JACK_ENABLED := disabled

LOCAL_AAPT_INCLUDE_ALL_RESOURCES := true
LOCAL_AAPT_FLAGS := --extra-packages com.google.android.gms -S $(LOCAL_PATH)/../../google/google_play_services/libproject/google-play-services_lib/res --auto-add-overlay

LOCAL_STATIC_JAVA_LIBRARIES := play axolotl-android textsecure-android 

include $(BUILD_STATIC_JAVA_LIBRARY)

