package org.aohp.agentdriver.ad;

import org.json.JSONException;
import org.json.JSONObject;

public final class AohpAdSlot {
    public final String slotId;
    public final AohpAdFormat format;
    public final String placement;

    private AohpAdSlot(Builder b) {
        slotId = b.slotId;
        format = b.format;
        placement = b.placement;
    }

    JSONObject toJson(String packageName, int displayId) throws JSONException {
        return new JSONObject()
                .put("slotId", slotId)
                .put("format", format.name().toLowerCase())
                .put("placement", placement)
                .put("packageName", packageName)
                .put("displayId", displayId);
    }

    public static final class Builder {
        private final String slotId;
        private AohpAdFormat format = AohpAdFormat.BANNER;
        private String placement;

        public Builder(String slotId) {
            this.slotId = slotId;
            this.placement = slotId;
        }

        public Builder setFormat(AohpAdFormat format) {
            this.format = format != null ? format : AohpAdFormat.BANNER;
            return this;
        }

        public Builder setPlacement(String placement) {
            this.placement = placement != null ? placement : slotId;
            return this;
        }

        public AohpAdSlot build() {
            return new AohpAdSlot(this);
        }
    }
}
