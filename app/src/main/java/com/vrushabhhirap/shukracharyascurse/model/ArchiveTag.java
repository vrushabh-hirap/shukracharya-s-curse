package com.vrushabhhirap.shukracharyascurse.model;

import java.io.Serializable;
import java.util.Objects;

/**
 * Metadata tag entity associated with archival manuscript documents.
 */
public class ArchiveTag implements Serializable, Comparable<ArchiveTag> {

    private static final long serialVersionUID = 2026092402L;

    private String tagId;
    private String name;
    private String category;
    private float relevanceScore;
    private boolean systemTag;

    public ArchiveTag() {}

    public ArchiveTag(String tagId, String name, String category, float relevanceScore, boolean systemTag) {
        this.tagId = tagId;
        this.name = name;
        this.category = category;
        this.relevanceScore = relevanceScore;
        this.systemTag = systemTag;
    }

    public String getTagId() { return tagId; }
    public void setTagId(String tagId) { this.tagId = tagId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public float getRelevanceScore() { return relevanceScore; }
    public void setRelevanceScore(float relevanceScore) { this.relevanceScore = relevanceScore; }

    public boolean isSystemTag() { return systemTag; }
    public void setSystemTag(boolean systemTag) { this.systemTag = systemTag; }

    @Override
    public int compareTo(ArchiveTag o) {
        return Float.compare(o.relevanceScore, this.relevanceScore);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ArchiveTag that = (ArchiveTag) o;
        return Objects.equals(tagId, that.tagId) && Objects.equals(name, that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tagId, name);
    }

    @Override
    public String toString() {
        return "ArchiveTag{" + name + " (" + category + "): " + relevanceScore + "}";
    }
}
