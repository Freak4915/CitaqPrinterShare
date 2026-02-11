#include <jni.h>
#include <string>
#include <fcntl.h>
#include <unistd.h>
#include <termios.h>
#include <errno.h>
#include <android/log.h>

#define LOG_TAG "serial_port"
#define ALOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

extern "C" {

static speed_t getBaud(int baud) {
    switch (baud) {
        case 115200: return B115200;
        case 57600: return B57600;
        case 38400: return B38400;
        case 19200: return B19200;
        case 9600: return B9600;
        default: return B115200;
    }
}

JNIEXPORT jint JNICALL
Java_com_example_citaqprintershare_SerialPort_open(JNIEnv *env, jobject thiz, jstring path_, jint baud) {
    const char *path = env->GetStringUTFChars(path_, 0);
    int fd = open(path, O_RDWR | O_NOCTTY | O_NONBLOCK);
    env->ReleaseStringUTFChars(path_, path);

    if (fd < 0) {
        ALOGE("Cannot open %s, errno=%d", path, errno);
        return -1;
    }

    struct termios tty;
    if (tcgetattr(fd, &tty) != 0) {
        ALOGE("tcgetattr failed, errno=%d", errno);
        close(fd);
        return -2;
    }

    cfmakeraw(&tty);

    speed_t sp = getBaud(baud);
    cfsetispeed(&tty, sp);
    cfsetospeed(&tty, sp);

    tty.c_cflag &= ~CRTSCTS;
    tty.c_cflag |= CLOCAL | CREAD;
    tty.c_cflag &= ~CSTOPB;
    tty.c_cflag &= ~PARENB;
    tty.c_cflag &= ~CSIZE;
    tty.c_cflag |= CS8;

    tty.c_cc[VMIN] = 1;
    tty.c_cc[VTIME] = 0;

    if (tcsetattr(fd, TCSANOW, &tty) != 0) {
        ALOGE("tcsetattr failed, errno=%d", errno);
        close(fd);
        return -3;
    }

    // set blocking mode
    int flags = fcntl(fd, F_GETFL, 0);
    flags &= ~O_NONBLOCK;
    fcntl(fd, F_SETFL, flags);

    return fd;
}

JNIEXPORT void JNICALL
Java_com_example_citaqprintershare_SerialPort_close(JNIEnv *env, jobject thiz, jint fd) {
    if (fd >= 0) close(fd);
}

JNIEXPORT jint JNICALL
Java_com_example_citaqprintershare_SerialPort_write(JNIEnv *env, jobject thiz, jint fd, jbyteArray data, jint len) {
    if (fd < 0) return -1;
    jbyte *buf = env->GetByteArrayElements(data, NULL);
    ssize_t w = write(fd, buf, len);
    env->ReleaseByteArrayElements(data, buf, JNI_ABORT);
    if (w < 0) {
        ALOGE("write failed errno=%d", errno);
        return -1;
    }
    return (jint)w;
}

JNIEXPORT jint JNICALL
Java_com_example_citaqprintershare_SerialPort_read(JNIEnv *env, jobject thiz, jint fd, jbyteArray buffer, jint len) {
    if (fd < 0) return -1;
    jbyte *buf = env->GetByteArrayElements(buffer, NULL);
    ssize_t r = read(fd, buf, len);
    if (r > 0) env->ReleaseByteArrayElements(buffer, buf, 0);
    else env->ReleaseByteArrayElements(buffer, buf, JNI_ABORT);
    if (r < 0) {
        ALOGE("read failed errno=%d", errno);
        return -1;
    }
    return (jint)r;
}

}
