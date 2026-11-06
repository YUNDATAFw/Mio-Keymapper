#include <jni.h>
#include <zlib.h>

#include <algorithm>
#include <cstdint>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <ctime>
#include <pthread.h>
#include <string>
#include <unistd.h>
#include <vector>

#include <android/log.h>
#include <atomic>

#define INTEGRITY_TAG "IntegrityLib"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, INTEGRITY_TAG, __VA_ARGS__)

// ═══════════════════════════════════════════════════════════════════════════
// 本地调试总开关
//   true  = 启用原有完整性门禁 / 反调试 / 监控强杀
//   false = 完全关闭：不校验 verify_marker、不强杀 Activity/Service、
//           不启动监控线程、不做反调试退出（仅保留 JNI 注册）
// ═══════════════════════════════════════════════════════════════════════════
static constexpr bool kIntegrityEnforcementEnabled = false;

// ═══════════════════════════════════════════════════════════════════════════
// 反调试 / 反 Frida / 安全擦除
// ═══════════════════════════════════════════════════════════════════════════

#include <sys/ptrace.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <arpa/inet.h>

// TracerPid 检测：被调试时 TracerPid != 0
static bool detectTracerPid() {
    FILE *fp = fopen("/proc/self/status", "r");
    if (!fp) return false;
    char line[256];
    bool detected = false;
    while (fgets(line, sizeof(line), fp)) {
        if (strncmp(line, "TracerPid:", 10) == 0) {
            int pid = atoi(line + 10);
            if (pid != 0) detected = true;
            break;
        }
    }
    fclose(fp);
    return detected;
}

// Frida 端口检测（默认 27042）
static bool detectFridaPort() {
    int sock = socket(AF_INET, SOCK_STREAM, 0);
    if (sock < 0) return false;
    struct sockaddr_in addr = {};
    addr.sin_family = AF_INET;
    addr.sin_port = htons(27042);
    addr.sin_addr.s_addr = inet_addr("127.0.0.1");
    struct timeval tv = {1, 0};
    setsockopt(sock, SOL_SOCKET, SO_SNDTIMEO, &tv, sizeof(tv));
    bool detected = (connect(sock, (struct sockaddr*)&addr, sizeof(addr)) == 0);
    close(sock);
    return detected;
}

// /proc/self/maps 中搜索 frida 特征
static bool detectFridaMaps() {
    FILE *fp = fopen("/proc/self/maps", "r");
    if (!fp) return false;
    char line[512];
    bool detected = false;
    while (fgets(line, sizeof(line), fp)) {
        if (strstr(line, "frida") || strstr(line, "gadget") ||
            strstr(line, "gmain") || strstr(line, "gdbus")) {
            detected = true;
            break;
        }
    }
    fclose(fp);
    return detected;
}

// ptrace TRACEME 反附加
static void antiPtrace() {
    if (ptrace(PTRACE_TRACEME, 0, 0, 0) == -1) {
        _exit(1);
    }
    ptrace(PTRACE_DETACH, 0, 0, 0);
}

// 安全擦除：删除 verify_marker 文件内容并擦除 registry 中的 token
static void secureEraseAll(JNIEnv *env, jobject context) {
    if (env == nullptr) return;

    // 1. 覆写 verify_marker 后删除
    if (context != nullptr) {
        jclass ctxClass = env->GetObjectClass(context);
        if (ctxClass != nullptr) {
            jmethodID getAppInfo = env->GetMethodID(ctxClass, "getApplicationInfo",
                                                    "()Landroid/content/pm/ApplicationInfo;");
            jobject appInfo = env->CallObjectMethod(context, getAppInfo);
            if (appInfo != nullptr && !env->ExceptionCheck()) {
                jclass aiClass = env->FindClass("android/content/pm/ApplicationInfo");
                jfieldID dataDirField = env->GetFieldID(aiClass, "dataDir", "Ljava/lang/String;");
                jstring dataDirJ = (jstring) env->GetObjectField(appInfo, dataDirField);
                env->DeleteLocalRef(aiClass);
                env->DeleteLocalRef(appInfo);
                if (dataDirJ != nullptr) {
                    const char *dataDir = env->GetStringUTFChars(dataDirJ, nullptr);
                    if (dataDir != nullptr) {
                        std::string markerPath = std::string(dataDir) + "/verify_marker";
                        env->ReleaseStringUTFChars(dataDirJ, dataDir);
                        FILE *fp = fopen(markerPath.c_str(), "wb");
                        if (fp) {
                            uint8_t garbage[76];
                            for (int i = 0; i < 76; i++) garbage[i] = (uint8_t)(rand() & 0xFF);
                            fwrite(garbage, 1, 76, fp);
                            fclose(fp);
                            remove(markerPath.c_str());
                        }
                    }
                    env->DeleteLocalRef(dataDirJ);
                }
            } else {
                env->ExceptionClear();
            }
            env->DeleteLocalRef(ctxClass);
        } else {
            env->ExceptionClear();
        }
    }

    // 2. 通过 JNI 调用 RegistryUtils.get().clear() 擦除 token/密码
    jclass regUtilsClass = env->FindClass("io/takamiya/mio/utils/RegistryUtils");
    if (regUtilsClass != nullptr) {
        jmethodID getMethod = env->GetStaticMethodID(regUtilsClass, "get",
                                                     "()Lcn/panda/utils/library/Registry/Registry;");
        if (getMethod != nullptr) {
            jobject reg = env->CallStaticObjectMethod(regUtilsClass, getMethod);
            if (reg != nullptr && !env->ExceptionCheck()) {
                jclass regClass = env->GetObjectClass(reg);
                jmethodID clearMethod = env->GetMethodID(regClass, "clear", "()V");
                if (clearMethod != nullptr) {
                    env->CallVoidMethod(reg, clearMethod);
                }
                jmethodID saveMethod = env->GetMethodID(regClass, "save", "()V");
                if (saveMethod != nullptr) {
                    env->CallVoidMethod(reg, saveMethod);
                }
                env->DeleteLocalRef(regClass);
                env->DeleteLocalRef(reg);
            } else {
                env->ExceptionClear();
            }
        } else {
            env->ExceptionClear();
        }
        env->DeleteLocalRef(regUtilsClass);
    } else {
        env->ExceptionClear();
    }

    env->ExceptionClear();
}

// 综合响应：安全擦除 + 延迟退出（静默，不输出任何 log）
static void runSecurityResponse(JNIEnv *env, jobject context) {
    static volatile bool responded = false;
    if (responded) return;
    responded = true;

    secureEraseAll(env, context);
    usleep(2000000);  // 延迟 2 秒，干扰时序分析
    _exit(1);
}

// 综合反调试入口（检测到攻击返回 true）
static bool detectAttack() {
    return detectTracerPid() || detectFridaPort() || detectFridaMaps();
}

// ═══════════════════════════════════════════════════════════════════════════
// SHA-256（纯 C++，无外部依赖）
// ═══════════════════════════════════════════════════════════════════════════

static const uint32_t SHA256_K[64] = {
        0x428a2f98, 0x71374491, 0xb5c0fbcf, 0xe9b5dba5, 0x3956c25b, 0x59f111f1, 0x923f82a4, 0xab1c5ed5,
        0xd807aa98, 0x12835b01, 0x243185be, 0x550c7dc3, 0x72be5d74, 0x80deb1fe, 0x9bdc06a7, 0xc19bf174,
        0xe49b69c1, 0xefbe4786, 0x0fc19dc6, 0x240ca1cc, 0x2de92c6f, 0x4a7484aa, 0x5cb0a9dc, 0x76f988da,
        0x983e5152, 0xa831c66d, 0xb00327c8, 0xbf597fc7, 0xc6e00bf3, 0xd5a79147, 0x06ca6351, 0x14292967,
        0x27b70a85, 0x2e1b2138, 0x4d2c6dfc, 0x53380d13, 0x650a7354, 0x766a0abb, 0x81c2c92e, 0x92722c85,
        0xa2bfe8a1, 0xa81a664b, 0xc24b8b70, 0xc76c51a3, 0xd192e819, 0xd6990624, 0xf40e3585, 0x106aa070,
        0x19a4c116, 0x1e376c08, 0x2748774c, 0x34b0bcb5, 0x391c0cb3, 0x4ed8aa4a, 0x5b9cca4f, 0x682e6ff3,
        0x748f82ee, 0x78a5636f, 0x84c87814, 0x8cc70208, 0x90befffa, 0xa4506ceb, 0xbef9a3f7, 0xc67178f2};

static inline uint32_t rotr32(uint32_t x, uint32_t n) {
    return (x >> n) | (x << (32 - n));
}

class Sha256 {
public:
    Sha256() { reset(); }

    void reset() {
        state[0] = 0x6a09e667; state[1] = 0xbb67ae85; state[2] = 0x3c6ef372; state[3] = 0xa54ff53a;
        state[4] = 0x510e527f; state[5] = 0x9b05688c; state[6] = 0x1f83d9ab; state[7] = 0x5be0cd19;
        totalLen = 0;
        bufLen = 0;
    }

    void update(const uint8_t *data, size_t len) {
        totalLen += len;
        while (len > 0) {
            size_t take = std::min(len, sizeof(buf) - bufLen);
            memcpy(buf + bufLen, data, take);
            bufLen += take;
            data += take;
            len -= take;
            if (bufLen == sizeof(buf)) {
                transform(buf);
                bufLen = 0;
            }
        }
    }

    void final(uint8_t out[32]) {
        uint64_t bitLen = totalLen * 8;
        uint8_t pad[128];
        size_t padLen = 0;
        pad[padLen++] = 0x80;
        size_t rem = (bufLen + 1) % 64;
        size_t zeros = (rem <= 56) ? (56 - rem) : (120 - rem);
        memset(pad + padLen, 0, zeros);
        padLen += zeros;
        for (int i = 0; i < 8; ++i) {
            pad[padLen + i] = static_cast<uint8_t>((bitLen >> (56 - i * 8)) & 0xFF);
        }
        padLen += 8;
        update(pad, padLen);

        for (int i = 0; i < 8; ++i) {
            out[i * 4 + 0] = static_cast<uint8_t>((state[i] >> 24) & 0xFF);
            out[i * 4 + 1] = static_cast<uint8_t>((state[i] >> 16) & 0xFF);
            out[i * 4 + 2] = static_cast<uint8_t>((state[i] >> 8) & 0xFF);
            out[i * 4 + 3] = static_cast<uint8_t>(state[i] & 0xFF);
        }
        reset();
    }

private:
    uint32_t state[8];
    uint64_t totalLen;
    size_t bufLen;
    uint8_t buf[64];

    void transform(const uint8_t *block) {
        uint32_t w[64];
        for (int i = 0; i < 16; ++i) {
            w[i] = ((uint32_t) block[i * 4] << 24) | ((uint32_t) block[i * 4 + 1] << 16)
                   | ((uint32_t) block[i * 4 + 2] << 8) | (uint32_t) block[i * 4 + 3];
        }
        for (int i = 16; i < 64; ++i) {
            uint32_t s0 = rotr32(w[i - 15], 7) ^ rotr32(w[i - 15], 18) ^ (w[i - 15] >> 3);
            uint32_t s1 = rotr32(w[i - 2], 17) ^ rotr32(w[i - 2], 19) ^ (w[i - 2] >> 10);
            w[i] = w[i - 16] + s0 + w[i - 7] + s1;
        }
        uint32_t a = state[0], b = state[1], c = state[2], d = state[3];
        uint32_t e = state[4], f = state[5], g = state[6], h = state[7];
        for (int i = 0; i < 64; ++i) {
            uint32_t s1 = rotr32(e, 6) ^ rotr32(e, 11) ^ rotr32(e, 25);
            uint32_t ch = (e & f) ^ (~e & g);
            uint32_t t1 = h + s1 + ch + SHA256_K[i] + w[i];
            uint32_t s0 = rotr32(a, 2) ^ rotr32(a, 13) ^ rotr32(a, 22);
            uint32_t maj = (a & b) ^ (a & c) ^ (b & c);
            uint32_t t2 = s0 + maj;
            h = g; g = f; f = e; e = d + t1; d = c; c = b; b = a; a = t1 + t2;
        }
        state[0] += a; state[1] += b; state[2] += c; state[3] += d;
        state[4] += e; state[5] += f; state[6] += g; state[7] += h;
    }
};

