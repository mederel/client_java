package io.prometheus.client;

import lombok.Data;
import org.apache.commons.lang3.StringUtils;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.Matchers;
import org.hamcrest.TypeSafeMatcher;
import org.junit.Assert;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * A Hamcrest matcher for {@link CollectorRegistry} to assert that it contains a certain metric, match its labels and
 * value too.
 *
 * <p>
 * Examples:
 * </p>
 *
 * <pre>
 * assertThat(CollectorRegistry.defaultRegistry, metric("my_name")
 *     .label("my_label", "label_value").value(greatherThan(4d));
 * assertThat(CollectorRegistry.defaultRegistry, metric("a_metric_name"));
 * assertThat(CollectorRegistry.defaultRegistry, metric("another_metric_name").intValue(5));
 * </pre>
 */
public class MetricCollectionMatcher<T> extends TypeSafeMatcher<T> {
  private static final double EPSILON_FOR_INTEGERS = 0.1D;

  private final boolean matchPresence;

  private final Class<T> klazz;

  private final String name;

  private final Map<Matcher<String>, Matcher<String>> labels = new HashMap<Matcher<String>, Matcher<String>>();

  private Matcher<Double> value;

  private Map<String, List<Double>> foundValues;

  private List<UnmatchedLabel> unmatchedLabels;

  private Set<String> actualNames;

  private Set<String> actualSampleNames;

  MetricCollectionMatcher(boolean matchPresence, Class<T> klazz, String name) {
    if (StringUtils.isBlank(name)) {
      Assert.fail("Cannot match a metric without at least a name");
    }
    this.matchPresence = matchPresence;
    this.klazz = klazz;
    this.name = name;
  }

  /**
   * Main method to get a metric matcher on a {@link CollectorRegistry}. You are looking for a metric with a given name.
   *
   * <p>
   * Use {@link MetricCollectionMatcher#label(String, String)} and {@link MetricCollectionMatcher#value(Matcher)}
   * methods to
   * narrow down your search.
   * </p>
   *
   * @param name name of the metric. Both the family sample and the sample name are supported. I.e. for an histogram metric
   *             <code>cxf_client_elapsed_time_seconds</code>, there are sample names prefixed with the family name, like
   *             <code>cxf_client_elapsed_time_seconds_count</code> and <code>cxf_client_elapsed_time_seconds_sum</code>.
   * @return a matcher to {@link CollectorRegistry} object that will check whether it contains a metric with given name.
   */
  public static MetricCollectionMatcher<CollectorRegistry> hasMetric(String name) {
    return new MetricCollectionMatcher<CollectorRegistry>(true, CollectorRegistry.class, name);
  }

  /**
   * Main method to get a matcher on a {@link CollectorRegistry} verify it does not contain any metric with that name.
   *
   * <p>
   * Use {@link MetricCollectionMatcher#label(String, String)} and {@link MetricCollectionMatcher#value(Matcher)}
   * methods to narrow down your match.
   * </p>
   *
   * @param name name of the metric. Both the family sample and the sample name are supported. I.e. for an histogram metric
   *             <code>cxf_client_elapsed_time_seconds</code>, there are sample names prefixed with the family name, like
   *             <code>cxf_client_elapsed_time_seconds_count</code> and <code>cxf_client_elapsed_time_seconds_sum</code>.
   * @return a matcher to {@link CollectorRegistry} object that will check that it does not contain a metric with given
   * name.
   */
  public static MetricCollectionMatcher<CollectorRegistry> doesNotHaveMetric(String name) {
    return new MetricCollectionMatcher<CollectorRegistry>(false, CollectorRegistry.class, name);
  }

  /**
   * Main method to get a metric matcher on a {@link Collector}. You are looking for a metric with a given name.
   *
   * <p>
   * Use {@link MetricCollectionMatcher#label(String, String)} and {@link MetricCollectionMatcher#value(Matcher)}
   * methods to
   * narrow down your search.
   * </p>
   *
   * @param name name of the metric. Both the family sample and the sample name are supported. I.e. for an histogram metric
   *             <code>cxf_client_elapsed_time_seconds</code>, there are sample names prefixed with the family name, like
   *             <code>cxf_client_elapsed_time_seconds_count</code> and <code>cxf_client_elapsed_time_seconds_sum</code>.
   * @return a matcher to {@link Collector} object that will check whether it contains a metric with given name.
   */
  public static MetricCollectionMatcher<Collector> collectsMetric(String name) {
    return new MetricCollectionMatcher<Collector>(true, Collector.class, name);
  }

  /**
   * Main method to get a matcher on a {@link Collector} checking that it does not collect a metric with a given name.
   *
   * <p>
   * Use {@link MetricCollectionMatcher#label(String, String)} and {@link MetricCollectionMatcher#value(Matcher)}
   * methods to narrow down the match.
   * </p>
   *
   * @param name name of the metric. Both the family sample and the sample name are supported. I.e. for an histogram metric
   *             <code>cxf_client_elapsed_time_seconds</code>, there are sample names prefixed with the family name, like
   *             <code>cxf_client_elapsed_time_seconds_count</code> and <code>cxf_client_elapsed_time_seconds_sum</code>.
   * @return a matcher to {@link Collector} object that will check that it does not contain a metric with given name.
   */
  public static MetricCollectionMatcher<Collector> doesNotCollectMetric(String name) {
    return new MetricCollectionMatcher<Collector>(false, Collector.class, name);
  }

  /**
   * Match a label on the selected metric. If none is found a detailed match result is put out.
   *
   * @param labelName  a matcher for a label name
   * @param labelValue a matcher for a label value
   * @return the ongoing metric matcher instance for flow-like statement
   */
  public <K> MetricCollectionMatcher<K> label(Matcher<String> labelName, Matcher<String> labelValue) {
    labels.put(labelName, labelValue);
    return (MetricCollectionMatcher) this;
  }

  /**
   * Match a label on the selected metric. If none is found a detailed match result is put out.
   *
   * @param labelName  the name of the label
   * @param labelValue a matcher to the search value
   * @return the ongoing metric matcher instance for flow-like statement
   */
  public <K> MetricCollectionMatcher<K> label(String labelName, Matcher<String> labelValue) {
    return label(Matchers.equalTo(labelName), labelValue);
  }

  /**
   * Match a label on the selected metric. If none is found a detailed match result is put out.
   *
   * @param labelName  the name of the label
   * @param labelValue the searched label value
   * @return the ongoing metric matcher instance for flow-like statement
   */
  public <K> MetricCollectionMatcher<K> label(String labelName, String labelValue) {
    return label(labelName, Matchers.equalTo(labelValue));
  }

  /**
   * Match a specific value for the found metric sample. If none is found all matched sample values will be put out.
   *
   * @param value matcher to the sampled value
   * @return the ongoing metric matcher instance for flow-like statement
   */
  public <K> MetricCollectionMatcher<K> value(Matcher<Double> value) {
    this.value = value;
    return (MetricCollectionMatcher) this;
  }

  /**
   * Match a specific value for the found metric sample. If none is found all matched sample values will be put out.
   *
   * @param value   the fixed double sample value you want to find
   * @param epsilon the error epsilon used to match the value
   * @return the ongoing metric matcher instance for flow-like statement
   */
  public <K> MetricCollectionMatcher<K> value(Double value, double epsilon) {
    return value(value == null ? null : Matchers.closeTo(value, epsilon));
  }

  /**
   * Match a specific value for the found metric sample. If none is found all matched sample values will be put out.
   *
   * @param value a integer value to be matched as sample value. Typically useful for a metric of type
   *              {@link io.prometheus.client.Counter}
   * @return the ongoing metric matcher instance for flow-like statement
   */
  public <K> MetricCollectionMatcher<K> intValue(Integer value) {
    return value(value == null ? null : value.doubleValue(), EPSILON_FOR_INTEGERS);
  }

  @Override
  public boolean matchesSafely(T prometheusClientObject) {
    // reinit
    foundValues = new TreeMap<String, List<Double>>();
    unmatchedLabels = new ArrayList<UnmatchedLabel>();
    actualNames = new TreeSet<String>();
    actualSampleNames = new TreeSet<String>();

    boolean found = false;
    for (Collector.MetricFamilySamples metricFamilySamples : getMetricFamilySamples(prometheusClientObject)) {
      actualNames.add(metricFamilySamples.name);
      if (name.startsWith(metricFamilySamples.name)) {
        // startsWith because the name is either a family sample name or a sample name
        found = value == null && labels.isEmpty() || matchSamples(metricFamilySamples);
        if (found && (value != null || !labels.isEmpty() || matchPresence)) {
          break;
        }
      }
    }
    return matchPresence == found;
  }

  private List<Collector.MetricFamilySamples> getMetricFamilySamples(T prometheusClientObject) {
    if (prometheusClientObject instanceof CollectorRegistry) {
      return Collections.list(((CollectorRegistry) prometheusClientObject).metricFamilySamples());
    } else if (prometheusClientObject instanceof Collector) {
      return ((Collector) prometheusClientObject).collect();
    }
    Assert.fail("Cannot match an object of type: " + prometheusClientObject.getClass().getSimpleName());
    return Collections.emptyList();
  }

  private boolean matchSamples(Collector.MetricFamilySamples metricFamilySamples) {
    boolean foundInSamples = false;
    for (Collector.MetricFamilySamples.Sample sample : metricFamilySamples.samples) {
      actualSampleNames.add(sample.name);
      if (sample.name.startsWith(name)) {
        // startsWith as name is either a family sample name or a sample name
        if (value == null && labels.isEmpty()) {
          foundInSamples = true;
          if (matchPresence) {
            break;
          }
        } else {
          UnmatchedLabel unmatchedLabel = matchLabels(sample);
          if (unmatchedLabel.unmatchedLabelNames.isEmpty() && unmatchedLabel.unmatchedLabelValues.isEmpty()) {
            addToFoundValues(sample.name, sample.value);
            foundInSamples = value == null || value.matches(sample.value);
            if (foundInSamples && matchPresence) {
              break;
            }
          } else {
            for (int i = 0; i < sample.labelNames.size(); i++) {
              unmatchedLabel.actualLabels.put(sample.labelNames.get(i), sample.labelValues.get(i));
            }
            unmatchedLabels.add(unmatchedLabel);
          }
        }
      }
    }
    return foundInSamples;
  }

  private void addToFoundValues(String name, double value) {
    List<Double> valuesForName = foundValues.get(name);
    if (valuesForName == null) {
      valuesForName = new ArrayList<Double>();
      foundValues.put(name, valuesForName);
    }
    valuesForName.add(value);
  }

  private UnmatchedLabel matchLabels(Collector.MetricFamilySamples.Sample sample) {
    UnmatchedLabel unmatchedLabel = new UnmatchedLabel();
    unmatchedLabel.unmatchedLabelNames.addAll(labels.keySet());

    for (int i = 0; i < sample.labelNames.size(); i++) {
      for (Map.Entry<Matcher<String>, Matcher<String>> labelEntry : labels.entrySet()) {
        if (labelEntry.getKey().matches(sample.labelNames.get(i))) {
          unmatchedLabel.unmatchedLabelNames.remove(labelEntry.getKey());
          if (!labelEntry.getValue().matches(sample.labelValues.get(i))) {
            unmatchedLabel.unmatchedLabelValues
                .add(new UnmatchedLabelValue(labelEntry.getKey(), labelEntry.getValue(), sample.labelValues.get(i)));
          }
        }
      }
    }
    return unmatchedLabel;
  }

  @Override
  public void describeMismatchSafely(T item, Description description) {
    final String indentation = "          ";
    final String dashListWithNewline = "\n" + indentation + "- ";
    if (!foundValues.isEmpty() && value != null) {
      description.appendText(matchPresence ? "no" : "a").appendText(" value matches: ").appendDescriptionOf(value)
          .appendText("\n" + indentation + "found values: ");
      for (Map.Entry<String, List<Double>> foundValue : foundValues.entrySet()) {
        description.appendValueList(dashListWithNewline, ", ", " for sample ", foundValue.getValue())
            .appendValue(foundValue.getKey());
      }
    } else if (!unmatchedLabels.isEmpty()) {
      description.appendText("labels did");
      if (matchPresence) {
        description.appendText(" not");
      }
      description.appendText(" match:");
      for (UnmatchedLabel unmatchedLabel : unmatchedLabels) {
        description.appendText("\n");
        appendLabels(description, unmatchedLabel.actualLabels);
        if (matchPresence) {
          if (!unmatchedLabel.unmatchedLabelNames.isEmpty()) {
            description.appendText("\n  missing names: ").appendList("", ", ", "", unmatchedLabel.unmatchedLabelNames);
          }
          if (!unmatchedLabel.unmatchedLabelValues.isEmpty()) {
            description.appendText("\n  unmatched label values:");
            for (UnmatchedLabelValue unmatchedLabelValue : unmatchedLabel.unmatchedLabelValues) {
              description.appendText("\n  - for ").appendDescriptionOf(unmatchedLabelValue.getName())
                  .appendText(" expected: ").appendDescriptionOf(unmatchedLabelValue.getExpectedValue())
                  .appendText(" but got: ").appendValue(unmatchedLabelValue.getActualValue());
            }
          }
          description.appendText("\n");
        }
      }
    } else {
      if (matchPresence) {
        description.appendText("no metric with name: ").appendValue(name).appendText("\n").appendText(indentation);
      }
      if (!actualNames.isEmpty()) {
        description.appendValueList("found metric names:" + dashListWithNewline, dashListWithNewline, "", actualNames);
      }
      if (!actualSampleNames.isEmpty()) {
        if (!actualNames.isEmpty()) {
          description.appendText("\n" + indentation);
        }
        description.appendValueList("found metric sample names:" + dashListWithNewline, dashListWithNewline, "",
            actualSampleNames);
      }
    }
  }

  private void appendLabels(Description description, Map<String, String> labelEntries) {
    List<String> keyValueBound = new ArrayList<String>(labelEntries.size());
    for (Map.Entry<String, String> labelEntry : labelEntries.entrySet()) {
      keyValueBound.add(labelEntry.getKey() + "=" + labelEntry.getValue());
    }
    description.appendText("(").appendText(StringUtils.join(keyValueBound, ", ")).appendText(")");
  }

  @Override
  public void describeTo(Description description) {
    description.appendText("a ").appendText(klazz.getSimpleName());
    if (!matchPresence) {
      description.appendText(" not");
    }
    description.appendText(" containing a sample with name: ").appendValue(name);
    if (value != null) {
      description.appendText(" value: ").appendDescriptionOf(value);
    }
    if (!labels.isEmpty()) {
      description.appendText(" and labels:\n");
      for (Map.Entry<Matcher<String>, Matcher<String>> labelEntry : labels.entrySet()) {
        description.appendDescriptionOf(labelEntry.getKey()).appendText("=").appendDescriptionOf(labelEntry.getValue())
            .appendText("\n");
      }
    }
  }

  @Data
  private static final class UnmatchedLabel {
    private final List<Matcher<String>> unmatchedLabelNames = new ArrayList<Matcher<String>>();

    private final List<UnmatchedLabelValue> unmatchedLabelValues = new ArrayList<UnmatchedLabelValue>();

    private final Map<String, String> actualLabels = new LinkedHashMap<String, String>();
  }

  @Data
  private static final class UnmatchedLabelValue {
    private final Matcher<String> name;

    private final Matcher<String> expectedValue;

    private final String actualValue;
  }

}
