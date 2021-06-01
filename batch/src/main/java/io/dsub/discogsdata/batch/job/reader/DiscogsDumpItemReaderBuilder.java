package io.dsub.discogsdata.batch.job.reader;

import io.dsub.discogsdata.batch.dump.DiscogsDump;
import io.dsub.discogsdata.common.exception.InvalidArgumentException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.batch.item.support.SynchronizedItemStreamReader;
import org.springframework.util.Assert;

/**
 * Utility class that provides single static method {@link #build(Class, DiscogsDump)}.
 */
public final class DiscogsDumpItemReaderBuilder {

  private DiscogsDumpItemReaderBuilder() {
  }

  public static <T> SynchronizedItemStreamReader<T> build(Class<T> mappedClass, DiscogsDump dump)
      throws Exception {
    Assert.notNull(dump.getFileName(), "fileName of DiscogsDump cannot be null");
    Assert.notNull(dump.getType(), "type of DiscogsDump cannot be null");

    Path filePath = Path.of(dump.getFileName());

    ProgressBarStaxEventItemReader<T> delegate;
    delegate = new ProgressBarStaxEventItemReader<>(mappedClass, filePath,
        dump.getType().toString());
    delegate.afterPropertiesSet();

    SynchronizedItemStreamReader<T> reader = new SynchronizedItemStreamReader<>();
    reader.setDelegate(delegate);
    reader.afterPropertiesSet(); // this won't trigger that of delegate's.
    return reader;
  }
}