// ═══════════════════════════════════════════════════════════════════════════
// HMAC-SHA256（固定密钥 + 哈希参与 → 单向打乱，确定性）
// ═══════════════════════════════════════════════════════════════════════════

// 固定密钥 XOR 混淆存储（运行时还原），实际密钥：M1o!In4gr!K3y#24
static const uint8_t INTEGRITY_KEY_BYTES[16] = {0xAF, 0xDE, 0x2B, 0x39, 0x27, 0x5D, 0x03, 0x09, 0xB4, 0xD1, 0xEA, 0x8E, 0x16, 0xE9, 0xBB, 0x18};
static const uint8_t INTEGRITY_KEY_MASK[16]  = {0xE2, 0xEF, 0x44, 0x18, 0x6E, 0x33, 0x37, 0x6E, 0xC6, 0xF0, 0xA1, 0xBD, 0x6F, 0xCA, 0x89, 0x2C};
static const size_t INTEGRITY_KEY_LEN = 16;

static bool integrityKeyReady = false;
static uint8_t integrityKey[16];

static const uint8_t *getIntegrityKey() {
    if (!integrityKeyReady) {
        for (size_t i = 0; i < INTEGRITY_KEY_LEN; ++i) {
            integrityKey[i] = INTEGRITY_KEY_BYTES[i] ^ INTEGRITY_KEY_MASK[i];
        }
        integrityKeyReady = true;
    }
    return integrityKey;
}

// ═══════════════════════════════════════════════════════════════════════════
// 验证标记（native gate）：只有 nativeVerifyServer 成功路径会写入
// ═══════════════════════════════════════════════════════════════════════════

static bool jsonParseInt(const std::string &body, const char *key, long &out);
static void hmacSha256(const uint8_t *key, size_t keyLen,
                       const uint8_t *msg, size_t msgLen,
                       uint8_t out[32]);
static void computeDexFingerprint(const std::vector<std::vector<uint8_t>> &dexShas,
                                  uint32_t versionCode, uint8_t out[32]);

// 全局 JavaVM:forward-declare,JNI_OnLoad 处赋值,monitorProc 使用
static JavaVM *g_jvm;

// 进程内快照：nativeVerifyServer ok 分支设置，nativeCheckVerified 优先读取
static bool g_verified = false;
static long g_exp = 0;

// 全量校验结果的时间窗口缓存：窗口内跳过重复的 verify_marker 读取 + APK/dex 全量哈希
// 仅在全量校验成功时刷新；失败会使缓存失效（lastOk 置 0 前仍会按本次结果返回）
static const long kVerifyCacheWindowSecs = 60;   // 全量重哈希节流窗口（秒）
static std::atomic<long> g_lastFullOkAt{0};       // 最近一次全量校验通过的 unix 时间，0=未通过

static const uint8_t MARKER_MAGIC[4] = {'M', 'V', 'F', '2'};
static const char *MARKER_FILENAME = "verify_marker";

// base64url 解码（JWT payload 段，- _ 变体，容忍缺失 padding）
static bool base64UrlDecode(const std::string &in, std::vector<uint8_t> &out) {
    out.clear();
    if (in.empty()) return false;
    uint32_t acc = 0;
    int nbits = 0;
    for (size_t i = 0; i < in.size(); ++i) {
        char c = in[i];
        if (c == '=') break;
        int v;
        if (c >= 'A' && c <= 'Z') v = c - 'A';
        else if (c >= 'a' && c <= 'z') v = c - 'a' + 26;
        else if (c >= '0' && c <= '9') v = c - '0' + 52;
        else if (c == '-' || c == '+') v = 62;
        else if (c == '_' || c == '/') v = 63;
        else continue;
        acc = (acc << 6) | (uint32_t) v;
        nbits += 6;
        if (nbits >= 8) {
            nbits -= 8;
            out.push_back((uint8_t) ((acc >> nbits) & 0xFF));
        }
    }
    return !out.empty();
}

// 容错 base64 解码（标准与 URL-safe 双字母表，'=' 视为结尾），输出原始字节
static bool base64DecodeTolerant(const std::string &in, std::string &out) {
    out.clear();
    if (in.empty()) return false;
    uint32_t acc = 0;
    int nbits = 0;
    for (size_t i = 0; i < in.size(); ++i) {
        char c = in[i];
        if (c == '=') break;
        int v;
        if (c >= 'A' && c <= 'Z') v = c - 'A';
        else if (c >= 'a' && c <= 'z') v = c - 'a' + 26;
        else if (c >= '0' && c <= '9') v = c - '0' + 52;
        else if (c == '-' || c == '+') v = 62;
        else if (c == '_' || c == '/') v = 63;
        else continue;
        acc = (acc << 6) | (uint32_t) v;
        nbits += 6;
        if (nbits >= 8) {
            nbits -= 8;
            out.push_back((char) ((acc >> nbits) & 0xFF));
        }
    }
    return !out.empty();
}



// 标记文件路径：<dataDir>/verify_marker
static std::string markerPath(JNIEnv *env, jobject context) {
    jclass ctxClass = env->FindClass("android/content/Context");
    if (ctxClass == nullptr) {
        env->ExceptionClear();
        return "";
    }
    jmethodID getAppInfo = env->GetMethodID(ctxClass, "getApplicationInfo",
                                            "()Landroid/content/pm/ApplicationInfo;");
    jobject appInfo = env->CallObjectMethod(context, getAppInfo);
    if (env->ExceptionCheck() || appInfo == nullptr) {
        env->ExceptionClear();
        return "";
    }
    jclass aiClass = env->FindClass("android/content/pm/ApplicationInfo");
    jfieldID dataDirField = env->GetFieldID(aiClass, "dataDir", "Ljava/lang/String;");
    jstring dataDirJ = (jstring) env->GetObjectField(appInfo, dataDirField);
    env->DeleteLocalRef(aiClass);
    env->DeleteLocalRef(appInfo);
    if (dataDirJ == nullptr) return "";
    const char *c = env->GetStringUTFChars(dataDirJ, nullptr);
    if (c == nullptr) return "";
    std::string dir(c);
    env->ReleaseStringUTFChars(dataDirJ, c);
    env->DeleteLocalRef(dataDirJ);
    if (dir.empty()) return "";
    return dir + "/" + MARKER_FILENAME;
}

// 写入标记：magic(4) | version(4 LE) | fingerprint(32) | exp(4 LE) | HMAC(32) = 76 bytes
// fingerprint 绑定到 APK 的 versionCode + 各 dex 的原始 SHA-256；nativeCheckVerified 会重新计算并比对
static bool writeVerifyMarker(JNIEnv *env, jobject context,
                              long versionCode,
                              const std::vector<std::vector<uint8_t>> &dexShas,
                              long exp) {
    std::string path = markerPath(env, context);
    if (path.empty()) return false;
    uint8_t data[44];
    memcpy(data, MARKER_MAGIC, 4);
    uint32_t vc = (uint32_t) versionCode;
    data[4] = (uint8_t) (vc & 0xFF);
    data[5] = (uint8_t) ((vc >> 8) & 0xFF);
    data[6] = (uint8_t) ((vc >> 16) & 0xFF);
    data[7] = (uint8_t) ((vc >> 24) & 0xFF);
    uint8_t fp[32];
    computeDexFingerprint(dexShas, vc, fp);
    memcpy(data + 8, fp, 32);
    uint32_t e = (uint32_t) exp;
    data[40] = (uint8_t) (e & 0xFF);
    data[41] = (uint8_t) ((e >> 8) & 0xFF);
    data[42] = (uint8_t) ((e >> 16) & 0xFF);
    data[43] = (uint8_t) ((e >> 24) & 0xFF);
    uint8_t mac[32];
    hmacSha256(getIntegrityKey(), INTEGRITY_KEY_LEN, data, 44, mac);
    FILE *fp2 = fopen(path.c_str(), "wb");
    if (fp2 == nullptr) {
        return false;
    }
    bool ok = fwrite(data, 1, 44, fp2) == 44 && fwrite(mac, 1, 32, fp2) == 32;
    if (fclose(fp2) != 0) ok = false;
    return ok;
}

// 读取标记：返回 1=HMAC 有效, 0=不存在/旧格式/不完整, -1=被篡改；成功时填充 storedFp / versionCode / exp
static int readVerifyMarker(JNIEnv *env, jobject context,
                            uint8_t storedFp[32],
                            long &versionCode,
                            long &exp) {
    std::string path = markerPath(env, context);
    if (path.empty()) return 0;
    FILE *fp = fopen(path.c_str(), "rb");
    if (fp == nullptr) return 0;
    uint8_t data[76];
    size_t rd = fread(data, 1, 76, fp);
    fclose(fp);
    if (rd != 76) return 0;
    if (memcmp(data, MARKER_MAGIC, 4) != 0) return 0;
    uint8_t mac[32];
    hmacSha256(getIntegrityKey(), INTEGRITY_KEY_LEN, data, 44, mac);
    if (memcmp(mac, data + 44, 32) != 0) return -1;
    memcpy(storedFp, data + 8, 32);
    uint32_t vc = (uint32_t) data[4] | ((uint32_t) data[5] << 8)
                  | ((uint32_t) data[6] << 16) | ((uint32_t) data[7] << 24);
    uint32_t e = (uint32_t) data[40] | ((uint32_t) data[41] << 8)
                 | ((uint32_t) data[42] << 16) | ((uint32_t) data[43] << 24);
    versionCode = (long) vc;
    exp = (long) e;
    return 1;
}

static void hmacSha256(const uint8_t *key, size_t keyLen,
                       const uint8_t *msg, size_t msgLen,
                       uint8_t out[32]) {
    uint8_t k[64];
    memset(k, 0, sizeof(k));
    if (keyLen > sizeof(k)) {
        Sha256 s;
        s.update(key, keyLen);
        s.final(k);
    } else {
        memcpy(k, key, keyLen);
    }
    uint8_t ipad[64], opad[64];
    for (int i = 0; i < 64; ++i) {
        ipad[i] = k[i] ^ 0x36;
        opad[i] = k[i] ^ 0x5c;
    }
    uint8_t inner[32];
    Sha256 s;
    s.update(ipad, 64);
    s.update(msg, msgLen);
    s.final(inner);
    Sha256 s2;
    s2.update(opad, 64);
    s2.update(inner, 32);
    s2.final(out);
}

// 对原始 SHA-256 做单向打乱：out = HMAC-SHA256(固定密钥, sha256)
static std::string scramble(const uint8_t sha[32]) {
    uint8_t hmac[32];
    hmacSha256(getIntegrityKey(), INTEGRITY_KEY_LEN, sha, 32, hmac);
    static const char HEX[] = "0123456789abcdef";
    std::string result;
    result.reserve(64);
    for (int i = 0; i < 32; ++i) {
        result.push_back(HEX[hmac[i] >> 4]);
        result.push_back(HEX[hmac[i] & 0xF]);
    }
    return result;
}

// ═══════════════════════════════════════════════════════════════════════════
// 通用工具
// ═══════════════════════════════════════════════════════════════════════════

static inline uint16_t r16(const uint8_t *p) {
    return (uint16_t) (p[0] | (p[1] << 8));
}

static inline uint32_t r32(const uint8_t *p) {
    return (uint32_t) p[0] | ((uint32_t) p[1] << 8) | ((uint32_t) p[2] << 16) | ((uint32_t) p[3] << 24);
}

