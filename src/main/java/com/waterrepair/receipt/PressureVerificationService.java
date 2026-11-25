package com.waterrepair.receipt;

import com.waterrepair.common.ApiException;
import com.waterrepair.network.PipeSegment;
import com.waterrepair.network.PipeSegmentRepository;
import com.waterrepair.network.PressureReading;
import com.waterrepair.network.PressureReadingRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * 复压核验：压力值必须落在该支管正常区间；采样时刻加校时偏差后，
 * 与复压回执时刻之差必须不超过校时容差；读数设备管段还必须与当前边界一致。
 */
@Service
public class PressureVerificationService {

    private final PressureReadingRepository readingRepo;
    private final PipeSegmentRepository segmentRepo;
    private final int defaultToleranceSeconds;

    public PressureVerificationService(PressureReadingRepository readingRepo,
                                       PipeSegmentRepository segmentRepo,
                                       @Value("${app.pressure.default-time-tolerance-seconds:600}") int tolerance) {
        this.readingRepo = readingRepo;
        this.segmentRepo = segmentRepo;
        this.defaultToleranceSeconds = tolerance;
    }

    public record VerificationResult(String verificationStatus,
                                     String readingCode,
                                     String deviceNo,
                                     String segmentCode,
                                     Double measuredPressureMpa,
                                     Double normalMinMpa,
                                     Double normalMaxMpa,
                                     OffsetDateTime sampledAt,
                                     Integer clockOffsetSeconds,
                                     OffsetDateTime correctedSampledAt,
                                     OffsetDateTime eventAt,
                                     Long correctedTimeDeltaSeconds,
                                     Integer timeToleranceSeconds,
                                     boolean boundaryConsistent,
                                     List<String> reasons) {
    }

    @Transactional(readOnly = true)
    public VerificationResult verify(String readingCode, OffsetDateTime eventAt,
                                     Collection<String> boundarySegmentCodes) {
        if (readingCode == null || readingCode.isBlank()) {
            return new VerificationResult("FAILED", null, null, null, null, null, null,
                    null, null, null, eventAt, null, null, false,
                    List.of("必须引用压力读数 readingCode"));
        }
        PressureReading reading = readingRepo.findByReadingCode(readingCode)
                .orElseThrow(() -> ApiException.notFound("READING_NOT_FOUND", "压力读数不存在: " + readingCode));

        List<String> reasons = new ArrayList<>();
        PipeSegment segment = segmentRepo.findById(reading.getSegmentCode()).orElse(null);
        double min = segment != null ? segment.getNormalPressureMinMpa() : 0.2;
        double max = segment != null ? segment.getNormalPressureMaxMpa() : 0.45;
        int tolerance = segment != null && segment.getPressureTimeToleranceSeconds() != null
                ? segment.getPressureTimeToleranceSeconds() : defaultToleranceSeconds;

        if (reading.getPressureMpa() < min || reading.getPressureMpa() > max) {
            reasons.add("复压值 " + reading.getPressureMpa() + "MPa 超出 "
                    + reading.getSegmentCode() + " 支管正常区间 [" + min + ", " + max + "]MPa");
        }

        OffsetDateTime correctedSample = reading.getSampledAt()
                .plusSeconds(reading.getClockOffsetSeconds() == null ? 0 : reading.getClockOffsetSeconds());
        long delta = Math.abs(Duration.between(correctedSample, eventAt).getSeconds());
        if (delta > tolerance) {
            reasons.add("校时后采样时刻 " + correctedSample + " 与复压时刻 " + eventAt
                    + " 相差 " + delta + "s，超过校时容差 " + tolerance + "s");
        }

        boolean boundaryConsistent = true;
        if (boundarySegmentCodes != null && !boundarySegmentCodes.isEmpty()
                && !boundarySegmentCodes.contains(reading.getSegmentCode())) {
            boundaryConsistent = false;
            reasons.add("读数设备属于 " + reading.getSegmentCode() + "，与当前边界 "
                    + boundarySegmentCodes + " 不一致；若判断已被推翻请先做压力复核更正");
        }

        return new VerificationResult(reasons.isEmpty() ? "PASSED" : "FAILED",
                reading.getReadingCode(), reading.getDeviceNo(), reading.getSegmentCode(),
                reading.getPressureMpa(), min, max, reading.getSampledAt(),
                reading.getClockOffsetSeconds(), correctedSample, eventAt,
                delta, tolerance, boundaryConsistent, List.copyOf(reasons));
    }
}
