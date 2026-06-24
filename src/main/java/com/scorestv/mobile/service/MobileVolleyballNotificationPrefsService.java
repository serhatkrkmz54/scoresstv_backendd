package com.scorestv.mobile.service;

import com.scorestv.common.ApiException;
import com.scorestv.mobile.domain.MobileDeviceToken;
import com.scorestv.mobile.domain.MobileDeviceTokenRepository;
import com.scorestv.mobile.domain.VolleyballNotificationPref;
import com.scorestv.mobile.domain.VolleyballNotificationPrefRepository;
import com.scorestv.mobile.web.dto.SyncNotificationPrefsResponse;
import com.scorestv.mobile.web.dto.SyncVolleyballNotificationPrefsRequest;
import com.scorestv.mobile.web.dto.VolleyballNotificationPrefsDto;
import com.scorestv.volleyball.domain.VolleyballTeam;
import com.scorestv.volleyball.domain.VolleyballTeamRepository;
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
 * Cihaz bazli VOLEYBOL bildirim tercihleri batch sync servisi.
 *
 * <p>Basketbol {@link MobileBasketballNotificationPrefsService} ile ayni pattern
 * (REPLACE). 3 olay tipi + VolleyballTeam FK.
 */
@Service
public class MobileVolleyballNotificationPrefsService {

    private static final Logger log = LoggerFactory.getLogger(
            MobileVolleyballNotificationPrefsService.class);

    private final MobileDeviceTokenRepository deviceRepository;
    private final VolleyballNotificationPrefRepository prefRepository;
    private final VolleyballTeamRepository teamRepository;

    public MobileVolleyballNotificationPrefsService(
            MobileDeviceTokenRepository deviceRepository,
            VolleyballNotificationPrefRepository prefRepository,
            VolleyballTeamRepository teamRepository) {
        this.deviceRepository = deviceRepository;
        this.prefRepository = prefRepository;
        this.teamRepository = teamRepository;
    }

    @Transactional
    public SyncNotificationPrefsResponse syncAll(
            SyncVolleyballNotificationPrefsRequest req) {
        MobileDeviceToken device = deviceRepository.findByFcmToken(req.fcmToken())
                .orElseThrow(() -> ApiException.notFound(
                        "Cihaz bulunamadi — once /device-tokens ile kaydet."));

        List<VolleyballNotificationPref> existing =
                prefRepository.findByDeviceTokenId(device.getId());
        Map<Long, VolleyballNotificationPref> byTeam = new HashMap<>();
        for (VolleyballNotificationPref p : existing) {
            byTeam.put(p.getTeam().getId(), p);
        }

        Map<Long, VolleyballNotificationPrefsDto> incoming = req.prefs() == null
                ? Map.of() : req.prefs();
        Set<Long> incomingTeamIds = new HashSet<>(incoming.keySet());

        int upserted = 0;
        int removed = 0;

        for (Map.Entry<Long, VolleyballNotificationPrefsDto> entry : incoming.entrySet()) {
            Long teamId = entry.getKey();
            VolleyballNotificationPrefsDto dto = entry.getValue();
            if (dto == null) continue;

            VolleyballNotificationPref pref = byTeam.get(teamId);
            if (pref == null) {
                VolleyballTeam team = teamRepository.findById(teamId).orElse(null);
                if (team == null) {
                    log.debug("Voleybol sync: takim bulunamadi, atlandi: id={}", teamId);
                    continue;
                }
                pref = new VolleyballNotificationPref();
                pref.setDeviceToken(device);
                pref.setTeam(team);
            }
            pref.setNotifyStart(dto.basladi());
            pref.setNotifyPeriod(dto.set());
            pref.setNotifyFinal(dto.bitti());
            prefRepository.save(pref);
            upserted++;
        }

        for (VolleyballNotificationPref p : existing) {
            if (!incomingTeamIds.contains(p.getTeam().getId())) {
                prefRepository.delete(p);
                removed++;
            }
        }

        log.info("Voleybol notification prefs sync: deviceId={} upserted={} removed={}",
                device.getId(), upserted, removed);
        return new SyncNotificationPrefsResponse(device.getId(), upserted, removed);
    }

    @Transactional(readOnly = true)
    public Map<Long, VolleyballNotificationPrefsDto> getAll(String fcmToken) {
        MobileDeviceToken device = deviceRepository.findByFcmToken(fcmToken)
                .orElseThrow(() -> ApiException.notFound("Cihaz bulunamadi."));
        List<VolleyballNotificationPref> prefs =
                prefRepository.findByDeviceTokenId(device.getId());
        Map<Long, VolleyballNotificationPrefsDto> result = new HashMap<>();
        for (VolleyballNotificationPref p : prefs) {
            result.put(p.getTeam().getId(), new VolleyballNotificationPrefsDto(
                    p.isNotifyStart(),
                    p.isNotifyPeriod(),
                    p.isNotifyFinal()
            ));
        }
        return result;
    }
}
