package com.actionow.system.dto;

import lombok.Data;

import java.time.LocalDate;
import java.util.Map;

/**
 * 平台统计响应
 *
 * @author Actionow
 */
@Data
public class PlatformStatsResponse {

    private String id;

    private LocalDate statsDate;

    private String period;

    private String metricType;

    private String workspaceId;

    private Long metricValue;

    private Map<String, Object> details;
}
