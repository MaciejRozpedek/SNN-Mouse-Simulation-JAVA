package com.macroz.snnmousesimulation.loader.structure;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class NeuronInfo {
    private int typeId;
    private int count;
    private int startIndex;

    public NeuronInfo() {}

    public NeuronInfo(int typeId, int count, int startIndex) {
        this.typeId = typeId;
        this.count = count;
        this.startIndex = startIndex;
    }

}
