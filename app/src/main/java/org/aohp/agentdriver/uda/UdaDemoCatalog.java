package org.aohp.agentdriver.uda;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/** Built-in demo apps shipped from {@code aohp-app/UDA/*_output}. */
public final class UdaDemoCatalog {
    public static final String META_JOB_ID =
            "org.aohp.agentdriver.UDA_DEMO_JOB_ID";

    public static final class Entry {
        @NonNull public final String jobId;
        @NonNull public final String appName;
        @NonNull public final String idea;
        /** Asset folder under {@code assets/uda_demos/}. */
        @NonNull public final String assetDir;
        /** Activity-alias component for the system launcher. */
        @NonNull public final String gatewayActivity;
        /** Whether {@link UdaDemoLauncher} shows this demo on first boot. */
        public final boolean defaultLauncherVisible;

        Entry(
                @NonNull String jobId,
                @NonNull String appName,
                @NonNull String idea,
                @NonNull String assetDir,
                @NonNull String gatewayActivity,
                boolean defaultLauncherVisible) {
            this.jobId = jobId;
            this.appName = appName;
            this.idea = idea;
            this.assetDir = assetDir;
            this.gatewayActivity = gatewayActivity;
            this.defaultLauncherVisible = defaultLauncherVisible;
        }
    }

    private static final String ALIAS_PREFIX =
            "org.aohp.agentdriver.ui.uda.UdaDemo";
    private static final String ALIAS_SUFFIX = "Launcher";

    public static final String JOB_GIFT = "demo-gift-picker";
    public static final String JOB_PYTHON = "demo-python-learning";
    public static final String JOB_SHANGHAI = "demo-shanghai-trip";
    public static final String JOB_CROSS_PLATFORM_TRIP = "demo-cross-platform-trip-planner";
    public static final String JOB_DINNER_DESK = "demo-dinner-decision-desk";
    public static final String JOB_EXPENSE_SUMMARY = "demo-expense-summary";
    public static final String JOB_HEALTH_HUB = "demo-health-hub";
    public static final String JOB_MORNING_BRIEFING = "demo-morning-briefing";
    public static final String JOB_MOVING_CHECKLIST = "demo-moving-checklist";
    public static final String JOB_RENTAL_HOUSING = "demo-rental-housing-finder";

    private static final Entry[] DEMOS = {
        new Entry(
                JOB_GIFT,
                "Gift Picker",
                "A gift selection app for romantic occasions like 520, helping users choose luxury items for their partners.",
                "demo-gift-picker",
                alias("GiftPicker"),
                true),
        new Entry(
                JOB_PYTHON,
                "Python Learning Assistant",
                "A kid-friendly Python learning app with lessons, exercises, and progress tracking for primary school students.",
                "demo-python-learning",
                alias("PythonLearning"),
                true),
        new Entry(
                JOB_SHANGHAI,
                "Shanghai Trip Assistant",
                "A travel companion for a Shanghai business trip with flight details, airport reminders, and offline Agent OS reading materials.",
                "demo-shanghai-trip",
                alias("ShanghaiTrip"),
                true),
        new Entry(
                JOB_CROSS_PLATFORM_TRIP,
                "Cross Platform Trip Planner",
                "Business trip planner for Shanghai with flight and hotel options in portrait and landscape layouts.",
                "demo-cross-platform-trip-planner",
                alias("CrossPlatformTripPlanner"),
                true),
        new Entry(
                JOB_DINNER_DESK,
                "Dinner Decision Desk",
                "Nearby restaurant recommendations filtered by party size, spice level, time, and budget.",
                "demo-dinner-decision-desk",
                alias("DinnerDecisionDesk"),
                true),
        new Entry(
                JOB_EXPENSE_SUMMARY,
                "Expense Summary",
                "Weekly income and expense summary aggregated from WeChat Pay and Alipay.",
                "demo-expense-summary",
                alias("ExpenseSummary"),
                true),
        new Entry(
                JOB_HEALTH_HUB,
                "Health Hub",
                "Unified fitness, sleep, and weight dashboard from Huawei Health and Mi Fitness.",
                "demo-health-hub",
                alias("HealthHub"),
                true),
        new Entry(
                JOB_MORNING_BRIEFING,
                "Morning Briefing",
                "Morning portal with weather, sleep, todos, stocks, and news in one view.",
                "demo-morning-briefing",
                alias("MorningBriefing"),
                true),
        new Entry(
                JOB_MOVING_CHECKLIST,
                "Moving Checklist",
                "Furniture shopping checklist with purchase links and installation booking entry points.",
                "demo-moving-checklist",
                alias("MovingChecklist"),
                true),
        new Entry(
                JOB_RENTAL_HOUSING,
                "Rental Housing Finder",
                "Studio rentals within 3 km of Tsinghua Science Park from major Chinese listing platforms.",
                "demo-rental-housing-finder",
                alias("RentalHousingFinder"),
                true),
    };

    private UdaDemoCatalog() {}

    @NonNull
    private static String alias(@NonNull String shortName) {
        return ALIAS_PREFIX + shortName + ALIAS_SUFFIX;
    }

    @NonNull
    public static Entry[] all() {
        return DEMOS;
    }

    public static boolean isDemoJobId(@NonNull String jobId) {
        for (Entry e : DEMOS) {
            if (e.jobId.equals(jobId)) {
                return true;
            }
        }
        return false;
    }

    @Nullable
    public static Entry find(@NonNull String jobId) {
        for (Entry e : DEMOS) {
            if (e.jobId.equals(jobId)) {
                return e;
            }
        }
        return null;
    }
}
