package edu.hm.hafner.coverage;

import java.io.Serializable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.NavigableMap;
import java.util.NavigableSet;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.Fraction;

import edu.hm.hafner.util.Ensure;
import edu.umd.cs.findbugs.annotations.CheckForNull;

/**
 * A hierarchical decomposition of coverage results.
 *
 * @author Ullrich Hafner
 */
@SuppressWarnings("PMD.GodClass")
public class CoverageNode implements Serializable {
    private static final long serialVersionUID = -6608885640271135273L;

    private static final Coverage COVERED_NODE = new Coverage(1, 0);
    private static final Coverage MISSED_NODE = new Coverage(0, 1);

    static final String ROOT = "^";

    private final CoverageMetric metric;
    private final String name;
    private final List<CoverageNode> children = new ArrayList<>();
    private final List<CoverageLeaf> leaves = new ArrayList<>();
    @CheckForNull
    private CoverageNode parent;

    /**
     * Creates a new coverage item node with the given name.
     *
     * @param metric
     *         the coverage metric this node belongs to
     * @param name
     *         the human-readable name of the node
     */
    public CoverageNode(final CoverageMetric metric, final String name) {
        this.metric = metric;
        this.name = name;
    }

    /**
     * Gets the parent node.
     *
     * @return the parent, if existent
     * @throws NoSuchElementException
     *         if no parent exists
     */
    public CoverageNode getParent() {
        if (parent == null) {
            throw new NoSuchElementException("Parent is not set");
        }
        return parent;
    }

    /**
     * Returns the source code path of this node.
     *
     * @return the element type
     */
    public String getPath() {
        return StringUtils.EMPTY;
    }

    protected String mergePath(final String localPath) {
        // default packages are named '-' at the moment
        if ("-".equals(localPath)) {
            return StringUtils.EMPTY;
        }

        if (hasParent()) {
            String parentPath = getParent().getPath();

            if (StringUtils.isBlank(parentPath)) {
                return localPath;
            }
            if (StringUtils.isBlank(localPath)) {
                return parentPath;
            }
            return parentPath + "/" + localPath;
        }

        return localPath;
    }

    /**
     * Returns the type of the coverage metric for this node.
     *
     * @return the element type
     */
    public CoverageMetric getMetric() {
        return metric;
    }

    /**
     * Returns the available coverage metrics for the whole tree starting with this node.
     *
     * @return the elements in this tree
     */
    public NavigableSet<CoverageMetric> getMetrics() {
        NavigableSet<CoverageMetric> elements = children.stream()
                .map(CoverageNode::getMetrics)
                .flatMap(Collection::stream)
                .collect(Collectors.toCollection(TreeSet::new));

        elements.add(getMetric());
        leaves.stream().map(CoverageLeaf::getMetric).forEach(elements::add);

        return elements;
    }

    /**
     * Returns a mapping of metric to coverage. The root of the tree will be skipped.
     *
     * @return a mapping of metric to coverage.
     */
    public NavigableMap<CoverageMetric, Coverage> getMetricsDistribution() {
        return getMetrics().stream()
                .collect(Collectors.toMap(Function.identity(), this::getCoverage, (o1, o2) -> o1, TreeMap::new));
    }

    public NavigableMap<CoverageMetric, Fraction> getMetricsPercentages() {
        return getMetrics().stream().collect(Collectors.toMap(
                Function.identity(),
                searchMetric -> getCoverage(searchMetric).getCoveredPercentage(),
                (o1, o2) -> o1, // is never reached because stream input is already a set
                TreeMap::new));
    }

    public String getName() {
        return name;
    }

    public List<CoverageNode> getChildren() {
        return children;
    }

    public List<CoverageLeaf> getLeaves() {
        return leaves;
    }

    private void addAll(final List<CoverageNode> nodes) {
        nodes.forEach(this::add);
    }

    /**
     * Appends the specified child element to the list of children.
     *
     * @param child
     *         the child to add
     */
    public void add(final CoverageNode child) {
        children.add(child);
        child.setParent(this);
    }

    /**
     * Appends the specified leaf element to the list of leaves.
     *
     * @param leaf
     *         the leaf to add
     */
    public void add(final CoverageLeaf leaf) {
        leaves.add(leaf);
    }

