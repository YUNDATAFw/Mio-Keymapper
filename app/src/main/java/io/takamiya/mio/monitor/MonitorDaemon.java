package io.takamiya.mio.monitor;

import android.os.IBinder;
import android.os.RemoteException;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.File;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import cn.panda.utils.library.AppLogger;

/**
 * 外接键鼠监听 UserService：以 shell 权限跑常驻 {@code getevent}，逐行解析
 * Linux input 事件，过滤出按键事件后经 oneway 回调推给宿主。
 * <p>
 * 结构要点（移植自 sth-key 项目的实战教训）：
 * <ul>
 *   <li><b>一个设备一个 getevent 进程</b>——getevent 的 [device] 参数是单数，
 *       多设备一次传入会打印 usage 并退出；单设备挂掉也不影响其它设备。</li>
 *   <li>命令头 {@code cat /proc/self/stat; exec …} 让进程<b>自报 pid</b>，
 *       用于探活复核；cat 必须在 exec 之前（exec 会替换掉 shell）。</li>
 *   <li>{@code 2>&1} 必须带上——getevent 的失败原因（usage、could not open）
 *       走 stderr，不合并就只剩"进程退出了"一句废话。</li>
 *   <li>探活与正式读取<b>必须复用同一个 BufferedReader</b>——它会预读，
 *       换实例会丢掉缓冲里已读到的事件。</li>
 *   <li>探活期读到的非事件行作为 <b>evidence</b> 保留，失败时一起上报。</li>
 * </ul>
 * 注意：设备清单在启动时快照，运行中热插拔的新设备不会被监听（已知限制）。
 * <p>
 * 与 ShellService/GamepadDaemon 一样，不进 AndroidManifest——Shizuku 的
 * ServiceStarter 直接把实例强转为 IBinder。
 */
public class MonitorDaemon extends IMonitorService.Stub {

    private static final String TAG = "MonitorDaemon";

    public static final int STATE_IDLE = 0;
    public static final int STATE_STARTING = 1;
    public static final int STATE_RUNNING = 2;
    public static final int STATE_FAILED = 3;

    private static final int EV_KEY = 0x01;
    private static final int VALUE_UP = 0;
    private static final int VALUE_DOWN = 1;
    /** evdev KEY_* + BTN_* 的取值范围（linux/input-event-codes.h：1..0x2ff）。 */
    private static final int MIN_KEY_CODE = 1;
    private static final int MAX_KEY_CODE = 0x2FF;

    private static final String LIST_COMMAND = "ls /dev/input/event* 2>/dev/null";
    private static final Pattern DEVICE_PATTERN = Pattern.compile("/dev/input/event\\d+");
    private static final String GETEVENT = "/system/bin/getevent";

    /** 读设备名清单（回环防护：排除虚拟手柄等自造设备）。 */
    private static final String NAMES_COMMAND = "cat /proc/bus/input/devices 2>/dev/null";
    private static final Pattern PROC_NAME_PATTERN = Pattern.compile("N: Name=\"(.*)\"");
    private static final Pattern PROC_HANDLERS_PATTERN = Pattern.compile("H: Handlers=(.*)");
    /**
     * 设备名黑名单关键词（小写匹配）。Mio 的按键注入（Shizuku InputManager /
     * IME）不写内核 evdev，getevent 天然看不到，不构成回环；唯一回环源是
     * GamepadDaemon 的 uinput 虚拟手柄（默认名 "Virtual Xbox Gamepad"），
     * 它会注册进内核输入子系统出现在 /dev/input/event*，必须排除。
     */
    private static final String[] BLACKLIST_NAME_TOKENS = {"virtual", "uinput"};

    private static final long HANDSHAKE_TIMEOUT_MS = 1500L;
    private static final long PROBE_WINDOW_MS = 600L;
    private static final long POLL_INTERVAL_MS = 20L;
    private static final int MAX_CONCURRENT_STARTS = 6;
    private static final int MAX_EVIDENCE_CHARS = 400;

    private final AtomicBoolean running = new AtomicBoolean(false);

    private volatile IMonitorCallback callback;
    private volatile IBinder.DeathRecipient callbackDeath;
    private volatile int state = STATE_IDLE;
    private volatile int deviceCount;
    private volatile String lastError = "";

    private final List<DeviceReader> readers =
            Collections.synchronizedList(new ArrayList<DeviceReader>());

