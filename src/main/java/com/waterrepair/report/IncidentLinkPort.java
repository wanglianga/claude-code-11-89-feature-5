package com.waterrepair.report;

import java.util.List;

/**
 * 报修线索与事故之间的关联端口，由 incident 模块实现。
 * 用于“热线重拨只添来源”：若原线索已归入事故，重拨线索自动追加为同一事故来源。
 */
public interface IncidentLinkPort {

    /** 原报修单已经归属的事故 id（通常 0 或 1 个）。 */
    List<Long> findIncidentIdsForReport(Long reportId);

    /**
     * 把重拨新线索追加到原线索所在事故。
     * @return 自动追加到的事故 id；原线索尚未形成事故时返回 null
     */
    Long autoAttachRedial(Long newReportId, Long originalReportId);
}
