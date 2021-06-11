package io.dsub.discogs.batch.argument.formatter;

import java.util.ArrayList;
import java.util.List;

/** Collection of argument formatter that delegates the format() method to its delegates. */
public class CompositeArgumentFormatter implements ArgumentFormatter {

  private final List<ArgumentFormatter> delegates;

  public CompositeArgumentFormatter() {
    this.delegates = new ArrayList<>();
  }

  public CompositeArgumentFormatter addFormatter(ArgumentFormatter additionalFormatter) {
    this.delegates.add(additionalFormatter);
    return this;
  }

  @Override
  public String format(String arg) {
    String formatted = arg;
    for (ArgumentFormatter delegate : delegates) {
      formatted = delegate.format(formatted);
    }
    return formatted;
  }
}