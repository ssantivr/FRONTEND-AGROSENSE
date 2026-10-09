package com.agrosense.frontend.service;

import com.agrosense.frontend.backend.BackendClient;
import com.agrosense.frontend.backend.BackendClient.IrrigationResponse;
import com.agrosense.frontend.backend.BackendUnavailableException;
import com.agrosense.frontend.dto.IrrigationForm;
import com.agrosense.frontend.dto.Views.DailyWater;
import com.agrosense.frontend.dto.Views.IrrigationView;
import com.agrosense.frontend.entity.Crop;
import com.agrosense.frontend.entity.Estate;
import com.agrosense.frontend.entity.Irrigation;
import com.agrosense.frontend.entity.User;
import com.agrosense.frontend.entity.enums.IrrigationType;
import com.agrosense.frontend.exception.BusinessRuleException;
import com.agrosense.frontend.exception.NotFoundException;
import com.agrosense.frontend.repository.CropRepository;
import com.agrosense.frontend.repository.IrrigationRepository;
import com.agrosense.frontend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IrrigationServiceTests {

    private static final String EMAIL = "farmer@agrosense.test";

    @Mock
    private IrrigationRepository irrigationRepository;
    @Mock
    private CropRepository cropRepository;
    @Mock
    private UserRepository userRepository;
    /** Not stubbed: an unavailable backend, so every irrigation goes to the database. */
    @Mock
    private BackendClient backendClient;
    @InjectMocks
    private IrrigationService service;

    private Crop crop;
    private IrrigationForm form;

    @BeforeEach
    void setUp() {
        Estate estate = Estate.builder().name("Estate").build();
        crop = Crop.builder().idCrop(7).name("Coffee").estate(estate).build();
        form = new IrrigationForm();
        form.setCropId(7);
        form.setDurationMin(20);
        form.setWaterLiters(new BigDecimal("80"));
    }

    @Test
    void startGoesThroughTheBackendWhenItIsAvailableAndStoresNothingItself() {
        LocalDateTime startedAt = LocalDateTime.now();
        when(cropRepository.findByIdCropAndEstateUserEmail(7, EMAIL)).thenReturn(Optional.of(crop));
        when(irrigationRepository.findByCropIdCropOrderByStartedAtDesc(7)).thenReturn(List.of());
        when(backendClient.isAvailable()).thenReturn(true);
        when(backendClient.startIrrigation(eq(EMAIL), eq(7), eq(new BigDecimal("80")), eq(20), anyString()))
                .thenReturn(new IrrigationResponse(41, startedAt, startedAt.plusMinutes(20), 20, new BigDecimal("80")));

        IrrigationView view = service.start(EMAIL, form);

        assertThat(view.id()).isEqualTo(41);
        assertThat(view.cropName()).isEqualTo("Coffee");
        assertThat(view.waterLiters()).isEqualByComparingTo("80");
        assertThat(view.running()).isTrue();
        verify(irrigationRepository, never()).save(any());
    }

    @Test
    void startIsNotRepeatedOnTheDatabaseWhenTheBackendStopsAnswering() {
        when(cropRepository.findByIdCropAndEstateUserEmail(7, EMAIL)).thenReturn(Optional.of(crop));
        when(irrigationRepository.findByCropIdCropOrderByStartedAtDesc(7)).thenReturn(List.of());
        when(backendClient.isAvailable()).thenReturn(true);
        when(backendClient.startIrrigation(any(), any(), any(), any(), any()))
                .thenThrow(new BackendUnavailableException("read timed out"));

        assertThatThrownBy(() -> service.start(EMAIL, form))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("no respondió");
        verify(irrigationRepository, never()).save(any());
    }

    @Test
    void anIrrigationWhosePlannedEndIsStillAheadCountsAsRunning() {
        Irrigation registeredByTheBackend = Irrigation.builder()
                .crop(crop)
                .startedAt(LocalDateTime.now().minusMinutes(5))
                .endedAt(LocalDateTime.now().plusMinutes(25))
                .durationMin(30)
                .build();
        when(cropRepository.findByIdCropAndEstateUserEmail(7, EMAIL)).thenReturn(Optional.of(crop));
        when(irrigationRepository.findByCropIdCropOrderByStartedAtDesc(7)).thenReturn(List.of(registeredByTheBackend));

        assertThatThrownBy(() -> service.start(EMAIL, form))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("riego en curso");
    }

    @Test
    void startStoresAManualIrrigationForTheUsersCrop() {
        User user = User.builder().email(EMAIL).build();
        when(cropRepository.findByIdCropAndEstateUserEmail(7, EMAIL)).thenReturn(Optional.of(crop));
        when(irrigationRepository.findByCropIdCropOrderByStartedAtDesc(7)).thenReturn(List.of());
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(irrigationRepository.save(any(Irrigation.class))).thenAnswer(invocation -> invocation.getArgument(0));

        IrrigationView view = service.start(EMAIL, form);

        ArgumentCaptor<Irrigation> saved = ArgumentCaptor.forClass(Irrigation.class);
        verify(irrigationRepository).save(saved.capture());
        assertThat(saved.getValue().getType()).isEqualTo(IrrigationType.MANUAL);
        assertThat(saved.getValue().getActivatedBy()).isSameAs(user);
        assertThat(saved.getValue().getDurationMin()).isEqualTo(20);
        assertThat(saved.getValue().getWaterLiters()).isEqualByComparingTo("80");
        assertThat(view.running()).isTrue();
        assertThat(view.endsAt()).isEqualTo(view.startedAt().plusMinutes(20));
    }

    @Test
    void startFailsWhenTheCropBelongsToSomeoneElse() {
        when(cropRepository.findByIdCropAndEstateUserEmail(7, EMAIL)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.start(EMAIL, form)).isInstanceOf(NotFoundException.class);
        verify(irrigationRepository, never()).save(any());
    }

    @Test
    void startFailsWhileAnotherIrrigationIsRunningForTheCrop() {
        Irrigation running = Irrigation.builder()
                .crop(crop)
                .startedAt(LocalDateTime.now().minusMinutes(5))
                .durationMin(30)
                .build();
        when(cropRepository.findByIdCropAndEstateUserEmail(7, EMAIL)).thenReturn(Optional.of(crop));
        when(irrigationRepository.findByCropIdCropOrderByStartedAtDesc(7)).thenReturn(List.of(running));

        assertThatThrownBy(() -> service.start(EMAIL, form))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("riego en curso");
        verify(irrigationRepository, never()).save(any());
    }

    @Test
    void startFailsForInactiveCrops() {
        crop.setActive(false);
        when(cropRepository.findByIdCropAndEstateUserEmail(7, EMAIL)).thenReturn(Optional.of(crop));

        assertThatThrownBy(() -> service.start(EMAIL, form)).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void dailyWaterCoversEveryDayAndIgnoresIrrigationsWithoutLitres() {
        LocalDateTime today = LocalDate.now().atTime(8, 0);
        List<IrrigationView> history = List.of(
                view(today, "100.5"),
                view(today.plusHours(2), "50"),
                view(today.minusDays(2), null),
                view(today.minusDays(30), "999"));

        List<DailyWater> days = service.dailyWater(history, 7);

        assertThat(days).hasSize(7);
        assertThat(days.get(6).date()).isEqualTo(LocalDate.now());
        assertThat(days.get(6).liters()).isEqualByComparingTo("150.5");
        assertThat(days.get(4).liters()).isEqualByComparingTo("0");
        assertThat(days.stream().map(DailyWater::liters).reduce(BigDecimal.ZERO, BigDecimal::add))
                .isEqualByComparingTo("150.5");
    }

    private static IrrigationView view(LocalDateTime startedAt, String liters) {
        return new IrrigationView(1, "Coffee", "Estate", startedAt, startedAt.plusMinutes(30), 30,
                liters == null ? null : new BigDecimal(liters), IrrigationType.MANUAL, false);
    }
}
