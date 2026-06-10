package org.aohp.agentdriver.ad;

import org.json.JSONException;
import org.json.JSONObject;

public final class AohpAdOpportunity {
    public final String opportunityId;
    public final AohpAdFormat format;
    public final String businessContext;
    public final String rewardPolicy;

    private AohpAdOpportunity(Builder b) {
        opportunityId = b.opportunityId;
        format = b.format;
        businessContext = b.businessContext;
        rewardPolicy = b.rewardPolicy;
    }

    JSONObject toJson(String packageName, int displayId) throws JSONException {
        return new JSONObject()
                .put("opportunityId", opportunityId)
                .put("format", format.name().toLowerCase())
                .put("businessContext", businessContext)
                .put("rewardPolicy", rewardPolicy)
                .put("packageName", packageName)
                .put("displayId", displayId);
    }

    public static final class Builder {
        private final String opportunityId;
        private AohpAdFormat format = AohpAdFormat.INTERSTITIAL;
        private String businessContext = "";
        private String rewardPolicy = "HUMAN_CONFIRMED_ONLY";

        public Builder(String opportunityId) {
            this.opportunityId = opportunityId;
        }

        public Builder setFormat(AohpAdFormat format) {
            this.format = format != null ? format : AohpAdFormat.INTERSTITIAL;
            return this;
        }

        public Builder setBusinessContext(String businessContext) {
            this.businessContext = businessContext != null ? businessContext : "";
            return this;
        }

        public Builder setRewardPolicy(String rewardPolicy) {
            this.rewardPolicy = rewardPolicy != null ? rewardPolicy : "HUMAN_CONFIRMED_ONLY";
            return this;
        }

        public AohpAdOpportunity build() {
            return new AohpAdOpportunity(this);
        }
    }
}
