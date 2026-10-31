package io.takamiya.mio.terminal.commands;

import android.os.Handler;
import android.os.Looper;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import io.takamiya.mio.dsl.tooling.DslBuildTool;
import io.takamiya.mio.terminal.command.CommandCallback;
import io.takamiya.mio.terminal.command.CommandResult;
import io.takamiya.mio.terminal.command.CommandState;
import io.takamiya.mio.terminal.command.ICommand;
import io.takamiya.mio.terminal.command.ParamDef;
import io.takamiya.mio.terminal.command.ParamType;

/**
 * 终端命令 plugin-utils —— 调用 dsl-cli 工具链编译/打包插件。
 * 用法: plugin-utils <build|parse|sign> [项目目录]
 */
public class PluginUtilsCommand implements ICommand {

    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    @Override
    public String getName() {
        return "plugin-utils";
    }

    @Override
    public String getDescription() {
        return "DSL 插件工具: 编译/生成签名 (build/parse/sign/genkeys)";
    }

    @Override
    public String getUsage() {
        return "plugin-utils <build|parse|sign|genkeys> [dir] | genkeys 必须传 dir";
    }

    @Override
    public List<ParamDef> getParams() {
        List<ParamDef> params = new ArrayList<>();
        params.add(new ParamDef("action", ParamType.STRING, true));
        params.add(new ParamDef("dir", ParamType.STRING, false));
        return params;
    }

    @Override
    public void execute(Map<String, Object> params, CommandCallback callback) {
        Object actionArg = params.get("action");
        String action = (actionArg == null) ? null : actionArg.toString().toLowerCase(Locale.US);
        Object dirArg = params.get("dir");

        if ("genkeys".equals(action)) {
            if (dirArg == null || dirArg.toString().isEmpty()) {
                callback.onResult(CommandResult.error("genkeys 需要传入项目路径\n用法: " + getUsage()));
                callback.onStateChange(CommandState.ERROR);
                return;
            }
            callback.onStateChange(CommandState.EXECUTING);
            DslBuildTool.genKeysAsync(dirArg.toString(), (ok, log) -> MAIN.post(() -> {
                callback.onResult(ok ? CommandResult.text(log) : CommandResult.error(log));
                callback.onStateChange(ok ? CommandState.SUCCESS : CommandState.ERROR);
            }));
            return;
        }

        String dir = (dirArg == null || dirArg.toString().isEmpty())
                ? DslBuildTool.DEFAULT_PROJECT_DIR
                : dirArg.toString();

        if (!"build".equals(action) && !"parse".equals(action) && !"sign".equals(action)) {
            callback.onResult(CommandResult.error("未知操作: " + action + "\n用法: " + getUsage()));
            callback.onStateChange(CommandState.ERROR);
            return;
        }

        callback.onStateChange(CommandState.EXECUTING);
        DslBuildTool.runAsync(action, dir, (ok, log) -> MAIN.post(() -> {
            callback.onResult(ok ? CommandResult.text(log) : CommandResult.error(log));
            callback.onStateChange(ok ? CommandState.SUCCESS : CommandState.ERROR);
        }));
    }
}