package com.scorestv.mobile.service;

import com.scorestv.basketball.domain.BasketballTeam;
import com.scorestv.basketball.domain.BasketballTeamRepository;
import com.scorestv.common.ApiException;
import com.scorestv.mobile.domain.BasketballNotificationPref;
import com.scorestv.mobile.domain.BasketballNotificationPrefRepository;
import com.scorestv.mobile.domain.MobileDeviceToken;
import com.scorestv.mobile.domain.MobileDeviceTokenRepository;
import com.scorestv.mobile.web.dto.BasketballNotificationPrefsDto;
import com.scorestv.mobile.web.dto.SyncBasketballNotificationPrefsRequest;
import com.scorestv.mobile.web.dto.SyncNotificationPrefsResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Cihaz bazlı BASKETBOL bildirim tercihleri batch sync servisi.
 *
 * <p>Futbol {@link MobileNotificationPrefsService} ile aynı pattern (REPLACE).
 * Tek fark: 3 olay tipi + BasketballTeam FK.
 */
@Service
public class MobileBasketballNotificationPrefsService {

    private static final Logger log = LoggerFactory.getLogger(
            MobileBasketballNotificationPrefsService.class);

    private final MobileDeviceTokenRepository deviceRepository;
    private final BasketballNotificationPrefRepository prefRepository;
    private final BasketballTeamRepository teamRepository;

    public MobileBasketballNotificationPrefsService(
            MobileDeviceTokenRepository deviceRepository,
            BasketballNotificationPrefRepository prefRepository,
            BasketballTeamRepository teamRepository) {
        this.deviceRepository = deviceRepository;
        this.prefRepository = prefRepository;
        this.teamRepository = teamRepository;
    }

    @Transactional
    public SyncNotificationPrefsResponse syncAll(
            SyncBasketballNotificationPrefsRequest req) {
        MobileDeviceToken device = deviceRepository.findByFcmToken(req.fcmToken())
                .orElseThrow(() -> ApiException.notFound(
                        "Cihaz bulunamadı — once /device-tokens ile kaydet."));

        List<BasketballNotificationPref> existing =
                prefRepository.findByDeviceTokenId(device.getId());
        Map<Long, BasketballNotificationPref> byTeam = new HashMap<>();
        for (BasketballNotificationPref p : existing) {
            byTeam.put(p.getTeam().getId(), p);
        }

        Map<Long, BasketballNotificationPrefsDto> incoming = req.prefs() == null
                ? Map.of() : req.prefs();
        Set<Long> incomingTeamIds = new HashSet<>(incoming.keySet());

        int upserted = 0;
        int removed = 0;

        // INSERT/UPDATE
        for (Map.Entry<Long, BasketballNotificationPrefsDto> entry
                : incoming.entrySet()) {
            Long teamId = entry.getKey();
            BasketballNotificationPrefsDto dto = entry.getValue();
            if (dto == null) continue;

            BasketballNotificationPref pref = byTeam.get(teamId);
            if (pref == null) {
                BasketballTeam team = teamRepository.findById(teamId).orElse(null);
                if (team == null) {
                    log.debug("Basketbol sync: takım bulunamadı, atlandı: id={}",
                            teamId);
                    continue;
                }
                pref = new BasketballNotificationPref();
                pref.setDeviceToken(device);
                pref.setTeam(team);
            }
            pref.setNotifyStart(dto.basladi());
            pref.setNotifyPeriod(dto.ceyrek());
            pref.setNotifyFinal(dto.bitti());
            prefRepository.save(pref);
            upserted++;
        }

        // DELETE — mevcut ama incoming'te olmayan
        for (BasketballNotificationPref p : existing) {
            if (!incomingTeamIds.contains(p.getTeam().getId())) {
                prefRepository.delete(p);
                removed++;
            }
        }

        log.info("Basketbol notification prefs sync: deviceId={} upserted={} removed={}",
                device.getId(), upserted, removed);
        return new SyncNotificationPrefsResponse(device.getId(), upserted, removed);
    }

    /** Bir cihazın tüm basketbol tercihlerini döndür. */
    @Transactional(readOnly = true)
    public Map<Long, BasketballNotificationPrefsDto> getAll(String fcmToken) {
        MobileDeviceToken device = deviceRepository.findByFcmToken(fcmToken)
                .orElseThrow(() -> ApiException.notFound("Cihaz bulunamadı."));
        List<BasketballNotificationPref> prefs =
                prefRepository.findByDeviceTokenId(device.getId());
        Map<Long, BasketballNotificationPrefsDto> result = new HashMap<>();
        for (BasketballNotificationPref p : prefs) {
            result.put(p.getTeam().getId(), new BasketballNotificationPrefsDto(
                    p.isNotifyStart(),
                    p.isNotifyPeriod(),
                    p.isNotifyFinal()
            ));
        }
        return result;
    }
}
