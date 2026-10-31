#include <jni.h>
#include <linux/uinput.h>
#include <fcntl.h>
#include <unistd.h>
#include <string.h>
#include <errno.h>
#include <pthread.h>
#include <android/log.h>

#define LOG_TAG "GamepadUinput"
#define ALOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define ALOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

static int g_fd = -1;
static pthread_mutex_t g_lock = PTHREAD_MUTEX_INITIALIZER;
static volatile jint g_lastError = 0;

static int setup_device(int fd, const char *name) {
    if (ioctl(fd, UI_SET_EVBIT, EV_KEY) < 0) return -errno;
    if (ioctl(fd, UI_SET_EVBIT, EV_ABS) < 0) return -errno;
    if (ioctl(fd, UI_SET_EVBIT, EV_SYN) < 0) return -errno;

    static const int keys[] = {
        BTN_SOUTH, BTN_EAST, BTN_NORTH, BTN_WEST,
        BTN_TL, BTN_TR, BTN_TL2, BTN_TR2,
        BTN_SELECT, BTN_START,
        BTN_THUMBL, BTN_THUMBR,
        KEY_UP, KEY_DOWN, KEY_LEFT, KEY_RIGHT
    };
    for (size_t i = 0; i < sizeof(keys) / sizeof(keys[0]); i++) {
        if (ioctl(fd, UI_SET_KEYBIT, keys[i]) < 0) {
            ALOGE("UI_SET_KEYBIT %d failed errno=%d", keys[i], errno);
            return -errno;
        }
    }

    struct axis_range {
        int axis;
        int min;
        int max;
    };
    static const struct axis_range abs_axes[] = {
        {ABS_X, -32767, 32767},
        {ABS_Y, -32767, 32767},
        {ABS_RX, -32767, 32767},
        {ABS_RY, -32767, 32767},
        {ABS_Z, 0, 255},
        {ABS_RZ, 0, 255},
        {ABS_HAT0X, -1, 1},
        {ABS_HAT0Y, -1, 1},
    };
    for (size_t i = 0; i < sizeof(abs_axes) / sizeof(abs_axes[0]); i++) {
        if (ioctl(fd, UI_SET_ABSBIT, abs_axes[i].axis) < 0) {
            ALOGE("UI_SET_ABSBIT %d failed errno=%d", abs_axes[i].axis, errno);
            return -errno;
        }
    }

    struct uinput_user_dev dev;
    memset(&dev, 0, sizeof(dev));
    strncpy(dev.name, name, UINPUT_MAX_NAME_SIZE - 1);
    dev.id.bustype = BUS_USB;
    dev.id.vendor = 0x045e;
    dev.id.product = 0x028e;
    dev.id.version = 1;
    for (size_t i = 0; i < sizeof(abs_axes) / sizeof(abs_axes[0]); i++) {
        int axis = abs_axes[i].axis;
        dev.absmin[axis] = abs_axes[i].min;
        dev.absmax[axis] = abs_axes[i].max;
        dev.absfuzz[axis] = 0;
        dev.absflat[axis] = 1;
    }

    if (write(fd, &dev, sizeof(dev)) < 0) {
        ALOGE("write uinput_user_dev failed errno=%d", errno);
        return -errno;
    }
    if (ioctl(fd, UI_DEV_CREATE) < 0) {
        ALOGE("UI_DEV_CREATE failed errno=%d", errno);
        return -errno;
    }
    return 0;
}

static void emit(int fd, int type, int code, int value) {
    struct input_event ev;
    memset(&ev, 0, sizeof(ev));
    ev.type = type;
    ev.code = code;
    ev.value = value;
    if (write(fd, &ev, sizeof(ev)) != (ssize_t) sizeof(ev)) {
        g_lastError = errno;
        ALOGE("write input_event failed type=%d code=%d value=%d errno=%d", type, code, value, errno);
    }
}

static void emit_syn(int fd) {
    emit(fd, EV_SYN, SYN_REPORT, 0);
}