    /**
     * Returns whether this node is the root of the tree.
     *
     * @return {@code true} if this node is the root of the tree, {@code false} otherwise
     */
    public boolean isRoot() {
        return parent == null;
    }

    /**
     * Returns whether this node has a parent node.
     *
     * @return {@code true} if this node has a parent node, {@code false} if it is the root of the hierarchy
     */
    public boolean hasParent() {
        return !isRoot();
    }

    void setParent(final CoverageNode parent) {
        this.parent = Objects.requireNonNull(parent);
    }

    /**
     * Returns the name of the parent element or {@link #ROOT} if there is no such element.
     *
     * @return the name of the parent element
     */
    public String getParentName() {
        if (parent == null) {
            return ROOT;
        }
        CoverageMetric type = parent.getMetric();

        List<String> parentsOfSameType = new ArrayList<>();
        for (CoverageNode node = parent; node != null && node.getMetric().equals(type); node = node.parent) {
            parentsOfSameType.add(0, node.getName());
        }
        return String.join(".", parentsOfSameType);
    }

    /**
     * Prints the coverage for the specified element. Uses {@code Locale.getDefault()} to format the percentage.
     *
     * @param searchMetric
     *         the element to print the coverage for
     *
     * @return coverage ratio in a human-readable format
     * @see #printCoverageFor(CoverageMetric, Locale)
     */
    public String printCoverageFor(final CoverageMetric searchMetric) {
        return printCoverageFor(searchMetric, Locale.getDefault());
    }

    /**
     * Prints the coverage for the specified element.
     *
     * @param searchMetric
     *         the element to print the coverage for
     * @param locale
     *         the locale to use when formatting the percentage
     *
     * @return coverage ratio in a human-readable format
     */
    public String printCoverageFor(final CoverageMetric searchMetric, final Locale locale) {
        return getCoverage(searchMetric).formatCoveredPercentage(locale);
    }

    /**
     * Returns the coverage for the specified metric.
     *
     * @param searchMetric
     *         the element to get the coverage for
     *
     * @return coverage ratio
     */
    public Coverage getCoverage(final CoverageMetric searchMetric) {
        Coverage childrenCoverage = aggregateChildren(searchMetric);
        if (searchMetric.isLeaf()) {
            return leaves.stream()
                    .map(node -> node.getCoverage(searchMetric))
                    .reduce(childrenCoverage, Coverage::add);
        }
        else {
            if (metric.equals(searchMetric)) {
                if (getCoverage(CoverageMetric.LINE).getCovered() > 0) {
                    return childrenCoverage.add(COVERED_NODE);
                }
                else {
                    return childrenCoverage.add(MISSED_NODE);
                }
            }
            return childrenCoverage;
        }
    }

    private Coverage aggregateChildren(final CoverageMetric searchMetric) {
        return children.stream()
                .map(node -> node.getCoverage(searchMetric))
                .reduce(Coverage.NO_COVERAGE, Coverage::add);
    }

    /**
     * Computes the coverage delta between this node and the specified reference node.
     *
     * @param reference
     *         the reference node
     *
     * @return the delta coverage for each available metric
     */
    public SortedMap<CoverageMetric, Fraction> computeDelta(final CoverageNode reference) {
        SortedMap<CoverageMetric, Fraction> deltaPercentages = new TreeMap<>();
        SortedMap<CoverageMetric, Fraction> metricPercentages = getMetricsPercentages();
        SortedMap<CoverageMetric, Fraction> referencePercentages = reference.getMetricsPercentages();
        metricPercentages.forEach((key, value) ->
                deltaPercentages.put(key,
                        saveSubtractFraction(value, referencePercentages.getOrDefault(key, Fraction.ZERO))));
        return deltaPercentages;
    }

    /**
     * Calculates the difference between two fraction. Since there might be an arithmetic exception due to an overflow,
     * the method handles it and calculates the difference based on the double values of the fractions.
     *
     * @param minuend
     *         The minuend as a fraction
     * @param subtrahend
     *         The subtrahend as a fraction
     *
     * @return the difference as a fraction
     */
    private Fraction saveSubtractFraction(final Fraction minuend, final Fraction subtrahend) {
        try {
            return minuend.subtract(subtrahend);
        }
        catch (ArithmeticException e) {
            double diff = minuend.doubleValue() - subtrahend.doubleValue();
            return Fraction.getFraction(diff);
        }
    }

