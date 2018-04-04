LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

OPENCV_ROOT:=C:/opencv-3.3.1-android-sdk/OpenCV

LOCAL_MODULE    := mycameraapp

include $(BUILD_SHARED_LIBRARY)