package com.lycoris.loconautics.common.strand;

public enum StrandKind {

    STEEL_CABLE,
    SCREW_LINK,
    HOSE_LINK,
    WINCH_HOSE;

    private final ClientStrands clientStrands = new ClientStrands();

    public ClientStrands clientStrands() {
        return clientStrands;
    }
}
