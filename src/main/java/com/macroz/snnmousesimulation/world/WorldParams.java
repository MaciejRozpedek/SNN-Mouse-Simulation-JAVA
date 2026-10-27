package com.macroz.snnmousesimulation.world;

import com.macroz.snnmousesimulation.utility.ParamChecks;

public record WorldParams(double width, double height) {

    public WorldParams {
        ParamChecks.positive("width", width);
        ParamChecks.positive("height", height);
    }
}
