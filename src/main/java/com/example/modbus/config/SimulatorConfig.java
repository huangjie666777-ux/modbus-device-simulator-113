package com.example.modbus.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Loads and validates the JSON simulator configuration. */
public final class SimulatorConfig {

    public record Block(int start, boolean[] coils, int[] registers) {}

    public record Unit(int unitId, Block coils, Block holdingRegisters) {}

    public final int port;
    public final int maxConnections;
    public final List<Unit> units;

    private SimulatorConfig(int port, int maxConnections, List<Unit> units) {
        this.port = port;
        this.maxConnections = maxConnections;
        this.units = units;
    }

    public static SimulatorConfig load(Path path) throws IOException {
        JsonNode root = new ObjectMapper().readTree(path.toFile());
        int port = requiredInt(root, "port");
        if (port < 1024 || port > 65535) {
            throw new IllegalArgumentException("port must be a high port (1024-65535): " + port);
        }
        int maxConnections = root.has("maxConnections") ? root.get("maxConnections").asInt() : 32;
        if (maxConnections < 1 || maxConnections > 1024) {
            throw new IllegalArgumentException("maxConnections out of range: " + maxConnections);
        }
        JsonNode unitsNode = root.get("units");
        if (unitsNode == null || !unitsNode.isArray() || unitsNode.isEmpty()) {
            throw new IllegalArgumentException("config must contain a non-empty 'units' array");
        }
        List<Unit> units = new ArrayList<>();
        Set<Integer> seenIds = new HashSet<>();
        for (JsonNode u : unitsNode) {
            int unitId = requiredInt(u, "unitId");
            if (unitId < 1 || unitId > 247) {
                throw new IllegalArgumentException("unitId out of range (1-247): " + unitId);
            }
            if (!seenIds.add(unitId)) {
                throw new IllegalArgumentException("duplicate unitId: " + unitId);
            }
            units.add(new Unit(unitId, parseCoils(u.get("coils")), parseRegisters(u.get("holdingRegisters"))));
        }
        return new SimulatorConfig(port, maxConnections, List.copyOf(units));
    }

    private static Block parseCoils(JsonNode node) {
        if (node == null) return new Block(0, new boolean[0], null);
        int start = requiredInt(node, "start");
        JsonNode values = node.get("values");
        if (values == null || !values.isArray() || values.isEmpty()) {
            throw new IllegalArgumentException("coils.values must be a non-empty array");
        }
        checkRange(start, values.size(), "coils");
        boolean[] coils = new boolean[values.size()];
        for (int i = 0; i < coils.length; i++) {
            if (!values.get(i).isBoolean()) {
                throw new IllegalArgumentException("coil initial value must be boolean at index " + i);
            }
            coils[i] = values.get(i).asBoolean();
        }
        return new Block(start, coils, null);
    }

    private static Block parseRegisters(JsonNode node) {
        if (node == null) return new Block(0, null, new int[0]);
        int start = requiredInt(node, "start");
        JsonNode values = node.get("values");
        if (values == null || !values.isArray() || values.isEmpty()) {
            throw new IllegalArgumentException("holdingRegisters.values must be a non-empty array");
        }
        checkRange(start, values.size(), "holdingRegisters");
        int[] regs = new int[values.size()];
        for (int i = 0; i < regs.length; i++) {
            JsonNode v = values.get(i);
            if (!v.isInt() || v.asInt() < 0 || v.asInt() > 0xFFFF) {
                throw new IllegalArgumentException("register initial value must be 0-65535 at index " + i);
            }
            regs[i] = v.asInt();
        }
        return new Block(start, null, regs);
    }

    private static void checkRange(int start, int count, String what) {
        if (start < 0 || start + count > 65536) {
            throw new IllegalArgumentException(what + " range out of bounds: start=" + start + " count=" + count);
        }
    }

    private static int requiredInt(JsonNode node, String field) {
        JsonNode v = node.get(field);
        if (v == null || !v.isInt()) {
            throw new IllegalArgumentException("missing or non-integer field: " + field);
        }
        return v.asInt();
    }
}
