package com.swiftdrop.notification;

import com.swiftdrop.merchant.MerchantRepository;
import com.swiftdrop.messaging.ParcelEventMessage;
import com.swiftdrop.user.User;
import com.swiftdrop.user.UserRepository;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Builds and sends the notification for a status event: to the customer who booked the parcel,
 * or to the merchant for API orders (report 4.3).
 */
@Service
public class NotificationService {

    private final UserRepository users;
    private final MerchantRepository merchants;
    private final NotificationFactory factory;
    private final NotificationRepository notifications;

    public NotificationService(UserRepository users, MerchantRepository merchants,
                               NotificationFactory factory, NotificationRepository notifications) {
        this.users = users;
        this.merchants = merchants;
        this.factory = factory;
        this.notifications = notifications;
    }

    public void notifyFor(ParcelEventMessage event) {
        Optional<User> recipient = event.customerId() != null
                ? users.findById(event.customerId())
                : Optional.ofNullable(event.merchantId())
                        .flatMap(merchants::findById)
                        .map(m -> m.getUser());
        if (recipient.isEmpty()) {
            return;
        }
        User user = recipient.get();
        String subject = "SwiftDrop parcel " + event.trackingNo() + ": " + readable(event.newStatus());
        String text = message(event, user.getFullName());

        send(user, event, "EMAIL", user.getEmail(), subject, text);
        if (user.getPhone() != null) {
            send(user, event, "SMS", user.getPhone(), subject, text);
        }
    }

    /** A failure throws, so the transaction rolls back and RabbitMQ retries the message. */
    private void send(User user, ParcelEventMessage event, String channel, String to, String subject, String text) {
        factory.forChannel(channel).send(to, subject, text);
        Notification n = new Notification();
        n.setUserId(user.getId());
        n.setParcelId(event.parcelId());
        n.setChannel(channel);
        n.setRecipient(to);
        n.setMessage(text);
        n.setStatus("SENT");
        notifications.save(n);
    }

    private static String message(ParcelEventMessage e, String name) {
        String status = e.newStatus();
        String body = switch (status) {
            case "CREATED" -> "your delivery request has been received.";
            case "PICKED_UP" -> "your parcel has been picked up by our courier.";
            case "IN_TRANSIT" -> "your parcel is on its way.";
            case "OUT_FOR_DELIVERY" -> "your parcel is out for delivery today.";
            case "DELIVERED" -> "your parcel has been delivered. Thank you for using SwiftDrop!";
            case "FAILED" -> "we could not deliver your parcel. Our team will contact you.";
            default -> "the status of your parcel changed to " + readable(status) + ".";
        };
        return "Hi " + name + ", " + body + " Tracking number: " + e.trackingNo();
    }

    private static String readable(String status) {
        return status == null ? "" : status.replace('_', ' ');
    }
}
