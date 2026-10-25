package dev.opluskeycontrol.vector;

/** Exact profiles inventoried from device framework DEX; unknown OTA builds fail closed. */
public final class RuntimeProfile {
    private RuntimeProfile() {}

    public static boolean supports(int sdk, String model, String build) {
        return "CPH2745".equals(model) &&
                ((sdk == 36 && "CPH2745_16.0.10.601(EX01)".equals(build)) ||
                 (sdk == 37 && "CPH2745_17.0.0.102(EX01)".equals(build)));
    }

    public static String combinationBase(int sdk) {
        return sdk == 37 ? "com.android.server.input.KeyCombinationManager$TwoKeysCombinationRule"
                : "com.android.server.policy.KeyCombinationManager$TwoKeysCombinationRule";
    }

    public static boolean powerGestureEvent(int sdk) {
        return sdk == 37;
    }

    public static String combinationRule(int sdk, boolean volumeUp) {
        return sdk == 37 ? "com.android.server.input.KeyGestureController$" + (volumeUp ? "7" : "4")
                : "com.android.server.policy.PhoneWindowManager$" + (volumeUp ? "9" : "6");
    }
}
