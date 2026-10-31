package io.takamiya.mio.building;

import java.util.ArrayList;
import java.util.List;

/**
 * 命令模板：[0] 手动执行 的固定部分与 [1] 其他 的全部命令。
 * 其余数值（totalCoords/mainSx/area）从建筑 meta 读取拼装，方块命令存 sqlite。
 */
public final class CommandTemplates {

    private CommandTemplates() {
    }

    /**
     * [0] 手动执行命令 = 6 条固定初始化 + 敏感词手动 setblock（可能为空）。
     *
     * @param manualExtraCommands Converter 产出的敏感词手动命令（可空/空列表）
     */
    public static List<String> buildManualCommands(List<String> manualExtraCommands) {
        List<String> lines = new ArrayList<>();
        lines.add("你需要在聊天栏执行以下命令喵～");
        lines.add("/scoreboard objectives add a dummy a");
        lines.add("/scoreboard objectives add b dummy b");
        lines.add("/scoreboard players set @e[name=a] a 0");
        lines.add("/scoreboard players set @e[name=b] a 0");
        lines.add("/scoreboard players set @e[name=b] b 0");
        if (manualExtraCommands != null && !manualExtraCommands.isEmpty()) {
            lines.add("都怪可恶的网易敏感词系统，需要主人在盔甲架a的位置手动执行下面这些命令喵～");
            lines.addAll(manualExtraCommands);
        }
        return lines;
    }

    /**
     * [1] 其他命令（11 条，循环/进度），依赖建筑尺寸与总坐标数。
     *
     * @param mainSx      主区域宽
     * @param mainSz      主区域深
     * @param totalCoords 方块坐标总数 (sx*sy*sz)
     */
    public static List<String> buildOtherCommands(int mainSx, int mainSz, long totalCoords) {
        long area = (long) mainSx * mainSz;
        List<String> lines = new ArrayList<>();
        lines.add("execute as @e[name=b] at @s run tp @s ~ ~ ~");
        lines.add("titleraw @a actionbar {\"rawtext\":[{\"text\":\"§g打印进度: \"},{\"score\":{\"name\":\"@e[name=a]\",\"objective\":\"a\"}},{\"text\":\"§g/" + totalCoords + "\"}]}");
        lines.add("scoreboard players add @e[name=a] a 1");
        lines.add("scoreboard players add @e[name=b] a 1");
        lines.add("scoreboard players add @e[name=b] b 1");
        lines.add("execute as @e[name=a] at @s run tp @s ~1 ~ ~");
        lines.add("execute if entity @e[name=b,scores={a=" + (mainSx + 1) + "..}] as @e[name=a] at @s run tp @s ~-" + mainSx + " ~ ~1");
        lines.add("scoreboard players set @e[name=b,scores={a=" + (mainSx + 1) + "..}] a 1");
        lines.add("execute as @e[name=b,scores={b=" + (area + 1) + "..}] at @s run tp @s ~ ~1 ~");
        lines.add("execute as @e[name=b,scores={b=" + (area + 1) + "..}] at @s run tp @e[name=a] ~1 ~ ~");
        lines.add("scoreboard players set @e[name=b,scores={b=" + (area + 1) + "..}] b 1");
        return lines;
    }
}
