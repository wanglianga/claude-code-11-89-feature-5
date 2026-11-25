package com.waterrepair.config;

import java.time.OffsetDateTime;
import java.util.List;

/** 随项目提供的 samples/*.json 对应的播种 DTO。 */
public final class SeedDtos {

    private SeedDtos() {
    }

    public record NetworkSeed(List<NodeDto> nodes,
                              List<SegmentDto> segments,
                              List<ValveDto> valves,
                              List<AddressDto> addresses,
                              List<LandmarkDto> landmarks,
                              List<DeviceDto> devices,
                              List<ReadingDto> readings) {
    }

    public record NodeDto(String code, String name, String type, Double lng, Double lat) {
    }

    public record SegmentDto(String code, String name, String from, String to,
                             Double pMin, Double pMax, Integer tolSec, String topologyStatus) {
    }

    public record ValveDto(String code, String name, String nodeCode,
                           String qualification, String permit) {
    }

    public record AddressDto(String code, String address, String segment, String building,
                             Double lng, Double lat, List<String> aliases) {
    }

    public record LandmarkDto(String code, String name, Double lng, Double lat,
                              List<String> candidateSegments) {
    }

    public record DeviceDto(String deviceNo, String name, String segment) {
    }

    public record ReadingDto(String code, String deviceNo, String segment, Double pressureMpa,
                             OffsetDateTime sampledAt, Integer clockOffsetSeconds, String note) {
    }

    public record CrewsSeed(List<CrewDto> crews,
                            List<VehicleDto> vehicles,
                            List<ToolDto> tools,
                            List<PermitDto> permits) {
    }

    public record CrewDto(String code, String name, String shiftStart, String shiftEnd,
                          String contactPhone, Boolean active, String primaryVehicleCode,
                          List<String> qualificationCodes, List<String> standardToolCodes) {
    }

    public record VehicleDto(String code, String name, String plateNo,
                             Boolean available, String unavailableReason) {
    }

    public record ToolDto(String code, String name, Boolean available, String unavailableReason) {
    }

    public record PermitDto(String code, String name, String valveCode) {
    }

    public record AccountsSeed(List<AccountDto> accounts) {
    }

    public record AccountDto(String username, String password, String displayName,
                             String role, String crewCode) {
    }

    public record TopologyCaseSeed(List<CaseIncidentDto> cases) {
    }

    public record CaseIncidentDto(String code, String title, String status,
                                  List<String> boundarySegmentCodes,
                                  List<String> valveCodes,
                                  @com.fasterxml.jackson.annotation.JsonProperty("notify") Boolean sendNotify,
                                  OffsetDateTime createdAt, OffsetDateTime confirmedAt,
                                  OffsetDateTime closedAt, String note) {
    }
}
