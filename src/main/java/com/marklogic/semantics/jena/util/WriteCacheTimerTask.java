package com.marklogic.semantics.jena.util;

import java.util.Date;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hp.hpl.jena.graph.Graph;
import com.hp.hpl.jena.graph.Node;
import com.hp.hpl.jena.graph.NodeFactory;
import com.hp.hpl.jena.graph.Triple;
import com.hp.hpl.jena.sparql.graph.GraphFactory;
import com.marklogic.semantics.jena.graph.MarkLogicDatasetGraph;

public class WriteCacheTimerTask extends TimerTask {

    private ConcurrentHashMap<Node, Graph> cache;
    private MarkLogicDatasetGraph outer;

    private static long DEFAULT_CACHE_SIZE = 500;
    private long cacheSize = DEFAULT_CACHE_SIZE;
    private static long DEFAULT_CACHE_MILLIS = 1000;
    private long cacheMillis = DEFAULT_CACHE_MILLIS;
    private Date lastCacheAccess = new Date();
    private static Node DEFAULT_GRAPH_NODE = NodeFactory.createURI(MarkLogicDatasetGraph.DEFAULT_GRAPH_URI);
    
    private static Logger log = LoggerFactory.getLogger(WriteCacheTimerTask.class);
    
    public WriteCacheTimerTask(MarkLogicDatasetGraph outer) {
        super();
        this.cache = new ConcurrentHashMap<Node, Graph>();
        this.outer = outer;
    }

    @Override
    public void run() {
        Date now = new Date();
        if (cache.size() > cacheSize || cache.size() > 0 && now.getTime() - lastCacheAccess.getTime() > cacheMillis) {
            log.debug("Cache stale, flushing");
            flush();
        } else {
            return;
        }
    }
    
    private synchronized void flush() {
        for (Node graphNode : cache.keySet()) {
            log.debug("Persisting " + graphNode);
            outer.mergeGraph(graphNode, cache.get(graphNode));
        }
        if (cache.containsKey(DEFAULT_GRAPH_NODE)) {
            outer.mergeGraph(DEFAULT_GRAPH_NODE, cache.get(DEFAULT_GRAPH_NODE));
        }
        lastCacheAccess = new Date();
        cache.clear();
    }
    
    public void forceRun() {
        flush();
    }

    public synchronized void add(Node g, Node s, Node p, Node o) {
        Triple newTiple = new Triple(s, p, o);
        if (g == null) {
            g = DEFAULT_GRAPH_NODE;
        }
        if (cache.containsKey(g)) {
            cache.get(g).add(newTiple);
        } else {
            Graph graph = GraphFactory.createGraphMem();
            graph.add(newTiple);
            cache.put(g, graph);
        }
    }
}