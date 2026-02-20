LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)
LOCAL_MODULE := serial_port
LOCAL_SRC_FILES := serial_port.c
LOCAL_LDLIBS := -llog
include $(BUILD_SHARED_LIBRARY)
