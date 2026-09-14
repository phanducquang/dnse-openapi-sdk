package vn.dnse.openapi.websocket.dispatcher;

import com.fasterxml.jackson.databind.JsonNode;
import vn.dnse.openapi.websocket.model.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public final class MessageMapper {
    private MessageMapper() {}

    public static Object map(JsonNode data, long receivedAt) {
        String type = text(data, "T");
        return switch (type == null ? "" : type) {
            case "t" -> trade(data, receivedAt);
            case "te" -> tradeExtra(data, receivedAt);
            case "e" -> expectedPrice(data, receivedAt);
            case "q" -> quote(data, receivedAt);
            case "b", "bc" -> ohlc(data, receivedAt);
            case "sd" -> securityDefinition(data, receivedAt);
            case "f" -> foreignInvestor(data, receivedAt);
            case "mi" -> marketIndex(data, receivedAt);
            case "emi" -> estimatedMarketIndex(child(data, "marketIndex"), receivedAt);
            case "ii" -> indexInfluence(data, receivedAt);
            case "do", "eo" -> order(child(data, "order"), receivedAt);
            case "dp", "ep" -> position(child(data, "position"), receivedAt);
            case "s" -> session(data, receivedAt);
            case "a" -> account(data, receivedAt);
            default -> null;
        };
    }

    public static String symbol(JsonNode data) {
        String symbol = text(data, "symbol");
        if (symbol == null) symbol = text(data, "Symbol");
        if (symbol == null && data.has("order")) symbol = text(data.get("order"), "symbol");
        if (symbol == null && data.has("position")) symbol = text(data.get("position"), "symbol");
        return symbol;
    }

    private static Trade trade(JsonNode d, long r) { return new Trade(text(d,"marketId"),text(d,"boardId"),text(d,"isin"),text(d,"symbol"),decimal(d,"matchPrice"),number(d,"matchQtty"),number(d,"totalVolumeTraded"),decimal(d,"grossTradeAmount"),decimal(d,"highestPrice"),decimal(d,"lowestPrice"),decimal(d,"openPrice"),(int)number(d,"tradingSessionId"),timestamp(d.get("time")),r); }
    private static TradeExtra tradeExtra(JsonNode d,long r){ return new TradeExtra(text(d,"marketId"),text(d,"boardId"),text(d,"isin"),text(d,"symbol"),decimal(d,"matchPrice"),number(d,"matchQtty"),(int)number(d,"side"),decimal(d,"avgPrice"),number(d,"totalVolumeTraded"),decimal(d,"grossTradeAmount"),decimal(d,"highestPrice"),decimal(d,"lowestPrice"),decimal(d,"openPrice"),(int)number(d,"tradingSessionId"),timestamp(d.get("time")),r); }
    private static ExpectedPrice expectedPrice(JsonNode d,long r){ return new ExpectedPrice(text(d,"marketId"),text(d,"boardId"),text(d,"isin"),text(d,"symbol"),decimal(d,"closePrice"),decimal(d,"expectedTradePrice"),number(d,"expectedTradeQuantity"),timestamp(d.get("time")),r); }
    private static Quote quote(JsonNode d,long r){ return new Quote(text(d,"marketId"),text(d,"boardId"),text(d,"symbol"),text(d,"isin"),levels(d.get("bid")),levels(d.get("offer")),decimal(d,"totalOfferQtty"),decimal(d,"totalBidQtty"),timestamp(d.get("time")),r); }
    private static Ohlc ohlc(JsonNode d,long r){ return new Ohlc(text(d,"symbol"),text(d,"resolution"),decimal(d,"open"),decimal(d,"high"),decimal(d,"low"),decimal(d,"close"),number(d,"volume"),number(d,"time"),number(d,"lastUpdated"),text(d,"type"),r); }
    private static SecurityDefinition securityDefinition(JsonNode d,long r){ return new SecurityDefinition(text(d,"marketId"),text(d,"boardId"),text(d,"symbol"),text(d,"isin"),text(d,"productGrpId"),text(d,"securityGroupId"),decimal(d,"basicPrice"),decimal(d,"ceilingPrice"),decimal(d,"floorPrice"),number(d,"openInterestQuantity"),text(d,"securityStatus"),text(d,"symbolAdminStatusCode"),text(d,"symbolTradingMethodStatusCode"),text(d,"symbolTradingSanctionStatusCode"),timestamp(d.get("finalTradeDate")),timestamp(d.get("listingDate")),timestamp(d.get("time")),r); }
    private static ForeignInvestor foreignInvestor(JsonNode d,long r){ return new ForeignInvestor(text(d,"marketId"),text(d,"boardId"),text(d,"tradingSessionId"),text(d,"symbol"),text(d,"transactTime"),text(d,"foreignInvestorTypeCode"),number(d,"sellVolume"),number(d,"sellTradedAmount"),number(d,"buyVolume"),number(d,"buyTradedAmount"),number(d,"totalSellVolume"),number(d,"totalSellTradedAmount"),number(d,"totalBuyVolume"),number(d,"totalBuyTradedAmount"),number(d,"foreignerOrderLimitQuantity"),number(d,"foreignerBuyPossibleQuantity"),r); }
    private static MarketIndex marketIndex(JsonNode d,long r){ return new MarketIndex(text(d,"indexName"),decimal(d,"changedRatio"),decimal(d,"changedValue"),number(d,"fluctuationSteadinessIssueCount"),number(d,"fluctuationDownIssueCount"),number(d,"fluctuationUpIssueCount"),number(d,"fluctuationLowerLimitIssueCount"),number(d,"fluctuationUpperLimitIssueCount"),number(d,"fluctuationDownIssueVolume"),number(d,"fluctuationUpIssueVolume"),number(d,"fluctuationSteadinessIssueVolume"),text(d,"currencyCode"),text(d,"indexTypeCode"),decimal(d,"lowestValueIndexes"),decimal(d,"highestValueIndexes"),decimal(d,"priorValueIndexes"),decimal(d,"valueIndexes"),decimal(d,"contauctAccTrdVal"),number(d,"contauctAccTrdVol"),decimal(d,"blkTrdAccTrdVal"),number(d,"blkTrdAccTrdVol"),decimal(d,"grossTradeAmount"),number(d,"totalVolumeTraded"),(int)number(d,"marketIndexClass"),(int)number(d,"marketId"),(int)number(d,"tradingSessionId"),timestamp(d.get("transactTime")),r); }
    private static EstimatedMarketIndex estimatedMarketIndex(JsonNode d,long r){ return new EstimatedMarketIndex(text(d,"indexName"),decimal(d,"changedRatio"),decimal(d,"changedValue"),decimal(d,"fluctuationSteadinessIssueCount"),decimal(d,"fluctuationDownIssueCount"),decimal(d,"fluctuationUpIssueCount"),decimal(d,"valueIndexes"),decimal(d,"grossTradeAmount"),decimal(d,"totalVolumeTraded"),timestamp(d.get("time")),r); }
    private static IndexInfluence indexInfluence(JsonNode d,long r){ List<IndexInfluenceItem> items=new ArrayList<>(); JsonNode a=d.has("Data")?d.get("Data"):d.get("data"); if(a!=null&&a.isArray()) for(JsonNode i:a) items.add(new IndexInfluenceItem(timestamp(i.get("time")),text(i,"symbol"),decimal(i,"influence"),decimal(i,"influenceRatio"),decimal(i,"proportion"),decimal(i,"changeRatio"),decimal(i,"changeValue"),decimal(i,"price"),decimal(i,"grossTradeAmount"),decimal(i,"totalVolumeTraded"))); return new IndexInfluence(text(d,"index_name"),List.copyOf(items),r); }
    private static Order order(JsonNode d,long r){ return new Order(text(d,"id"),text(d,"side"),text(d,"accountNo"),text(d,"symbol"),decimal(d,"price"),decimal(d,"priceSecure"),decimal(d,"averagePrice"),number(d,"quantity"),number(d,"fillQuantity"),number(d,"canceledQuantity"),number(d,"leaveQuantity"),text(d,"orderType"),text(d,"orderStatus"),number(d,"loanPackageId"),text(d,"marketType"),text(d,"transDate"),text(d,"createdDate"),text(d,"modifiedDate"),r); }
    private static Position position(JsonNode d,long r){ return new Position(number(d,"id"),text(d,"accountNo"),text(d,"symbol"),text(d,"status"),number(d,"loanPackageId"),text(d,"side"),number(d,"accumulateQuantity"),number(d,"tradeQuantity"),number(d,"closedQuantity"),decimal(d,"costPrice"),decimal(d,"marketPrice"),decimal(d,"breakEvenPrice"),number(d,"openQuantity"),number(d,"overNightQuantity"),decimal(d,"averageClosePrice"),text(d,"marketType"),text(d,"createdDate"),text(d,"modifiedDate"),r); }
    private static Session session(JsonNode d,long r){ return new Session(text(d,"marketId"),text(d,"boardId"),text(d,"eventId"),(int)number(d,"tradingSessionId"),text(d,"tscProdGrpId"),timestamp(d.get("sendingTime")),r); }
    private static AccountUpdate account(JsonNode d,long r){ long ts=number(d,"timestamp"); return new AccountUpdate(decimal(d,"cash"),decimal(d,"buyingPower"),decimal(d,"portfolioValue"),decimal(d,"equity"),ts==0?null:Instant.ofEpochMilli(ts),r); }

    private static JsonNode child(JsonNode d,String field){ JsonNode c=d.get(field); return c==null||c.isNull()?d:c; }
    private static List<PriceLevel> levels(JsonNode n){ List<PriceLevel> result=new ArrayList<>(); if(n!=null&&n.isArray()) for(JsonNode i:n) result.add(new PriceLevel(decimal(i,"price"),number(i,"qtty"))); return List.copyOf(result); }
    private static String text(JsonNode n,String f){ JsonNode v=n==null?null:n.get(f); return v==null||v.isNull()?null:v.asText(); }
    private static long number(JsonNode n,String f){ JsonNode v=n==null?null:n.get(f); if(v==null||v.isNull()) return 0; if(v.isNumber()) return v.asLong(); try{return Long.parseLong(v.asText("0"));}catch(Exception e){return 0;} }
    private static BigDecimal decimal(JsonNode n,String f){ JsonNode v=n==null?null:n.get(f); if(v==null||v.isNull()) return BigDecimal.ZERO; try{return v.decimalValue();}catch(Exception e){try{return new BigDecimal(v.asText("0"));}catch(Exception ignored){return BigDecimal.ZERO;}} }
    private static String timestamp(JsonNode v){ if(v==null||v.isNull())return null; if(v.isTextual()||v.isNumber())return v.asText(); if(v.isObject()){JsonNode s=v.has("Seconds")?v.get("Seconds"):v.get("seconds");JsonNode n=v.has("Nanos")?v.get("Nanos"):v.get("nanos");return(s==null?"0":s.asText())+":"+(n==null?"0":n.asText());} return v.toString(); }
}
