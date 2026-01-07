package com.macroz.snnmousesimulation.loader;

import com.macroz.snnmousesimulation.core.IzhikevichParams;
import com.macroz.snnmousesimulation.core.SnnNetworkData;
import com.macroz.snnmousesimulation.core.input.InputConfig;
import com.macroz.snnmousesimulation.core.output.OutputConfig;
import com.macroz.snnmousesimulation.exception.SnnParseException;
import com.macroz.snnmousesimulation.loader.structure.GroupInfo;
import com.macroz.snnmousesimulation.loader.structure.NeuronInfo;
import com.macroz.snnmousesimulation.utility.WeightGenerator;
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

    // Helper to prevent duplicate connections during generation: existingConnections[srcIndex].contains(targetIndex)
    private final List<Set<Integer>> existingConnections = new ArrayList<>();

    private GroupInfo rootGroup;
    private int totalNeuronCount = 0;
    private final Random random = new Random();

    private final List<InputConfig> inputConfigs = new ArrayList<>();
    private final List<OutputConfig> outputConfigs = new ArrayList<>();

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
            existingConnections.add(new HashSet<>());
        }

        loadConnections(getChildNodeRequired(root, "connections"));

        Node inputsNode = getChildNode(root, "inputs");
        if (inputsNode != null) {
            loadInputs(inputsNode);
        }

        Node outputsNode = getChildNode(root, "outputs");
        if (outputsNode != null) {
            loadOutputs(outputsNode);
        }

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
                weightsArr,
                new ArrayList<>(inputConfigs),
                new ArrayList<>(outputConfigs)
        );
    }

    private void loadInputs(Node node) {
        if (!(node instanceof SequenceNode sequenceNode)) {
            throw new SnnParseException("section 'inputs' must be a sequence.", node.getStartMark());
        }

        for (Node inputNode : sequenceNode.getValue()) {
            String name = nodeAs(getChildNodeRequired(inputNode, "name"), String.class);
            String type = nodeAs(getChildNodeRequired(inputNode, "sensor_type"), String.class);
            String target = nodeAs(getChildNodeRequired(inputNode, "target_group"), String.class);
            String targetType = nodeAs(getChildNodeRequired(inputNode, "target_type"), String.class);

            Map<String, Object> params = new HashMap<>();
            Node paramsNode = getChildNode(inputNode, "params");
            if (paramsNode instanceof MappingNode mappingNode) {
                for (NodeTuple tuple : mappingNode.getValue()) {
                    String key = nodeAs(tuple.getKeyNode(), String.class);
                    Node valNode = tuple.getValueNode();
                    Object value = parseScalarValue((ScalarNode) valNode);
                    params.put(key, value);
                }
            }

            var targetGroup = findMatchingGroups(target, target, false).stream()
                    .filter(pair -> pair.from().getFullName().equals(pair.to().getFullName()))
                    .map(GroupPair::from)
                    .reduce( (a, b) -> {
                        throw new SnnParseException("Input target group '" + target + "' is ambiguous.", inputNode.getStartMark());
                    })
                    .orElseThrow(() -> new SnnParseException("Input target group '" + target + "' not found.", inputNode.getStartMark()));

            int targetTypeId = targetType.equals("all") ? -1 : getNeuronTypeId(targetType, inputNode);

            List<Integer> targetNeurons = collectNeurons(targetGroup, targetTypeId);

            inputConfigs.add(new InputConfig(
                    name,
                    type,
                    targetNeurons.stream().mapToInt(i -> i).toArray(),
                    params
            ));
        }
    }

    private void loadOutputs(Node node) {
        if (!(node instanceof SequenceNode sequenceNode)) {
            throw new SnnParseException("section 'outputs' must be a sequence.", node.getStartMark());
        }

        for (Node outputNode : sequenceNode.getValue()) {
            String name = nodeAs(getChildNodeRequired(outputNode, "name"), String.class);
            String strategyType = nodeAs(getChildNodeRequired(outputNode, "output_type"), String.class);
            String sourceGroupPattern = nodeAs(getChildNodeRequired(outputNode, "source_group"), String.class);
            String sourceType = nodeAs(getChildNodeRequired(outputNode, "source_type"), String.class);

            Map<String, Object> params = new HashMap<>();
            Node paramsNode = getChildNode(outputNode, "params");
            if (paramsNode instanceof MappingNode mappingNode) {
                for (NodeTuple tuple : mappingNode.getValue()) {
                    String key = nodeAs(tuple.getKeyNode(), String.class);
                    Object value = parseScalarValue((ScalarNode) tuple.getValueNode());
                    params.put(key, value);
                }
            }

            // Find the group (reusing your existing findMatchingGroups logic)
            // We assume the source_group points to a specific single group
            var sourceGroupPair = findMatchingGroups(sourceGroupPattern, sourceGroupPattern, false).stream()
                    .filter(pair -> pair.from().getFullName().equals(pair.to().getFullName())) // Ensure it's the group itself
                    .findFirst()
                    .orElseThrow(() -> new SnnParseException("Output source group '" + sourceGroupPattern + "' not found.", outputNode.getStartMark()));

            int sourceTypeId = sourceType.equals("all") ? -1 : getNeuronTypeId(sourceType, outputNode);
            List<Integer> sourceNeurons = collectNeurons(sourceGroupPair.from(), sourceTypeId);

            outputConfigs.add(new OutputConfig(
                    name,
                    strategyType,
                    sourceNeurons.stream().mapToInt(i -> i).toArray(),
                    params
            ));
        }
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

    private void loadConnections(Node node) {
        if (!(node instanceof SequenceNode sequenceNode)) {
            throw new SnnParseException("Expected sequence for 'connections'.", node.getStartMark());
        }

        for (Node connectionNode : sequenceNode.getValue()) {
            String fromGroup = nodeAs(getChildNodeRequired(connectionNode, "from"), String.class);
            String toGroup = nodeAs(getChildNodeRequired(connectionNode, "to"), String.class);
            String fromType = nodeAs(getChildNodeRequired(connectionNode, "from_type"), String.class);
            String toType = nodeAs(getChildNodeRequired(connectionNode, "to_type"), String.class);

            boolean excludeSelf = false;
            Node excludeNode = getChildNode(connectionNode, "exclude_self");
            if (excludeNode != null) {
                excludeSelf = nodeAs(excludeNode, Boolean.class);
            }

            Node ruleNode = getChildNodeRequired(connectionNode, "rule");
            Node weightNode = getChildNodeRequired(connectionNode, "weight");

            WeightGenerator weightGen = createWeightGenerator(weightNode);

            List<GroupPair> matchedPairs = findMatchingGroups(fromGroup, toGroup, excludeSelf);

            for (GroupPair pair : matchedPairs) {
                createConnectionsBetweenGroups(pair.from(), pair.to(), fromType, toType, ruleNode, weightGen);
            }
        }
    }

    private void createConnectionsBetweenGroups(GroupInfo fromGroup, GroupInfo toGroup,
                                                String fromType, String toType,
                                                Node ruleNode, WeightGenerator weightGen) {

        int fromTypeId = fromType.equals("all") ? -1 : getNeuronTypeId(fromType, ruleNode);
        int toTypeId = toType.equals("all") ? -1 : getNeuronTypeId(toType, ruleNode);

        List<Integer> fromNeurons = collectNeurons(fromGroup, fromTypeId);
        List<Integer> toNeurons = collectNeurons(toGroup, toTypeId);

        if (fromNeurons.isEmpty() || toNeurons.isEmpty()) return;

        String ruleType = nodeAs(getChildNodeRequired(ruleNode, "type"), String.class);

        Boolean allowAutapses = false;

        Node autapsesNode = getChildNode(ruleNode, "allow_autapses");
        if (autapsesNode != null) {
            allowAutapses = nodeAs(autapsesNode, Boolean.class);
        }


        switch (ruleType) {
            case "all_to_all" -> {
                for (int src : fromNeurons) {
                    for (int tgt : toNeurons) {
                        addConnection(src, tgt, weightGen, allowAutapses);
                    }
                }
            }
            case "one_to_one" -> {
                if (fromNeurons.size() != toNeurons.size()) {
                    throw new SnnParseException("Size mismatch for 'one_to_one' rule.", ruleNode.getStartMark());
                }
                for (int i = 0; i < fromNeurons.size(); i++) {
                    addConnection(fromNeurons.get(i), toNeurons.get(i), weightGen, allowAutapses);
                }
            }
            case "probabilistic" -> {
                double probability = nodeAs(getChildNodeRequired(ruleNode, "probability"), Double.class);
                for (int src : fromNeurons) {
                    for (int tgt : toNeurons) {
                        if (random.nextDouble() < probability) {
                            addConnection(src, tgt, weightGen, allowAutapses);
                        }
                    }
                }
            }
            case "fixed_out_degree" -> {
                int count = nodeAs(getChildNodeRequired(ruleNode, "count"), Integer.class);
                for (int src : fromNeurons) {
                    List<Integer> candidates = new ArrayList<>();
                    for (int tgt : toNeurons) {
                        if (!isValidConnection(src, tgt, allowAutapses)) continue;
                        candidates.add(tgt);
                    }
                    Collections.shuffle(candidates, random);
                    int limit = Math.min(count, candidates.size());
                    for (int k = 0; k < limit; k++) {
                        addConnection(src, candidates.get(k), weightGen, false);
                    }
                }
            }
            case "fixed_in_degree" -> {
                int count = nodeAs(getChildNodeRequired(ruleNode, "count"), Integer.class);
                for (int tgt : toNeurons) {
                    List<Integer> candidates = new ArrayList<>();
                    for (int src : fromNeurons) {
                        if (!isValidConnection(src, tgt, allowAutapses)) continue;
                        candidates.add(src);
                    }
                    Collections.shuffle(candidates, random);
                    int limit = Math.min(count, candidates.size());
                    for (int k = 0; k < limit; k++) {
                        addConnection(candidates.get(k), tgt, weightGen, false);
                    }
                }
            }
            default -> throw new SnnParseException("Unknown rule type: " + ruleType, ruleNode.getStartMark());
        }
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    private boolean isValidConnection(int src, int tgt, boolean allowAutapses) {
        if (allowAutapses && src == tgt) return false;
        return !existingConnections.get(src).contains(tgt);
    }

    private void addConnection(int src, int tgt, WeightGenerator weightGen, boolean allowAutapses) {
        if (!isValidConnection(src, tgt, allowAutapses)) return;

        if (existingConnections.get(src).contains(tgt)) return;

        synapticTargets.get(src).add(tgt);
        synapticWeights.get(src).add(weightGen.generate());
        existingConnections.get(src).add(tgt);
    }

    private List<Integer> collectNeurons(GroupInfo group, int typeId) {
        List<Integer> indices = new ArrayList<>();
        collectNeuronsRecursive(group, typeId, indices);
        return indices;
    }

    private void collectNeuronsRecursive(GroupInfo group, int typeId, List<Integer> indices) {
        if (!group.getSubgroups().isEmpty()) {
            for (GroupInfo sub : group.getSubgroups()) {
                collectNeuronsRecursive(sub, typeId, indices);
            }
        }
        for (NeuronInfo nInfo : group.getNeurons()) {
            if (typeId == -1 || nInfo.getTypeId() == typeId) {
                for (int i = 0; i < nInfo.getCount(); i++) {
                    indices.add(nInfo.getStartIndex() + i);
                }
            }
        }
    }

    private int getNeuronTypeId(String typeName, Node context) {
        if (!neuronTypeToIdMap.containsKey(typeName)) {
            throw new SnnParseException("Unknown neuron type '" + typeName + "' in connections.", context.getStartMark());
        }
        return neuronTypeToIdMap.get(typeName);
    }

    private WeightGenerator createWeightGenerator(Node weightNode) {
        try {
            if (!(weightNode instanceof MappingNode)) {
                throw new SnnParseException("Weight definition must be a map.", weightNode.getStartMark());
            }

            Node fixedNode = getChildNode(weightNode, "fixed");
            Node uniformNode = getChildNode(weightNode, "uniform");
            Node normalNode = getChildNode(weightNode, "normal");

            int count = 0;
            if (fixedNode != null) count++;
            if (uniformNode != null) count++;
            if (normalNode != null) count++;

            if (count != 1) {
                throw new SnnParseException("Exactly one weight type must be specified (fixed, uniform, or normal).", weightNode.getStartMark());
            }

            if (fixedNode != null) {
                return WeightGenerator.createFixed(nodeAs(fixedNode, Double.class));
            }

            if (uniformNode != null) {
                double min = nodeAs(getChildNodeRequired(uniformNode, "min"), Double.class);
                double max = nodeAs(getChildNodeRequired(uniformNode, "max"), Double.class);
                return WeightGenerator.createUniform(min, max);
            }

            if (normalNode != null) {
                double mean = nodeAs(getChildNodeRequired(normalNode, "mean"), Double.class);
                double std = nodeAs(getChildNodeRequired(normalNode, "std"), Double.class);
                return WeightGenerator.createNormal(mean, std);
            }

            throw new SnnParseException("Unknown weight type. Expected fixed, uniform or normal.", weightNode.getStartMark());

        } catch (SnnParseException e) {
            throw e;
        } catch (Exception e) {
            throw new SnnParseException("Unexpected error during weight generation: " + e.getMessage(), weightNode.getStartMark());
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

            if (wildcardValues.containsKey(wildcardId)) {
                String expectedName = wildcardValues.get(wildcardId);
                for (GroupInfo sub : currentFrom.getSubgroups()) {
                    if (sub.getName().equals(expectedName)) {
                        findMatchingGroupsRecursive(sub, rootForTo, fromSegments, toSegments, index + 1, wildcardValues, excludeSelf, matchedPairs);
                    }
                }
            } else {
                for (GroupInfo sub : currentFrom.getSubgroups()) {
                    Map<Integer, String> nextWildcards = new HashMap<>(wildcardValues);
                    nextWildcards.put(wildcardId, sub.getName());
                    findMatchingGroupsRecursive(sub, rootForTo, fromSegments, toSegments, index + 1, nextWildcards, excludeSelf, matchedPairs);
                }
            }
        } else {
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

    private Object parseScalarValue(ScalarNode node) {
        String val = node.getValue();
        try { return Integer.parseInt(val); } catch (NumberFormatException e1) {}
        try { return Double.parseDouble(val); } catch (NumberFormatException e2) {}
        if ("true".equalsIgnoreCase(val) || "false".equalsIgnoreCase(val)) return Boolean.parseBoolean(val);
        return val;
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