// IO 缓冲走堆，避免后台线程（默认栈 ~1MB）栈溢出
static const size_t HASH_CHUNK = 256 * 1024;
static const size_t DEFLATE_IN = 64 * 1024;

// 分块流式哈希整个文件
static bool hashFile(FILE *fp, uint8_t out[32]) {
    if (fseek(fp, 0, SEEK_END) != 0) return false;
    long size = ftell(fp);
    if (size < 0) return false;
    if (fseek(fp, 0, SEEK_SET) != 0) return false;
    Sha256 s;
    std::vector<uint8_t> buf(HASH_CHUNK);
    long remaining = size;
    while (remaining > 0) {
        size_t take = (size_t) std::min<long>(remaining, (long) buf.size());
        if (fread(buf.data(), 1, take, fp) != take) return false;
        s.update(buf.data(), take);
        remaining -= (long) take;
    }
    s.final(out);
    return true;
}

// ═══════════════════════════════════════════════════════════════════════════
// ZIP 解析：定位 classes*.dex 条目（EOCD → 中央目录 → 本地头）
// ═══════════════════════════════════════════════════════════════════════════

struct DexEntry {
    std::string name;
    uint32_t compMethod;
    uint32_t compSize;
    uint32_t localOffset;
};

static bool isDexName(const std::string &n) {
    if (n.rfind("classes", 0) != 0) return false;
    size_t pos = 7;
    while (pos < n.size() && n[pos] >= '0' && n[pos] <= '9') pos++;
    if (pos == 7) return n == "classes.dex";
    return pos + 4 == n.size() && n.compare(pos, 4, ".dex") == 0;
}

static int dexIndex(const std::string &n) {
    if (n == "classes.dex") return 0;
    int idx = 0;
    size_t pos = 7;
    while (pos < n.size() && n[pos] >= '0' && n[pos] <= '9') {
        idx = idx * 10 + (n[pos] - '0');
        pos++;
    }
    return idx;
}

static bool readZipEntries(FILE *fp, std::vector<DexEntry> &out) {
    if (fseek(fp, 0, SEEK_END) != 0) return false;
    long fileSize = ftell(fp);
    if (fileSize < 22) return false;
    long readLen = std::min<long>(fileSize, 65536);
    std::vector<uint8_t> tail((size_t) readLen);
    if (fseek(fp, fileSize - readLen, SEEK_SET) != 0) return false;
    if (fread(tail.data(), 1, (size_t) readLen, fp) != (size_t) readLen) return false;

    long eocdRel = -1;
    for (long i = readLen - 22; i >= 0; --i) {
        if (tail[i] == 0x50 && tail[i + 1] == 0x4b && tail[i + 2] == 0x05 && tail[i + 3] == 0x06) {
            eocdRel = i;
            break;
        }
    }
    if (eocdRel < 0) return false;

    size_t e = (size_t) eocdRel;
    uint32_t cdCount = r16(tail.data() + e + 10);
    uint32_t cdSize = r32(tail.data() + e + 12);
    uint32_t cdOffset = r32(tail.data() + e + 16);
    if (cdSize > 512u * 1024 * 1024) return false;

    std::vector<uint8_t> cd(cdSize);
    if (fseek(fp, cdOffset, SEEK_SET) != 0) return false;
    if (fread(cd.data(), 1, cdSize, fp) != cdSize) return false;

    size_t pos = 0;
    for (uint32_t i = 0; i < cdCount && pos + 46 <= cd.size(); ++i) {
        if (cd[pos] != 0x50 || cd[pos + 1] != 0x4b || cd[pos + 2] != 0x01 || cd[pos + 3] != 0x02) break;
        uint32_t method = r16(cd.data() + pos + 10);
        uint32_t compSize = r32(cd.data() + pos + 20);
        uint16_t nameLen = r16(cd.data() + pos + 28);
        uint16_t extraLen = r16(cd.data() + pos + 30);
        uint16_t commentLen = r16(cd.data() + pos + 32);
        uint32_t localOffset = r32(cd.data() + pos + 42);
        if (pos + 46 + nameLen > cd.size()) break;
        std::string name((const char *) (cd.data() + pos + 46), nameLen);
        if (isDexName(name)) {
            DexEntry de;
            de.name = name;
            de.compMethod = method;
            de.compSize = compSize;
            de.localOffset = localOffset;
            out.push_back(de);
        }
        pos += 46 + nameLen + extraLen + commentLen;
    }
    return true;
}

// 哈希单个 dex 条目（解压后内容）
static bool hashDexEntry(FILE *fp, const DexEntry &de, uint8_t out[32]) {
    if (fseek(fp, de.localOffset, SEEK_SET) != 0) return false;
    uint8_t lh[30];
    if (fread(lh, 1, 30, fp) != 30) return false;
    if (lh[0] != 0x50 || lh[1] != 0x4b || lh[2] != 0x03 || lh[3] != 0x04) return false;
    uint16_t nameLen = r16(lh + 26);
    uint16_t extraLen = r16(lh + 28);
    long dataOffset = de.localOffset + 30 + nameLen + extraLen;
    if (fseek(fp, dataOffset, SEEK_SET) != 0) return false;

    Sha256 s;
    if (de.compMethod == 0) {
        std::vector<uint8_t> buf(HASH_CHUNK);
        uint32_t remaining = de.compSize;
        while (remaining > 0) {
            size_t take = std::min<size_t>(remaining, buf.size());
            if (fread(buf.data(), 1, take, fp) != take) return false;
            s.update(buf.data(), take);
            remaining -= (uint32_t) take;
        }
    } else if (de.compMethod == 8) {
        z_stream zs;
        memset(&zs, 0, sizeof(zs));
        if (inflateInit2(&zs, -15) != Z_OK) return false; // raw deflate
        std::vector<uint8_t> in(DEFLATE_IN);
        std::vector<uint8_t> outBuf(HASH_CHUNK);
        uint32_t remaining = de.compSize;
        bool ok = true;
        for (;;) {
            if (zs.avail_in == 0 && remaining > 0) {
                size_t take = std::min<size_t>(remaining, in.size());
                if (fread(in.data(), 1, take, fp) != take) { ok = false; break; }
                zs.next_in = in.data();
                zs.avail_in = (uInt) take;
                remaining -= (uint32_t) take;
            }
            zs.next_out = outBuf.data();
            zs.avail_out = (uInt) outBuf.size();
            int ret = inflate(&zs, Z_NO_FLUSH);
            size_t produced = outBuf.size() - zs.avail_out;
            if (produced > 0) s.update(outBuf.data(), produced);
            if (ret == Z_STREAM_END) break;
            if (ret != Z_OK) { ok = false; break; }
        }
        inflateEnd(&zs);
        if (!ok) return false;
    } else {
        return false;
    }
    s.final(out);
    return true;
}

// 组合指纹：SHA-256( versionCode(4 LE) || 各 dex 原始 SHA-256(按 index 排序) )
// 用于把验证标记与 APK 真实身份绑定；nativeCheckVerified 会重新计算并比对
static void computeDexFingerprint(const std::vector<std::vector<uint8_t>> &dexShas,
                                  uint32_t versionCode, uint8_t out[32]) {
    Sha256 s;
    uint8_t v[4] = {
            (uint8_t) (versionCode & 0xFF),
            (uint8_t) ((versionCode >> 8) & 0xFF),
            (uint8_t) ((versionCode >> 16) & 0xFF),
            (uint8_t) ((versionCode >> 24) & 0xFF)};
    s.update(v, 4);
    for (const auto &sha : dexShas) {
        s.update(sha.data(), sha.size());
    }
    s.final(out);
}

// ═══════════════════════════════════════════════════════════════════════════
// 服务器校验：payload JSON 拼装 / URL 构造 / 响应解析
// ═══════════════════════════════════════════════════════════════════════════

static std::string jsonEscape(const std::string &s) {
    std::string out;
    out.reserve(s.size() + 8);
    for (unsigned char c : s) {
        switch (c) {
            case '"': out += "\\\""; break;
            case '\\': out += "\\\\"; break;
            case '\b': out += "\\b"; break;
            case '\f': out += "\\f"; break;
            case '\n': out += "\\n"; break;
            case '\r': out += "\\r"; break;
            case '\t': out += "\\t"; break;
            default:
                if (c < 0x20) {
                    char buf[8];
                    snprintf(buf, sizeof(buf), "\\u%04x", c);
                    out += buf;
                } else {
                    out += (char) c;
                }
        }
    }
    return out;
}

static std::string trimWs(const std::string &s) {
    size_t a = 0, b = s.size();
    while (a < b && (s[a] == ' ' || s[a] == '\t' || s[a] == '\n' || s[a] == '\r')) a++;
    while (b > a && (s[b - 1] == ' ' || s[b - 1] == '\t' || s[b - 1] == '\n' || s[b - 1] == '\r')) b--;
    return s.substr(a, b - a);
}

// 定位 "key" 之后的值起始位置（跳过空白与冒号）
static bool jsonFieldValuePos(const std::string &body, const char *key, size_t &valuePos) {
    std::string pat = std::string("\"") + key + "\"";
    size_t pos = body.find(pat);
    if (pos == std::string::npos) return false;
    pos += pat.size();
    while (pos < body.size() && (body[pos] == ' ' || body[pos] == '\t' || body[pos] == '\n' || body[pos] == '\r')) pos++;
    if (pos < body.size() && body[pos] == ':') {
        pos++;
        while (pos < body.size() && (body[pos] == ' ' || body[pos] == '\t' || body[pos] == '\n' || body[pos] == '\r')) pos++;
    }
    valuePos = pos;
    return pos < body.size();
}

static bool jsonParseInt(const std::string &body, const char *key, long &out) {
    size_t pos;
    if (!jsonFieldValuePos(body, key, pos)) return false;
    const char *start = body.c_str() + pos;
    char *end = nullptr;
    long v = strtol(start, &end, 10);
    if (end == start) return false;
    out = v;
    return true;
}

static bool jsonParseString(const std::string &body, const char *key, std::string &out) {
    size_t pos;
    if (!jsonFieldValuePos(body, key, pos)) return false;
    if (pos >= body.size() || body[pos] != '"') return false;
    size_t i = pos + 1;
    std::string res;
    res.reserve(64);
    while (i < body.size()) {
        char c = body[i];
        if (c == '"') {
            out = res;
            return true;
        }
        if (c == '\\') {
            i++;
            if (i >= body.size()) return false;
            char e = body[i];
            switch (e) {
                case '"': res += '"'; break;
                case '\\': res += '\\'; break;
                case '/': res += '/'; break;
                case 'b': res += '\b'; break;
                case 'f': res += '\f'; break;
                case 'n': res += '\n'; break;
                case 'r': res += '\r'; break;
                case 't': res += '\t'; break;
                case 'u': {
                    if (i + 4 >= body.size()) return false;
                    unsigned int cp = 0;
                    for (int k = 0; k < 4; ++k) {
                        char h = body[i + 1 + k];
                        cp <<= 4;
                        if (h >= '0' && h <= '9') cp |= (unsigned int) (h - '0');
                        else if (h >= 'a' && h <= 'f') cp |= (unsigned int) (h - 'a' + 10);
                        else if (h >= 'A' && h <= 'F') cp |= (unsigned int) (h - 'A' + 10);
                        else return false;
                    }
                    i += 4;
                    if (cp < 0x80) {
                        res += (char) cp;
                    } else if (cp < 0x800) {
                        res += (char) (0xC0 | (cp >> 6));
                        res += (char) (0x80 | (cp & 0x3F));
                    } else {
                        res += (char) (0xE0 | (cp >> 12));
                        res += (char) (0x80 | ((cp >> 6) & 0x3F));
                        res += (char) (0x80 | (cp & 0x3F));
                    }
                    break;
                }
                default: return false;
            }
        } else {
            res += c;
        }
        i++;
    }
    return false;
}

