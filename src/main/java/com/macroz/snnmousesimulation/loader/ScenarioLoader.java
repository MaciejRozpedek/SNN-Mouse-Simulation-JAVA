package com.macroz.snnmousesimulation.loader;

import com.macroz.snnmousesimulation.core.SnnNetworkData;
import com.macroz.snnmousesimulation.exception.SnnParseException;
import com.macroz.snnmousesimulation.utility.ConfigParams;
import com.macroz.snnmousesimulation.utility.ConfigValue;
import com.macroz.snnmousesimulation.world.ScenarioData;
import com.macroz.snnmousesimulation.world.ScenarioData.AgentData;
import com.macroz.snnmousesimulation.world.WorldParams;
import com.macroz.snnmousesimulation.world.rule.WorldRuleConfig;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.error.Mark;
import org.yaml.snakeyaml.nodes.MappingNode;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.SequenceNode;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import static com.macroz.snnmousesimulation.loader.YamlNodes.configValue;
import static com.macroz.snnmousesimulation.loader.YamlNodes.getChildNodeRequired;
import static com.macroz.snnmousesimulation.loader.YamlNodes.loadParams;
import static com.macroz.snnmousesimulation.loader.YamlNodes.loadValues;
import static com.macroz.snnmousesimulation.loader.YamlNodes.nodeAs;

/**
 * Loads a scenario: the world, its rules and the agents with their brain configs.
 */
public class ScenarioLoader {

    /**
     * @param openFile opens a file referenced by the scenario (e.g. a brain config) by its
     *                 path relative to the scenario; returns null when the file does not exist
     */
    public ScenarioData load(InputStream inputStream, Function<String, InputStream> openFile) {
        if (inputStream == null) throw new SnnParseException("Wrong path to YAML file.");
        Node root = new Yaml().compose(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
        if (root == null) throw new SnnParseException("Empty YAML file.");
        if (!(root instanceof MappingNode)) throw new SnnParseException("Root must be a mapping.", root.getStartMark());

        return new ScenarioData(
                loadWorld(getChildNodeRequired(root, "world")),
                loadRules(getChildNodeRequired(root, "rules")),
                loadAgents(getChildNodeRequired(root, "agents"), openFile));
    }

    private WorldParams loadWorld(Node node) {
        Mark mark = node.getStartMark();
        ConfigParams worldParams = new ConfigParams(loadValues(node, "world"), mark.getLine() + 1, mark.getColumn() + 1);
        return worldParams.build(params -> new WorldParams(
                params.getRequiredDouble("width"),
                params.getRequiredDouble("height")));
    }

    private List<WorldRuleConfig> loadRules(Node node) {
        if (!(node instanceof SequenceNode sequenceNode)) {
            throw new SnnParseException("section 'rules' must be a sequence.", node.getStartMark());
        }

        List<WorldRuleConfig> rules = new ArrayList<>();
        for (Node ruleNode : sequenceNode.getValue()) {
            rules.add(new WorldRuleConfig(
                    nodeAs(getChildNodeRequired(ruleNode, "name"), String.class),
                    nodeAs(getChildNodeRequired(ruleNode, "rule_type"), String.class),
                    loadParams(ruleNode)
            ));
        }
        return rules;
    }

    private List<AgentData> loadAgents(Node node, Function<String, InputStream> openFile) {
        if (!(node instanceof SequenceNode sequenceNode)) {
            throw new SnnParseException("section 'agents' must be a sequence.", node.getStartMark());
        }
        if (sequenceNode.getValue().size() != 1) {
            throw new SnnParseException("Exactly one agent is supported for now.", node.getStartMark());
        }

        List<AgentData> agents = new ArrayList<>();
        for (Node agentNode : sequenceNode.getValue()) {
            String name = nodeAs(getChildNodeRequired(agentNode, "name"), String.class);
            SnnNetworkData brain = loadBrain(configValue(getChildNodeRequired(agentNode, "brain")), openFile);

            Node startNode = getChildNodeRequired(agentNode, "start");
            Mark startMark = startNode.getStartMark();
            ConfigParams startParams = new ConfigParams(loadValues(startNode, "start"), startMark.getLine() + 1, startMark.getColumn() + 1);
            agents.add(startParams.build(params -> new AgentData(
                    name,
                    brain,
                    params.getRequiredDouble("x"),
                    params.getRequiredDouble("y"),
                    params.getDouble("angle", 0.0))));
        }
        return agents;
    }

    private SnnNetworkData loadBrain(ConfigValue file, Function<String, InputStream> openFile) {
        InputStream inputStream = openFile.apply(file.raw());
        if (inputStream == null) {
            throw new SnnParseException("Brain file '" + file.raw() + "' not found.", file.line(), file.column());
        }
        try (inputStream) {
            return new NetworkTopologyLoader().load(inputStream);
        } catch (SnnParseException e) {
            throw new SnnParseException("Brain file '" + file.raw() + "': " + e.getMessage());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
