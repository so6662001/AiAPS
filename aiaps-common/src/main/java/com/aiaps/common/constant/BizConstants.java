package com.aiaps.common.constant;

public final class BizConstants {
    private BizConstants() {}

    public static final class MaterialType {
        public static final String RAW = "RAW";
        public static final String SEMI = "SEMI";
        public static final String FG = "FG";
        public static final String PACK = "PACK";
    }

    public static final class Category {
        public static final String COIL = "COIL";
        public static final String STRIP = "STRIP";
        public static final String PLATE = "PLATE";
        public static final String RECT_PIPE = "RECT_PIPE";
        public static final String ROUND_PIPE = "ROUND_PIPE";
        public static final String H_STEEL = "H_STEEL";
        public static final String BEND_PART = "BEND_PART";
        public static final String BEAM_FRAME = "BEAM_FRAME";
    }

    public static final class DemandSource {
        public static final String MTO = "MTO";
        public static final String MTS = "MTS";
        public static final String SSK = "SSK";
    }

    public static final class OrderType {
        public static final String MFG = "MFG";
        public static final String PUR = "PUR";
        public static final String SUB = "SUB";
    }

    public static final class ScheduleStatus {
        public static final String DRAFT = "DRAFT";
        public static final String CONFIRMED = "CONFIRMED";
        public static final String RELEASED = "RELEASED";
        public static final String IN_PROGRESS = "IN_PROGRESS";
        public static final String WAITING_MATERIAL = "WAITING_MATERIAL";
        public static final String COMPLETED = "COMPLETED";
        public static final String CANCELLED = "CANCELLED";
    }

    public static final class FlowType {
        public static final String FG_STOCK = "FG_STOCK";
        public static final String SEMI_STOCK = "SEMI_STOCK";
        public static final String NEXT_OPER = "NEXT_OPER";
        public static final String NEXT_SCHEDULE = "NEXT_SCHEDULE";
        public static final String OUTSOURCE = "OUTSOURCE";
        public static final String CUSTOMER = "CUSTOMER";
    }

    public static final class LotPolicy {
        public static final String LFL = "LFL";
        public static final String FOQ = "FOQ";
        public static final String POQ = "POQ";
        public static final String EOQ = "EOQ";
    }
}