    /**
     * Returns recursively all nodes for the specified metric type.
     *
     * @param searchMetric
     *         the metric to look for
     *
     * @return all nodes for the given metric
     * @throws AssertionError
     *         if the coverage metric is a LEAF metric
     */
    public List<CoverageNode> getAll(final CoverageMetric searchMetric) {
        Ensure.that(searchMetric.isLeaf())
                .isFalse("Leaves like '%s' are not stored as inner nodes of the tree", searchMetric);

        List<CoverageNode> childNodes = children.stream()
                .map(child -> child.getAll(searchMetric))
                .flatMap(List::stream).collect(Collectors.toList());
        if (metric.equals(searchMetric)) {
            childNodes.add(this);
        }
        return childNodes;
    }

    /**
     * Finds the coverage metric with the given name starting from this node.
     *
     * @param searchMetric
     *         the coverage metric to search for
     * @param searchName
     *         the name of the node
     *
     * @return the result if found
     */
    public Optional<CoverageNode> find(final CoverageMetric searchMetric, final String searchName) {
        if (matches(searchMetric, searchName)) {
            return Optional.of(this);
        }
        return children.stream()
                .map(child -> child.find(searchMetric, searchName))
                .flatMap(o -> o.map(Stream::of).orElseGet(Stream::empty))
                .findAny();
    }

    /**
     * Finds the coverage metric with the given hash code starting from this node.
     *
     * @param searchMetric
     *         the coverage metric to search for
     * @param searchNameHashCode
     *         the hash code of the node name
     *
     * @return the result if found
     */
    public Optional<CoverageNode> findByHashCode(final CoverageMetric searchMetric, final int searchNameHashCode) {
        if (matches(searchMetric, searchNameHashCode)) {
            return Optional.of(this);
        }
        return children.stream()
                .map(child -> child.findByHashCode(searchMetric, searchNameHashCode))
                .flatMap(o -> o.map(Stream::of).orElseGet(Stream::empty))
                .findAny();
    }

    /**
     * Returns whether this node matches the specified coverage metric and name.
     *
     * @param searchMetric
     *         the coverage metric to search for
     * @param searchName
     *         the name of the node
     *
     * @return the result if found
     */
    public boolean matches(final CoverageMetric searchMetric, final String searchName) {
        return metric.equals(searchMetric) && name.equals(searchName);
    }

    /**
     * Returns whether this node matches the specified coverage metric and name.
     *
     * @param searchMetric
     *         the coverage metric to search for
     * @param searchNameHashCode
     *         the hash code of the node name
     *
     * @return the result if found
     */
    public boolean matches(final CoverageMetric searchMetric, final int searchNameHashCode) {
        if (!metric.equals(searchMetric)) {
            return false;
        }
        return name.hashCode() == searchNameHashCode || getPath().hashCode() == searchNameHashCode;
    }

    /**
     * Splits flat packages into a package hierarchy. Changes the internal tree structure in place.
     */
    public void splitPackages() {
        if (CoverageMetric.MODULE.equals(metric)) {
            List<CoverageNode> allPackages = children.stream()
                    .filter(child -> CoverageMetric.PACKAGE.equals(child.getMetric()))
                    .collect(Collectors.toList());
            if (!allPackages.isEmpty()) {
                children.clear();
                for (CoverageNode packageNode : allPackages) {
                    String[] packageParts = packageNode.getName().split("\\.");
                    if (packageParts.length > 1) {
                        Deque<String> packageLevels = new ArrayDeque<>(Arrays.asList(packageParts));
                        insertPackage(packageNode, packageLevels);
                    }
                    else {
                        add(packageNode);
                    }
                }
            }
        }
    }

    /**
     * Creates a deep copy of the coverage tree with this as root node.
     *
     * @return the root node of the copied tree
     */
    public CoverageNode copyTree() {
        return copyTree(null);
    }

    /**
     * Recursively copies the coverage tree with the passed {@link CoverageNode} as root.
     *
     * @param copiedParent
     *         The root node
     *
     * @return the copied tree
     */
    protected CoverageNode copyTree(@CheckForNull final CoverageNode copiedParent) {
        CoverageNode copy = copyEmpty();
        if (copiedParent != null) {
            copy.setParent(copiedParent);
        }

        getChildren().stream()
                .map(node -> node.copyTree(this))
                .forEach(copy::add);
        getLeaves().forEach(copy::add);

        return copy;
    }

