#
# Mbed TLS 3.6.7 (LTS), from https://github.com/Mbed-TLS/mbedtls/releases/tag/mbedtls-3.6.7
# Only the library and include folders of the release archive are kept, with the default
# configuration (include/mbedtls/mbedtls_config.h).
#

LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_MODULE            := mbedtls
LOCAL_SRC_FILES         := $(patsubst $(LOCAL_PATH)/%,%,$(wildcard $(LOCAL_PATH)/library/*.c))
LOCAL_C_INCLUDES        := $(LOCAL_PATH)/include $(LOCAL_PATH)/library
LOCAL_EXPORT_C_INCLUDES := $(LOCAL_PATH)/include
LOCAL_CFLAGS            := -std=c99 -O2 -W -Wall -pipe

include $(BUILD_STATIC_LIBRARY)
