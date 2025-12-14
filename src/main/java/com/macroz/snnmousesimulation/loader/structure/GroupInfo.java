package com.macroz.snnmousesimulation.loader.structure;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Setter
@Getter
public class GroupInfo {
    private String name;
    private String fullName; // including parent group names

    private List<GroupInfo> subgroups = new ArrayList<>();
    private List<NeuronInfo> neurons = new ArrayList<>();

    private int startIndex;
    private int totalCount;

    public GroupInfo() {}

}