    /**
     * Creates a copied instance of this node that has no children, leaves, and parent yet.
     *
     * @return the new and empty node
     */
    protected CoverageNode copyEmpty() {
        return new CoverageNode(metric, name);
    }

    private void insertPackage(final CoverageNode aPackage, final Deque<String> packageLevels) {
        String nextLevelName = packageLevels.pop();
        CoverageNode subPackage = createChild(nextLevelName);
        if (packageLevels.isEmpty()) {
            subPackage.addAll(aPackage.children);
        }
        else {
            subPackage.insertPackage(aPackage, packageLevels);
        }
    }

    private CoverageNode createChild(final String childName) {
        for (CoverageNode child : children) {
            if (child.getName().equals(childName)) {
                return child;
            }

        }
        CoverageNode newNode = new PackageCoverageNode(childName);
        add(newNode);
        return newNode;
    }

    @Override
    public boolean equals(@CheckForNull final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        CoverageNode that = (CoverageNode) o;
        return Objects.equals(metric, that.metric) && Objects.equals(name, that.name)
                && Objects.equals(children, that.children) && Objects.equals(leaves, that.leaves);
    }

    @Override
    public int hashCode() {
        return Objects.hash(metric, name, children, leaves);
    }

    @Override
    public String toString() {
        return String.format("[%s] %s", getMetric(), getName());
    }

    /**
     * Combines two related or unrelated coverage-reports.
     * @param other module to combine with
     * @return combined report
     */
    public CoverageNode combineWith(final CoverageNode other) {

        if (!other.getMetric().equals(CoverageMetric.MODULE)) {
            throw new IllegalArgumentException("Provided Node is not of MetricType MODULE");
        }
        if (!this.getMetric().equals(CoverageMetric.MODULE)) {
            throw new IllegalStateException("Cannot perform combineWith on a non-module Node");
        }

        final CoverageNode combinedReport;
        if (this.getName().equals(other.getName())) {
            combinedReport = this.copyTree();
            combinedReport.safelyCombineChildren(other);
        }
        else {
            combinedReport = new CoverageNode(CoverageMetric.GROUP, "Combined Report");
            combinedReport.add(this.copyTree());
            combinedReport.add(other.copyTree());
        }

        return combinedReport;
    }

    private void safelyCombineChildren(final CoverageNode other) {
        if (!this.leaves.isEmpty()) {
            if (other.getChildren().isEmpty()) {
                mergeLeaves(this.getMetricsDistribution(), other.getMetricsDistribution());
                return;
            }
            this.leaves.clear();
        }

        other.getChildren().forEach(otherChild -> {
            Optional<CoverageNode> existingChild = this.getChildren().stream()
                    .filter(c -> c.getName().equals(otherChild.getName())).findFirst();
            if (existingChild.isPresent()) {
                existingChild.get().safelyCombineChildren(otherChild);
            }
            else {
                this.add(otherChild.copyTree());
            }
        });
    }

    private void mergeLeaves(final SortedMap<CoverageMetric, Coverage> metricsDistribution, final SortedMap<CoverageMetric, Coverage> metricsDistributionOther) {
        if (!metricsDistribution.keySet().equals(metricsDistributionOther.keySet())) {
            throw new IllegalStateException(
                    String.format("Reports to combine have a mismatch of leaves in %s %s", this.getMetric(), this.getName()));
        }

        leaves.clear();
        metricsDistribution.keySet().forEach(key -> {
            if (metricsDistribution.get(key).getTotal() != metricsDistributionOther.get(key).getTotal()) {
                throw new IllegalStateException(
                        String.format("Reports to combine have a mismatch of total %s coverage in %s %s",
                                key.getName(), this.getMetric(), this.getName()));
            }
            Coverage maxCoverage = Stream.of(metricsDistribution.get(key), metricsDistributionOther.get(key))
                    .max(Comparator.comparing(Coverage::getCovered)).get();
            leaves.add(new CoverageLeaf(key, maxCoverage));
        });
    }
}
