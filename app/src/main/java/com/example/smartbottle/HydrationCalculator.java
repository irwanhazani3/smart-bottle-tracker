package com.example.smartbottle;

public class HydrationCalculator {

    /**
     * Calculates recommended daily water intake (in ml)
     * General formula: Weight(kg) * 35ml
     * Adjustments can be made based on age/activity if desired.
     */
    public static int calculateTarget(float weightKg, int age) {
        // Basic recommendation
        int target = Math.round(weightKg * 35);
        
        // Age adjustment: Seniors (65+) might need slightly less due to metabolism/activity, 
        // but generally kept simple for this app.
        if (age > 65) {
            target = Math.round(target * 0.9f); 
        }
        
        return target;
    }
}