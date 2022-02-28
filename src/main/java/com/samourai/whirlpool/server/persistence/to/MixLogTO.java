package com.samourai.whirlpool.server.persistence.to;

import com.samourai.wallet.util.TxUtil;
import com.samourai.whirlpool.server.beans.Mix;
import com.samourai.whirlpool.server.persistence.to.shared.EntityTO;
import javax.persistence.*;

@Entity(name = "mixLog")
public class MixLogTO extends EntityTO {
  @OneToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "mix_id")
  private MixTO mix;

  private String txid;

  @Column(columnDefinition = "MEDIUMTEXT")
  private String rawTx;

  public MixLogTO() {}

  public void update(Mix mix, MixTO mixTO) {
    this.mix = mixTO;

    if (mix.getTx() != null) {
      this.txid = mix.getTx().getHashAsString();
      this.rawTx = TxUtil.getInstance().getTxHex(mix.getTx());
    }
  }

  public String getRawTx() {
    return rawTx;
  }

  public String getTxid() {
    return txid;
  }
}
