package com.macroz.snnmousesimulation.loader;

import com.macroz.snnmousesimulation.core.IzhikevichParams;
import com.macroz.snnmousesimulation.core.SnnNetworkData;
import com.macroz.snnmousesimulation.exception.SnnParseException;
import com.macroz.snnmousesimulation.loader.structure.GroupInfo;
import com.macroz.snnmousesimulation.loader.structure.NeuronInfo;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.nodes.*;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class NetworkTopologyLoader {

    private final List<IzhikevichParams> neuronParamTypes = new ArrayList<>();
    private final Map<String, Integer> neuronTypeToIdMap = new HashMap<>();
    private final List<Integer> globalNeuronTypeIds = new ArrayList<>();
    private final List<Double> initialV = new ArrayList<>();
    private final List<Double> initialU = new ArrayList<>();

    private final List<List<Integer>> synapticTargets = new ArrayList<>();
    private final List<List<Double>> synapticWeights = new ArrayList<>();

    private GroupInfo rootGroup;
    private int totalNeuronCount = 0;

    public record GroupPair(GroupInfo from, GroupInfo to) {}

    public SnnNetworkData load(InputStream inputStream) {
        Yaml yaml = new Yaml();
        if (inputStream == null) throw new SnnParseException("Wrong path to YAML file.");
        Node root = yaml.compose(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
        if (root == null) throw new SnnParseException("Empty YAML file.");
        if (!(root instanceof MappingNode)) throw new SnnParseException("Root must be a mapping.", root.getStartMark());

        loadNeuronTypes(getChildNodeRequired(root, "neuron_types"));

        rootGroup = new GroupInfo();
        rootGroup.setFullName("root");
        rootGroup.setStartIndex(0);

        loadGroups(getChildNodeRequired(root, "groups"), rootGroup);
        rootGroup.setTotalCount(totalNeuronCount);

        for(int i = 0; i < totalNeuronCount; i++) {
            synapticTargets.add(new ArrayList<>());
            synapticWeights.add(new ArrayList<>());
        }

        loadConnections(getChildNodeRequired(root, "connections"));

        return buildDto();
    }

    private SnnNetworkData buildDto() {
        int[] typeIdsArr = globalNeuronTypeIds.stream().mapToInt(i -> i).toArray();
        double[] vArr = initialV.stream().mapToDouble(d -> d).toArray();
        double[] uArr = initialU.stream().mapToDouble(d -> d).toArray();

        int[][] targetsArr = new int[totalNeuronCount][];
        double[][] weightsArr = new double[totalNeuronCount][];

        for (int i = 0; i < totalNeuronCount; i++) {
            List<Integer> tList = synapticTargets.get(i);
            List<Double> wList = synapticWeights.get(i);

            targetsArr[i] = tList.stream().mapToInt(v -> v).toArray();
            weightsArr[i] = wList.stream().mapToDouble(v -> v).toArray();
        }

        return new SnnNetworkData(
                new ArrayList<>(neuronParamTypes),
                typeIdsArr,
                vArr,
                uArr,
                targetsArr,
                weightsArr
        );
    }

    private void loadNeuronTypes(Node node) {
        if (!(node instanceof MappingNode mappingNode)) {
            throw new SnnParseException("section 'neuron_types' must be a map.", node.getStartMark());
        }

        for (NodeTuple tuple : mappingNode.getValue()) {
            String typeName = nodeAs(tuple.getKeyNode(), String.class);
            Node paramsNode = tuple.getValueNode();

            double a = nodeAs(getChildNodeRequired(paramsNode, "a"), Double.class);
            double b = nodeAs(getChildNodeRequired(paramsNode, "b"), Double.class);
            double c = nodeAs(getChildNodeRequired(paramsNode, "c"), Double.class);
            double d = nodeAs(getChildNodeRequired(paramsNode, "d"), Double.class);
            double v0 = nodeAs(getChildNodeRequired(paramsNode, "v0"), Double.class);
            double u0 = nodeAs(getChildNodeRequired(paramsNode, "u0"), Double.class);

            int typeId = neuronParamTypes.size();
            neuronParamTypes.add(new IzhikevichParams(a, b, c, d, v0, u0));
            neuronTypeToIdMap.put(typeName, typeId);
        }
    }

    private void loadGroups(Node node, GroupInfo parentGroup) {
        if (!(node instanceof SequenceNode sequenceNode)) {
            throw new SnnParseException("Expected sequence of groups.", node.getStartMark());
        }

        int currentStartIndex = parentGroup.getStartIndex();

        for (Node groupNode : sequenceNode.getValue()) {
            String name = nodeAs(getChildNodeRequired(groupNode, "name"), String.class);

            GroupInfo subgroup = new GroupInfo();
            subgroup.setName(name);
            subgroup.setFullName(parentGroup.getFullName().equals("root") ? name : parentGroup.getFullName() + "." + name);
            subgroup.setStartIndex(currentStartIndex);

            Node neuronsNode = getChildNode(groupNode, "neurons");
            Node subgroupsNode = getChildNode(groupNode, "subgroups");

            if (neuronsNode != null && subgroupsNode != null) {
                throw new SnnParseException("Group '" + subgroup.getFullName() + "' cannot have both 'neurons' and 'subgroups'.", groupNode.getStartMark());
            }

            if (neuronsNode != null) {
                loadNeuronData(neuronsNode, subgroup);
            } else if (subgroupsNode != null) {
                loadGroups(subgroupsNode, subgroup);
            }

            subgroup.setTotalCount(totalNeuronCount - subgroup.getStartIndex());
            parentGroup.getSubgroups().add(subgroup);
            currentStartIndex += subgroup.getTotalCount();
        }
    }

    private void loadNeuronData(Node neuronsNode, GroupInfo groupInfo) {
        if (!(neuronsNode instanceof SequenceNode sequenceNode)) {
            throw new SnnParseException("Expected sequence for 'neurons'.", neuronsNode.getStartMark());
        }

        for (Node nNode : sequenceNode.getValue()) {
            String typeName = nodeAs(getChildNodeRequired(nNode, "type"), String.class);
            int count = nodeAs(getChildNodeRequired(nNode, "count"), Integer.class);

            if (count > 0) {
                if (!neuronTypeToIdMap.containsKey(typeName)) {
                    throw new SnnParseException("Unknown neuron type: " + typeName, nNode.getStartMark());
                }
                int typeId = neuronTypeToIdMap.get(typeName);

                NeuronInfo nInfo = new NeuronInfo(typeId, count, totalNeuronCount);
                groupInfo.getNeurons().add(nInfo);

                for (int i = 0; i < count; i++) {
                    globalNeuronTypeIds.add(typeId);
                    IzhikevichParams params = neuronParamTypes.get(typeId);
                    initialV.add(params.v0());
                    initialU.add(params.u0());
                }
                totalNeuronCount += count;
            }
        }
    }

    protected List<GroupPair> findMatchingGroups(String fromPattern, String toPattern, boolean excludeSelf) {
        List<GroupPair> matchedPairs = new ArrayList<>();
        Map<Integer, String> wildcardValues = new HashMap<>();

        String[] fromSegments = fromPattern.split("\\.");
        String[] toSegments = toPattern.split("\\.");

        findMatchingGroupsRecursive(rootGroup, rootGroup, fromSegments, toSegments, 0, wildcardValues, excludeSelf, matchedPairs);
        return matchedPairs;
    }

    private void findMatchingGroupsRecursive(GroupInfo currentFrom, GroupInfo rootForTo,
                                           String[] fromSegments, String[] toSegments,
                                           int index, Map<Integer, String> wildcardValues,
                                           boolean excludeSelf, List<GroupPair> matchedPairs) {

        if (index == fromSegments.length) {
            findMatchingToGroups(currentFrom, rootForTo, toSegments, 0, wildcardValues, excludeSelf, matchedPairs);
            return;
        }

        String segment = fromSegments[index];

        if (isWildcard(segment)) {
            int wildcardId = getWildcardNumber(segment);

            // If this wildcard has been seen before, it must match the same subgroup
            if (wildcardValues.containsKey(wildcardId)) {
                String expectedName = wildcardValues.get(wildcardId);
                for (GroupInfo sub : currentFrom.getSubgroups()) {
                    if (sub.getName().equals(expectedName)) {
                        findMatchingGroupsRecursive(sub, rootForTo, fromSegments, toSegments, index + 1, wildcardValues, excludeSelf, matchedPairs);
                    }
                }
            } else {
                // New wildcard, try all subgroups
                for (GroupInfo sub : currentFrom.getSubgroups()) {
                    Map<Integer, String> nextWildcards = new HashMap<>(wildcardValues);
                    nextWildcards.put(wildcardId, sub.getName());
                    findMatchingGroupsRecursive(sub, rootForTo, fromSegments, toSegments, index + 1, nextWildcards, excludeSelf, matchedPairs);
                }
            }
        } else {
            // Literal segment, check if any subgroup matches exactly
            for (GroupInfo sub : currentFrom.getSubgroups()) {
                if (sub.getName().equals(segment)) {
                    findMatchingGroupsRecursive(sub, rootForTo, fromSegments, toSegments, index + 1, wildcardValues, excludeSelf, matchedPairs);
                }
            }
        }
    }

    private void findMatchingToGroups(GroupInfo fixedFrom, GroupInfo currentTo,
                                    String[] toSegments, int index,
                                    Map<Integer, String> wildcardValues,
                                    boolean excludeSelf, List<GroupPair> matchedPairs) {

        if (index == toSegments.length) {
            if (excludeSelf && fixedFrom.getFullName().equals(currentTo.getFullName())) {
                return;
            }
            matchedPairs.add(new GroupPair(fixedFrom, currentTo));
            return;
        }

        String segment = toSegments[index];

        if (isWildcard(segment)) {
            int wildcardId = getWildcardNumber(segment);
            if (wildcardValues.containsKey(wildcardId)) {
                String expectedName = wildcardValues.get(wildcardId);
                for (GroupInfo sub : currentTo.getSubgroups()) {
                    if (sub.getName().equals(expectedName)) {
                        findMatchingToGroups(fixedFrom, sub, toSegments, index + 1, wildcardValues, excludeSelf, matchedPairs);
                    }
                }
            } else {
                for (GroupInfo sub : currentTo.getSubgroups()) {
                    Map<Integer, String> nextWildcards = new HashMap<>(wildcardValues);
                    nextWildcards.put(wildcardId, sub.getName());
                    findMatchingToGroups(fixedFrom, sub, toSegments, index + 1, nextWildcards, excludeSelf, matchedPairs);
                }
            }
        } else {
            for (GroupInfo sub : currentTo.getSubgroups()) {
                if (sub.getName().equals(segment)) {
                    findMatchingToGroups(fixedFrom, sub, toSegments, index + 1, wildcardValues, excludeSelf, matchedPairs);
                }
            }
        }
    }

    private boolean isWildcard(String segment) {
        return segment.startsWith("[") && segment.endsWith("]") && segment.length() > 2;
    }

    private int getWildcardNumber(String segment) {
        return Integer.parseInt(segment.substring(1, segment.length() - 1));
    }

    private void loadConnections(Node node) {
        // TODO: Implement in next step
    }

    private Node getChildNode(Node parent, String key) {
        if (!(parent instanceof MappingNode mappingNode)) {
            return null;
        }
        for (NodeTuple tuple : mappingNode.getValue()) {
            if (tuple.getKeyNode() instanceof ScalarNode keyNode) {
                if (keyNode.getValue().equals(key)) {
                    return tuple.getValueNode();
                }
            }
        }
        return null;
    }

    private Node getChildNodeRequired(Node parent, String key) {
        Node child = getChildNode(parent, key);
        if (child == null) {
            throw new SnnParseException("Missing required key '" + key + "'.", parent.getStartMark());
        }
        return child;
    }

    @SuppressWarnings("unchecked")
    private <T> T nodeAs(Node node, Class<T> clazz) {
        if (!(node instanceof ScalarNode scalarNode)) {
            throw new SnnParseException("Expected scalar value.", node.getStartMark());
        }
        String value = scalarNode.getValue();
        try {
            if (clazz == Double.class) return (T) Double.valueOf(value);
            if (clazz == Integer.class) return (T) Integer.valueOf(value);
            if (clazz == String.class) return (T) value;
            if (clazz == Boolean.class) return (T) Boolean.valueOf(value);
        } catch (NumberFormatException e) {
            throw new SnnParseException("Invalid format for type " + clazz.getSimpleName() + ": " + value, node.getStartMark());
        }
        throw new IllegalArgumentException("Unsupported type: " + clazz);
    }
}