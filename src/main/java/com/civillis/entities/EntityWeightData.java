package com.civillis.entities;

/**
 * Data class representing an entity's contribution to civilization score
 */
public class EntityWeightData {
    
    public static final EntityWeightData NONE = new EntityWeightData(0.0, false);
    
    private final double weight;
    private final boolean contributes;
    
    public EntityWeightData(double weight, boolean contributes) {
        this.weight = weight;
        this.contributes = contributes;
    }
    
    /**
     * Get the weight value (can be negative for hostile entities)
     */
    public double getWeight() {
        return weight;
    }
    
    /**
     * Check if this entity contributes to civilization calculation
     */
    public boolean contributesToCivilization() {
        return contributes;
    }
    
    @Override
    public String toString() {
        return "EntityWeightData{weight=" + weight + ", contributes=" + contributes + "}";
    }
}