// 复刻 UrlBuilder.longPath(path).build()：折叠连续斜杠、去首尾斜杠，base + "/" + path
static std::string buildVerifyUrl(const std::string &base, const std::string &rawPath) {
    if (base.empty()) return "";
    std::string path;
    path.reserve(rawPath.size());
    bool prevSlash = false;
    for (char c : rawPath) {
        if (c == '/') {
            if (!prevSlash) path += c;
            prevSlash = true;
        } else {
            path += c;
            prevSlash = false;
        }
    }
    size_t a = 0, b = path.size();
    while (a < b && path[a] == '/') a++;
    while (b > a && path[b - 1] == '/') b--;
    std::string trimmed = path.substr(a, b - a);
    if (trimmed.empty()) return base;
    return base + "/" + trimmed;
}

// ═══════════════════════════════════════════════════════════════════════════
// RSA-2048 / RS256 验签（纯自实现：无 OpenSSL/BoringSSL 依赖）
// 公钥模数 XOR 混淆（掩码 0x5A），运行时还原；grep 不到明文字节
// ═══════════════════════════════════════════════════════════════════════════

static const uint8_t RSA_MASK = 0x5A;
static const size_t kRsaWords = 32;   // 2048-bit = 32 x 64-bit limbs
static const size_t kRsaBytes = 256;

// 公钥模数 n（LE 字节序，运行时 ^0x5A 还原）
static const uint8_t K_RSAN_BYTES[256] = {
    0x89, 0x4d, 0x79, 0x49, 0x38, 0x6f, 0x7e, 0xaa, 0x2f, 0x8f, 0x3c, 0x40,
    0x40, 0x49, 0x71, 0x6c, 0xa9, 0xdb, 0x7a, 0xc6, 0xbf, 0xc9, 0x6c, 0x07,
    0x42, 0x3e, 0xff, 0x30, 0xc5, 0xdb, 0x82, 0x15, 0x31, 0x41, 0x44, 0xaf,
    0xe7, 0xe0, 0xd0, 0x41, 0xf8, 0x3f, 0x06, 0x8a, 0x52, 0xe1, 0x02, 0x05,
    0x09, 0x83, 0x09, 0x9e, 0x31, 0x83, 0xc4, 0x69, 0xdb, 0x62, 0x37, 0xf6,
    0xbc, 0xc3, 0x76, 0x2e, 0xb2, 0x5d, 0x82, 0xb8, 0x48, 0x11, 0x43, 0xd4,
    0x2f, 0x8e, 0xd4, 0xcf, 0x50, 0xee, 0x07, 0xa3, 0xc2, 0x93, 0xf2, 0x2a,
    0xca, 0xd3, 0x37, 0x32, 0xf8, 0x48, 0x4b, 0x86, 0x54, 0x59, 0xb7, 0x06,
    0xeb, 0xaa, 0x61, 0x20, 0x17, 0xf1, 0x77, 0x3b, 0x42, 0x5d, 0xbe, 0x1d,
    0x23, 0x3a, 0x74, 0x77, 0x8d, 0x31, 0xad, 0x2b, 0xe0, 0xb1, 0x27, 0x6a,
    0xb4, 0xb5, 0x96, 0x58, 0x51, 0xe5, 0x96, 0x8b, 0x1d, 0x56, 0x4a, 0x33,
    0x45, 0x06, 0x76, 0x02, 0xbc, 0x88, 0x70, 0x9f, 0x3f, 0x71, 0xbb, 0x46,
    0xa5, 0x02, 0x51, 0x36, 0x76, 0x3c, 0x67, 0x15, 0xa0, 0x3d, 0xa4, 0x80,
    0x7e, 0x29, 0xac, 0x9b, 0x71, 0xb1, 0xf7, 0xa6, 0x1d, 0x7c, 0x1f, 0x79,
    0xa4, 0xe9, 0x69, 0x86, 0x46, 0x24, 0x7f, 0x5e, 0xbb, 0x38, 0x9a, 0x3f,
    0xa5, 0x59, 0x6d, 0xaa, 0x06, 0xd9, 0x04, 0x43, 0x9b, 0x3d, 0x08, 0xcf,
    0xd1, 0x1d, 0x7a, 0x3f, 0xff, 0x09, 0x6c, 0xd3, 0x78, 0x4e, 0xf8, 0xa9,
    0xb9, 0xdd, 0x15, 0x9d, 0xb5, 0x99, 0x87, 0x50, 0xfe, 0x25, 0xaf, 0x4e,
    0x5a, 0xcc, 0xbe, 0xed, 0x0f, 0x97, 0x73, 0x09, 0x59, 0x90, 0x05, 0xa0,
    0x0a, 0x06, 0x01, 0x84, 0x7d, 0xf3, 0xd1, 0x4d, 0xd7, 0x92, 0xbe, 0xf7,
    0x6d, 0x81, 0x2a, 0x2f, 0x64, 0x8e, 0xdd, 0x21, 0xf8, 0x82, 0x01, 0x74,
    0x5b, 0xff, 0x19, 0xf0};

// R2 = 2^(2*2048) mod n（Montgomery 入域转换用），LE 字节序混淆
static const uint8_t K_RSAR2_BYTES[256] = {
    0xac, 0xee, 0x88, 0x21, 0x99, 0xfc, 0xa5, 0xf7, 0xb7, 0x7f, 0x70, 0x2a,
    0x3f, 0x7b, 0x02, 0xa3, 0xde, 0x55, 0x74, 0xf7, 0xe7, 0xc8, 0x50, 0xd2,
    0x2b, 0x45, 0x97, 0x9d, 0xd4, 0x93, 0x11, 0x15, 0xd8, 0x46, 0xe1, 0xda,
    0xde, 0x0b, 0x97, 0x42, 0x73, 0x9a, 0x28, 0x74, 0x8a, 0x5e, 0xd0, 0x69,
    0xe8, 0x9d, 0x09, 0x4f, 0x17, 0x2d, 0x93, 0x1d, 0xa9, 0xab, 0xfb, 0x17,
    0x32, 0x35, 0xf0, 0xef, 0x53, 0xc3, 0xa7, 0xe5, 0xa9, 0xb2, 0x67, 0xae,
    0x32, 0x76, 0xec, 0xff, 0x12, 0x7e, 0xf1, 0x7c, 0x64, 0xa2, 0xbd, 0x2a,
    0x4e, 0xc8, 0xfa, 0xae, 0xf5, 0x4d, 0x3d, 0xfa, 0x62, 0xad, 0x0a, 0x41,
    0x87, 0x73, 0x81, 0x33, 0xbd, 0x18, 0xe5, 0x62, 0xf1, 0x6e, 0xb6, 0x46,
    0xe3, 0x68, 0x6d, 0xa7, 0x00, 0xe5, 0x66, 0x1d, 0x38, 0x49, 0xc4, 0x4d,
    0x08, 0xe2, 0x59, 0xdf, 0x32, 0x8b, 0x73, 0xc1, 0x3c, 0x5f, 0xba, 0xa6,
    0x3a, 0x40, 0xc9, 0x05, 0xc3, 0xf5, 0x63, 0x76, 0x3b, 0x26, 0x41, 0xd3,
    0x4f, 0x76, 0x40, 0x85, 0x29, 0xa9, 0x4e, 0x3c, 0x14, 0xac, 0x1a, 0x41,
    0x43, 0x5f, 0x39, 0xcb, 0x42, 0x14, 0x98, 0xdb, 0x95, 0x4c, 0x90, 0xeb,
    0x4f, 0xf3, 0xaa, 0x77, 0xab, 0x7d, 0x3b, 0xa2, 0xb4, 0x31, 0x08, 0x2b,
    0x6a, 0x28, 0x4f, 0xbd, 0x52, 0xfe, 0xc7, 0xab, 0xba, 0x29, 0x44, 0xd7,
    0x7e, 0xf6, 0x46, 0x9a, 0x37, 0x73, 0x53, 0xe5, 0x7f, 0x79, 0xfb, 0x74,
    0xb2, 0xc5, 0x71, 0xcb, 0xf7, 0x44, 0x3f, 0xd3, 0x8c, 0x98, 0x37, 0xb0,
    0x07, 0x13, 0xc3, 0x3e, 0x9c, 0xe8, 0x2c, 0x14, 0x6f, 0x23, 0xc9, 0xc3,
    0xc7, 0x6b, 0xa0, 0x3f, 0x87, 0xbf, 0x6b, 0x0a, 0x12, 0x56, 0xdd, 0x6c,
    0xe4, 0x1c, 0x85, 0xad, 0x8e, 0x3a, 0xfa, 0x4d, 0x06, 0x30, 0xd3, 0xe2,
    0x5b, 0xc4, 0xd5, 0x15};

// n0inv = -n^{-1} mod 2^64（Montgomery 乘法 m = T[0]*n0inv mod 2^64），LE 混淆
static const uint8_t K_RSAN0INV_BYTES[8] = {0xff, 0xfd, 0x37, 0x61, 0x1f, 0x4d, 0x3c, 0x17};

static bool rsaReady = false;
static uint64_t rsaN[kRsaWords];
static uint64_t rsaR2[kRsaWords];
static uint64_t rsaN0Inv;

static void unmaskBytes(const uint8_t *masked, size_t len, uint8_t *out) {
    for (size_t i = 0; i < len; ++i) out[i] = masked[i] ^ RSA_MASK;
}

static void rsaEnsureInit() {
    if (rsaReady) return;
    uint8_t raw[256];
    unmaskBytes(K_RSAN_BYTES, kRsaBytes, raw);
    for (size_t i = 0; i < kRsaWords; ++i) {
        uint64_t v = 0;
        for (size_t k = 0; k < 8; ++k) v |= (uint64_t) raw[i * 8 + k] << (8 * k);
        rsaN[i] = v;
    }
    unmaskBytes(K_RSAR2_BYTES, kRsaBytes, raw);
    for (size_t i = 0; i < kRsaWords; ++i) {
        uint64_t v = 0;
        for (size_t k = 0; k < 8; ++k) v |= (uint64_t) raw[i * 8 + k] << (8 * k);
        rsaR2[i] = v;
    }
    uint8_t raw8[8];
    unmaskBytes(K_RSAN0INV_BYTES, 8, raw8);
    uint64_t v = 0;
    for (size_t k = 0; k < 8; ++k) v |= (uint64_t) raw8[k] << (8 * k);
    rsaN0Inv = v;
    memset(raw, 0, sizeof(raw));
    memset(raw8, 0, sizeof(raw8));
    rsaReady = true;
}

// 大端字节（RSA 签名/消息的规范表示）→ LE 64-bit words
static void rsaBEToWords(const uint8_t *be, uint64_t out[kRsaWords]) {
    for (size_t i = 0; i < kRsaWords; ++i) {
        uint64_t v = 0;
        for (size_t k = 0; k < 8; ++k) v |= (uint64_t) be[i * 8 + k] << (56 - 8 * k);
        out[kRsaWords - 1 - i] = v;
    }
}

// 64x64 乘法 → 128 位（lo,hi），纯 32 位拆分，无 __int128 依赖（兼容 armeabi-v7a）
static inline void mul64x64(uint64_t a, uint64_t b, uint64_t *lo, uint64_t *hi) {
    uint64_t a0 = a & 0xFFFFFFFF, a1 = a >> 32;
    uint64_t b0 = b & 0xFFFFFFFF, b1 = b >> 32;
    uint64_t w0 = a0 * b0;
    uint64_t t = (w0 >> 32) + a0 * b1;
    uint64_t w1 = t & 0xFFFFFFFF;
    uint64_t w2 = t >> 32;
    w1 += a1 * b0;
    *hi = a1 * b1 + w2 + (w1 >> 32);
    *lo = (w1 << 32) | (w0 & 0xFFFFFFFF);
}

