package mx.itcelaya.hackonlinces.HackOnLinces.mapper;

import mx.itcelaya.hackonlinces.HackOnLinces.dto.response.DocumentResponse;
import mx.itcelaya.hackonlinces.HackOnLinces.dto.response.SubmissionResponse;
import mx.itcelaya.hackonlinces.HackOnLinces.entity.Document;
import mx.itcelaya.hackonlinces.HackOnLinces.entity.Submission;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class SubmissionMapper {

    public SubmissionResponse toResponse(Submission submission) {
        List<DocumentResponse> documents = submission.getDocuments().stream()
                .map(this::toDocumentResponse)
                .toList();

        return new SubmissionResponse(
                submission.getId(),
                submission.getUser().getId(),
                submission.getAttemptNumber(),
                submission.getStatus(),
                submission.getReason(),
                submission.getReviewedBy() != null ? submission.getReviewedBy().getId() : null,
                submission.getReviewedAt(),
                submission.getSentAt(),
                submission.getCreatedAt(),
                documents
        );
    }

    public DocumentResponse toDocumentResponse(Document document) {
        return new DocumentResponse(
                document.getId(),
                document.getOriginalName(),
                document.getMimeType(),
                document.getSize(),
                document.getUploadedAt()
        );
    }
}