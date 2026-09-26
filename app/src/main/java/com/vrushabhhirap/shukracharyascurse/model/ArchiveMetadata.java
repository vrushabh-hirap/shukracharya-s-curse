package com.vrushabhhirap.shukracharyascurse.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Encapsulates extended manuscript metadata attributes, provenance records,
 * and linguistic classifications.
 */
public class ArchiveMetadata implements Serializable {

    private static final long serialVersionUID = 2026092403L;

    private String manuscriptId;
    private String primaryLanguage;
    private String scriptVariant;
    private int estimatedFolioCount;
    private String preservationState;
    private final List<ArchiveTag> tags;
    private final Map<String, String> extendedAttributes;

    public ArchiveMetadata() {
        this.tags = new ArrayList<>();
        this.extendedAttributes = new HashMap<>();
    }

    public ArchiveMetadata(String manuscriptId, String primaryLanguage, String scriptVariant,
                           int estimatedFolioCount, String preservationState) {
        this();
        this.manuscriptId = manuscriptId;
        this.primaryLanguage = primaryLanguage;
        this.scriptVariant = scriptVariant;
        this.estimatedFolioCount = estimatedFolioCount;
        this.preservationState = preservationState;
    }

    public String getManuscriptId() { return manuscriptId; }
    public void setManuscriptId(String manuscriptId) { this.manuscriptId = manuscriptId; }

    public String getPrimaryLanguage() { return primaryLanguage; }
    public void setPrimaryLanguage(String primaryLanguage) { this.primaryLanguage = primaryLanguage; }

    public String getScriptVariant() { return scriptVariant; }
    public void setScriptVariant(String scriptVariant) { this.scriptVariant = scriptVariant; }

    public int getEstimatedFolioCount() { return estimatedFolioCount; }
    public void setEstimatedFolioCount(int count) { this.estimatedFolioCount = count; }

    public String getPreservationState() { return preservationState; }
    public void setPreservationState(String state) { this.preservationState = state; }

    public void addTag(ArchiveTag tag) {
        if (tag != null && !tags.contains(tag)) {
            tags.add(tag);
        }
    }

    public List<ArchiveTag> getTags() {
        return Collections.unmodifiableList(tags);
    }

    public void setAttribute(String key, String value) {
        extendedAttributes.put(key, value);
    }

    public String getAttribute(String key, String defaultValue) {
        String val = extendedAttributes.get(key);
        return val != null ? val : defaultValue;
    }

    public Map<String, String> getExtendedAttributes() {
        return Collections.unmodifiableMap(extendedAttributes);
    }
}
