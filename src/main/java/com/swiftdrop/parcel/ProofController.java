package com.swiftdrop.parcel;

import com.swiftdrop.common.exception.ApiException;
import com.swiftdrop.common.security.CurrentUser;
import com.swiftdrop.courier.DeliveryProof;
import com.swiftdrop.courier.DeliveryProofRepository;
import com.swiftdrop.courier.PhotoStorage;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Proof of delivery (same visibility rules as the parcel: owner, assigned courier, staff).
 *  GET /api/v1/parcels/{id}/proofs
 *  GET /api/v1/parcels/{id}/proofs/{proofId}/photo
 */
@RestController
@RequestMapping("/api/v1/parcels/{id}/proofs")
public class ProofController {

    private final ParcelService parcelService;
    private final DeliveryProofRepository proofs;
    private final PhotoStorage photoStorage;

    public ProofController(ParcelService parcelService, DeliveryProofRepository proofs, PhotoStorage photoStorage) {
        this.parcelService = parcelService;
        this.proofs = proofs;
        this.photoStorage = photoStorage;
    }

    @GetMapping
    public List<ProofResponse> list(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        Parcel parcel = parcelService.loadVisible(id, CurrentUser.id(jwt), CurrentUser.role(jwt));
        return proofs.findByParcelIdOrderByCreatedAtDesc(parcel.getId()).stream()
                .map(p -> new ProofResponse(p.getId(), p.getRecipientName(), p.getNote(), p.getFailureReason(),
                        p.getPhotoPath() != null, p.getCreatedAt()))
                .toList();
    }

    @GetMapping("/{proofId}/photo")
    public ResponseEntity<byte[]> photo(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                        @PathVariable UUID proofId) {
        Parcel parcel = parcelService.loadVisible(id, CurrentUser.id(jwt), CurrentUser.role(jwt));
        DeliveryProof proof = proofs.findById(proofId)
                .filter(p -> p.getParcelId().equals(parcel.getId()) && p.getPhotoPath() != null)
                .orElseThrow(() -> ApiException.notFound("Photo not found"));
        MediaType type = proof.getPhotoPath().endsWith(".png") ? MediaType.IMAGE_PNG : MediaType.IMAGE_JPEG;
        return ResponseEntity.ok().contentType(type).body(photoStorage.load(proof.getPhotoPath()));
    }

    public record ProofResponse(UUID id, String recipientName, String note, String failureReason,
                                boolean hasPhoto, Instant createdAt) {
    }
}
