package ru.gavrilovegor519;

import ru.gavrilovegor519.dto.NodelistEntryDto;
import ru.gavrilovegor519.enums.CurrentNodelistTree;
import ru.gavrilovegor519.enums.Keywords;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Fidonet Nodelist parser
 */
public class Nodelist {

    private final Map<Integer, NodelistEntryDto> nodelistEntries = new HashMap<>();

    /**
     * Nodelist constructor with path to nodelist
     * @param path path to nodelist
     */
    public Nodelist(Path path) {
        if (path == null) {
            throw new IllegalArgumentException("Path is null");
        }

        if (!Files.exists(path) || !Files.isRegularFile(path)) {
            throw new IllegalArgumentException("File does not exist");
        }

        try {
            indexNodelist(Files.newInputStream(path));
        } catch (IOException e) {
            throw new IllegalArgumentException("Cannot read file", e);
        }
    }

    public Nodelist(Map<Integer, NodelistEntryDto> entries) {
        nodelistEntries.putAll(entries);
    }

    /**
     * Nodelist constructor with input stream
     * @param inputStream input stream
     */
    public Nodelist(InputStream inputStream) {
        indexNodelist(inputStream);
    }

    /**
     * Get all data from nodelist
     * @return {@link Map} of {@link NodelistEntryDto} with data from the nodelist
     * *
     * @see NodelistEntryDto
     */
    public Map<Integer, NodelistEntryDto> getNodelistEntries() {
        return nodelistEntries;
    }

    /**
     * Returns {@link NodelistEntryDto} by address
     *
     * @param address address of the node
     * @return {@link NodelistEntryDto} with data from the nodelist
     */
    public NodelistEntryDto getNodelistEntry(String address) {
        if (address == null) {
            throw new IllegalArgumentException("Address is null");
        }

        Matcher matcher = Pattern.compile("^([0-9]+):([0-9]+)/([0-9]+)$").matcher(address);

        if (!matcher.matches()) {
            throw new IllegalArgumentException("Incorrect address format");
        }

        int zone = Integer.parseInt(matcher.group(1));
        int network = Integer.parseInt(matcher.group(2));
        int node = Integer.parseInt(matcher.group(3));

        return getNodelistEntry(zone, network, node);
    }

    /**
     * Returns {@link NodelistEntryDto} by address
     *
     * @param zone zone number
     * @param network network number
     * @param node node number
     * @return {@link NodelistEntryDto} with data from the nodelist
     */
    public NodelistEntryDto getNodelistEntry(int zone, int network, int node) {
        NodelistEntryDto networkNodelistEntries = getNetworkNodelistEntries(zone, network);

        if (networkNodelistEntries.children().get(node) == null) {
            throw new IllegalArgumentException("Node is not valid");
        }

        return networkNodelistEntries.children().get(node);
    }

    /**
     * Get a list of nodes from the specified network
     *
     * @param zone zone number
     * @param network network number
     * @return list of nodes from the specified network
     */
    public NodelistEntryDto getNetworkNodelistEntries(int zone, int network) {
        NodelistEntryDto zoneNodelistEntries = getZoneNodelistEntries(zone);

        if (zoneNodelistEntries.children().get(network) == null) {
            throw new IllegalArgumentException("Network is not valid");
        }

        return zoneNodelistEntries.children().get(network);
    }

    /**
     * Returns a list of nodes from the specified zone
     *
     * @param zone zone number
     * @return list of nodes from the specified zone
     */
    public NodelistEntryDto getZoneNodelistEntries(int zone) {
        NodelistEntryDto nodelistEntryDto = nodelistEntries.get(zone);

        if (nodelistEntryDto == null) {
            throw new IllegalArgumentException("Zone is not valid");
        }

        return nodelistEntryDto;
    }

    /**
     * Reads Fidonet nodelist and indexes it in memory
     *
     * @param streamReader {@link InputStream} of the nodelist
     */
    private void indexNodelist(InputStream streamReader) {
        try (BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(streamReader))) {
            CurrentNodelistTree currentNodelistTree = null;
            int currentZone = 0;
            int currentNetwork = 0;

            while (bufferedReader.ready()) {
                String line = bufferedReader.readLine();

                if (!line.startsWith(";") || !line.isBlank()) {
                    // Fixes an issue with the empty keyword at the beginning of the nodelist
                    if (line.startsWith(",")) line = "###" + line;

                    String[] splitLine = line.split(",");

                    if (splitLine.length >= 7) {
                        if (Keywords.fromString(splitLine[0]) == Keywords.ZONE) {
                            nodelistEntries.put(Integer.valueOf(splitLine[1]), generateNodelistEntry(splitLine));
                            currentNodelistTree = CurrentNodelistTree.ZONE;
                            currentZone = Integer.parseInt(splitLine[1]);
                        } else if (Keywords.fromString(splitLine[0]) == Keywords.HOST ||
                                Keywords.fromString(splitLine[0]) == Keywords.REGION) {
                            nodelistEntries.get(currentZone).children()
                                    .put(Integer.valueOf(splitLine[1]), generateNodelistEntry(splitLine));
                            currentNodelistTree = CurrentNodelistTree.NETWORK;
                            currentNetwork = Integer.parseInt(splitLine[1]);
                        } else {
                            if (currentNodelistTree == CurrentNodelistTree.ZONE) {
                                nodelistEntries.get(currentZone).children()
                                        .put(Integer.valueOf(splitLine[1]), generateNodelistEntry(splitLine));
                            } else if (currentNodelistTree == CurrentNodelistTree.NETWORK) {
                                nodelistEntries.get(currentZone).children().get(currentNetwork).children()
                                        .put(Integer.valueOf(splitLine[1]), generateNodelistEntry(splitLine));
                            }
                        }
                    }
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to read nodelist", e);
        }
    }

    /**
     * Generates {@link NodelistEntryDto} from a line of the nodelist
     *
     * @param splitLine line of the nodelist
     * @return {@link NodelistEntryDto} with data from the line
     */
    private NodelistEntryDto generateNodelistEntry(String[] splitLine) {
        return new NodelistEntryDto(Keywords.fromString(splitLine[0]), splitLine[2], splitLine[3],
                splitLine[4], splitLine[5], Integer.parseInt(splitLine[6]),
                Arrays.copyOfRange(splitLine, 7, splitLine.length), new HashMap<>());
    }

}