// 128 位累加：out = a*b + c + d（结果仍为 128 位，不溢出）
static inline void mulAdd3(uint64_t a, uint64_t b, uint64_t c, uint64_t d,
                           uint64_t *lo, uint64_t *hi) {
    uint64_t mlo, mhi;
    mul64x64(a, b, &mlo, &mhi);
    uint64_t s1 = mlo + c;
    uint64_t c1 = (s1 < mlo) ? 1 : 0;
    uint64_t s2 = s1 + d;
    uint64_t c2 = (s2 < d) ? 1 : 0;
    *lo = s2;
    *hi = mhi + c1 + c2;
}

// 64 位加并返回进位
static inline uint64_t addCarry(uint64_t a, uint64_t b, uint64_t *out) {
    uint64_t s = a + b;
    *out = s;
    return (s < b) ? 1 : 0;
}

// CIOS Montgomery 乘法：out = a*b*R^{-1} mod n
// 加宽工作缓冲容纳进位溢出；末尾条件减法基于全 (S+1)-word 值
static void rsaMontMul(const uint64_t a[kRsaWords], const uint64_t b[kRsaWords],
                       const uint64_t n[kRsaWords], uint64_t n0inv,
                       uint64_t out[kRsaWords]) {
    const size_t L = kRsaWords + 8;
    uint64_t T[kRsaWords + 8];
    memset(T, 0, sizeof(T));
    for (size_t i = 0; i < kRsaWords; ++i) {
        uint64_t carry = 0;
        uint64_t ai = a[i];
        for (size_t j = 0; j < kRsaWords; ++j) {
            uint64_t lo, hi;
            mulAdd3(ai, b[j], T[j], carry, &lo, &hi);
            T[j] = lo;
            carry = hi;
        }
        size_t k = kRsaWords;
        while (carry) {
            carry = addCarry(T[k], carry, &T[k]);
            ++k;
        }
        uint64_t m = T[0] * n0inv;
        carry = 0;
        for (size_t j = 0; j < kRsaWords; ++j) {
            uint64_t lo, hi;
            mulAdd3(m, n[j], T[j], carry, &lo, &hi);
            T[j] = lo;
            carry = hi;
        }
        k = kRsaWords;
        while (carry) {
            carry = addCarry(T[k], carry, &T[k]);
            ++k;
        }
        for (size_t j = 0; j < L - 1; ++j) T[j] = T[j + 1];
        T[L - 1] = 0;
    }
    // 条件减法：比较完整值（低 S 字 + 顶字 T[S]），>= n 则减一次
    bool needSub = (T[kRsaWords] != 0);
    if (!needSub) {
        for (size_t j = kRsaWords; j-- > 0;) {
            if (T[j] != n[j]) {
                needSub = T[j] > n[j];
                break;
            }
        }
    }
    if (needSub) {
        uint64_t borrow = 0;
        for (size_t j = 0; j < kRsaWords; ++j) {
            uint64_t d = T[j] - n[j] - borrow;
            if (borrow) {
                borrow = (T[j] <= n[j]) ? 1 : 0;
            } else {
                borrow = (T[j] < n[j]) ? 1 : 0;
            }
            T[j] = d;
        }
        // borrow-out 被丢弃的顶字吸收，按要求忽略
    }
    memcpy(out, T, kRsaWords * sizeof(uint64_t));
    memset(T, 0, sizeof(T));
}

// LE words → 大端字节
static void rsaWordsToBE(const uint64_t w[kRsaWords], uint8_t out[kRsaBytes]) {
    for (size_t i = 0; i < kRsaWords; ++i) {
        uint64_t v = w[kRsaWords - 1 - i];
        out[i * 8 + 0] = (uint8_t) (v >> 56);
        out[i * 8 + 1] = (uint8_t) (v >> 48);
        out[i * 8 + 2] = (uint8_t) (v >> 40);
        out[i * 8 + 3] = (uint8_t) (v >> 32);
        out[i * 8 + 4] = (uint8_t) (v >> 24);
        out[i * 8 + 5] = (uint8_t) (v >> 16);
        out[i * 8 + 6] = (uint8_t) (v >> 8);
        out[i * 8 + 7] = (uint8_t) v;
    }
}

// PKCS#1 v1.5 DigestInfo 前缀（SHA-256）
static const uint8_t RsaDerSha256[19] = {
    0x30, 0x31, 0x30, 0x0d, 0x06, 0x09, 0x60, 0x86, 0x48, 0x01,
    0x65, 0x03, 0x04, 0x02, 0x01, 0x05, 0x00, 0x04, 0x20};

// RSA 验签：sig^65537 mod n == EMSA-PKCS1-v1_5(SHA-256(message))
static bool rsaVerifySha256(const uint8_t sig[kRsaBytes], const uint8_t hash32[32]) {
    rsaEnsureInit();

    uint64_t s[kRsaWords];
    rsaBEToWords(sig, s);

    // 规范要求 0 <= s < n；s >= n 直接判无效
    bool ge = false;
    for (size_t j = kRsaWords; j-- > 0;) {
        if (s[j] != rsaN[j]) {
            ge = s[j] > rsaN[j];
            break;
        }
    }
    if (ge) {
        memset(s, 0, sizeof(s));
        return false;
    }

    // m = s^65537 mod n = s^(2^16+1) mod n
    uint64_t one[kRsaWords] = {0};
    one[0] = 1;
    uint64_t t1[kRsaWords], x[kRsaWords], y[kRsaWords], m[kRsaWords];
    rsaMontMul(s, rsaR2, rsaN, rsaN0Inv, t1);   // s*R
    memcpy(x, t1, sizeof(x));
    for (int i = 0; i < 16; ++i) {
        rsaMontMul(x, x, rsaN, rsaN0Inv, m);
        memcpy(x, m, sizeof(x));
    }
    rsaMontMul(x, t1, rsaN, rsaN0Inv, y);       // s^(2^16+1)*R
    rsaMontMul(y, one, rsaN, rsaN0Inv, m);      // m mod n

    uint8_t mb[kRsaBytes];
    rsaWordsToBE(m, mb);

    // EMSA-PKCS1-v1_5：0x00 0x01 || FF*202 || 0x00 || DigestInfo || hash
    uint8_t em[kRsaBytes];
    em[0] = 0x00;
    em[1] = 0x01;
    // psLen = k - tLen - 3 = 256 - 51 - 3 = 202
    memset(em + 2, 0xFF, kRsaBytes - 51 - 3);
    em[kRsaBytes - 51 - 1] = 0x00;
    memcpy(em + kRsaBytes - 51, RsaDerSha256, 19);
    memcpy(em + kRsaBytes - 32, hash32, 32);

    bool ok = (memcmp(mb, em, kRsaBytes) == 0);

    memset(s, 0, sizeof(s));
    memset(t1, 0, sizeof(t1));
    memset(x, 0, sizeof(x));
    memset(y, 0, sizeof(y));
    memset(m, 0, sizeof(m));
    memset(mb, 0, sizeof(mb));
    memset(em, 0, sizeof(em));
    return ok;
}

// JWT RS256 全量校验：struct 三段 / 外层 base64url 两种形态
// 要求：header.alg == "RS256"；签名有效；exp 严格（> now，无兜底）；
//        payload.app_id / version_code 与本地一致。成功时输出 exp。
static bool jwtVerifyRS256(const std::string &token,
                           const std::string &expectAppId,
                           long expectVersionCode,
                           long &outExp) {
    std::string jwt = token;
    if (jwt.find('.') == std::string::npos) {
        std::string dec;
        if (!base64DecodeTolerant(token, dec) || dec.find('.') == std::string::npos) {
            return false;
        }
        jwt = dec;
    }
    size_t d1 = jwt.find('.');
    if (d1 == std::string::npos) return false;
    size_t d2 = jwt.find('.', d1 + 1);
    if (d2 == std::string::npos) return false;
    // 恰好三段：signature 段内不得再有 '.'
    if (jwt.find('.', d2 + 1) != std::string::npos) return false;

    std::string hB64 = jwt.substr(0, d1);
    std::string pB64 = jwt.substr(d1 + 1, d2 - d1 - 1);
    std::string sB64 = jwt.substr(d2 + 1);
    if (hB64.empty() || pB64.empty() || sB64.empty()) return false;

    std::vector<uint8_t> sig;
    if (!base64UrlDecode(sB64, sig)) return false;
    if (sig.size() != kRsaBytes) return false;

    std::vector<uint8_t> hRaw;
    if (!base64UrlDecode(hB64, hRaw)) return false;
    std::string hJson((const char *) hRaw.data(), hRaw.size());
    std::string alg;
    if (!jsonParseString(hJson, "alg", alg) || alg != "RS256") return false;

    std::vector<uint8_t> pRaw;
    if (!base64UrlDecode(pB64, pRaw)) return false;
    std::string pJson((const char *) pRaw.data(), pRaw.size());
    long exp = 0;
    if (!jsonParseInt(pJson, "exp", exp)) return false;
    std::string appId;
    if (!jsonParseString(pJson, "app_id", appId) || appId != expectAppId) return false;
    long vc = 0;
    if (!jsonParseInt(pJson, "version_code", vc) || vc != expectVersionCode) return false;
    if (exp <= (long) std::time(nullptr)) return false;

    // 签名输入 = 原 token（或解码后 JWT）的 header.payload 原文
    std::string signingInput = hB64 + "." + pB64;

    uint8_t h[32];
    Sha256 s;
    s.update((const uint8_t *) signingInput.data(), signingInput.size());
    s.final(h);
    bool ok = rsaVerifySha256(sig.data(), h);
    memset(h, 0, sizeof(h));
    if (ok) outExp = exp;
    return ok;
}

// ═══════════════════════════════════════════════════════════════════════════
// JNI 接口
// ═══════════════════════════════════════════════════════════════════════════

static jstring
apkIntegrityHashImpl(
        JNIEnv *env,
        jclass /* clazz */,
        jstring apkPath) {
    const char *path = env->GetStringUTFChars(apkPath, nullptr);
    if (path == nullptr) return nullptr;

    FILE *fp = fopen(path, "rb");
    env->ReleaseStringUTFChars(apkPath, path);
    if (fp == nullptr) {
        return nullptr;
    }

    uint8_t sha[32];
    if (!hashFile(fp, sha)) {
        fclose(fp);
        return nullptr;
    }
    fclose(fp);

    std::string result = scramble(sha);
    return env->NewStringUTF(result.c_str());
}

static jobjectArray
dexIntegrityHashListImpl(
        JNIEnv *env,
        jclass /* clazz */,
        jstring apkPath) {
    const char *path = env->GetStringUTFChars(apkPath, nullptr);
    if (path == nullptr) return nullptr;
    std::string apk(path);
    env->ReleaseStringUTFChars(apkPath, path);

    FILE *fp = fopen(apk.c_str(), "rb");
    if (fp == nullptr) {
        return nullptr;
    }

    std::vector<DexEntry> entries;
    if (!readZipEntries(fp, entries)) {
        fclose(fp);
        return nullptr;
    }
    std::sort(entries.begin(), entries.end(),
              [](const DexEntry &a, const DexEntry &b) { return dexIndex(a.name) < dexIndex(b.name); });

    jclass stringClass = env->FindClass("java/lang/String");
    if (stringClass == nullptr) {
        fclose(fp);
        return nullptr;
    }
    jobjectArray arr = env->NewObjectArray((jsize) entries.size(), stringClass, nullptr);
    env->DeleteLocalRef(stringClass);
    if (arr == nullptr) {
        fclose(fp);
        return nullptr;
    }

    for (size_t i = 0; i < entries.size(); ++i) {
        uint8_t sha[32];
        if (!hashDexEntry(fp, entries[i], sha)) {
            continue;
        }
        std::string value = scramble(sha);
        jstring str = env->NewStringUTF(value.c_str());
        env->SetObjectArrayElement(arr, (jsize) i, str);
        env->DeleteLocalRef(str);
    }
    fclose(fp);
    return arr;
}

