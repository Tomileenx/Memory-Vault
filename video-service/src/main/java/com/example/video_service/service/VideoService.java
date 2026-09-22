package com.example.video_service.service;

import com.example.video_service.dto.UploadUrlResponse;
import com.example.video_service.dto.VideoResponse;
import com.example.video_service.event.VideoUploadedEvent;
import com.example.video_service.dto.VideoUploadRequest;
import com.example.video_service.entity.Video;
import com.example.video_service.enumFolder.VideoStatus;
import com.example.video_service.exception.AlreadyExists;
import com.example.video_service.exception.BadRequest;
import com.example.video_service.exception.NotFound;
import com.example.video_service.exception.Unauthorized;
import com.example.video_service.idempotency.Idempotency;
import com.example.video_service.idempotency.IdempotencyOperation;
import com.example.video_service.idempotency.IdempotencyRepo;
import com.example.video_service.idempotency.IdempotencyStatus;
import com.example.video_service.repo.VideoRepo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;


import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class VideoService {
    private final S3StorageService s3StorageService;
    private final VideoRepo videoRepo;
    private final IdempotencyRepo idempotencyRepo;
    private final KafkaTemplate<String, VideoUploadedEvent> kafkaTemplate;

    @Value("${aws.s3.bucket-name}")
    private String bucketName;

    private static final String VIDEO_UPLOADED_TOPIC = "video.uploaded";

    /**
     * Upload video to AWS s3 and publish VideoUploadedEvent to Kafka
     *
     * FLOW:
     * 1. Receive multipart Video file
     * 2. Generate unique S3 key
     * 3. Upload to S3
     * 4. Publish VideoUploadedEvent to Kafka
     * 5. Encoding Service picks up and start FFmpeg
     */

    public UploadUrlResponse createUploadUrl(
            UUID userId,
            VideoUploadRequest request,
            String idempotencyKey
    ) {

        // Generate idempotency first
        Idempotency idempotency = Idempotency.builder()
                .userId(userId)
                .key(idempotencyKey)
                .operation(IdempotencyOperation.CREATE_UPLOAD_URL)
                .createdAt(LocalDateTime.now())
                .build();

        try {
            // Save and Flush idempotency
            idempotencyRepo.saveAndFlush(idempotency);
        } catch (DataIntegrityViolationException e) {

            // Find the existing idempotency record for this request
            Idempotency existing = idempotencyRepo.findByUserIdAndKeyAndOperation(
                    userId,
                    idempotencyKey,
                    IdempotencyOperation.CREATE_UPLOAD_URL
            ).orElseThrow(() -> new BadRequest("Unable to process idempotent request"));

            // Check if the request is still processing
            if (existing.getStatus() == IdempotencyStatus.PROCESSING) {
                throw new BadRequest(
                        "Upload request is being processed"
                );
            }

            // Find the completed request
            Video existingVideo = videoRepo.findById(existing.getResourceId())
                    .orElseThrow(() -> new NotFound("Video not found"));

            log.info(
                    "Duplicate create-upload-url request. " +
                            "Returning existing video: {}",
                    existingVideo.getId()
            );

            // Generate a new URL because pre-signed url expires
            String uploadUrl =
                    s3StorageService.generateUploadUrl(
                            existingVideo.getVideoKey()
                    );

            return new UploadUrlResponse(
                    existingVideo.getId(),
                    existingVideo.getVideoKey(),
                    uploadUrl,
                    existingVideo.getVideoStatus(),
                    existingVideo.getCreatedAt()
            );
        }

        // Check if video tile exists
        if (videoRepo.existsByUserIdAndTitle(
                userId,
                request.title()
        )) {
            throw new AlreadyExists(
                    request.title() + " already exists"
            );
        }

        // ADDING NEW VIDEO
        log.info("Adding new movie: {}", request.title());

        Video video = Video.builder()
                .userId(userId)
                .title(request.title())
                .description(request.description())
                .videoStatus(VideoStatus.PENDING)
                .build();

        Video savedVideo = videoRepo.save(video);

        log.info("Video saved with ID: {}", savedVideo.getId());

        // Generate video key needed for to generate presigned url
        String videoKey = "videos/" + userId + "/"
                + savedVideo.getId() + "/original/video";

        savedVideo.setVideoKey(videoKey);

        videoRepo.save(savedVideo);

        // Generate presigned url
        String uploadUrl =
                s3StorageService.generateUploadUrl(
                        videoKey
                );

        log.info(
                "Generated presigned upload URL for video: {}",
                savedVideo.getId()
        );

        // Generate idempotency record
        idempotency.setResourceId(savedVideo.getId());
        idempotency.setStatus(IdempotencyStatus.COMPLETED);
        idempotencyRepo.saveAndFlush(idempotency);

        return new UploadUrlResponse(
                savedVideo.getId(),
                videoKey,
                uploadUrl,
                VideoStatus.PENDING,
                LocalDateTime.now()
        );
    }

    public void completeUpload(
            UUID userId,
            String videoId,
            String idempotencyKey
    ) {
        // Find the uploaded videoId
        Video video = videoRepo.findById(videoId)
                .orElseThrow(() -> new NotFound("Video not found"));

        // Check if the video belongs to the user
        if (!video.getUserId().equals(userId)) {
            throw new Unauthorized("You do not own this video");
        }

        // Generate idempotency after checking if video was found
        Idempotency idempotency = Idempotency.builder()
                .userId(userId)
                .key(idempotencyKey)
                .resourceId(videoId)
                .operation(IdempotencyOperation.COMPLETE_UPLOAD)
                .createdAt(LocalDateTime.now())
                .build();

        try {
            idempotencyRepo.saveAndFlush(idempotency);
        } catch (DataIntegrityViolationException e) {
            // Find the existing idempotency record for this request
            Idempotency existing =
                    idempotencyRepo
                            .findByUserIdAndKeyAndOperation(
                                    userId,
                                    idempotencyKey,
                                    IdempotencyOperation.COMPLETE_UPLOAD
                            )
                            .orElseThrow(() ->
                                    new BadRequest(
                                            "Unable to process idempotent request"
                                    )
                            );

            if (!existing.getResourceId().equals(videoId)) {
                throw new BadRequest(
                        "Idempotency key was used for another video"
                );
            }

            if (existing.getStatus() == IdempotencyStatus.PROCESSING) {
                throw new BadRequest(
                        "Complete-upload request is being processed"
                );
            }

            log.info(
                    "Duplicate complete-upload request for video: {}",
                    videoId
            );

            return;
        }

        // VALIDATING UPLOAD
        if (video.getVideoStatus() == VideoStatus.UPLOADED) {
            log.info(  "Video {} is already uploaded",
                    videoId);

            idempotency.setStatus(
                    IdempotencyStatus.COMPLETED
            );

            idempotencyRepo.saveAndFlush(idempotency);

            return;
        }

        String videoKey = video.getVideoKey();

        // Check that the video has an S3 key
        if (videoKey == null || videoKey.isBlank()) {
            throw new IllegalStateException(
                    "Video does not have an S3 key"
            );
        }

        log.info(
                "Verifying S3 upload for video: {}",
                videoId
        );

        try {
            // Get the metada of the videoKey
            HeadObjectResponse metadata =
                    s3StorageService.getObjectMetadata(videoKey);

            long fileSize = metadata.contentLength();
            String contentType = metadata.contentType();

            log.info(
                    "S3 object found. Size: {} bytes, Content-Type: {}",
                    fileSize,
                    contentType
            );

            video.setFileSize(fileSize);
            video.setContentType(contentType);
            video.setVideoStatus(VideoStatus.UPLOADED);
            videoRepo.save(video);

            // Complete idempotency
            idempotency.setStatus(IdempotencyStatus.COMPLETED);
            idempotencyRepo.saveAndFlush(idempotency);

            VideoUploadedEvent event =
                    new VideoUploadedEvent(
                            video.getId(),
                            video.getUserId(),
                            videoKey,
                            bucketName,
                            fileSize
                    );

            kafkaTemplate.send(
                    VIDEO_UPLOADED_TOPIC,
                    video.getId(),
                    event
            );

            log.info(
                    "VideoUploadedEvent published for video: {}",
                    videoId
            );
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {

                throw new BadRequest(
                        "Video has not been uploaded to S3"
                );
            }

            throw e;
        }
    }

    public void updateHlsUrl(String videoId, String hlsUrl) {
        log.info("videoId = {}", videoId);
        log.info("hlsUrl = {}", hlsUrl);

        log.info("Updating hls Url for video {}", videoId);
        Video video = videoRepo.findById(videoId)
                .orElseThrow(() -> new NotFound("Video not found"));

        video.setHlsUrl(hlsUrl);
        video.setVideoStatus(VideoStatus.READY);
        videoRepo.save(video);

        log.info("Saved hlsUrl = {}",  video.getHlsUrl());

        log.info("Video is now ready for streaming {}", videoId);
    }

    public void updateVideoStatus(String videoId, VideoStatus videoStatus) {
        Video video = videoRepo.findById(videoId)
                .orElseThrow(() -> new NotFound("Video not found"));

        video.setVideoStatus(videoStatus);
        videoRepo.save(video);
    }

    public void updateVideoDurationSeconds(String videoId, Long durationSeconds) {
        Video video = videoRepo.findById(videoId)
                .orElseThrow(() -> new NotFound("Video not found"));

        video.setDurationSeconds(durationSeconds);
        videoRepo.save(video);
    }

    public VideoResponse getVideo(UUID userId, String videoId) {
        Video video = videoRepo.findByUserIdAndId(userId, videoId)
                .orElseThrow(() -> new NotFound("Video not found"));

        return new VideoResponse(
                videoId,
                video.getTitle(),
                video.getDescription(),
                video.getDurationSeconds(),
                video.getContentType(),
                video.getFileSize(),
                video.getVideoStatus(),
                video.getVideoKey(),
                video.getCreatedAt()
        );
    }

    public Page<VideoResponse> getAllVideos(UUID userId, Pageable pageable) {
        Pageable pageRequest = PageRequest.of(
                pageable.getPageNumber(),
                Math.min(pageable.getPageSize(), 10),
                Sort.by("createdAt").descending()
        );

        Page<Video> page = videoRepo.findAllByUserId(userId, pageRequest);

        return page.map(
                video -> new VideoResponse(
                        video.getId(),
                        video.getTitle(),
                        video.getDescription(),
                        video.getDurationSeconds(),
                        video.getContentType(),
                        video.getFileSize(),
                        video.getVideoStatus(),
                        video.getVideoKey(),
                        video.getCreatedAt()
                )
        );
    }

    public Page<VideoResponse> searchVideos(
            UUID userId,
            String title,
            Pageable pageable
    ) {
        Pageable pageRequest = PageRequest.of(
                pageable.getPageNumber(),
                Math.min(pageable.getPageSize(), 10),
                pageable.getSort()
        );

        Page<Video> page = videoRepo.searchVideos(userId, title, pageRequest);

        return page.map(
                video -> new VideoResponse(
                        video.getId(),
                        video.getTitle(),
                        video.getDescription(),
                        video.getDurationSeconds(),
                        video.getContentType(),
                        video.getFileSize(),
                        video.getVideoStatus(),
                        video.getVideoKey(),
                        video.getCreatedAt()
                )
        );
    }
}