    public MonitorDaemon() {
        AppLogger.i(TAG, "MonitorDaemon started, pid=" + android.os.Process.myPid());
        try {
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                AppLogger.w(TAG, "shutdown hook: killing getevent children");
                killAllReaders();
            }));
        } catch (Throwable t) {
            AppLogger.w(TAG, "failed to register shutdown hook: " + t);
        }
    }

    @Override
    public void registerCallback(IMonitorCallback cb) {
        IBinder.DeathRecipient oldDeath = callbackDeath;
        IMonitorCallback old = callback;
        if (old != null && oldDeath != null) {
            try {
                old.asBinder().unlinkToDeath(oldDeath, 0);
            } catch (Throwable ignored) {
            }
        }
        callback = cb;
        callbackDeath = null;
        if (cb == null) {
            return;
        }
        final IBinder.DeathRecipient death = () -> {
            AppLogger.w(TAG, "callback binder died (宿主进程退出?), stopping monitor");
            callback = null;
            callbackDeath = null;
            stopMonitor();
        };
        try {
            cb.asBinder().linkToDeath(death, 0);
            callbackDeath = death;
        } catch (RemoteException e) {
            AppLogger.e(TAG, "registerCallback: linkToDeath failed", e);
        }
    }

    @Override
    public boolean startMonitor() {
        if (!running.compareAndSet(false, true)) {
            AppLogger.d(TAG, "startMonitor: already running");
            return true;
        }
        readers.clear(); // 防御：清掉上一轮竞态可能残留的条目
        lastError = "";
        setState(STATE_STARTING, "正在扫描输入设备…");
        Thread starter = new Thread(this::startAsync, "MonitorStart");
        starter.setDaemon(true);
        starter.start();
        return true;
    }

    private void startAsync() {
        try {
            List<String> devices = scanDevices();
            if (devices.isEmpty()) {
                fail("没有找到输入设备（ls 未列出 /dev/input/event*）");
                return;
            }
            AppLogger.i(TAG, "启动监听，设备 " + devices.size() + " 个");
            List<DeviceReader> ready = launchAll(devices);
            if (!running.get()) {
                // 探活期间被 stop：新 reader 可能在 killAllReaders 之后才入列，补清
                for (DeviceReader r : ready) {
                    r.stop();
                    readers.remove(r);
                }
                return;
            }
            if (ready.isEmpty()) {
                fail("所有 " + devices.size() + " 个输入设备都无法读取（原因见日志）");
                return;
            }
            deviceCount = ready.size();
            String detail = "正在监听 " + ready.size() + " 个输入设备"
                    + (ready.size() < devices.size() ? "（共 " + devices.size() + " 个，部分不可读）" : "");
            setState(STATE_RUNNING, detail);
            AppLogger.i(TAG, detail);
        } catch (Throwable t) {
            AppLogger.e(TAG, "startAsync failed", t);
            fail("启动异常：" + t.getClass().getSimpleName() + " " + t.getMessage());
        }
    }

    /** 全部设备并行启动（设备间相互独立，串行会把探活窗口累加成秒级）。 */
    private List<DeviceReader> launchAll(List<String> devices) {
        ExecutorService pool = Executors.newFixedThreadPool(MAX_CONCURRENT_STARTS);
        try {
            List<Future<DeviceReader>> futures = new ArrayList<>();
            for (final String device : devices) {
                futures.add(pool.submit((Callable<DeviceReader>) () -> launchOne(device)));
            }
            List<DeviceReader> ready = new ArrayList<>();
            for (Future<DeviceReader> f : futures) {
                DeviceReader r;
                try {
                    r = f.get(HANDSHAKE_TIMEOUT_MS + PROBE_WINDOW_MS + 3000L, TimeUnit.MILLISECONDS);
                } catch (Throwable t) {
                    r = null;
                }
                if (r != null) {
                    ready.add(r);
                }
            }
            return ready;
        } finally {
            pool.shutdownNow();
        }
    }

    /** 起一个设备：依次尝试参数变体，成功则返回已探活的 reader。 */
    private DeviceReader launchOne(String device) {
        List<String> problems = new ArrayList<>();
        StringBuilder evidence = new StringBuilder();
        // 顺序：先不带 -q（输出带设备前缀，最基本、最不可能出问题），失败再试 quiet。
        String[] variants = new String[]{
                "cat /proc/self/stat; exec " + GETEVENT + " 2>&1 " + device,
                "cat /proc/self/stat; exec " + GETEVENT + " -q 2>&1 " + device,
        };
        for (int i = 0; i < variants.length && running.get(); i++) {
            String label = (i == 0) ? "默认参数" : "quiet 参数";
            Process process = null;
            BufferedReader input = null;
            try {
                process = new ProcessBuilder("sh", "-c", variants[i])
                        .redirectErrorStream(true)
                        .start();
                input = new BufferedReader(new InputStreamReader(process.getInputStream()));
                Integer pid = readHandshakePid(input);
                Probe probe = probeAlive(input, pid);
                if (probe.alive) {
                    if (!running.get()) {
                        closeQuietly(input);
                        process.destroy();
                        return null;
                    }
                    if (i > 0) {
                        AppLogger.w(TAG, device + " 使用「" + label + "」参数（默认那组不可用）");
                    }
                    DeviceReader reader = new DeviceReader(device, process, input);
                    reader.evidence = probe.evidence.toString();
                    readers.add(reader);
                    reader.start();
                    return reader;
                }
                problems.add(label + "：" + probe.reason);
                if (probe.evidence.length() > 0) {
                    evidence.append(probe.evidence);
                }
            } catch (Throwable t) {
                problems.add(label + "：启动失败（" + t.getMessage() + "）");
            }
            closeQuietly(input);
            if (process != null) {
                process.destroy();
            }
        }
        AppLogger.w(TAG, device + " 启动失败：" + String.join("，", problems)
                + (evidence.length() > 0 ? "，输出：" + evidence : ""));
        return null;
    }

    /**
     * 读握手行拿 pid（命令头 cat /proc/self/stat 的输出："{pid} (comm) …"）。
     * 在<b>当前线程</b>轮询 ready() + 超时，不另开线程——另开线程读超时后它
     * 仍阻塞在 readLine 上，会与探活读取在同一 BufferedReader 上竞态。
     */
    private Integer readHandshakePid(BufferedReader input) {
        long deadline = System.currentTimeMillis() + HANDSHAKE_TIMEOUT_MS;
        try {
            while (System.currentTimeMillis() < deadline) {
                if (input.ready()) {
                    String line = input.readLine();
                    if (line == null) {
                        return null;
                    }
                    String first = line.trim();
                    int sp = first.indexOf(' ');
                    String token = sp > 0 ? first.substring(0, sp) : first;
                    int pid = Integer.parseInt(token);
                    return pid > 0 ? pid : null;
                }
                Thread.sleep(POLL_INTERVAL_MS);
            }
            AppLogger.w(TAG, "握手超时（" + HANDSHAKE_TIMEOUT_MS + "ms 内没读到 pid）");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Throwable ignored) {
            // 行首不是 pid（format 异常/读取中断）→ 退化为"只看输出"的探活
        }
        return null;
    }

    /**
     * 探活窗口：窗口内读到 EOF 即为硬失败；窗口结束时安静但还活着即成功。
     * pid 可用时再问一次 /proc 做确定性复核。
     */
    private Probe probeAlive(BufferedReader input, Integer pid) {
        long deadline = System.currentTimeMillis() + PROBE_WINDOW_MS;
        StringBuilder collected = new StringBuilder();
        do {
            try {
                if (!input.ready()) {
                    Thread.sleep(POLL_INTERVAL_MS);
                    continue;
                }
                String line = input.readLine();
                if (line == null) {
                    // EOF：流已结束，进程必然没了
                    return new Probe(false, "启动后立刻结束（" + evidenceOf(collected) + "）", collected);
                }
                if (parse(line) == null && collected.length() < MAX_EVIDENCE_CHARS) {
                    // 非事件行 = getevent 的报错/usage，留作证据
                    collected.append(line.trim()).append(" | ");
                }
            } catch (Throwable t) {
                return new Probe(false, "读取中断（" + t.getClass().getSimpleName() + "）", collected);
            }
        } while (System.currentTimeMillis() < deadline);

        if (pid != null && !new File("/proc/" + pid).exists()) {
            return new Probe(false, "进程已退出（pid=" + pid + " 不在 /proc）", collected);
        }
        return new Probe(true, "存活", collected);
    }

    private String evidenceOf(StringBuilder collected) {
        String s = collected.toString().trim();
        return s.isEmpty() ? "无输出" : s;
    }

    /** 扫描设备节点（shell 身份），排除虚拟手柄等黑名单设备（回环防护）。 */
    private List<String> scanDevices() throws Exception {
        Process process = new ProcessBuilder("sh", "-c", LIST_COMMAND)
                .redirectErrorStream(true)
                .start();
        List<String> devices = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (DEVICE_PATTERN.matcher(line).matches()) {
                    devices.add(line);
                }
            }
        }
        process.waitFor(2, TimeUnit.SECONDS);

        Map<String, String> names = readDeviceNames();
        List<String> filtered = new ArrayList<>();
        for (String device : devices) {
            String node = device.substring(device.lastIndexOf('/') + 1); // "event3"
            String name = names.get(node);
            if (name != null && isBlacklistedName(name)) {
                AppLogger.i(TAG, "scanDevices: 排除黑名单设备 " + device + " name=" + name);
                continue;
            }
            filtered.add(device);
        }
        // names 为空（/proc 解析失败）时不做任何过滤 = fail-open，保留全部设备
        if (names.isEmpty() && !devices.isEmpty()) {
            AppLogger.w(TAG, "scanDevices: 设备名解析失败，跳过黑名单过滤");
        }
        Collections.sort(filtered);
        return filtered;
    }

    /**
     * 解析 {@code /proc/bus/input/devices}：event 节点名（"event3"）→ 设备名。
     * 解析失败返回空表（调用方 fail-open：不做过滤）。
     */
    private Map<String, String> readDeviceNames() {
        Map<String, String> result = new HashMap<>();
        try {
            Process process = new ProcessBuilder("sh", "-c", NAMES_COMMAND)
                    .redirectErrorStream(true)
                    .start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                String name = null;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("I:")) {
                        name = null; // 新设备块开始
                        continue;
                    }
                    Matcher nm = PROC_NAME_PATTERN.matcher(line);
                    if (nm.matches()) {
                        name = nm.group(1);
                        continue;
                    }
                    Matcher hm = PROC_HANDLERS_PATTERN.matcher(line);
                    if (hm.matches() && name != null) {
                        for (String token : hm.group(1).trim().split("\\s+")) {
                            if (token.startsWith("event")) {
                                result.put(token, name);
                            }
                        }
                    }
                }
            }
            process.waitFor(2, TimeUnit.SECONDS);
        } catch (Exception e) {
            AppLogger.w(TAG, "readDeviceNames failed: " + e);
            result.clear();
        }
        return result;
    }

    private static boolean isBlacklistedName(String name) {
        String lower = name.toLowerCase(java.util.Locale.ROOT);
        for (String token : BLACKLIST_NAME_TOKENS) {
            if (lower.contains(token)) return true;
        }
        return false;
    }

    private void fail(String message) {
        running.compareAndSet(true, false);
        killAllReaders();
        deviceCount = 0;
        lastError = message;
        AppLogger.e(TAG, message);
        setState(STATE_FAILED, message);
    }

    /** 某个设备的读取线程结束；仅当已无任何设备在读时才算整体失败。 */
    private void onDeviceDied(DeviceReader reader) {
        readers.remove(reader);
        AppLogger.w(TAG, reader.device + " 的读取结束，期间收到 " + reader.events + " 个事件");
        if (readers.isEmpty() && running.get()) {
            fail(reader.device + " 的读取结束，且已无其它设备在读取"
                    + (reader.evidence.isEmpty() ? "" : "，它的输出：" + reader.evidence));
        }
    }

    @Override
    public void stopMonitor() {
        if (!running.getAndSet(false)) {
            killAllReaders(); // 没在跑也清一遍，保证无残留子进程
            return;
        }
        AppLogger.i(TAG, "停止监听");
        killAllReaders();
        deviceCount = 0;
        setState(STATE_IDLE, "已停止");
    }

    private void killAllReaders() {
        List<DeviceReader> snapshot;
        synchronized (readers) {
            snapshot = new ArrayList<>(readers);
            readers.clear();
        }
        for (DeviceReader r : snapshot) {
            r.stop();
        }
    }

    @Override
    public int getState() {
        return state;
    }

    @Override
    public int getDeviceCount() {
        return deviceCount;
    }

    @Override
    public String getLastError() {
        return lastError;
    }

    @Override
    public void destroy() {
        stopMonitor();
        registerCallback(null);
    }

    private void setState(int newState, String detail) {
        state = newState;
        IMonitorCallback cb = callback;
        if (cb == null) {
            return;
        }
        try {
            cb.onMonitorState(newState, detail);
        } catch (Throwable t) {
            AppLogger.w(TAG, "onMonitorState failed: " + t);
        }
    }

    /**
     * 解析一行 getevent 输出并过滤。
     * 取末三个空白分隔字段为 type/code/value（十六进制）；type 字段可能带
     * {@code /dev/input/event3:} 前缀，按最后一个冒号剥离。只放行：
     * EV_KEY + KEY/BTN 范围 + value 为 0/1（丢弃 EV_SYN、EV_REL、value=2 重复）。
     *
     * @return {code, value} 或 null（非按键事件行）
     */
    static long[] parse(String line) {
        String trimmed = line.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        String[] parts = trimmed.split("\\s+");
        if (parts.length < 3) {
            return null;
        }
        String valueToken = parts[parts.length - 1];
        String codeToken = parts[parts.length - 2];
        String typeToken = parts[parts.length - 3];
        int colon = typeToken.lastIndexOf(':');
        if (colon >= 0) {
            typeToken = typeToken.substring(colon + 1);
        }
        long type = parseHex(typeToken);
        long code = parseHex(codeToken);
        long value = parseHex(valueToken);
        if (type != EV_KEY) {
            return null;
        }
        // 负值以 ffffffff 形式打印（32 位补码），EV_KEY 的 value 不会是负数
        if (value > Integer.MAX_VALUE) {
            return null;
        }
        if (value != VALUE_UP && value != VALUE_DOWN) {
            return null;
        }
        if (code < MIN_KEY_CODE || code > MAX_KEY_CODE) {
            return null;
        }
        return new long[]{code, value};
    }

    private static long parseHex(String token) {
        try {
            return Long.parseLong(token.trim(), 16);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static void closeQuietly(Closeable c) {
        if (c == null) {
            return;
        }
        try {
            c.close();
        } catch (Throwable ignored) {
        }
    }

    private static final class Probe {
        final boolean alive;
        final String reason;
        final StringBuilder evidence;

        Probe(boolean alive, String reason, StringBuilder evidence) {
            this.alive = alive;
            this.reason = reason;
            this.evidence = evidence;
        }
    }

    /** 一个设备的进程 + 读取线程。停进程是唯一能唤醒阻塞 read 的办法。 */
    private final class DeviceReader {
        final String device;
        final Process process;
        final BufferedReader input;
        final AtomicBoolean stopped = new AtomicBoolean(false);
        volatile Thread thread;
        volatile String evidence = "";
        volatile long events;

        DeviceReader(String device, Process process, BufferedReader input) {
            this.device = device;
            this.process = process;
            this.input = input;
        }

        void start() {
            Thread t = new Thread(this::readLoop,
                    "MonitorGetevent-" + device.substring(device.lastIndexOf('/') + 1));
            t.setDaemon(true);
            thread = t;
            t.start();
        }

        void stop() {
            if (!stopped.compareAndSet(false, true)) {
                return;
            }
            // 先杀子进程：read 是阻塞的，不打断线程醒不过来
            try {
                process.destroy();
            } catch (Throwable ignored) {
            }
            closeQuietly(input);
            Thread t = thread;
            if (t != null) {
                t.interrupt();
            }
        }

        private void readLoop() {
            boolean externallyStopped;
            try {
                String line;
                while (running.get() && !stopped.get()) {
                    line = input.readLine();
                    if (line == null) {
                        break;
                    }
                    long[] ev = parse(line);
                    if (ev == null) {
                        continue;
                    }
                    events++;
                    IMonitorCallback cb = callback;
                    if (cb != null) {
                        try {
                            cb.onKeyEvent((int) ev[0], (int) ev[1]);
                        } catch (Throwable t) {
                            // oneway 回调失败多半是宿主已死，death recipient 会兜底
                            AppLogger.w(TAG, "onKeyEvent failed: " + t);
                        }
                    }
                }
            } catch (Throwable t) {
                if (!stopped.get()) {
                    AppLogger.w(TAG, device + " 读取中断：" + t.getClass().getSimpleName());
                }
            } finally {
                // 自然结束（EOF/异常）才算设备死亡；外部 stopMonitor 已置位则不回调
                externallyStopped = stopped.get();
                stop();
                if (running.get() && !externallyStopped) {
                    onDeviceDied(this);
                }
            }
        }
    }
}
