package io.takamiya.mio.configManage;

import java.util.ArrayList;
import java.util.List;

public class ConfigDiffHelper {

    public static final int TYPE_ADDED = 1;
    public static final int TYPE_REMOVED = 2;
    public static final int TYPE_UNCHANGED = 3;

    public static class DiffLine {
        private final int type;
        private final String text;

        public DiffLine(int type, String text) {
            this.type = type;
            this.text = text;
        }

        public int getType() { return type; }
        public String getText() { return text; }

        public String getPrefix() {
            switch (type) {
                case TYPE_ADDED: return "[+]";
                case TYPE_REMOVED: return "[-]";
                default: return "   ";
            }
        }
    }

    public static List<DiffLine> computeDiff(String localJson, String remoteJson) {
        if (localJson == null) localJson = "";
        if (remoteJson == null) remoteJson = "";

        String[] localLines = localJson.split("\\n", -1);
        String[] remoteLines = remoteJson.split("\\n", -1);

        int[][] dp = new int[localLines.length + 1][remoteLines.length + 1];

        for (int i = localLines.length - 1; i >= 0; i--) {
            for (int j = remoteLines.length - 1; j >= 0; j--) {
                if (localLines[i].equals(remoteLines[j])) {
                    dp[i][j] = dp[i + 1][j + 1] + 1;
                } else {
                    dp[i][j] = Math.max(dp[i + 1][j], dp[i][j + 1]);
                }
            }
        }

        List<DiffLine> result = new ArrayList<>();
        int i = 0, j = 0;
        while (i < localLines.length && j < remoteLines.length) {
            if (localLines[i].equals(remoteLines[j])) {
                result.add(new DiffLine(TYPE_UNCHANGED, localLines[i]));
                i++;
                j++;
            } else if (dp[i + 1][j] >= dp[i][j + 1]) {
                result.add(new DiffLine(TYPE_REMOVED, localLines[i]));
                i++;
            } else {
                result.add(new DiffLine(TYPE_ADDED, remoteLines[j]));
                j++;
            }
        }
        while (i < localLines.length) {
            result.add(new DiffLine(TYPE_REMOVED, localLines[i]));
            i++;
        }
        while (j < remoteLines.length) {
            result.add(new DiffLine(TYPE_ADDED, remoteLines[j]));
            j++;
        }

        return result;
    }
}
