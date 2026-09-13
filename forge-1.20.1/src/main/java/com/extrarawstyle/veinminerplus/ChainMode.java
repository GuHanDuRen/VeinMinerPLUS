package com.extrarawstyle.veinminerplus;

public enum ChainMode {
    NORMAL("chain.veinminerplus.normal", 0),
    AREA_1X1("chain.veinminerplus.area_1x1", 1),
    AREA_3X3("chain.veinminerplus.area_3x3", 2),
    AREA_5X5("chain.veinminerplus.area_5x5", 7),
    BLAST_SAME("chain.veinminerplus.blast_same", 3),
    BLAST_ORES("chain.veinminerplus.blast_ores", 4),
    BLAST_ANY("chain.veinminerplus.blast_any", 5),
    BLAST_LOGS("chain.veinminerplus.blast_logs", 6);

    private final String translationKey;
    private final int id;

    ChainMode(String translationKey, int id) {
        this.translationKey = translationKey;
        this.id = id;
    }

    public String translationKey() {
        return translationKey;
    }

    public int id() {
        return id;
    }

    public static ChainMode fromOrdinal(int id) {
        for (ChainMode mode : values()) {
            if (mode.id == id) {
                return mode;
            }
        }
        return NORMAL;
    }

    public static ChainMode cycle(ChainMode current, int direction) {
        ChainMode[] modes = values();
        int next = Math.floorMod(current.ordinal() + direction, modes.length);
        return modes[next];
    }

    public boolean isArea() {
        return this == AREA_1X1 || this == AREA_3X3 || this == AREA_5X5;
    }

    public int areaSize() {
        switch (this) {
        case AREA_1X1:
            return 1;
        case AREA_3X3:
            return 3;
        case AREA_5X5:
            return 5;
        default:
            return 0;
        }
    }

    public boolean isBlast() {
        return this == BLAST_SAME || this == BLAST_ORES || this == BLAST_ANY || this == BLAST_LOGS;
    }
}
