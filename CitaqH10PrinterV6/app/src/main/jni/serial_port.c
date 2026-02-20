#include <jni.h>
#include <termios.h>
#include <unistd.h>
#include <fcntl.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <android/log.h>

#define LOG_TAG "SerialPortJNI"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

static speed_t getBaud(jint baudrate) {
    switch (baudrate) {
        case 9600: return B9600;
        case 19200: return B19200;
        case 38400: return B38400;
        case 57600: return B57600;
        case 115200: return B115200;
        case 230400: return B230400;
        default: return B115200;
    }
}

JNIEXPORT jobject JNICALL
Java_com_example_citaqh10printer_SerialPort_open(JNIEnv* env, jclass clazz, jstring path, jint baudrate, jint flags) {
    const char* cpath = (*env)->GetStringUTFChars(env, path, NULL);
    int fd = open(cpath, O_RDWR | O_NOCTTY | O_SYNC);
    if (fd == -1) {
        LOGE("Cannot open %s", cpath);
        (*env)->ReleaseStringUTFChars(env, path, cpath);
        return NULL;
    }

    struct termios cfg;
    if (tcgetattr(fd, &cfg)) {
        LOGE("tcgetattr() failed");
        close(fd);
        (*env)->ReleaseStringUTFChars(env, path, cpath);
        return NULL;
    }

    cfmakeraw(&cfg);
    cfsetispeed(&cfg, getBaud(baudrate));
    cfsetospeed(&cfg, getBaud(baudrate));

    cfg.c_cflag |= (CLOCAL | CREAD);
    cfg.c_cflag &= ~CSIZE;
    cfg.c_cflag |= CS8;
    cfg.c_cflag &= ~PARENB;
    cfg.c_cflag &= ~CSTOPB;

    cfg.c_cc[VMIN] = 1;
    cfg.c_cc[VTIME] = 0;

    if (tcsetattr(fd, TCSANOW, &cfg)) {
        LOGE("tcsetattr() failed");
        close(fd);
        (*env)->ReleaseStringUTFChars(env, path, cpath);
        return NULL;
    }

    (*env)->ReleaseStringUTFChars(env, path, cpath);

    jclass cFileDescriptor = (*env)->FindClass(env, "java/io/FileDescriptor");
    jmethodID iCtor = (*env)->GetMethodID(env, cFileDescriptor, "<init>", "()V");
    jfieldID descriptorID = (*env)->GetFieldID(env, cFileDescriptor, "descriptor", "I");
    jobject fileDescriptor = (*env)->NewObject(env, cFileDescriptor, iCtor);
    (*env)->SetIntField(env, fileDescriptor, descriptorID, (jint)fd);

    return fileDescriptor;
}

JNIEXPORT void JNICALL
Java_com_example_citaqh10printer_SerialPort_closeNative(JNIEnv* env, jobject thiz) {
    // Streams close the fd; nothing to do.
}
