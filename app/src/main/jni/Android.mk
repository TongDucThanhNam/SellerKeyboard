LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)
LOCAL_MODULE := sellerprivacy
LOCAL_SRC_FILES := translation_log_guard.c
LOCAL_LDLIBS := -ldl
include $(BUILD_SHARED_LIBRARY)
