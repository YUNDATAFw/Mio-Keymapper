package io.takamiya.mio.floating.toolBoxFragment.tool.beacon;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class BeaconColorMixer {

    public static final String[] COLOR_NAMES = {
        "white", "light_gray", "gray", "black", "brown", "red", "orange",
        "yellow", "lime", "green", "cyan", "light_blue", "blue",
        "purple", "magenta", "pink"
    };

    public static final int[][] COLOR_RGB = {
        {249, 255, 254}, {157, 157, 151}, {71, 79, 82}, {29, 29, 33},
        {131, 84, 50}, {176, 46, 38}, {249, 128, 29}, {254, 216, 61},
        {128, 199, 31}, {94, 124, 22}, {22, 156, 156}, {58, 179, 218},
        {60, 68, 170}, {137, 50, 184}, {199, 78, 189}, {243, 139, 170}
    };

    public static final String[] DISPLAY_NAMES = {
        "白色玻璃", "淡灰色玻璃", "灰色玻璃", "黑色玻璃", "棕色玻璃",
        "红色玻璃", "橙色玻璃", "黄色玻璃", "黄绿色玻璃", "绿色玻璃",
        "青色玻璃", "淡蓝色玻璃", "蓝色玻璃", "紫色玻璃",
        "品红色玻璃", "粉红色玻璃"
    };

    public static class Result {
        public final List<String> sequence;
        public final List<String> displayNames;
        public final List<int[]> glassRgb;
        public final int[] resultRgb;
        public final double deltaE;

        public Result(List<String> sequence, List<String> displayNames, List<int[]> glassRgb,
                      int[] resultRgb, double deltaE) {
            this.sequence = sequence;
            this.displayNames = displayNames;
            this.glassRgb = glassRgb;
            this.resultRgb = resultRgb;
            this.deltaE = deltaE;
        }

        public String resultHex() {
            return String.format("#%02x%02x%02x", resultRgb[0], resultRgb[1], resultRgb[2]);
        }
    }

    public static Result calculate(int targetRgb) {
        return calculate(targetRgb, false, "de2000", 200);
    }

    public static Result calculate(String hexColor) {
        return calculate(parseHex(hexColor));
    }

    public static Result calculate(String hexColor, boolean wikiMode, String distance, int beamWidth) {
        return calculate(parseHex(hexColor), wikiMode, distance, beamWidth);
    }

    public static int parseHex(String s) {
        s = s.trim();
        if (s.startsWith("#")) s = s.substring(1);
        if (s.startsWith("0x") || s.startsWith("0X")) s = s.substring(2);
        return Integer.parseInt(s, 16);
    }

    public static Result calculate(int targetRgb, boolean wikiMode, String distance, int beamWidth) {
        int[] target = {(targetRgb >> 16) & 0xFF, (targetRgb >> 8) & 0xFF, targetRgb & 0xFF};
        if (wikiMode) {
            return wikiSearch(target, distance);
        } else {
            return beamSearch(target, beamWidth, distance);
        }
    }

    public static int[] blendSequence(String[] sequence) {
        int[] first = COLOR_RGB[indexOf(sequence[0])];
        double r = first[0] / 255.0;
        double g = first[1] / 255.0;
        double b = first[2] / 255.0;
        for (int i = 1; i < sequence.length; i++) {
            int[] c = COLOR_RGB[indexOf(sequence[i])];
            r = (r + c[0] / 255.0) / 2.0;
            g = (g + c[1] / 255.0) / 2.0;
            b = (b + c[2] / 255.0) / 2.0;
        }
        return new int[]{(int) (r * 255), (int) (g * 255), (int) (b * 255)};
    }

    private static int indexOf(String name) {
        for (int i = 0; i < COLOR_NAMES.length; i++) {
            if (COLOR_NAMES[i].equals(name)) return i;
        }
        return 0;
    }

    private static double[] rgb2lab(int[] rgb) {
        double r = rgb[0] / 255.0;
        double g = rgb[1] / 255.0;
        double b = rgb[2] / 255.0;

        r = r > 0.04045 ? Math.pow((r + 0.055) / 1.055, 2.4) : r / 12.92;
        g = g > 0.04045 ? Math.pow((g + 0.055) / 1.055, 2.4) : g / 12.92;
        b = b > 0.04045 ? Math.pow((b + 0.055) / 1.055, 2.4) : b / 12.92;

        double x = (r * 0.4124 + g * 0.3576 + b * 0.1805) / 0.95047;
        double y = (r * 0.2126 + g * 0.7152 + b * 0.0722) / 1.0;
        double z = (r * 0.0193 + g * 0.1192 + b * 0.9505) / 1.08883;

        x = x > 0.008856 ? Math.pow(x, 1.0 / 3.0) : 7.787 * x + 16.0 / 116.0;
        y = y > 0.008856 ? Math.pow(y, 1.0 / 3.0) : 7.787 * y + 16.0 / 116.0;
        z = z > 0.008856 ? Math.pow(z, 1.0 / 3.0) : 7.787 * z + 16.0 / 116.0;

        return new double[]{116.0 * y - 16.0, 500.0 * (x - y), 200.0 * (y - z)};
    }

    private static double ciede94(double[] lab1, double[] lab2) {
        double dL = lab1[0] - lab2[0];
        double da = lab1[1] - lab2[1];
        double db = lab1[2] - lab2[2];
        double c1 = Math.sqrt(lab1[1] * lab1[1] + lab1[2] * lab1[2]);
        double c2 = Math.sqrt(lab2[1] * lab2[1] + lab2[2] * lab2[2]);
        double dC = c1 - c2;
        double dH2 = da * da + db * db - dC * dC;
        double dH = dH2 < 0 ? 0 : Math.sqrt(dH2);
        double sc = 1.0 + 0.045 * c1;
        double sh = 1.0 + 0.015 * c1;
        return Math.sqrt(
            (dL / 1.0) * (dL / 1.0) +
            (dC / sc) * (dC / sc) +
            (dH / sh) * (dH / sh)
        );
    }

    private static double ciede2000(double[] lab1, double[] lab2) {
        double L1 = lab1[0], a1 = lab1[1], b1 = lab1[2];
        double L2 = lab2[0], a2 = lab2[1], b2 = lab2[2];

        double C1 = Math.sqrt(a1 * a1 + b1 * b1);
        double C2 = Math.sqrt(a2 * a2 + b2 * b2);
        double Cab = (C1 + C2) / 2.0;

        double G = 0.5 * (1.0 - Math.sqrt(
            Math.pow(Cab, 7) / (Math.pow(Cab, 7) + Math.pow(25, 7))));

        double a1p = (1.0 + G) * a1;
        double a2p = (1.0 + G) * a2;

        double C1p = Math.sqrt(a1p * a1p + b1 * b1);
        double C2p = Math.sqrt(a2p * a2p + b2 * b2);

        double h1p = Math.toDegrees(Math.atan2(b1, a1p));
        if (h1p < 0) h1p += 360;
        double h2p = Math.toDegrees(Math.atan2(b2, a2p));
        if (h2p < 0) h2p += 360;

        double dhp;
        if (Math.abs(h1p - h2p) <= 180) {
            dhp = h2p - h1p;
        } else if (h2p <= h1p) {
            dhp = h2p - h1p + 360;
        } else {
            dhp = h2p - h1p - 360;
        }

        double dLp = L2 - L1;
        double dCp = C2p - C1p;
        double dHp = 2.0 * Math.sqrt(C1p * C2p) * Math.sin(Math.toRadians(dhp / 2.0));

        double Lp = (L1 + L2) / 2.0;
        double Cp = (C1p + C2p) / 2.0;

        double hp;
        if (Math.abs(h1p - h2p) <= 180) {
            hp = (h1p + h2p) / 2.0;
        } else if (h1p + h2p < 360) {
            hp = (h1p + h2p + 360.0) / 2.0;
        } else {
            hp = (h1p + h2p - 360.0) / 2.0;
        }

        double T = 1.0
            - 0.17 * Math.cos(Math.toRadians(hp - 30.0))
            + 0.24 * Math.cos(Math.toRadians(2.0 * hp))
            + 0.32 * Math.cos(Math.toRadians(3.0 * hp + 6.0))
            - 0.20 * Math.cos(Math.toRadians(4.0 * hp - 63.0));

        double SL = 1.0 + 0.015 * (Lp - 50.0) * (Lp - 50.0)
            / Math.sqrt(20.0 + (Lp - 50.0) * (Lp - 50.0));
        double SC = 1.0 + 0.045 * Cp;
        double SH = 1.0 + 0.015 * Cp * T;

        double dtheta = 30.0 * Math.exp(-((hp - 275.0) / 25.0) * ((hp - 275.0) / 25.0));
        double RC = 2.0 * Math.sqrt(
            Math.pow(Cp, 7) / (Math.pow(Cp, 7) + Math.pow(25, 7)));
        double RT = -RC * Math.sin(Math.toRadians(2.0 * dtheta));

        return Math.sqrt(
            (dLp / SL) * (dLp / SL) +
            (dCp / SC) * (dCp / SC) +
            (dHp / SH) * (dHp / SH) +
            RT * (dCp / SC) * (dHp / SH)
        );
    }

    private static double[] evaluate(String[] sequence, double[] targetLab, String distance) {
        int[] rgb = blendSequence(sequence);
        double[] lab = rgb2lab(rgb);
        double de = distance.equals("de94") ? ciede94(lab, targetLab) : ciede2000(lab, targetLab);
        return new double[]{de, rgb[0], rgb[1], rgb[2]};
    }

    private static Result trimSequence(String[] sequence, double[] targetLab, String distance, double currentDe) {
        for (int i = sequence.length - 1; i >= 1; i--) {
            String[] trimmed = new String[i];
            System.arraycopy(sequence, 0, trimmed, 0, i);
            double[] ev = evaluate(trimmed, targetLab, distance);
            if (ev[0] <= currentDe) {
                currentDe = ev[0];
                sequence = trimmed;
                if (i == 1) break;
            } else {
                break;
            }
        }
        if (sequence.length == 0) sequence = new String[]{"white"};
        int[] rgb = blendSequence(sequence);
        return new Result(seqList(sequence), dispList(sequence), rgbList(sequence), rgb, currentDe);
    }

    private static List<String> seqList(String[] arr) {
        List<String> list = new ArrayList<>(arr.length);
        for (String s : arr) list.add(s);
        return list;
    }

    private static List<String> dispList(String[] arr) {
        List<String> list = new ArrayList<>(arr.length);
        for (String s : arr) list.add(DISPLAY_NAMES[indexOf(s)]);
        return list;
    }

    private static List<int[]> rgbList(String[] arr) {
        List<int[]> list = new ArrayList<>(arr.length);
        for (String s : arr) list.add(COLOR_RGB[indexOf(s)]);
        return list;
    }

    private static Result beamSearch(int[] targetRgb, int beamWidth, String distance) {
        double[] targetLab = rgb2lab(targetRgb);
        int maxDepth = 6;

        List<String[]> candidates = new ArrayList<>();
        for (String name : COLOR_NAMES) {
            candidates.add(new String[]{name});
        }

        double bestDe = Double.MAX_VALUE;
        String[] bestSeq = null;

        for (int depth = 1; depth <= maxDepth; depth++) {
            List<String[]> newCandidates = new ArrayList<>();
            for (String[] seq : candidates) {
                for (String name : COLOR_NAMES) {
                    String[] extended = new String[seq.length + 1];
                    System.arraycopy(seq, 0, extended, 0, seq.length);
                    extended[seq.length] = name;
                    newCandidates.add(extended);
                }
            }

            List<ScoredEntry> scored = new ArrayList<>();
            for (String[] seq : newCandidates) {
                double[] ev = evaluate(seq, targetLab, distance);
                scored.add(new ScoredEntry(ev[0], seq));
            }
            scored.sort(Comparator.comparingDouble(e -> e.deltaE));

            candidates.clear();
            int limit = Math.min(beamWidth, scored.size());
            for (int i = 0; i < limit; i++) {
                candidates.add(scored.get(i).sequence);
            }

            double topDe = scored.get(0).deltaE;
            if (topDe < bestDe) {
                bestDe = topDe;
                bestSeq = scored.get(0).sequence;
            }
        }

        if (bestSeq == null) bestSeq = new String[]{"white"};
        return trimSequence(bestSeq, targetLab, distance, bestDe);
    }

    private static class ScoredEntry {
        final double deltaE;
        final String[] sequence;
        ScoredEntry(double deltaE, String[] sequence) {
            this.deltaE = deltaE;
            this.sequence = sequence;
        }
    }

    private static Result wikiSearch(int[] targetRgb, String distance) {
        double[] targetLab = rgb2lab(targetRgb);
        MutableResult mr = new MutableResult();
        mr.bestDe = Double.MAX_VALUE;
        mr.bestSeq = null;

        for (int r = 6; r >= 1; r--) {
            int[] combo = new int[r];
            wikiSearchGenerate(combo, 0, 0, targetLab, distance, mr);
        }

        if (mr.bestSeq == null) mr.bestSeq = new String[]{"white"};
        return trimSequence(mr.bestSeq, targetLab, distance, mr.bestDe);
    }

    private static class MutableResult {
        double bestDe;
        String[] bestSeq;
    }

    private static void wikiSearchGenerate(int[] combo, int pos, int start,
                                            double[] targetLab, String distance, MutableResult mr) {
        if (pos == combo.length) {
            String[] seq = new String[combo.length];
            for (int i = 0; i < combo.length; i++) {
                seq[i] = COLOR_NAMES[combo[combo.length - 1 - i]];
            }
            double[] ev = evaluate(seq, targetLab, distance);
            if (ev[0] < mr.bestDe) {
                mr.bestDe = ev[0];
                mr.bestSeq = seq;
            }
            return;
        }
        for (int i = start; i < COLOR_NAMES.length; i++) {
            combo[pos] = i;
            wikiSearchGenerate(combo, pos + 1, i, targetLab, distance, mr);
        }
    }
}
