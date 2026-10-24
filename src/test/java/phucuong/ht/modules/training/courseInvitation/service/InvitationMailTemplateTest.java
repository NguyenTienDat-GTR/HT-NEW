package phucuong.ht.modules.training.courseInvitation.service;

import org.junit.jupiter.api.Test;
import phucuong.ht.modules.training.courseInvitation.service.InvitationMailTemplate.Content;
import phucuong.ht.modules.training.courseInvitation.service.InvitationMailTemplate.CourseInfo;
import phucuong.ht.modules.training.courseInvitation.service.InvitationMailTemplate.Recipient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InvitationMailTemplateTest {

    private final CourseInfo course = new CourseInfo("KH-1", "Khóa Dự Trưởng 2026", "từ 01/08/2026 đến 05/08/2026", "Nhà thờ Chánh Tòa");

    private Recipient recipient(String holy, String name, String team) {
        return new Recipient("p-1", "a@b.vn", holy, name, "Giáo xứ A", "Giáo hạt B", "Giáo phận C", team);
    }

    @Test
    void containsEveryRequiredField() {
        Content content = InvitationMailTemplate.build(course, recipient("Giuse", "Nguyễn Văn A", "Đội 1"), false);
        for (String expected : new String[]{"Giuse", "Nguyễn Văn A", "Giáo xứ A", "Giáo hạt B", "Giáo phận C",
                "Khóa Dự Trưởng 2026", "từ 01/08/2026 đến 05/08/2026", "Nhà thờ Chánh Tòa", "Đội 1"}) {
            assertTrue(content.html().contains(expected), "html thiếu " + expected);
            assertTrue(content.text().contains(expected), "text thiếu " + expected);
        }
        assertEquals("Thông tin tham dự khóa huấn luyện: Khóa Dự Trưởng 2026", content.subject());
    }

    @Test
    void qrIsOnlyReferencedWhenRequested() {
        Content without = InvitationMailTemplate.build(course, recipient("Giuse", "A", null), false);
        assertFalse(without.html().contains("cid:"));
        assertFalse(without.text().contains("QR"));

        Content with = InvitationMailTemplate.build(course, recipient("Giuse", "A", null), true);
        assertTrue(with.html().contains("cid:" + InvitationMailTemplate.QR_CONTENT_ID));
        assertTrue(with.text().contains("QR"));
    }

    @Test
    void blankOptionalFieldsAreOmittedInsteadOfPrintedEmpty() {
        Recipient sparse = new Recipient("p", "a@b.vn", null, "Trần B", " ", null, "", null);
        Content content = InvitationMailTemplate.build(new CourseInfo("K", "Khóa X", "", null), sparse, false);
        assertFalse(content.text().contains("Tên thánh"));
        assertFalse(content.text().contains("Giáo xứ"));
        assertFalse(content.text().contains("Đội"));
        assertFalse(content.text().contains("Địa điểm"));
        assertFalse(content.text().contains("Thời gian tổ chức"));
        assertTrue(content.text().contains("Họ và tên: Trần B"));
        assertTrue(content.text().contains("Khóa huấn luyện: Khóa X"));
    }

    @Test
    void htmlSpecialCharactersAreEscaped() {
        Recipient evil = recipient("<script>alert(1)</script>", "A & B \"C\"", "<b>Đội</b>");
        Content content = InvitationMailTemplate.build(new CourseInfo("K", "Khóa <img src=x>", "p", null), evil, true);
        assertFalse(content.html().contains("<script>"));
        assertFalse(content.html().contains("<img src=x>"));
        assertFalse(content.html().contains("<b>Đội</b>"));
        assertTrue(content.html().contains("&lt;script&gt;"));
        assertTrue(content.html().contains("&amp;"));
    }

    @Test
    void controlCharactersCannotInjectHeadersIntoTheSubject() {
        Content content = InvitationMailTemplate.build(new CourseInfo("K", "Khóa\r\nBcc: x@y.z", "p", null), recipient("A", "B", null), false);
        assertFalse(content.subject().contains("\r"));
        assertFalse(content.subject().contains("\n"));
        assertEquals("Thông tin tham dự khóa huấn luyện: Khóa Bcc: x@y.z", content.subject());
    }

    @Test
    void greetingFallsBackWhenNameIsMissing() {
        Content content = InvitationMailTemplate.build(course, new Recipient("p", "a@b.vn", null, null, null, null, null, null), false);
        assertTrue(content.text().startsWith("Xin chào bạn,"));
    }

    @Test
    void cleanHandlesNullAndWhitespace() {
        assertEquals("", InvitationMailTemplate.clean(null));
        assertEquals("a b", InvitationMailTemplate.clean("  a\tb \n"));
    }
}
