package io.dsub.discogs.batch.job.reader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.when;

import io.dsub.discogs.batch.domain.artist.ArtistSubItemsXML;
import io.dsub.discogs.batch.dump.DiscogsDump;
import io.dsub.discogs.batch.dump.EntityType;
import io.dsub.discogs.batch.exception.FileException;
import io.dsub.discogs.batch.util.FileUtil;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class DumpItemReaderBuilderTest {

  DiscogsDump dump;
  FileUtil fileUtil;
  DiscogsDumpItemReaderBuilder readerBuilder;

  @BeforeEach
  void setUp() {
    fileUtil = Mockito.mock(FileUtil.class);
    dump = Mockito.mock(DiscogsDump.class);
    readerBuilder = new DiscogsDumpItemReaderBuilder(fileUtil);
  }

  @Test
  void whenBuild__ShouldNotThrow() {
    try {
      when(fileUtil.getFilePath("artist.xml.gz"))
          .thenReturn(Path.of("src/test/resources/test/reader/artist.xml.gz"));
      when(dump.getFileName()).thenReturn("artist.xml.gz");
      when(dump.getType()).thenReturn(EntityType.ARTIST);
      assertDoesNotThrow(() -> readerBuilder.build(ArtistSubItemsXML.class, dump));
    } catch (FileException e) {
      fail(e);
    }
  }

  @Test
  void whenTypeNotSet__ShouldThrow() {
    try {
      when(dump.getType()).thenReturn(null);
      when(dump.getFileName()).thenReturn("src/test/resources/test/reader/artist.xml.gz");
      when(fileUtil.getFilePath(dump.getFileName()))
          .thenReturn(Path.of("src/test/resources/test/reader/artist.xml.gz"));
      Throwable t = catchThrowable(() -> readerBuilder.build(ArtistSubItemsXML.class, dump));
      assertThat(t).hasMessageContaining("type of DiscogsDump cannot be null");
    } catch (FileException e) {
      fail(e);
    }
  }

  @Test
  void whenUriNotSet__ShouldThrow() {
    try {
      when(dump.getFileName()).thenReturn(null);
      when(fileUtil.getFilePath(dump.getFileName())).thenThrow(FileException.class);
      Throwable t = catchThrowable(() -> readerBuilder.build(ArtistSubItemsXML.class, dump));
      assertThat(t).hasMessageContaining("fileName of DiscogsDump cannot be null");
    } catch (FileException e) {
      fail();
    }
  }
}
