package io.dsub.discogs.batch.dump.service;

import static io.dsub.discogs.batch.TestArguments.getRandomDump;
import static io.dsub.discogs.batch.TestArguments.getRandomDumpWithType;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.catchThrowable;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.atMostOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.spi.ILoggingEvent;
import io.dsub.discogs.batch.dump.DiscogsDump;
import io.dsub.discogs.batch.dump.DumpSupplier;
import io.dsub.discogs.batch.dump.EntityType;
import io.dsub.discogs.batch.dump.repository.DiscogsDumpRepository;
import io.dsub.discogs.batch.exception.DumpNotFoundException;
import io.dsub.discogs.batch.exception.InitializationFailureException;
import io.dsub.discogs.batch.testutil.LogSpy;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import net.bytebuddy.utility.RandomString;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class DefaultDiscogsDumpServiceUnitTest {

  final Random random = new Random();
  @RegisterExtension
  public LogSpy logSpy = new LogSpy();
  @Mock
  DiscogsDumpRepository repository;
  @Mock
  DumpSupplier dumpSupplier;
  @InjectMocks
  DefaultDiscogsDumpService dumpService;
  @Captor
  private ArgumentCaptor<List<DiscogsDump>> dumpListCaptor;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
  }

  @Test
  void whenUpdateDB__IfMonthlyDumpCountIs4__ThenShouldNotProceedUpdate() {
    when(repository.countItemsAfter(LocalDate.now().withDayOfMonth(1)))
        .thenReturn(4);

    // when
    dumpService.updateDB();

    // then
    assertThat(logSpy.getEvents().get(0).getMessage())
        .isEqualTo("repository is up to date. skipping the update...");
    verify(dumpSupplier, never()).get();
    verify(repository, times(1))
        .countItemsAfter(LocalDate.now().withDayOfMonth(1));
  }

  @Test
  void whenUpdateDB__ShouldFilterNullValues__BeforeCallDumpRepository__SaveAllMethod() {
    List<DiscogsDump> listIncludingNull =
        IntStream.range(0, 10 + random.nextInt(10))
            .mapToObj(n -> n % 3 == 0 ? null : getRandomDump())
            .collect(Collectors.toList());

    List<DiscogsDump> filtered =
        listIncludingNull.stream().filter(Objects::nonNull).collect(Collectors.toList());

    assertThat(listIncludingNull.contains(null)).isTrue();
    when(dumpSupplier.get()).thenReturn(listIncludingNull);

    // when
    dumpService.updateDB();

    // then
    verify(dumpSupplier, times(1)).get();
    verify(repository, times(1)).saveAll(dumpListCaptor.capture());
    assertThat(dumpListCaptor.getValue())
        .isNotNull()
        .isNotSameAs(listIncludingNull)
        .isEqualTo(filtered);
  }

  @Test
  void whenUpdateDBWithNull__ShouldNotCallRepositoryMethod() {
    when(dumpSupplier.get()).thenReturn(null);
    dumpService.updateDB();
    verify(dumpSupplier, times(1)).get();
    verify(repository, times(0)).saveAll(any());
    List<ILoggingEvent> logs = logSpy.getEvents();
    assertThat(logs.size()).isEqualTo(1);
    assertThat(logs.get(0).getMessage())
        .isEqualTo("failed to fetch items via DumpSupplier. cancelling the update...");
  }

  @Test
  void whenUpdateDB__ShouldCallProperDelegatedMethodsWithValues() {
    List<DiscogsDump> dumpList =
        IntStream.range(0, 10 + random.nextInt(10))
            .mapToObj(n -> getRandomDump())
            .collect(Collectors.toList());

    // when
    when(dumpSupplier.get()).thenReturn(dumpList);
    dumpService.updateDB();

    // then
    verify(dumpSupplier, times(1)).get();
    verify(repository, times(1)).saveAll(dumpListCaptor.capture());
    assertThat(dumpListCaptor.getValue()).isEqualTo(dumpList);
  }

  @Test
  void getDiscogsDumpShouldHandoverSameParameter__ThenReturnsTheSameResult__FromRepository()
      throws DumpNotFoundException {
    String fakeETag = RandomString.make(20);
    DiscogsDump fakeDump = getRandomDump();
    ArgumentCaptor<String> stringCaptor = ArgumentCaptor.forClass(String.class);

    when(repository.findByETag(stringCaptor.capture())).thenReturn(fakeDump);

    // when
    DiscogsDump result = dumpService.getDiscogsDump(fakeETag);

    // then
    verify(repository, times(1)).findByETag(fakeETag);
    assertThat(stringCaptor.getValue()).isEqualTo(fakeETag);
    assertThat(result).isEqualTo(fakeDump);
  }

  @Test
  void
  whenGetMostRecentDiscogsDumpByTypeCalled__ThenShouldCallRepositoryOnce__AndShouldHaveProperResult() {
    DiscogsDump fakeDump = getRandomDump();
    EntityType type = fakeDump.getType();
    ArgumentCaptor<EntityType> dumpTypeCaptor = ArgumentCaptor.forClass(EntityType.class);
    when(repository.findTopByType(dumpTypeCaptor.capture()))
        .thenReturn(fakeDump);

    // when
    DiscogsDump result = dumpService.getMostRecentDiscogsDumpByType(type);

    // then
    verify(repository, times(1)).findTopByType(type);
    assertThat(dumpTypeCaptor.getValue()).isEqualTo(type);
    assertThat(result).isEqualTo(fakeDump);
  }

  @Test
  void getDumpByTypeInRange() {
    DiscogsDump fakeDump = getRandomDump();
    EntityType type = fakeDump.getType();

    ArgumentCaptor<EntityType> typeCaptor = ArgumentCaptor.forClass(EntityType.class);
    ArgumentCaptor<LocalDate> localDateCaptor = ArgumentCaptor.forClass(LocalDate.class);
    when(repository.findByTypeAndLastModifiedAtBetween(
        typeCaptor.capture(), localDateCaptor.capture(), localDateCaptor.capture()))
        .thenReturn(List.of(fakeDump));

    LocalDate startDate = fakeDump.getLastModifiedAt().withDayOfMonth(1);
    LocalDate endDate = startDate.plusMonths(1).minusDays(1);
    int year = startDate.getYear();
    int month = startDate.getMonthValue();

    // when
    List<DiscogsDump> result = dumpService.getDumpByTypeInRange(type, year, month);

    // then
    verify(repository, times(1)).findByTypeAndLastModifiedAtBetween(type, startDate, endDate);
    assertThat(localDateCaptor.getAllValues().get(0)).isEqualTo(startDate);
    assertThat(localDateCaptor.getAllValues().get(1)).isEqualTo(endDate);
    assertThat(result.get(0)).isEqualTo(fakeDump);
  }

  @Test
  void whenGetLatestCompleteDumpSet__ThenShouldRetryWithPreviousMonths__IfCountIsLowerThan4()
      throws io.dsub.discogs.batch.exception.DumpNotFoundException {
    LocalDate existingDate = LocalDate.now(Clock.systemUTC()).minusMonths(2);

    List<DiscogsDump> fakeDumps =
        IntStream.range(0, 4)
            .mapToObj(n -> getRandomDumpWithType(EntityType.values()[n], existingDate))
            .collect(Collectors.toList());

    LocalDate firstStartDate = LocalDate.now(Clock.systemUTC()).withDayOfMonth(1);
    LocalDate firstEndDate = firstStartDate.plusMonths(1);

    LocalDate secondStartDate = firstStartDate.minusMonths(1);
    LocalDate secondEndDate = firstEndDate.minusMonths(1);

    when(repository.countItemsBetween(firstStartDate, firstEndDate)).thenReturn(2);
    when(repository.countItemsBetween(secondStartDate, secondEndDate)).thenReturn(4);
    when(repository.findAllByLastModifiedAtIsBetween(secondStartDate, secondEndDate))
        .thenReturn(fakeDumps);

    List<DiscogsDump> result = dumpService.getLatestCompleteDumpSet();

    verify(repository, times(1)).countItemsBetween(firstStartDate, firstEndDate);
    verify(repository, times(0)).findAllByLastModifiedAtIsBetween(firstStartDate, firstEndDate);
    verify(repository, times(1)).countItemsBetween(secondStartDate, secondEndDate);
    verify(repository, times(1)).findAllByLastModifiedAtIsBetween(secondStartDate, secondEndDate);

    assertThat(result).isEqualTo(fakeDumps);
  }

  @Test
  void whenAfterPropertiesSet__ThenShouldCallUpdatedDBMethod() {
    when(repository.countItemsAfter(any())).thenReturn(4);

    // when
    assertDoesNotThrow(() -> dumpService.afterPropertiesSet());
    verify(repository, times(1)).countItemsAfter(any());
  }

  @Test
  void whenGetLatestCompleteDumpSet__ThenShouldThrowDumpNotFoundException() {
    for (int i = 0; i < 4; i++) {
      when(repository.countItemsBetween(any(), any())).thenReturn(i);
      assertThrows(DumpNotFoundException.class, () -> dumpService.getLatestCompleteDumpSet());
    }
  }

  @Test
  void whenGetAllCalled__ThenShouldCall__RepositoryFindAllMethod() {
    List<DiscogsDump> fakeList = new ArrayList<>();
    when(repository.findAll()).thenReturn(fakeList);

    // when
    assertThat(dumpService.getAll()).isEqualTo(fakeList);
    verify(repository, atMostOnce()).findAll();
  }

  @ParameterizedTest
  @EnumSource(EntityType.class)
  void whenGetMostRecentDiscogsDumpByTypeYearMonth__ThenShouldCallRepositoryWithExpectedValue(
      EntityType type) {
    ArgumentCaptor<LocalDate> startDateCaptor = ArgumentCaptor.forClass(LocalDate.class);
    ArgumentCaptor<LocalDate> endDateCaptor = ArgumentCaptor.forClass(LocalDate.class);
    ArgumentCaptor<EntityType> dumpTypeArgumentCaptor = ArgumentCaptor.forClass(EntityType.class);
    DiscogsDump expectedDump = getRandomDump();

    LocalDate startDate = LocalDate.now().minusDays(1000 + random.nextInt(1000)).withDayOfMonth(1);
    LocalDate endDate = startDate.plusMonths(1).minusDays(1);

    when(repository.findTopByTypeAndLastModifiedAtBetween(
        dumpTypeArgumentCaptor.capture(), startDateCaptor.capture(), endDateCaptor.capture()))
        .thenReturn(expectedDump);

    // when
    DiscogsDump resultDump =
        dumpService.getMostRecentDiscogsDumpByTypeYearMonth(
            type, startDate.getYear(), startDate.getMonthValue());
    // then
    assertThat(resultDump).isEqualTo(expectedDump);
    assertThat(dumpTypeArgumentCaptor.getValue()).isEqualTo(type);
    assertThat(startDateCaptor.getValue()).isEqualTo(startDate);
    assertThat(endDateCaptor.getValue()).isEqualTo(endDate);
  }

  @Test
  void whenRepositoryNotSet__ThenShouldThrow() {
    dumpService = new DefaultDiscogsDumpService(null, dumpSupplier);

    // when
    Throwable t = catchThrowable(() -> dumpService.afterPropertiesSet());

    // then
    assertThat(t)
        .isInstanceOf(InitializationFailureException.class)
        .hasMessage("repository cannot be null");
  }

  @Test
  void whenDumpSupplierNotSet__ThenShouldThrow() {
    dumpService = new DefaultDiscogsDumpService(repository, null);

    // when
    Throwable t = catchThrowable(() -> dumpService.afterPropertiesSet());

    // then
    assertThat(t)
        .isInstanceOf(InitializationFailureException.class)
        .hasMessage("dumpSupplier cannot be null");
  }

  @Test
  void whenGetAllByTypeYearMonth__WithDuplicatedType__ShouldNotThrow() {
    EntityType type = EntityType.values()[random.nextInt(4)];
    when(repository.findTopByTypeAndLastModifiedAtBetween(any(), any(), ArgumentMatchers.any()))
        .thenReturn(getRandomDumpWithType(type));

    // when
    Assertions.assertDoesNotThrow(
        () -> dumpService.getAllByTypeYearMonth(List.of(type, type), 1, 1));
  }

  @Test
  void whenGetAllByTypeYearMonth__ShouldThrowIfRepositoryReturnsNull() {
    when(repository.findTopByTypeAndLastModifiedAtBetween(any(), any(), ArgumentMatchers.any()))
        .thenReturn(null);
    EntityType type = EntityType.values()[random.nextInt(4)];
    Throwable t = catchThrowable(() -> dumpService.getAllByTypeYearMonth(List.of(type), 1, 1));
    assertThat(t)
        .isInstanceOf(DumpNotFoundException.class)
        .hasMessage("dump of type " + type + " from 1-1 not found");
  }

  @ParameterizedTest
  @EnumSource(EntityType.class)
  void whenGetAllByTypeYearMonth__ShouldReturnProperValue(EntityType type)
      throws io.dsub.discogs.batch.exception.DumpNotFoundException {
    Collection<DiscogsDump> expected = List.of(getRandomDumpWithType(type));
    when(repository.findTopByTypeAndLastModifiedAtBetween(any(), any(), ArgumentMatchers.any()))
        .thenReturn(expected.iterator().next());

    // when
    Collection<DiscogsDump> result = dumpService.getAllByTypeYearMonth(List.of(type), 1, 1);

    // then
    assertThat(result.size()).isEqualTo(expected.size());
    assertThat(result.iterator().next()).isEqualTo(expected.iterator().next());
    verify(repository, times(1))
        .findTopByTypeAndLastModifiedAtBetween(any(), ArgumentMatchers.any(), any());
  }

  @Test
  void whenAfterPropertiesSetCalled__ShouldThrowIfAnythingIsMissing() {
    DefaultDiscogsDumpService service = new DefaultDiscogsDumpService(repository, dumpSupplier);
    assertDoesNotThrow(service::afterPropertiesSet);

    DefaultDiscogsDumpService secondService = new DefaultDiscogsDumpService(null, dumpSupplier);
    assertThrows(InitializationFailureException.class, secondService::afterPropertiesSet);

    DefaultDiscogsDumpService thirdService = new DefaultDiscogsDumpService(repository, null);
    assertThrows(InitializationFailureException.class, thirdService::afterPropertiesSet);

    DefaultDiscogsDumpService fourthService = new DefaultDiscogsDumpService(null, null);
    assertThrows(InitializationFailureException.class, fourthService::afterPropertiesSet);
  }
}
