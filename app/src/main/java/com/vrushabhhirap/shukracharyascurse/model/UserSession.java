package com.vrushabhhirap.shukracharyascurse.model;

import java.io.Serializable;
import java.util.Date;
import java.util.Objects;

/**
 * Encapsulates the user's authenticated session state, permissions tier,
 * and security token metadata.
 */
public class UserSession implements Serializable {
    private static final long serialVersionUID = 2026092401L;

    public enum AccessTier {
        SEEKER(1, "Seeker of Knowledge", 100),
        ADEPT(2, "Adept of Sanjivani", 250),
        SCHOLAR(3, "Scholar of the Ashrama", 500),
        MAHAPUROHIT(4, "High Priest of Asuras", 1000);

        private final int level;
        private final String title;
        private final int quotaLimit;

        AccessTier(int level, String title, int quotaLimit) {
            this.level = level;
            this.title = title;
            this.quotaLimit = quotaLimit;
        }

        public int getLevel() {
            return level;
        }

        public String getTitle() {
            return title;
        }

        public int getQuotaLimit() {
            return quotaLimit;
        }

        public static AccessTier fromLevel(int level) {
            for (AccessTier tier : values()) {
                if (tier.level == level) return tier;
            }
            return SEEKER;
        }
    }

    private String sessionId;
    private String username;
    private AccessTier tier;
    private long loginTimestamp;
    private long lastAccessTimestamp;
    private long expiryTimestamp;
    private boolean active;
    private int operationCount;
    private int flags;

    public static final int FLAG_MFA_VERIFIED       = 1 << 0;
    public static final int FLAG_HARDWARE_BACKED    = 1 << 1;
    public static final int FLAG_RESTRICTED_ACCESS  = 1 << 2;
    public static final int FLAG_EPHEMERAL_TOKEN    = 1 << 3;

    public UserSession() {
        this.sessionId = generateSessionId();
        this.username = "anonymous";
        this.tier = AccessTier.SEEKER;
        this.loginTimestamp = System.currentTimeMillis();
        this.lastAccessTimestamp = this.loginTimestamp;
        this.expiryTimestamp = this.loginTimestamp + (3600 * 1000 * 24); // 24 hours
        this.active = true;
        this.operationCount = 0;
        this.flags = FLAG_EPHEMERAL_TOKEN;
    }

    public UserSession(String username, AccessTier tier) {
        this();
        this.username = username;
        this.tier = tier != null ? tier : AccessTier.SEEKER;
    }

    public static UserSession createDefault() {
        return new UserSession("devotee_sanjivani", AccessTier.ADEPT);
    }

    private static String generateSessionId() {
        long t = System.currentTimeMillis();
        long r = (long) (Math.random() * 0xFFFFFFFFFFFFL);
        return String.format("SESS-%08X-%012X", (int) (t & 0xFFFFFFFFL), r);
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public AccessTier getTier() {
        return tier;
    }

    public void setTier(AccessTier tier) {
        this.tier = tier;
    }

    public long getLoginTimestamp() {
        return loginTimestamp;
    }

    public void setLoginTimestamp(long loginTimestamp) {
        this.loginTimestamp = loginTimestamp;
    }

    public long getLastAccessTimestamp() {
        return lastAccessTimestamp;
    }

    public void touch() {
        this.lastAccessTimestamp = System.currentTimeMillis();
        this.operationCount++;
    }

    public long getExpiryTimestamp() {
        return expiryTimestamp;
    }

    public void setExpiryTimestamp(long expiryTimestamp) {
        this.expiryTimestamp = expiryTimestamp;
    }

    public boolean isActive() {
        return active && System.currentTimeMillis() < expiryTimestamp;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public int getOperationCount() {
        return operationCount;
    }

    public void setOperationCount(int operationCount) {
        this.operationCount = operationCount;
    }

    public int getFlags() {
        return flags;
    }

    public void setFlags(int flags) {
        this.flags = flags;
    }

    public void addFlag(int flag) {
        this.flags |= flag;
    }

    public void removeFlag(int flag) {
        this.flags &= ~flag;
    }

    public boolean hasFlag(int flag) {
        return (this.flags & flag) == flag;
    }

    public boolean canAccessClassification(String classification) {
        if (classification == null) return true;
        switch (classification.toUpperCase()) {
            case "FORBIDDEN":
                return tier.getLevel() >= AccessTier.MAHAPUROHIT.getLevel();
            case "SEALED":
                return tier.getLevel() >= AccessTier.SCHOLAR.getLevel();
            case "RESTRICTED":
                return tier.getLevel() >= AccessTier.ADEPT.getLevel();
            default:
                return true;
        }
    }

    public String getFormattedLoginTime() {
        return new Date(loginTimestamp).toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UserSession that = (UserSession) o;
        return Objects.equals(sessionId, that.sessionId) &&
                Objects.equals(username, that.username);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sessionId, username);
    }

    @Override
    public String toString() {
        return "UserSession{" +
                "id='" + sessionId + '\'' +
                ", user='" + username + '\'' +
                ", tier=" + tier.getTitle() +
                ", active=" + isActive() +
                ", ops=" + operationCount +
                '}';
    }
}
