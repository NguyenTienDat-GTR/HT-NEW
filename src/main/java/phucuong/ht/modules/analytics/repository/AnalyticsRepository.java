package phucuong.ht.modules.analytics.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import phucuong.ht.modules.analytics.model.AnalyticsScope;
import phucuong.ht.modules.analytics.model.AnalyticsScopeType;
import phucuong.ht.modules.analytics.model.TimeBucket;
import phucuong.ht.modules.analytics.model.request.AnalyticsFilterRequest;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

@Repository @RequiredArgsConstructor
public class AnalyticsRepository {
    private final EntityManager entityManager;

    public long scalar(String sql, Map<String, Object> params) { Object value = query(sql, params).getSingleResult(); return value == null ? 0L : ((Number) value).longValue(); }
    public BigDecimal decimal(String sql, Map<String, Object> params) { Object value = query(sql, params).getSingleResult(); return value == null ? BigDecimal.ZERO : new BigDecimal(value.toString()); }
    public List<Object[]> rows(String sql, Map<String, Object> params) { return query(sql, params).getResultList(); }
    private Query query(String sql, Map<String, Object> params) { Query query = entityManager.createNativeQuery(sql); params.forEach(query::setParameter); return query; }

    public String leaderScope(AnalyticsScope scope, String leaderAlias) { return switch (scope.type()) {
        case DIOCESE -> leaderAlias + ".parish_id in (select p.id from parish p join deanery d on d.id = p.deanery_id where d.diocese_id = :dioceseId)";
        case DEANERY -> leaderAlias + ".parish_id in (select p.id from parish p where p.deanery_id = :deaneryId)";
        case PARISH -> leaderAlias + ".parish_id = :parishId";
        case ALL -> "1=1";
    }; }
    // Chỉ huynh trưởng thuộc giáo xứ đang hiệu lực (giáo xứ, giáo hạt và giáo phận cha đều bật).
    public String operationalLeaderUnits(String leaderAlias) {
        return leaderAlias + ".parish_id in (select p.id from parish p join deanery d on d.id = p.deanery_id join diocese di on di.id = d.diocese_id where p.status = true and d.status = true and di.status = true)";
    }
    public String operationalDeanery(String deaneryAlias) {
        return deaneryAlias + ".status = true and " + deaneryAlias + ".diocese_id in (select di.id from diocese di where di.status = true)";
    }
    public String operationalParish(String parishAlias) {
        return parishAlias + ".status = true and " + parishAlias + ".deanery_id in (select d.id from deanery d join diocese di on di.id = d.diocese_id where d.status = true and di.status = true)";
    }
    public String courseScope(AnalyticsScope scope, String courseAlias) { return switch (scope.type()) {
        case DIOCESE -> "(" + courseAlias + ".diocese_id = :dioceseId or " + courseAlias + ".deanery_id in (select id from deanery where diocese_id = :dioceseId))";
        case DEANERY -> courseAlias + ".deanery_id = :deaneryId";
        case PARISH -> "1=0";
        case ALL -> "1=1";
    }; }
    public Map<String,Object> params(AnalyticsScope scope) { Map<String,Object> p = new HashMap<>(); if (scope.dioceseId()!=null)p.put("dioceseId",scope.dioceseId()); if(scope.deaneryId()!=null)p.put("deaneryId",scope.deaneryId()); if(scope.parishId()!=null)p.put("parishId",scope.parishId()); return p; }
    public String dateFilter(String field, AnalyticsFilterRequest request, Map<String,Object> params) { StringBuilder s=new StringBuilder(); if(request.getFromDate()!=null){s.append(" and ").append(field).append(" >= :fromDate");params.put("fromDate",request.getFromDate());} if(request.getToDate()!=null){s.append(" and ").append(field).append(" <= :toDate");params.put("toDate",request.getToDate());} return s.toString(); }
    public String periodExpression(String field, TimeBucket bucket) { return switch(bucket) { case MONTH -> "to_char("+field+", 'YYYY-MM')"; case QUARTER -> "to_char("+field+", 'YYYY') || '-Q' || extract(quarter from "+field+")"; case YEAR -> "to_char("+field+", 'YYYY')"; }; }
}