// ── 第1步服务器校验（native）：哈希 + payload + URL + HTTP + 响应判定 ──
// 返回 String[]{status, token, msg}
//   status: ok / no_network / internal_error / http_error / server_error / rejected / parse_error
static jobjectArray
nativeVerifyServerImpl(
        JNIEnv *env,
        jclass /* clazz */,
        jobject context) {

    auto makeResult = [&](const char *status, const std::string &token, const std::string &msg) -> jobjectArray {
        jclass sc = env->FindClass("java/lang/String");
        if (sc == nullptr) return nullptr;
        jobjectArray arr = env->NewObjectArray(3, sc, nullptr);
        env->DeleteLocalRef(sc);
        if (arr == nullptr) return nullptr;
        jstring s0 = env->NewStringUTF(status);
        jstring s1 = env->NewStringUTF(token.empty() ? "" : token.c_str());
        jstring s2 = env->NewStringUTF(msg.empty() ? "" : msg.c_str());
        env->SetObjectArrayElement(arr, 0, s0);
        env->SetObjectArrayElement(arr, 1, s1);
        env->SetObjectArrayElement(arr, 2, s2);
        env->DeleteLocalRef(s0);
        env->DeleteLocalRef(s1);
        env->DeleteLocalRef(s2);
        return arr;
    };

    if (context == nullptr) return makeResult("internal_error", "", "null context");

    // ── sourceDir（APK 路径） ──
    jclass contextClass = env->FindClass("android/content/Context");
    if (contextClass == nullptr) {
        env->ExceptionClear();
        return makeResult("internal_error", "", "no Context class");
    }
    jmethodID getAppInfo = env->GetMethodID(contextClass, "getApplicationInfo",
                                            "()Landroid/content/pm/ApplicationInfo;");
    jobject appInfo = env->CallObjectMethod(context, getAppInfo);
    if (env->ExceptionCheck() || appInfo == nullptr) {
        env->ExceptionClear();
        return makeResult("internal_error", "", "getApplicationInfo failed");
    }
    jclass aiClass = env->FindClass("android/content/pm/ApplicationInfo");
    jfieldID sourceDirField = env->GetFieldID(aiClass, "sourceDir", "Ljava/lang/String;");
    jstring sourceDirJ = (jstring) env->GetObjectField(appInfo, sourceDirField);
    env->DeleteLocalRef(aiClass);
    env->DeleteLocalRef(appInfo);
    if (sourceDirJ == nullptr) return makeResult("internal_error", "", "no sourceDir");
    const char *apkC = env->GetStringUTFChars(sourceDirJ, nullptr);
    if (apkC == nullptr) return makeResult("internal_error", "", "sourceDir utf8");
    std::string apkPath(apkC);
    env->ReleaseStringUTFChars(sourceDirJ, apkC);
    env->DeleteLocalRef(sourceDirJ);

    // ── versionCode（与 DeviceInfoUtils.getAppVersionCode 一致） ──
    jclass duClass = env->FindClass("cn/panda/utils/library/DeviceInfoUtils");
    jlong versionCode = -1;
    if (duClass != nullptr) {
        jmethodID getVersion = env->GetStaticMethodID(duClass, "getAppVersionCode",
                                                      "(Landroid/content/Context;)J");
        if (getVersion == nullptr) {
            env->ExceptionClear();
            versionCode = -1;
        } else {
            versionCode = env->CallStaticLongMethod(duClass, getVersion, context);
            if (env->ExceptionCheck()) {
                env->ExceptionClear();
                versionCode = -1;
            }
        }
    } else {
        env->ExceptionClear();
    }

    // ── 凭据与端点（o.s.o.l.get().a() 解密串） ──
    jclass lClass = env->FindClass("o/s/o/l");
    if (lClass == nullptr) {
        env->ExceptionClear();
        return makeResult("internal_error", "", "secure lib class missing");
    }
    jmethodID getMethod = env->GetStaticMethodID(lClass, "get", "()Lo/s/o/l;");
    jobject lInst = env->CallStaticObjectMethod(lClass, getMethod);
    if (env->ExceptionCheck() || lInst == nullptr) {
        env->ExceptionClear();
        return makeResult("internal_error", "", "secure lib not initialized");
    }
    jmethodID aMethod = env->GetMethodID(lClass, "a", "()[Ljava/lang/String;");
    jobjectArray strs = (jobjectArray) env->CallObjectMethod(lInst, aMethod);
    env->DeleteLocalRef(lInst);
    if (env->ExceptionCheck() || strs == nullptr) {
        env->ExceptionClear();
        return makeResult("internal_error", "", "secure strings fetch failed");
    }
    std::string appId, appSecret, baseUrl, path10;
    const int indices[4] = {0, 1, 2, 10};
    std::string *targets[4] = {&appId, &appSecret, &baseUrl, &path10};
    for (int i = 0; i < 4; ++i) {
        jstring s = (jstring) env->GetObjectArrayElement(strs, indices[i]);
        if (s != nullptr) {
            const char *c = env->GetStringUTFChars(s, nullptr);
            if (c != nullptr) {
                *targets[i] = c;
                env->ReleaseStringUTFChars(s, c);
            }
            env->DeleteLocalRef(s);
        }
    }
    env->DeleteLocalRef(strs);
    env->DeleteLocalRef(lClass);

    // ── 哈希集合：[apk, dex1, dex2, ...]（失败则跳过，全空则置 ""） ──
    std::vector<std::string> hashes;
    std::vector<std::vector<uint8_t>> rawDexShas; // 用于写入 marker fingerprint
    FILE *fp = fopen(apkPath.c_str(), "rb");
    if (fp != nullptr) {
        uint8_t sha[32];
        if (hashFile(fp, sha)) {
            hashes.push_back(scramble(sha));
        }
        std::vector<DexEntry> entries;
        if (readZipEntries(fp, entries)) {
            std::sort(entries.begin(), entries.end(),
                      [](const DexEntry &a, const DexEntry &b) { return dexIndex(a.name) < dexIndex(b.name); });
            for (size_t i = 0; i < entries.size(); ++i) {
                if (hashDexEntry(fp, entries[i], sha)) {
                    hashes.push_back(scramble(sha));
                    rawDexShas.emplace_back(sha, sha + 32);
                }
            }
        }
        fclose(fp);
    }
    if (hashes.empty()) hashes.push_back("");

    // ── payload JSON ──
    std::string payload = "{\"app_id\":\"" + jsonEscape(appId)
                          + "\",\"app_secret\":\"" + jsonEscape(appSecret)
                          + "\",\"version_code\":" + std::to_string(versionCode)
                          + ",\"version_hash\":[";
    for (size_t i = 0; i < hashes.size(); ++i) {
        if (i > 0) payload += ",";
        payload += "\"" + jsonEscape(hashes[i]) + "\"";
    }
    payload += "]}";

    // ── URL（HTTPS 传输走 NativeHttp 薄封装） ──
    std::string url = buildVerifyUrl(baseUrl, path10);
    if (url.empty()) return makeResult("internal_error", "", "empty url");

    jclass nhClass = env->FindClass("io/takamiya/mio/utils/NativeHttp");
    if (nhClass == nullptr) {
        env->ExceptionClear();
        return makeResult("internal_error", "", "NativeHttp missing");
    }
    jmethodID postMethod = env->GetStaticMethodID(nhClass, "post",
                                                  "(Ljava/lang/String;Ljava/lang/String;)[Ljava/lang/String;");
    if (postMethod == nullptr) {
        env->ExceptionClear();
        return makeResult("internal_error", "", "NativeHttp.post missing");
    }
    jstring urlJ = env->NewStringUTF(url.c_str());
    jstring bodyJ = env->NewStringUTF(payload.c_str());
    jobjectArray httpRes = (jobjectArray) env->CallStaticObjectMethod(nhClass, postMethod, urlJ, bodyJ);
    env->DeleteLocalRef(urlJ);
    env->DeleteLocalRef(bodyJ);
    env->DeleteLocalRef(nhClass);
    if (env->ExceptionCheck() || httpRes == nullptr) {
        env->ExceptionClear();
        return makeResult("http_error", "", "request failed");
    }
    jstring codeJ = (jstring) env->GetObjectArrayElement(httpRes, 0);
    jstring bodyRJ = (jstring) env->GetObjectArrayElement(httpRes, 1);
    std::string httpCode, respBody;
    if (codeJ != nullptr) {
        const char *c = env->GetStringUTFChars(codeJ, nullptr);
        if (c != nullptr) {
            httpCode = c;
            env->ReleaseStringUTFChars(codeJ, c);
        }
        env->DeleteLocalRef(codeJ);
    }
    if (bodyRJ != nullptr) {
        const char *c = env->GetStringUTFChars(bodyRJ, nullptr);
        if (c != nullptr) {
            respBody = c;
            env->ReleaseStringUTFChars(bodyRJ, c);
        }
        env->DeleteLocalRef(bodyRJ);
    }
    env->DeleteLocalRef(httpRes);

    // ── 响应判定 ──
    if (httpCode != "200") {
        return makeResult("server_error", "", httpCode.empty() ? "no http code" : httpCode);
    }
    std::string body = trimWs(respBody);
    long serverCode = 0;
    if (!jsonParseInt(body, "code", serverCode)) {
        return makeResult("parse_error", "", "code missing");
    }
    if (serverCode != 200) {
        return makeResult("rejected", "", std::to_string(serverCode));
    }
    std::string token;
    if (!jsonParseString(body, "token", token)) {
        return makeResult("parse_error", "", "token missing");
    }
    // ── RS256 验签 + 声明绑定：验签失败一律拒绝（严格 exp，无 24h 兜底） ──
    long exp = 0;
    if (!jwtVerifyRS256(token, appId, (long) versionCode, exp)) {
        return makeResult("rejected", "", "invalid_token");
    }
    // ── 校验通过：写 native 验证标记（进程内快照 + 持久文件，带 exp） ──
    g_verified = true;
    g_exp = exp;
    writeVerifyMarker(env, context, versionCode, rawDexShas, exp);
    return makeResult("ok", token, "");
}

