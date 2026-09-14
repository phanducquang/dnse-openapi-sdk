package vn.dnse.openapi.websocket.model;

import java.math.BigDecimal;

public record IndexInfluenceItem(
        String time, String symbol,
        BigDecimal influence, BigDecimal influenceRatio, BigDecimal proportion,
        BigDecimal changeRatio, BigDecimal changeValue, BigDecimal price,
        BigDecimal grossTradeAmount, BigDecimal totalVolumeTraded
) {}
