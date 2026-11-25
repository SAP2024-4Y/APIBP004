package com.jobportal.seed;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Small, valid PDF containing only synthetic demonstration text; no embedded content or external links. */
final class SampleResume {
    private SampleResume() {}
    static byte[] pdf(int candidateIndex) {
        String content = "BT /F1 16 Tf 50 750 Td (Synthetic candidate " + (candidateIndex + 1)
                + ") Tj 0 -30 Td /F1 11 Tf (Demo resume - no real personal information.) Tj ET\n";
        List<String> objects = List.of(
                "<< /Type /Catalog /Pages 2 0 R >>",
                "<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Resources << /Font << /F1 4 0 R >> >> /Contents 5 0 R >>",
                "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>",
                "<< /Length " + content.length() + " >>\nstream\n" + content + "endstream");
        StringBuilder pdf = new StringBuilder("%PDF-1.4\n");
        List<Integer> offsets = new ArrayList<>();
        for (int i = 0; i < objects.size(); i++) {
            offsets.add(pdf.length());
            pdf.append(i + 1).append(" 0 obj\n").append(objects.get(i)).append("\nendobj\n");
        }
        int xref = pdf.length();
        pdf.append("xref\n0 6\n0000000000 65535 f \n");
        offsets.forEach(offset -> pdf.append(String.format(java.util.Locale.ROOT, "%010d 00000 n \n", offset)));
        pdf.append("trailer\n<< /Size 6 /Root 1 0 R >>\nstartxref\n").append(xref).append("\n%%EOF\n");
        return pdf.toString().getBytes(StandardCharsets.US_ASCII);
    }
}
