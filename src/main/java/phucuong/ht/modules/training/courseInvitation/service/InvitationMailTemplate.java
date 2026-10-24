package phucuong.ht.modules.training.courseInvitation.service;

import org.springframework.web.util.HtmlUtils;

// Nội dung email thông tin tham dự khóa huấn luyện (HTML + bản chữ thuần). Mọi giá trị động đều được escape HTML.
final class InvitationMailTemplate {

    static final String QR_CONTENT_ID = "attendance-qr";

    private InvitationMailTemplate() {
    }

    record CourseInfo(String code, String name, String period, String location) {
    }

    record Recipient(String participationId, String email, String holyName, String fullName, String parish, String deanery,
                     String diocese, String teamName) {
    }

    record Content(String subject, String html, String text) {
    }

    static Content build(CourseInfo course, Recipient recipient, boolean includeQr) {
        String subject = "Thông tin tham dự khóa huấn luyện: " + clean(course.name());

        StringBuilder text = new StringBuilder();
        text.append("Xin chào ").append(display(recipient)).append(",\n\n")
                .append("Bạn đã được duyệt tham dự khóa huấn luyện. Thông tin tham dự của bạn:\n\n");
        StringBuilder rows = new StringBuilder();
        row(rows, text, "Tên thánh", recipient.holyName());
        row(rows, text, "Họ và tên", recipient.fullName());
        row(rows, text, "Giáo xứ", recipient.parish());
        row(rows, text, "Giáo hạt", recipient.deanery());
        row(rows, text, "Giáo phận", recipient.diocese());
        row(rows, text, "Khóa huấn luyện", course.name());
        row(rows, text, "Thời gian tổ chức", course.period());
        row(rows, text, "Địa điểm", course.location());
        row(rows, text, "Đội", recipient.teamName());
        if (includeQr) {
            text.append("\nMã QR điểm danh được đính kèm trong email này (ảnh qr-diem-danh.png). ")
                    .append("Vui lòng mang theo và xuất trình khi đến điểm danh. Không chia sẻ mã này cho người khác.\n");
        }
        text.append("\nTrân trọng.");

        StringBuilder html = new StringBuilder();
        html.append("<div style=\"font-family:Arial,Helvetica,sans-serif;font-size:14px;color:#1f2937;max-width:560px\">")
                .append("<p>Xin chào <strong>").append(esc(display(recipient))).append("</strong>,</p>")
                .append("<p>Bạn đã được duyệt tham dự khóa huấn luyện. Thông tin tham dự của bạn:</p>")
                .append("<table style=\"border-collapse:collapse;width:100%\">").append(rows).append("</table>");
        if (includeQr) {
            html.append("<p style=\"margin-top:18px\">Mã QR điểm danh của bạn (mang theo và xuất trình khi đến điểm danh, ")
                    .append("không chia sẻ cho người khác):</p>")
                    .append("<p><img src=\"cid:").append(QR_CONTENT_ID)
                    .append("\" alt=\"Mã QR điểm danh\" width=\"220\" height=\"220\" style=\"display:block\"></p>");
        }
        html.append("<p>Trân trọng.</p></div>");
        return new Content(subject, html.toString(), text.toString());
    }

    private static void row(StringBuilder html, StringBuilder text, String label, String value) {
        String cleaned = clean(value);
        if (cleaned.isEmpty()) {
            return;
        }
        text.append(label).append(": ").append(cleaned).append('\n');
        html.append("<tr><td style=\"padding:6px 10px;border:1px solid #e5e7eb;background:#f9fafb;width:36%\">")
                .append(esc(label)).append("</td><td style=\"padding:6px 10px;border:1px solid #e5e7eb\">")
                .append(esc(cleaned)).append("</td></tr>");
    }

    private static String display(Recipient recipient) {
        String holy = clean(recipient.holyName());
        String name = clean(recipient.fullName());
        String combined = (holy + " " + name).trim();
        return combined.isEmpty() ? "bạn" : combined;
    }

    // Bỏ xuống dòng / ký tự điều khiển để giá trị động không phá tiêu đề thư hay bố cục chữ thuần.
    static String clean(String value) {
        return value == null ? "" : value.replaceAll("[\\p{Cntrl}]+", " ").trim();
    }

    private static String esc(String value) {
        return HtmlUtils.htmlEscape(value, "UTF-8");
    }
}
