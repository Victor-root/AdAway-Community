APP_ABI := all
APP_STL := c++_shared
APP_LDFLAGS += -Wl,-z,max-page-size=16384 -Wl,-z,common-page-size=16384
