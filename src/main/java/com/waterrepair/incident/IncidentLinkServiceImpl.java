package com.waterrepair.incident;

import com.waterrepair.report.IncidentLinkPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 报修模块 IncidentLinkPort 的事故侧实现：
 * 重拨线索按原线索的事故归属自动追加来源，不新建事故。
 */
@Service
public class IncidentLinkServiceImpl implements IncidentLinkPort {

    private final IncidentSourceRepository sourceRepo;

    public IncidentLinkServiceImpl(IncidentSourceRepository sourceRepo) {
        this.sourceRepo = sourceRepo;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Long> findIncidentIdsForReport(Long reportId) {
        return sourceRepo.findByReportId(reportId).stream().map(IncidentSource::getIncidentId).toList();
    }

    @Override
    @Transactional
    public Long autoAttachRedial(Long newReportId, Long originalReportId) {
        List<IncidentSource> originalLinks = sourceRepo.findByReportId(originalReportId);
        if (originalLinks.isEmpty()) {
            return null;
        }
        Long incidentId = originalLinks.get(0).getIncidentId();
        if (!sourceRepo.existsByReportId(newReportId)) {
            sourceRepo.save(new IncidentSource(incidentId, newReportId, "REDIAL", OffsetDateTime.now()));
        }
        return incidentId;
    }
}
