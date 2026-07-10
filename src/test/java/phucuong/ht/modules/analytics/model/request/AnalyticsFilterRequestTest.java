package phucuong.ht.modules.analytics.model.request;

import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AnalyticsFilterRequestTest {
    @Test void acceptsSupportedTimeBucketAndDateRange() { AnalyticsFilterRequest request=new AnalyticsFilterRequest(); request.setFromDate(LocalDate.of(2026,1,1)); request.setToDate(LocalDate.of(2026,12,31)); request.setTimeBucket("quarter"); request.setGroupBy("parish"); assertDoesNotThrow(request::validate); }
    @Test void rejectsReversedDateRangeAndUnsupportedBucket() { AnalyticsFilterRequest dates=new AnalyticsFilterRequest(); dates.setFromDate(LocalDate.of(2026,2,1)); dates.setToDate(LocalDate.of(2026,1,1)); assertThrows(IllegalArgumentException.class,dates::validate); AnalyticsFilterRequest bucket=new AnalyticsFilterRequest(); bucket.setTimeBucket("week"); assertThrows(IllegalArgumentException.class,bucket::validate); }
}
