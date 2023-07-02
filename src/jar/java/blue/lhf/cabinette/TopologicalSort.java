package blue.lhf.cabinette;

import com.google.common.graph.*;

import java.util.*;

@SuppressWarnings("UnstableApiUsage")
public class TopologicalSort {
    private TopologicalSort() {}

    public static <N> Iterable<N> sort(final Graph<N> inputGraph) {
        final MutableGraph<N> graph = Graphs.copyOf(inputGraph);

        final Deque<N> roots = new ArrayDeque<>(findRootNodes(graph));
        final List<N> sorted = new ArrayList<>();

        while (!roots.isEmpty()) {
            final N root = roots.pop();
            sorted.add(root);

            for (final N successor : graph.successors(root)) {
                graph.removeEdge(root, successor);
                if (graph.inDegree(successor) == 0) roots.push(successor);
            }
        }

        if (!graph.edges().isEmpty()) throw new IllegalArgumentException("Cyclic dependencies detected");
        return sorted;
    }

    private static <N> List<N> findRootNodes(final Graph<N> graph) {
        final List<N> roots = new ArrayList<>();
        for (final N node : graph.nodes()) {
            if (graph.inDegree(node) == 0) roots.add(node);
        }

        return roots;
    }
}
