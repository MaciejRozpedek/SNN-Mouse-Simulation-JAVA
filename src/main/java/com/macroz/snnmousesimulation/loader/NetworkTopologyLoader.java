package com.macroz.snnmousesimulation.loader;

import com.macroz.snnmousesimulation.core.IzhikevichParams;
import com.macroz.snnmousesimulation.core.SnnNetworkData;
import com.macroz.snnmousesimulation.exception.SnnParseException;
import com.macroz.snnmousesimulation.loader.structure.GroupInfo;
import com.macroz.snnmousesimulation.loader.structure.NeuronInfo;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.nodes.MappingNode;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.NodeTuple;
import org.yaml.snakeyaml.nodes.ScalarNode;
import org.yaml.snakeyaml.nodes.SequenceNode;

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

    public SnnNetworkData load(InputStream inputStream) {
        Yaml yaml = new Yaml();
        if (inputStream == null) {
            throw new SnnParseException("Wrong path to YAML file.");
        }
        Node root = yaml.compose(new InputStreamReader(inputStream, StandardCharsets.UTF_8));

        if (root == null) {
            throw new SnnParseException("Empty YAML file.");
        }

        if (!(root instanceof MappingNode)) {
            throw new SnnParseException("Root must be a mapping.", root.getStartMark());
        }

        loadNeuronTypes(getChildNodeRequired(root, "neuron_types"));
        loadGroups(getChildNodeRequired(root, "groups"));
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
        // TODO: Implement parsing logic
    }

    private void loadGroups(Node node) {
        // TODO: Implement parsing logic
    }

    private void loadConnections(Node node) {
        // TODO: Implement parsing logic
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