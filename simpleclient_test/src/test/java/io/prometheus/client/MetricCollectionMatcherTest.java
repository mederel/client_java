package io.prometheus.client;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.lessThan;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.Assert.assertThat;

import lombok.RequiredArgsConstructor;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeDiagnosingMatcher;
import org.hamcrest.core.IsNot;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Collections;
import java.util.List;

//@RunWith(Parameterized.class)
//@RequiredArgsConstructor
public class MetricCollectionMatcherTest {

  public static final String COUNT = "count";
  public static final String LABEL_ONE = "label_one";
  public static final String VALUE_ONE = "value_one";
  public static final String ANOTHER_LABEL = "another_label";
  private static Histogram countMetric =
      Histogram.build(COUNT, "well I count").labelNames(LABEL_ONE, ANOTHER_LABEL).create();

  public static final String PEANUTS = "peanuts";
  private static Gauge peanutsMetric =
      Gauge.build(PEANUTS, "peanuts plus peanutes still amount to peanuts")
          .labelNames(LABEL_ONE, ANOTHER_LABEL).create();

  private static CollectorRegistry registry = new CollectorRegistry();

  private static Collector collector = new Collector() {
    @Override
    public List<MetricFamilySamples> collect() {
      return Collections.list(registry.metricFamilySamples());
    }
  };

  static {
    registry.register(countMetric);
    countMetric.labels("value_one", "4").observe(3d);
    countMetric.labels("value_two", "value22").observe(-3.4d);

    registry.register(peanutsMetric);
    peanutsMetric.labels("value_three", "4").dec(3d);
    peanutsMetric.labels("value_four", "value22").inc(3.4d);
  }

  private final Class<?> klazz;

  private final Object metricCollection;

  private final boolean matchPresence;

  public MetricCollectionMatcherTest() {
    klazz = CollectorRegistry.class;
    metricCollection = registry;
    matchPresence = true;
  }

//  @Parameterized.Parameters
//  public static Object[][] parameters() {
//    return new Object[][]{new Object[]{CollectorRegistry.class, registry, true}//,
//        //new Object[]{CollectorRegistry.class, registry, false}, new Object[]{Collector.class, collector, true},
//        //new Object[]{Collector.class, collector, false}};
//    };
//  }

  @SuppressWarnings("unchecked")
  private MetricCollectionMatcher<Object> hasMetric(String name) {
    return new MetricCollectionMatcher<Object>(!matchPresence, (Class) klazz, name);
  }

  private Matcher<Object> isOrNot(boolean isOrNot, Matcher<Object> matcher) {
    return matchPresence == isOrNot ? matcher : new IsNot<Object>(matcher);
  }

  @Test
  public void workingExample() {
    assertThat(metricCollection,
        isOrNot(true, hasMetric(COUNT).label(LABEL_ONE, "value_one").label(ANOTHER_LABEL, "4").value(3d, 0.1d)));
    assertThat(metricCollection, isOrNot(true,
        hasMetric(COUNT).label(LABEL_ONE, "value_two").label(ANOTHER_LABEL, "value22").value(-3.4d, 0.01d)));
    assertThat(metricCollection,
        isOrNot(true, hasMetric(PEANUTS).label(LABEL_ONE, "value_three").label(ANOTHER_LABEL, "4").value(3d, 0.1d)));
    assertThat(metricCollection, isOrNot(true,
        hasMetric(PEANUTS).label(LABEL_ONE, "value_four").label(ANOTHER_LABEL, "value22").value(-3.4d, 0.01d)));
  }

  @Test
  public void valueGivenWithInt() {
    assertThat(metricCollection,
        isOrNot(true, hasMetric(COUNT).label(LABEL_ONE, "value_one").label(ANOTHER_LABEL, "4").intValue(3)));
  }

  @Test
  public void onlyNameAndLabels() {
    assertThat(metricCollection,
        isOrNot(true, hasMetric(COUNT).label(LABEL_ONE, "value_one").label(ANOTHER_LABEL, "4")));
    assertThat(metricCollection,
        isOrNot(true, hasMetric(COUNT).label(LABEL_ONE, "value_two").label(ANOTHER_LABEL, "value22")));
  }

  @Test
  public void onlyName() {
    assertThat(metricCollection, isOrNot(true, hasMetric(COUNT)));
  }

  @Test
  public void onlyNameAndValue() {
    assertThat(metricCollection, isOrNot(true, hasMetric(COUNT).value(3d, 0.1d)));
    assertThat(metricCollection, isOrNot(true, hasMetric(COUNT).value(-3.4d, 0.01d)));
  }

  @Test
  public void doubleNullValueDeactivatesValueMatching() {
    assertThat(metricCollection, isOrNot(true, hasMetric(COUNT).value(null, 0.1d)));
  }

  @Test
  public void onlyNameAndIntValue() {
    assertThat(metricCollection, isOrNot(true, hasMetric(COUNT).intValue(3)));
  }

  @Test
  public void nullIntValueRemoveValueMatching() {
    assertThat(metricCollection, isOrNot(true, hasMetric(COUNT).intValue(null)));
  }

  @Test
  public void workingExampleWithMatcher() {
    assertThat(metricCollection, isOrNot(true, hasMetric(COUNT).label(LABEL_ONE, equalTo("value_one"))
        .label(equalTo(ANOTHER_LABEL), containsString("4")).value(greaterThan(0d))));
    assertThat(metricCollection, isOrNot(true, hasMetric(COUNT).label(endsWith("_one"), equalTo("value_two"))
        .label(equalTo(ANOTHER_LABEL), startsWith("value22")).value(lessThan(3.4d))));
  }

  @Test
  public void notExistingLabel() {
    assertThat(metricCollection, isOrNot(false, hasMetric(COUNT).label(LABEL_ONE, "value_one")
        .label("not_existing_label", "").label(ANOTHER_LABEL, "4").value(3d, 0.1d)));
  }

  @Test
  public void labelValueDifferent() {
    assertThat(metricCollection,
        isOrNot(false, hasMetric(COUNT).label(LABEL_ONE, "strangeValue").value(-3.4d, 0.01d)));
  }

  @Test
  public void secondLabelValueNotMatch() {
    assertThat(metricCollection, isOrNot(false, hasMetric(COUNT).label(LABEL_ONE, "value_one")
        .label(ANOTHER_LABEL, containsString("myString")).intValue(3)));
  }

  @Test
  public void nameNotMatching() {
    assertThat(metricCollection, isOrNot(false, hasMetric("fakes")));
  }

  @Test
  public void noMetricWithMatchingValue() {
    assertThat(metricCollection, isOrNot(false, hasMetric(COUNT).value(1d, 0.1d)));
  }

  @Test
  public void noMetricWithMatchingIntValue() {
    assertThat(metricCollection, isOrNot(false, hasMetric(COUNT).intValue(56)));
  }

  @Test(expected = AssertionError.class)
  public void noNameFail() {
    hasMetric(null);
  }

  public static class IsNot<T> extends TypeSafeDiagnosingMatcher<T> {
    private final Matcher<T> matcher;

    public IsNot(Matcher<T> matcher) {
      this.matcher = matcher;
    }

    @Override
    protected boolean matchesSafely(T t, Description description) {
      if (!matcher.matches(t)) {
        return true;
      }
      matcher.describeMismatch(t, description);
      return false;
    }

    public void describeTo(Description description) {
      description.appendText("not ").appendDescriptionOf(this.matcher);
    }
  }

}