// ── native 门禁检查：启动服务/入口前调用 ──
// ── native 门禁共享验证逻辑(返回状态码 + 错误信息) ──
//   status: 0=ok, 1=not_verified, 2=expired, 3=tampered, 4=internal_error
//   msgOut: 失败原因(成功时为空);指向内部静态缓冲,生命周期与本次调用绑定
static int checkVerifiedInternal(JNIEnv *env, jobject context, std::string &msgOut) {
    auto fail = [&](int code, const char *m) -> int {
        msgOut = m ? m : "";
        return code;
    };

    if (context == nullptr) return fail(4, "null context");

    long now = (long) std::time(nullptr);

    // 时间窗口缓存：最近一次全量校验通过距今 < 窗口时，直接放行，跳过
    // verify_marker 读取 + getApplicationInfo + APK/dex 全量哈希（大幅降低每次耗时）
    long lastOk = g_lastFullOkAt.load(std::memory_order_relaxed);
    if (lastOk > 0 && now - lastOk < kVerifyCacheWindowSecs) {
        msgOut.clear();
        return 0;
    }

    uint8_t storedFp[32];
    long fileVersion = 0;
    long fileExp = 0;
    int markerState = readVerifyMarker(env, context, storedFp, fileVersion, fileExp);
    if (markerState == 0) return fail(1, "no verified marker");
    if (markerState == -1) return fail(3, "marker tampered");

    jclass contextClass = env->FindClass("android/content/Context");
    if (contextClass == nullptr) {
        env->ExceptionClear();
        return fail(4, "no Context class");
    }
    jmethodID getAppInfo = env->GetMethodID(contextClass, "getApplicationInfo",
                                            "()Landroid/content/pm/ApplicationInfo;");
    jobject appInfo = env->CallObjectMethod(context, getAppInfo);
    env->DeleteLocalRef(contextClass);
    if (env->ExceptionCheck() || appInfo == nullptr) {
        env->ExceptionClear();
        return fail(4, "getApplicationInfo failed");
    }
    jclass aiClass = env->FindClass("android/content/pm/ApplicationInfo");
    jfieldID sourceDirField = env->GetFieldID(aiClass, "sourceDir", "Ljava/lang/String;");
    jstring sourceDirJ = (jstring) env->GetObjectField(appInfo, sourceDirField);
    env->DeleteLocalRef(aiClass);
    env->DeleteLocalRef(appInfo);
    if (sourceDirJ == nullptr) return fail(4, "no sourceDir");
    const char *apkC = env->GetStringUTFChars(sourceDirJ, nullptr);
    if (apkC == nullptr) return fail(4, "sourceDir utf8");
    std::string apkPath(apkC);
    env->ReleaseStringUTFChars(sourceDirJ, apkC);
    env->DeleteLocalRef(sourceDirJ);

    jclass duClass = env->FindClass("cn/panda/utils/library/DeviceInfoUtils");
    long curVersionCode = -1;
    if (duClass != nullptr) {
        jmethodID getVersion = env->GetStaticMethodID(duClass, "getAppVersionCode",
                                                      "(Landroid/content/Context;)J");
        if (getVersion == nullptr) {
            env->ExceptionClear();
        } else {
            curVersionCode = (long) env->CallStaticLongMethod(duClass, getVersion, context);
            if (env->ExceptionCheck()) {
                env->ExceptionClear();
                curVersionCode = -1;
            }
        }
    } else {
        env->ExceptionClear();
    }

    FILE *fp = fopen(apkPath.c_str(), "rb");
    if (fp == nullptr) {
        return fail(4, "cannot open apk");
    }
    std::vector<DexEntry> entries;
    if (!readZipEntries(fp, entries)) {
        fclose(fp);
        return fail(4, "zip parse failed");
    }
    std::sort(entries.begin(), entries.end(),
              [](const DexEntry &a, const DexEntry &b) { return dexIndex(a.name) < dexIndex(b.name); });
    std::vector<std::vector<uint8_t>> rawShas;
    uint8_t sha[32];
    for (size_t i = 0; i < entries.size(); ++i) {
        if (!hashDexEntry(fp, entries[i], sha)) {
            fclose(fp);
            return fail(4, "dex hash failed");
        }
        rawShas.emplace_back(sha, sha + 32);
    }
    fclose(fp);

    uint8_t curFp[32];
    computeDexFingerprint(rawShas, (uint32_t) curVersionCode, curFp);
    if (memcmp(curFp, storedFp, 32) != 0) {
        return fail(3, "apk mismatch");
    }

    if (now >= fileExp) {
        return fail(2, "marker expired");
    }
    // 全量校验通过：刷新缓存时间戳
    g_lastFullOkAt.store(now, std::memory_order_relaxed);
    msgOut.clear();
    return 0;
}

static const char *kStatusToStr(int status) {
    switch (status) {
        case 0: return "ok";
        case 1: return "not_verified";
        case 2: return "expired";
        case 3: return "tampered";
        default: return "internal_error";
    }
}

// 强制 finish Activity(JNI reflection,不走 Java 翻译,
// 确保 Java 层即使被 Smali patch 删掉 finish 指令,native 也能强制结束)
static void enforceGateFail(JNIEnv *env, jobject activity, int status, const char *msg) {
    if (activity == nullptr) return;

    jclass actClass = env->GetObjectClass(activity);
    if (actClass != nullptr) {
        jmethodID finishMid = env->GetMethodID(actClass, "finish", "()V");
        if (finishMid != nullptr) {
            env->CallVoidMethod(activity, finishMid);
        }
        env->DeleteLocalRef(actClass);
    }
    if (env->ExceptionCheck()) env->ExceptionClear();
}

// 返回 String[]{status, msg}
//   status: ok / not_verified / expired / tampered / internal_error
static jobjectArray
nativeCheckVerifiedImpl(
        JNIEnv *env,
        jclass /* clazz */,
        jobject context) {

    auto makeResult = [&](const char *status, const char *msg) -> jobjectArray {
        jclass sc = env->FindClass("java/lang/String");
        if (sc == nullptr) return nullptr;
        jobjectArray arr = env->NewObjectArray(2, sc, nullptr);
        env->DeleteLocalRef(sc);
        if (arr == nullptr) return nullptr;
        jstring s0 = env->NewStringUTF(status);
        jstring s1 = env->NewStringUTF(msg != nullptr ? msg : "");
        env->SetObjectArrayElement(arr, 0, s0);
        env->SetObjectArrayElement(arr, 1, s1);
        env->DeleteLocalRef(s0);
        env->DeleteLocalRef(s1);
        return arr;
    };

    std::string msg;
    int status = checkVerifiedInternal(env, context, msg);
    if (status == 0) {
        g_verified = true;
        // g_exp 在 internal 里没暴露,这里直接重读或省略;为简化不再写 g_exp
        return makeResult("ok", "");
    }
    return makeResult(kStatusToStr(status), msg.c_str());
}

// ── native 门禁执行入口:把决策权从 Java 移到 native ──
//   验证通过直接返回;验证失败在 native 内部 finish Activity
//   Java 端不再翻译 boolean,即使 Smali patch 删掉 if/finish 也无济于事
//   MainActivity 自身是写 marker 的入口,首次安装无 marker 必须放行,故白名单
static void
nativeEnforceGateImpl(
        JNIEnv *env,
        jclass /* clazz */,
        jobject activity) {

    // 调试开关关闭时直接放行，不做任何 marker 校验/强杀
    if (!kIntegrityEnforcementEnabled) return;

    if (activity == nullptr) return;

    // 排除 MainActivity(它本身就是写 marker 的入口,首次安装无 marker 须放行)
    jclass actClass = env->GetObjectClass(activity);
    if (actClass != nullptr) {
        jclass mainClass = env->FindClass("io/takamiya/mio/MainActivity");
        if (env->ExceptionCheck()) {
            env->ExceptionClear();
        } else if (mainClass != nullptr) {
            if (env->IsInstanceOf(activity, mainClass)) {
                env->DeleteLocalRef(mainClass);
                env->DeleteLocalRef(actClass);
                return;
            }
            env->DeleteLocalRef(mainClass);
        }
        env->DeleteLocalRef(actClass);
    } else if (env->ExceptionCheck()) {
        env->ExceptionClear();
    }

    std::string msg;
    int status = checkVerifiedInternal(env, activity, msg);
    if (status == 0) return;  // 验证通过,什么都不做,让 Activity 继续 onCreate
    enforceGateFail(env, activity, status, msg.c_str());
}

// ── Service 路径的门禁执行入口 ──
//   返回 true 表示验证通过;false 表示已被 native stopSelf
//   Service 必须从主线程调(否则 stopSelf 会抛 IllegalStateException)
//   即使 Java 层忽略返回值或被 Smali patch 删掉判断,native 监控线程也会兜底
static jboolean
nativeEnforceServiceImpl(
        JNIEnv *env,
        jclass /* clazz */,
        jobject service) {

    // 调试开关关闭时直接放行
    if (!kIntegrityEnforcementEnabled) return JNI_TRUE;

    if (service == nullptr) return JNI_FALSE;

    jobject ctx = service;
    std::string msg;
    int status = checkVerifiedInternal(env, ctx, msg);
    if (status == 0) return JNI_TRUE;

    // 静默 stopSelf（不弹 Toast，不给攻击者任何反馈）
    jclass svcClass = env->GetObjectClass(service);
    jmethodID stopSelfMid = env->GetMethodID(svcClass, "stopSelf", "()V");
    if (stopSelfMid != nullptr) {
        env->CallVoidMethod(service, stopSelfMid);
        if (env->ExceptionCheck()) env->ExceptionClear();
    }
    env->DeleteLocalRef(svcClass);
    return JNI_FALSE;
}

// ═══════════════════════════════════════════════════════════════════════════
// native 主动监控:so 加载即自启,周期性反射扫描进程内 Activity / Service
//   - marker 失效时把不在白名单的全部强 finish / stopSelf
//   - 无需 Java 触发,攻击者删 Smali 也关不掉(除非 Frida hook pthread)
//   - 白名单:MainActivity(首次安装必须放行做服务端校验),VerifyFailedActivity 类暂未实现
// ═══════════════════════════════════════════════════════════════════════════

static pthread_t g_monitorThread;
static volatile bool g_monitorRunning = false;

// 宽限期:JNI_OnLoad 自动启动 monitor 后,给 app 留出足够时间完成
// nativeVerifyServer 写入 verify_marker。宽限期内只做反调试,不执行
// marker 强杀,避免首次运行(无 marker)被误杀。
constexpr int64_t kGraceSeconds = 30;
static int64_t g_graceUntilNanos = 0;

// 单调时钟,不受系统改表影响
static int64_t monotonicNanos() {
    struct timespec ts;
    clock_gettime(CLOCK_MONOTONIC, &ts);
    return (int64_t)ts.tv_sec * 1000000000LL + ts.tv_nsec;
}

// 白名单类名(子串匹配)
static const char *kWhitelistSubstr[] = {
        "io/takamiya/mio/MainActivity",       // 启动页:首次安装无 marker 须放行
        nullptr};

// 反射拿到 ActivityThread 的 services list
static jobjectArray getCurrentServices(JNIEnv *env) {
    jclass atClass = env->FindClass("android/app/ActivityThread");
    if (atClass == nullptr) {
        env->ExceptionClear();
        return nullptr;
    }
    jmethodID currentAT = env->GetStaticMethodID(atClass, "currentActivityThread",
                                                 "()Landroid/app/ActivityThread;");
    if (currentAT == nullptr) {
        env->ExceptionCheck();
        env->DeleteLocalRef(atClass);
        return nullptr;
    }
    jobject at = env->CallStaticObjectMethod(atClass, currentAT);
    env->DeleteLocalRef(atClass);
    if (env->ExceptionCheck() || at == nullptr) {
        env->ExceptionClear();
        return nullptr;
    }

    // getServices() 返回 ArrayList<Service>
    jclass listClass = env->FindClass("java/util/ArrayList");
    jmethodID getServices = env->GetMethodID(env->GetObjectClass(at), "getServices",
                                             "()Ljava/util/ArrayList;");
    if (getServices == nullptr) {
        env->ExceptionClear();
        env->DeleteLocalRef(at);
        return nullptr;
    }
    jobject listObj = env->CallObjectMethod(at, getServices);
    env->DeleteLocalRef(at);
    if (env->ExceptionCheck() || listObj == nullptr) {
        env->ExceptionClear();
        return nullptr;
    }

    // 转 Object[]
    jmethodID toArray = env->GetMethodID(listClass, "toArray", "()[Ljava/lang/Object;");
    jobjectArray arr = (jobjectArray) env->CallObjectMethod(listObj, toArray);
    env->DeleteLocalRef(listObj);
    env->DeleteLocalRef(listClass);
    if (env->ExceptionCheck() || arr == nullptr) {
        env->ExceptionClear();
        return nullptr;
    }
    return arr;
}