extern "C" {
JNIEXPORT jint JNICALL
Java_io_takamiya_mio_gamepad_GamepadDaemon_nativeOpen(JNIEnv *env, jobject thiz, jstring jname, jboolean isXbox) {
    pthread_mutex_lock(&g_lock);

    if (g_fd >= 0) {
        ioctl(g_fd, UI_DEV_DESTROY);
        close(g_fd);
        g_fd = -1;
    }

    char buf[UINPUT_MAX_NAME_SIZE];
    strncpy(buf, "Virtual Xbox Gamepad", UINPUT_MAX_NAME_SIZE - 1);
    buf[UINPUT_MAX_NAME_SIZE - 1] = 0;
    if (jname != NULL) {
        const char *name = env->GetStringUTFChars(jname, NULL);
        if (name != NULL) {
            strncpy(buf, name, UINPUT_MAX_NAME_SIZE - 1);
            buf[UINPUT_MAX_NAME_SIZE - 1] = 0;
            env->ReleaseStringUTFChars(jname, name);
        }
    }

    int fd = open("/dev/uinput", O_WRONLY | O_NONBLOCK);
    if (fd < 0) {
        g_lastError = errno;
        g_fd = -1;
        ALOGE("open /dev/uinput failed errno=%d", errno);
        pthread_mutex_unlock(&g_lock);
        return -errno;
    }

    int rc = setup_device(fd, buf);
    if (rc != 0) {
        close(fd);
        g_fd = -1;
        g_lastError = rc > 0 ? rc : -rc;
        pthread_mutex_unlock(&g_lock);
        return rc;
    }

    g_fd = fd;
    g_lastError = 0;
    ALOGI("virtual gamepad opened: %s", buf);
    pthread_mutex_unlock(&g_lock);
    (void) isXbox;

    usleep(1000000);
    return 0;
}

JNIEXPORT void JNICALL
Java_io_takamiya_mio_gamepad_GamepadDaemon_nativeSendKey(JNIEnv *env, jobject thiz, jint code, jint value) {
    pthread_mutex_lock(&g_lock);
    if (g_fd >= 0) {
        emit(g_fd, EV_KEY, code, value);
        emit_syn(g_fd);
    }
    pthread_mutex_unlock(&g_lock);
}

JNIEXPORT void JNICALL
Java_io_takamiya_mio_gamepad_GamepadDaemon_nativeSendAxis(JNIEnv *env, jobject thiz, jint axis, jint value) {
    pthread_mutex_lock(&g_lock);
    if (g_fd >= 0) {
        emit(g_fd, EV_ABS, axis, value);
        emit_syn(g_fd);
    }
    pthread_mutex_unlock(&g_lock);
}

JNIEXPORT void JNICALL
Java_io_takamiya_mio_gamepad_GamepadDaemon_nativeSendStick(JNIEnv *env, jobject thiz, jint lx, jint ly, jint rx, jint ry) {
    pthread_mutex_lock(&g_lock);
    if (g_fd >= 0) {
        emit(g_fd, EV_ABS, ABS_X, lx);
        emit(g_fd, EV_ABS, ABS_Y, ly);
        emit(g_fd, EV_ABS, ABS_RX, rx);
        emit(g_fd, EV_ABS, ABS_RY, ry);
        emit_syn(g_fd);
    }
    pthread_mutex_unlock(&g_lock);
}

JNIEXPORT void JNICALL
Java_io_takamiya_mio_gamepad_GamepadDaemon_nativeSendTrigger(JNIEnv *env, jobject thiz, jint lt, jint rt) {
    pthread_mutex_lock(&g_lock);
    if (g_fd >= 0) {
        emit(g_fd, EV_ABS, ABS_Z, lt);
        emit(g_fd, EV_ABS, ABS_RZ, rt);
        emit_syn(g_fd);
    }
    pthread_mutex_unlock(&g_lock);
}

JNIEXPORT void JNICALL
Java_io_takamiya_mio_gamepad_GamepadDaemon_nativeSendDpad(JNIEnv *env, jobject thiz, jint x, jint y) {
    pthread_mutex_lock(&g_lock);
    if (g_fd >= 0) {
        emit(g_fd, EV_ABS, ABS_HAT0X, x);
        emit(g_fd, EV_ABS, ABS_HAT0Y, y);
        emit_syn(g_fd);
    }
    pthread_mutex_unlock(&g_lock);
}

JNIEXPORT jboolean JNICALL
Java_io_takamiya_mio_gamepad_GamepadDaemon_nativeIsAvailable(JNIEnv *env, jobject thiz) {
    return g_fd >= 0 ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_io_takamiya_mio_gamepad_GamepadDaemon_nativeDestroy(JNIEnv *env, jobject thiz) {
    pthread_mutex_lock(&g_lock);
    if (g_fd >= 0) {
        ioctl(g_fd, UI_DEV_DESTROY);
        close(g_fd);
        g_fd = -1;
        ALOGI("virtual gamepad destroyed");
    }
    pthread_mutex_unlock(&g_lock);
}

JNIEXPORT jint JNICALL
Java_io_takamiya_mio_gamepad_GamepadDaemon_nativeGetLastError(JNIEnv *env, jobject thiz) {
    return g_lastError;
}
} // extern "C"