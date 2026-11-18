package com.agrosense.frontend.service;

import com.agrosense.frontend.dto.Views.CropCard;
import com.agrosense.frontend.dto.Views.CropDetail;
import com.agrosense.frontend.dto.Views.CropLatest;
import com.agrosense.frontend.dto.Views.EstateView;
import com.agrosense.frontend.dto.Views.SensorView;
import com.agrosense.frontend.entity.enums.CropStage;
import com.agrosense.frontend.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CropService {

    public static final String ORDER_NAME = "nombre";
    public static final String ORDER_SOWING = "siembra";
    public static final String ORDER_ALERTS = "alertas";
    public static final List<String> ORDERS = List.of(ORDER_NAME, ORDER_SOWING, ORDER_ALERTS);

    private static final int DETAIL_ALERTS = 20;
    private static final int DETAIL_IRRIGATIONS = 20;

    private final EstateService estateService;
    private final SensorService sensorService;
    private final AlertService alertService;
    private final IrrigationService irrigationService;

    public record CropFilter(String query, CropStage stage, Integer estateId, String order) {

        public boolean active() {
            return (query != null && !query.isBlank()) || stage != null || estateId != null;
        }
    }

    public List<EstateView> findEstates(String email) {
        return estateService.findEstates(email);
    }

    public List<CropCard> findCards(String email) {
        return cardsOf(estateService.findEstates(email), sensorService.findSensors(email),
                alertService.countOpenByCrop(email));
    }

    public CropLatest findLatest(String email, Integer cropId) {
        return CropLatest.of(findCard(email, cropId));
    }

    private CropCard findCard(String email, Integer cropId) {
        return findCards(email).stream()
                .filter(candidate -> candidate.crop().id().equals(cropId))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Crop not found"));
    }

    public CropDetail findDetail(String email, Integer cropId) {
        CropCard card = findCard(email, cropId);
        return new CropDetail(card, alertService.findByCrop(email, cropId, DETAIL_ALERTS),
                irrigationService.findByCrop(email, cropId, DETAIL_IRRIGATIONS));
    }

    public static List<CropCard> cardsOf(List<EstateView> estates, List<SensorView> sensors,
            Map<Integer, Integer> openAlerts) {
        return estates.stream()
                .flatMap(estate -> estate.crops().stream()
                        .map(crop -> new CropCard(crop, estate.id(), estate.name(), estate.location(),
                                sensors.stream().filter(sensor -> crop.id().equals(sensor.cropId())).toList(),
                                openAlerts.getOrDefault(crop.id(), 0))))
                .toList();
    }

    public static List<CropCard> filter(List<CropCard> cards, CropFilter filter) {
        String term = normalize(filter.query());
        Comparator<CropCard> order = switch (filter.order() == null ? ORDER_NAME : filter.order()) {
            case ORDER_SOWING -> Comparator.comparing((CropCard card) -> card.crop().sowingDate(),
                    Comparator.nullsLast(Comparator.reverseOrder()));
            case ORDER_ALERTS -> Comparator.comparingLong(CropCard::openAlerts).reversed();
            default -> Comparator.comparing((CropCard card) -> normalize(card.crop().name()));
        };
        return cards.stream()
                .filter(card -> term.isEmpty() || normalize(card.crop().name() + " " + nullToEmpty(card.crop().variety())
                        + " " + card.estateName() + " " + nullToEmpty(card.location())).contains(term))
                .filter(card -> filter.stage() == null || filter.stage() == card.crop().stage())
                .filter(card -> filter.estateId() == null || filter.estateId().equals(card.estateId()))
                .sorted(order.thenComparing(card -> normalize(card.crop().name())))
                .toList();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String normalize(String value) {
        if (value == null) {
            return "";
        }
        return Normalizer.normalize(value.strip().toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
    }
}
