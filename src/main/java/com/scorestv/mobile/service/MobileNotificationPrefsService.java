package com.scorestv.mobile.service;

import com.scorestv.common.ApiException;
import com.scorestv.football.domain.Team;
import com.scorestv.football.domain.TeamRepository;
import com.scorestv.mobile.domain.MobileDeviceToken;
import com.scorestv.mobile.domain.MobileDeviceTokenRepository;
import com.scorestv.mobile.domain.UserNotificationPref;
import com.scorestv.mobile.domain.UserNotificationPrefRepository;
import com.scorestv.mobile.web.dto.NotificationPrefsDto;
import com.scorestv.mobile.web.dto.SyncNotificationPrefsRequest;
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
 * Cihaz bazli bildirim tercihleri batch sync servisi.
 *
 * <p>Mobile state-of-truth: her sync isteginde gelen harita uyari REPLACE
 * stratejisiyle yazilir. Yani:
 * <ul>
 *   <li>Map'te olan + DB'de olan → UPDATE bayraklar</li>
 *   <li>Map'te olan + DB'de yok → INSERT</li>
 *   <li>Map'te yok + DB'de var → DELETE (kullanici takimi favorilerden cikardi)</li>
 * </ul>
 */
@Service
public class MobileNotificationPrefsService {

    private static final Logger log =
            LoggerFactory.getLogger(MobileNotificationPrefsService.class);

    private final MobileDeviceTokenRepository deviceRepository;
    private final UserNotificationPrefRepository prefRepository;
    private final TeamRepository teamRepository;

    public MobileNotificationPrefsService(
            MobileDeviceTokenRepository deviceRepository,
            UserNotificationPrefRepository prefRepository,
            TeamRepository teamRepository) {
        this.deviceRepository = deviceRepository;
        this.prefRepository = prefRepository;
        this.teamRepository = teamRepository;
    }

    @Transactional
    public SyncNotificationPrefsResponse syncAll(SyncNotificationPrefsRequest req) {
        MobileDeviceToken device = deviceRepository.findByFcmToken(req.fcmToken())
                .orElseThrow(() -> ApiException.notFound(
                        "Cihaz bulunamadi — once /device-tokens ile kaydet."));

        // Tum mevcut kayitlari cek.
        List<UserNotificationPref> existing =
                prefRepository.findByDeviceTokenId(device.getId());
        Map<Long, UserNotificationPref> byTeam = new HashMap<>();
        for (UserNotificationPref p : existing) {
            byTeam.put(p.getTeam().getId(), p);
        }

        Map<Long, NotificationPrefsDto> incoming = req.prefs() == null
                ? Map.of() : req.prefs();
        Set<Long> incomingTeamIds = new HashSet<>(incoming.keySet());

        int upserted = 0;
        int removed = 0;

        // INSERT/UPDATE
        for (Map.Entry<Long, NotificationPrefsDto> entry : incoming.entrySet()) {
            Long teamId = entry.getKey();
            NotificationPrefsDto dto = entry.getValue();
            if (dto == null) continue;

            UserNotificationPref pref = byTeam.get(teamId);
            if (pref == null) {
                // Takim db'de var mi? Yoksa atla (geçersiz id).
                Team team = teamRepository.findById(teamId).orElse(null);
                if (team == null) {
                    log.debug("Sync: takim bulunamadi, atlandi: id={}", teamId);
                    continue;
                }
                pref = new UserNotificationPref();
                pref.setDeviceToken(device);
                pref.setTeam(team);
            }
            pref.setNotifyGoal(dto.gol());
            pref.setNotifyRedCard(dto.kirmizi());
            pref.setNotifyPenalty(dto.penalti());
            pref.setNotifyKickoff(dto.basladi());
            pref.setNotifyFinal(dto.bitti());
            // Geriye-uyumlu: eski client kadro göndermez (null) → dokunma,
            // entity default (true) / mevcut değer korunur.
            if (dto.kadro() != null) pref.setNotifyLineup(dto.kadro());
            prefRepository.save(pref);
            upserted++;
        }

        // DELETE — mevcut ama incoming'te olmayan
        for (UserNotificationPref p : existing) {
            if (!incomingTeamIds.contains(p.getTeam().getId())) {
                prefRepository.delete(p);
                removed++;
            }
        }

        log.info("Notification prefs sync: deviceId={} upserted={} removed={}",
                device.getId(), upserted, removed);
        return new SyncNotificationPrefsResponse(device.getId(), upserted, removed);
    }

    /** Bir cihazin tum tercihlerini dondur — restore (gelecekteki feature). */
    @Transactional(readOnly = true)
    public Map<Long, NotificationPrefsDto> getAll(String fcmToken) {
        MobileDeviceToken device = deviceRepository.findByFcmToken(fcmToken)
                .orElseThrow(() -> ApiException.notFound("Cihaz bulunamadi."));
        List<UserNotificationPref> prefs =
                prefRepository.findByDeviceTokenId(device.getId());
        Map<Long, NotificationPrefsDto> result = new HashMap<>();
        for (UserNotificationPref p : prefs) {
            result.put(p.getTeam().getId(), new NotificationPrefsDto(
                    p.isNotifyGoal(),
                    p.isNotifyRedCard(),
                    p.isNotifyPenalty(),
                    p.isNotifyKickoff(),
                    p.isNotifyFinal(),
                    p.isNotifyLineup()
            ));
        }
        return result;
    }
}