static jobjectArray getCurrentActivities(JNIEnv *env) {
    jclass atClass = env->FindClass("android/app/ActivityThread");
    if (atClass == nullptr) {
        env->ExceptionClear();
        return nullptr;
    }
    jmethodID currentAT = env->GetStaticMethodID(atClass, "currentActivityThread",
                                                 "()Landroid/app/ActivityThread;");
    jobject at = env->CallStaticObjectMethod(atClass, currentAT);
    env->DeleteLocalRef(atClass);
    if (env->ExceptionCheck() || at == nullptr) {
        env->ExceptionClear();
        return nullptr;
    }
    jmethodID getActivities = env->GetMethodID(env->GetObjectClass(at), "getActivities",
                                               "()Ljava/util/ArrayList;");
    if (getActivities == nullptr) {
        env->ExceptionClear();
        env->DeleteLocalRef(at);
        return nullptr;
    }
    jobject listObj = env->CallObjectMethod(at, getActivities);
    env->DeleteLocalRef(at);
    if (env->ExceptionCheck() || listObj == nullptr) {
        env->ExceptionClear();
        return nullptr;
    }
    jclass listClass = env->FindClass("java/util/ArrayList");
    jmethodID toArray = env->GetMethodID(listClass, "toArray", "()[Ljava/lang/Object;");
    jobjectArray arr = (jobjectArray) env->CallObjectMethod(listObj, toArray);
    env->DeleteLocalRef(listObj);
    env->DeleteLocalRef(listClass);
    if (env->ExceptionCheck() || arr == nullptr) {
        env->ExceptionClear();
        return nullptr;
    }
    return arr;
}

static bool isWhitelisted(const char *className) {
    if (className == nullptr) return false;
    for (int i = 0; kWhitelistSubstr[i] != nullptr; ++i) {
        if (strstr(className, kWhitelistSubstr[i]) != nullptr) return true;
    }
    return false;
}

// 通过 ActivityThread.currentActivityThread().getApplication() 拿 application context
static jobject getApplicationContext(JNIEnv *env) {
    jclass atClass = env->FindClass("android/app/ActivityThread");
    if (atClass == nullptr) {
        env->ExceptionClear();
        return nullptr;
    }
    jmethodID currentAT = env->GetStaticMethodID(atClass, "currentActivityThread",
                                                 "()Landroid/app/ActivityThread;");
    jobject at = env->CallStaticObjectMethod(atClass, currentAT);
    env->DeleteLocalRef(atClass);
    if (env->ExceptionCheck() || at == nullptr) {
        env->ExceptionClear();
        return nullptr;
    }
    jmethodID getApp = env->GetMethodID(env->GetObjectClass(at), "getApplication",
                                        "()Landroid/app/Application;");
    jobject app = env->CallObjectMethod(at, getApp);
    env->DeleteLocalRef(at);
    if (env->ExceptionCheck() || app == nullptr) {
        env->ExceptionClear();
        return nullptr;
    }
    return app;
}

// 监控线程主循环
static void *monitorProc(void * /* arg */) {
    while (g_monitorRunning) {
        if (g_jvm == nullptr) {
            sleep(1);
            continue;
        }
        JavaVM *vm = g_jvm;
        JNIEnv *env = nullptr;
        if (vm->AttachCurrentThread(&env, nullptr) != JNI_OK || env == nullptr) {
            sleep(1);
            continue;
        }

        jobject appCtx = getApplicationContext(env);
        if (appCtx == nullptr) {
            vm->DetachCurrentThread();
            sleep(1);
            continue;
        }

        // 每轮检测调试器和 Frida(宽限期内也要执行,攻击检测永不放宽)
        if (detectAttack()) {
            runSecurityResponse(env, appCtx);
        }

        std::string msg;
        int status = checkVerifiedInternal(env, appCtx, msg);
        env->DeleteLocalRef(appCtx);

        // 宽限期内未写入 marker 属正常流程,不强杀;宽限期后才开始强制执行
        bool inGrace = (monotonicNanos() < g_graceUntilNanos);

        if (status != 0 && !inGrace) {
            // marker 失效 → 遍历 Activity / Service,非白名单的强杀
            // 强杀 Service
            jobjectArray services = getCurrentServices(env);
            if (services != nullptr) {
                jsize n = env->GetArrayLength(services);
                for (jsize i = 0; i < n; ++i) {
                    jobject svc = env->GetObjectArrayElement(services, i);
                    if (svc == nullptr) continue;
                    jclass svcClass = env->GetObjectClass(svc);
                    jmethodID getNameMid = env->GetMethodID(svcClass, "getName",
                                                            "()Ljava/lang/String;");
                    jstring nameJ = (jstring) env->CallObjectMethod(svcClass, getNameMid);
                    const char *nameC = env->GetStringUTFChars(nameJ, nullptr);
                    bool whitelisted = isWhitelisted(nameC);
                    env->ReleaseStringUTFChars(nameJ, nameC);
                    env->DeleteLocalRef(nameJ);
                    env->DeleteLocalRef(svcClass);

                    if (!whitelisted) {
                        jmethodID stopSelf = env->GetMethodID(svcClass, "stopSelf", "()V");
                        if (stopSelf != nullptr) {
                            env->CallVoidMethod(svc, stopSelf);
                            if (env->ExceptionCheck()) env->ExceptionClear();
                        }
                    }
                    env->DeleteLocalRef(svc);
                }
                env->DeleteLocalRef(services);
            }

            // 强 finish Activity
            jobjectArray activities = getCurrentActivities(env);
            if (activities != nullptr) {
                jsize n = env->GetArrayLength(activities);
                for (jsize i = 0; i < n; ++i) {
                    jobject act = env->GetObjectArrayElement(activities, i);
                    if (act == nullptr) continue;
                    jclass actClass = env->GetObjectClass(act);
                    jmethodID getNameMid = env->GetMethodID(actClass, "getName",
                                                            "()Ljava/lang/String;");
                    jstring nameJ = (jstring) env->CallObjectMethod(actClass, getNameMid);
                    const char *nameC = env->GetStringUTFChars(nameJ, nullptr);
                    bool whitelisted = isWhitelisted(nameC);
                    env->ReleaseStringUTFChars(nameJ, nameC);
                    env->DeleteLocalRef(nameJ);

                    if (!whitelisted) {
                        jmethodID finish = env->GetMethodID(actClass, "finish", "()V");
                        if (finish != nullptr) {
                            env->CallVoidMethod(act, finish);
                            if (env->ExceptionCheck()) env->ExceptionClear();
                        }
                    }
                    env->DeleteLocalRef(actClass);
                    env->DeleteLocalRef(act);
                }
                env->DeleteLocalRef(activities);
            }
        }

        vm->DetachCurrentThread();
        sleep(1);  // 1s 扫描一次
    }
    return nullptr;
}

// 全局 JavaVM(在 JNI_OnLoad 设置)
// 注:g_jvm 已在文件顶部 forward-declare,这里不再重复定义

// 绕过 Android 9+ hidden API 限制(允许 reflection 调 ActivityThread 等)
// 在 JNI_OnLoad 调用一次即可,后续该线程所有 reflection 不受限
static void exemptHiddenApi(JNIEnv *env) {
    jclass vmClass = env->FindClass("dalvik/system/VMRuntime");
    if (vmClass == nullptr) {
        env->ExceptionClear();
        return;
    }
    jmethodID getRuntime = env->GetStaticMethodID(vmClass, "getRuntime",
                                                   "()Ldalvik/system/VMRuntime;");
    if (getRuntime == nullptr) {
        env->ExceptionClear();
        env->DeleteLocalRef(vmClass);
        return;
    }
    jobject runtime = env->CallStaticObjectMethod(vmClass, getRuntime);
    env->DeleteLocalRef(vmClass);
    if (env->ExceptionCheck() || runtime == nullptr) {
        env->ExceptionClear();
        return;
    }
    jmethodID setExemptions = env->GetMethodID(env->GetObjectClass(runtime),
                                               "setHiddenApiExemptions",
                                               "([Ljava/lang/String;)V");
    if (setExemptions == nullptr) {
        env->ExceptionClear();
        env->DeleteLocalRef(runtime);
        return;
    }
    jclass strClass = env->FindClass("java/lang/String");
    jobjectArray exemptions = env->NewObjectArray(1, strClass, nullptr);
    env->DeleteLocalRef(strClass);
    jstring L = env->NewStringUTF("L");
    env->SetObjectArrayElement(exemptions, 0, L);
    env->DeleteLocalRef(L);
    env->CallVoidMethod(runtime, setExemptions, exemptions);
    env->DeleteLocalRef(exemptions);
    env->DeleteLocalRef(runtime);
    if (env->ExceptionCheck()) env->ExceptionClear();
}

// ── RegisterNatives：动态注册 JNI 方法，不出现在 .so 导出表 ──
static void ensureMonitorStarted();
static void nativeStartIntegrityMonitorImpl(JNIEnv *, jclass);
static const JNINativeMethod gIntegrityMethods[] = {
    {"nativeEnforceGate",         "(Landroid/app/Activity;)V",                    (void*)nativeEnforceGateImpl},
    {"nativeEnforceService",      "(Landroid/app/Service;)Z",                     (void*)nativeEnforceServiceImpl},
    {"nativeStartIntegrityMonitor","()V",                                          (void*)nativeStartIntegrityMonitorImpl},
    {"nativeVerifyServer",        "(Landroid/content/Context;)[Ljava/lang/String;",(void*)nativeVerifyServerImpl},
    {"nativeCheckVerified",       "(Landroid/content/Context;)[Ljava/lang/String;",(void*)nativeCheckVerifiedImpl},
    {"apkIntegrityHash",          "(Ljava/lang/String;)Ljava/lang/String;",       (void*)apkIntegrityHashImpl},
    {"dexIntegrityHashList",      "(Ljava/lang/String;)[Ljava/lang/String;",      (void*)dexIntegrityHashListImpl},
};

extern "C" JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void * /* reserved */) {
    g_jvm = vm;
    JNIEnv *env = nullptr;
    if (vm->GetEnv((void **) &env, JNI_VERSION_1_6) == JNI_OK && env != nullptr) {
        exemptHiddenApi(env);

        // 反调试检测（调试开关关闭时跳过，避免本地调试被强杀）
        if (kIntegrityEnforcementEnabled && detectAttack()) {
            // JNI_OnLoad 没有 Context，通过 ActivityThread.currentApplication() 获取
            jobject appCtx = nullptr;
            jclass atClass = env->FindClass("android/app/ActivityThread");
            if (atClass != nullptr) {
                jmethodID currentApp = env->GetStaticMethodID(atClass, "currentApplication",
                                                              "()Landroid/app/Application;");
                if (currentApp != nullptr) {
                    appCtx = env->CallStaticObjectMethod(atClass, currentApp);
                    if (env->ExceptionCheck()) { env->ExceptionClear(); appCtx = nullptr; }
                }
                env->DeleteLocalRef(atClass);
            } else {
                env->ExceptionClear();
            }
            runSecurityResponse(env, appCtx);
            if (appCtx != nullptr) env->DeleteLocalRef(appCtx);
        }

        // 动态注册 JNI 方法
        jclass cls = env->FindClass("io/takamiya/mio/utils/ApkIntegrity");
        if (cls != nullptr) {
            env->RegisterNatives(cls, gIntegrityMethods,
                                sizeof(gIntegrityMethods) / sizeof(gIntegrityMethods[0]));
            env->DeleteLocalRef(cls);
        } else {
            env->ExceptionClear();
        }

        // 自动启动监控(不需要 Java 触发,Smali 无法关闭)
        ensureMonitorStarted();
    }
    return JNI_VERSION_1_6;
}

// 幂等启动:重复调用只起一次
static void ensureMonitorStarted() {
    // 调试开关关闭时不启动监控线程（否则宽限期后仍会强杀 Activity/Service）
    if (!kIntegrityEnforcementEnabled) return;
    if (g_monitorRunning) return;
    g_monitorRunning = true;
    g_graceUntilNanos = monotonicNanos() + (int64_t)kGraceSeconds * 1000000000LL;
    pthread_create(&g_monitorThread, nullptr, monitorProc, nullptr);
}

static void nativeStartIntegrityMonitorImpl(JNIEnv * /* env */, jclass /* clazz */) {
    ensureMonitorStarted();
}
