package com.example.smartbottle;

import android.content.Context;
import android.widget.Toast;
import android.util.Log;

public class AchievementManager {

    private static final String TAG = "AchievementManager";
    private Context context;
    private DatabaseHelper dbHelper;

    // Achievement IDs
    public static final String ACH_FIRST_DROP = "first_drop";
    public static final String ACH_HYDRATION_CHAMP = "hydration_champ";
    public static final String ACH_STREAK_MASTER = "streak_master";
    public static final String ACH_OCEAN_WARRIOR = "ocean_warrior";

    public AchievementManager(Context context, DatabaseHelper dbHelper) {
        this.context = context;
        this.dbHelper = dbHelper;
    }

    public void checkAchievements(int currentDailyIntake, int dailyGoal) {
        // Check First Drop
        if (!dbHelper.isAchievementUnlocked(ACH_FIRST_DROP) && currentDailyIntake > 0) {
            dbHelper.unlockAchievement(ACH_FIRST_DROP, "First Drop");
            showAchievementToast("Achievement Unlocked: First Drop!");
        }

        // Check Hydration Champ
        if (!dbHelper.isAchievementUnlocked(ACH_HYDRATION_CHAMP) && dailyGoal > 0 && currentDailyIntake >= dailyGoal) {
            dbHelper.unlockAchievement(ACH_HYDRATION_CHAMP, "Hydration Champ");
            showAchievementToast("Achievement Unlocked: Hydration Champ!");
        }

        // Check Streak Master (3-day streak)
        int currentStreak = dbHelper.getCurrentStreak(dailyGoal);
        if (!dbHelper.isAchievementUnlocked(ACH_STREAK_MASTER) && currentStreak >= 3) {
            dbHelper.unlockAchievement(ACH_STREAK_MASTER, "Streak Master");
            showAchievementToast("Achievement Unlocked: Streak Master (3 Days)!");
        }

        // Check Ocean Warrior (total intake 5000ml)
        if (!dbHelper.isAchievementUnlocked(ACH_OCEAN_WARRIOR) && dbHelper.getTotalIntakeEver() >= 5000) {
            dbHelper.unlockAchievement(ACH_OCEAN_WARRIOR, "Ocean Warrior");
            showAchievementToast("Achievement Unlocked: Ocean Warrior!");
        }

        // Log current streak (for debugging/monitoring)
        Log.d(TAG, "Current Streak: " + currentStreak);
    }

    private void showAchievementToast(String message) {
        Toast.makeText(context, message, Toast.LENGTH_LONG).show();
    }
}