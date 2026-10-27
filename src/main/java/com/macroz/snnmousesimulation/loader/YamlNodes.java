package com.macroz.snnmousesimulation.loader;

import com.macroz.snnmousesimulation.exception.SnnParseException;
import com.macroz.snnmousesimulation.utility.ConfigValue;
import org.yaml.snakeyaml.error.Mark;
import org.yaml.snakeyaml.nodes.MappingNode;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.NodeTuple;
import org.yaml.snakeyaml.nodes.ScalarNode;
import org.yaml.snakeyaml.nodes.Tag;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Reading of YAML nodes shared by the config loaders. Errors report the position in the file.
 */
final class YamlNodes {

    private YamlNodes() {
    }

    static Node getChildNode(Node parent, String key) {
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

    static Node getChildNodeRequired(Node parent, String key) {
        Node child = getChildNode(parent, key);
        if (child == null) {
            throw new SnnParseException("Missing required key '" + key + "'.", parent.getStartMark());
        }
        return child;
    }

    @SuppressWarnings("unchecked")
    static <T> T nodeAs(Node node, Class<T> clazz) {
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

    // Raw value with its position; its user converts it to the type it expects
    static ConfigValue configValue(Node node) {
        if (!(node instanceof ScalarNode scalarNode)) {
            throw new SnnParseException("Expected scalar value.", node.getStartMark());
        }
        Mark mark = scalarNode.getStartMark();
        return new ConfigValue(scalarNode.getValue(), !scalarNode.isPlain(), mark.getLine() + 1, mark.getColumn() + 1);
    }

    static Map<String, ConfigValue> loadParams(Node parentNode) {
        Node paramsNode = getChildNode(parentNode, "params");
        if (paramsNode == null || Tag.NULL.equals(paramsNode.getTag())) {
            return Map.of();
        }
        return loadValues(paramsNode, "params");
    }

    static Map<String, ConfigValue> loadValues(Node node, String sectionName) {
        if (!(node instanceof MappingNode mappingNode)) {
            throw new SnnParseException("'" + sectionName + "' must be a map.", node.getStartMark());
        }

        Map<String, ConfigValue> params = new LinkedHashMap<>();
        for (NodeTuple tuple : mappingNode.getValue()) {
            String name = nodeAs(tuple.getKeyNode(), String.class);
            if (!(tuple.getValueNode() instanceof ScalarNode valueNode)) {
                throw new SnnParseException("Parameter '" + name + "' must be a single value.", tuple.getValueNode().getStartMark());
            }
            if (params.containsKey(name)) {
                throw new SnnParseException("Duplicate parameter '" + name + "'.", tuple.getKeyNode().getStartMark());
            }
            params.put(name, configValue(valueNode));
        }
        return Collections.unmodifiableMap(params);
    }
}
