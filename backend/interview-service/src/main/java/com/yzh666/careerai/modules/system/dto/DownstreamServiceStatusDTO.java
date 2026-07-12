package com.yzh666.careerai.modules.system.dto;

public record DownstreamServiceStatusDTO(
    String serviceName,
    String status,
    boolean reachable,
    String detail
) {
}
