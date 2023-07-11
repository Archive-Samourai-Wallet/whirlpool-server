package com.samourai.whirlpool.server.persistence.to;

import com.samourai.whirlpool.server.beans.BlameReason;
import com.samourai.whirlpool.server.persistence.to.shared.EntityCreatedTO;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import org.apache.commons.lang3.BooleanUtils;

@Entity(name = "blame")
public class BlameTO extends EntityCreatedTO {
  private String identifier;

  @Enumerated(EnumType.STRING)
  private BlameReason reason;

  private String mixId;

  private Boolean tor;

  public BlameTO() {}

  public BlameTO(String identifier, BlameReason reason, String mixId, Boolean tor) {
    super();
    this.identifier = identifier;
    this.reason = reason;
    this.mixId = mixId;
    this.tor = tor;
  }

  public String getIdentifier() {
    return identifier;
  }

  public BlameReason getReason() {
    return reason;
  }

  public String getMixId() {
    return mixId;
  }

  public Boolean isTor() {
    return tor;
  }

  @Override
  public String toString() {
    return "identifier="
        + identifier
        + ", reason="
        + reason
        + ", mixId="
        + (mixId != null ? mixId : "null")
        + ", tor="
        + (tor != null ? BooleanUtils.toStringTrueFalse(tor) : "null")
        + ", created="
        + (getCreated() != null ? getCreated() : "");
  }
}
