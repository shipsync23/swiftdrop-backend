package com.swiftdrop.parcel;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.swiftdrop.user.RoleName;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

/** Builds the downloadable PDF delivery receipt (customer requirement "download a delivery receipt"). */
@Service
public class ReceiptService {

    private static final DateTimeFormatter DATE_TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.of("Asia/Colombo"));
    private static final Color BRAND = new Color(37, 99, 235);

    private final ParcelService parcelService;
    private final StatusHistoryRepository history;

    public ReceiptService(ParcelService parcelService, StatusHistoryRepository history) {
        this.parcelService = parcelService;
        this.history = history;
    }

    @Transactional(readOnly = true)
    public Receipt build(UUID parcelId, UUID userId, RoleName role) {
        Parcel parcel = parcelService.loadVisible(parcelId, userId, role);
        List<StatusHistory> steps = history.findByParcelIdOrderByChangedAtAsc(parcel.getId());

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4, 48, 48, 48, 48);
        try {
            PdfWriter.getInstance(document, out);
            document.open();

            Font title = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 22, BRAND);
            Font heading = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 13);
            Font normal = FontFactory.getFont(FontFactory.HELVETICA, 11);
            Font small = FontFactory.getFont(FontFactory.HELVETICA, 9, Color.GRAY);

            document.add(new Paragraph("SwiftDrop", title));
            document.add(new Paragraph("Delivery Receipt", heading));
            document.add(new Paragraph("Generated " + DATE_TIME.format(java.time.Instant.now()), small));
            document.add(new Paragraph(" "));

            PdfPTable details = new PdfPTable(new float[]{1.2f, 2.8f});
            details.setWidthPercentage(100);
            addRow(details, "Tracking number", parcel.getTrackingNumber(), normal);
            addRow(details, "Status", parcel.getStatus().name().replace('_', ' '), normal);
            addRow(details, "Booked on", DATE_TIME.format(parcel.getCreatedAt()), normal);
            addRow(details, "Pickup address", parcel.getPickupAddress(), normal);
            addRow(details, "Drop-off address", parcel.getDropoffAddress(), normal);
            addRow(details, "Destination zone", parcel.getDestZone().getName(), normal);
            addRow(details, "Recipient", parcel.getRecipientName() + "  (" + parcel.getRecipientPhone() + ")", normal);
            addRow(details, "Parcel", parcel.getSize().name() + ", " + parcel.getWeightKg() + " kg", normal);
            addRow(details, "Delivery fee", "LKR " + parcel.getFee(), heading);
            document.add(details);

            document.add(new Paragraph(" "));
            document.add(new Paragraph("Status history", heading));
            PdfPTable timeline = new PdfPTable(new float[]{1.5f, 2.5f});
            timeline.setWidthPercentage(100);
            for (StatusHistory step : steps) {
                addRow(timeline, DATE_TIME.format(step.getChangedAt()),
                        step.getNewStatus().name().replace('_', ' '), normal);
            }
            document.add(timeline);

            document.add(new Paragraph(" "));
            Paragraph footer = new Paragraph("Thank you for choosing SwiftDrop.", small);
            footer.setAlignment(Element.ALIGN_CENTER);
            document.add(footer);
        } catch (DocumentException e) {
            throw new IllegalStateException("Could not create receipt PDF", e);
        } finally {
            document.close();
        }
        return new Receipt("SwiftDrop-receipt-" + parcel.getTrackingNumber() + ".pdf", out.toByteArray());
    }

    private static void addRow(PdfPTable table, String label, String value, Font valueFont) {
        PdfPCell labelCell = new PdfPCell(new Phrase(label, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10)));
        labelCell.setBackgroundColor(new Color(241, 245, 249));
        labelCell.setPadding(6);
        PdfPCell valueCell = new PdfPCell(new Phrase(value, valueFont));
        valueCell.setPadding(6);
        table.addCell(labelCell);
        table.addCell(valueCell);
    }

    public record Receipt(String fileName, byte[] content) {
    }
}
