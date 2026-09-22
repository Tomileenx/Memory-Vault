package com.example.encoding_service.service;

import com.example.encoding_service.event.VideoEncodedEvent;
import com.example.encoding_service.event.VideoUploadedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class EncodingService {

    private final S3Client s3Client;
    private final KafkaTemplate<String, VideoEncodedEvent> kafkaTemplate;

    @Value("${aws.bucket-name}")
    private String bucketName;

    @Value("${ffmpeg.path}")
    private String ffmpegPath;

    @Value("${ffprobe.path}")
    private String ffprobePath;

    @Value("${encoding.temp-dir}")
    private String tempDir;

    private static final String VIDEO_ENCODED_TOPIC = "video.encoded";

    private static final List<int[]> VIDEO_QUALITIES = Arrays.asList(
            new int[]{1920, 5000, 1080},
            new int[]{1280, 2800, 720},
            new int[]{854, 1200, 480},
            new int[]{640, 800, 360}
    );


    /**
     * Main encoding pipeline
     *
     * Steps:
     * 1. Download raw video from S3
     * 2. Encode to multiple qualities using FFmpeg
     * 3. Generate HLS playlist (.m3u8) for each
     * 4. Create master playlist
     * 5. Upload all video files back to S3
     * 6. Publish video encoded event to kafka
     * @param event
     */

    public void encodeVideo(VideoUploadedEvent event) throws IOException {
        log.info("Encoding Video for: {}", event.videoId());

        // Create a unique path for a video
        String videoDir = tempDir + "/" + event.videoId();

        try {
            // Create temp directories
            Files.createDirectories(Paths.get(videoDir));
            Files.createDirectories(Paths.get(videoDir + "/encoded"));

            // Download raw video from S3
            String localVideoPath = videoDir + "/raw_video.mp4";
            downloadFromS3(event.videoKey(), localVideoPath);

            // Get video duration
            long durationSeconds = getVideoDuration(localVideoPath);
            log.info(
                    "Video duration extracted: videoId={}, durationSeconds={}",
                    event.videoId(),
                    durationSeconds
            );

            // Encode to multiple qualities + generate HLS
            for (int[] qualities : VIDEO_QUALITIES) {
                int width = qualities[0];
                int bitrate = qualities[1];
                int height = qualities[2];

                String qualityDir = videoDir + "/encoded/" + height + "p";
                Files.createDirectories(Paths.get(qualityDir));

                encodeToHLS(localVideoPath, qualityDir, width, height, bitrate);
                log.info("Encoded {}p successfully", height);
            }

            // Generate master playlist
            String masterPlaylist = videoDir + "/encoded/master.m3u8";
            generateMasterPlaylist(masterPlaylist);
            log.info("Master playlist generated");

            // Upload all encoded files to S3
            String encodedPrefix = "encoded/" + event.videoId() + "/";
            uploadEncodedFilesToS3(videoDir + "/encoded", encodedPrefix);
            log.info("Encoded files uploaded to S3");

            String masterPlaylistKey = encodedPrefix + "master.m3u8";
            String hlsUrl = "https://" + bucketName + ".s3.amazonaws.com/" + masterPlaylistKey;

            VideoEncodedEvent encodedEvent = new VideoEncodedEvent(
                    event.videoId(),
                    durationSeconds,
                    hlsUrl,
                    masterPlaylistKey,
                    true,
                    null
            );

            log.info("Publishing VideoEncodedEvent: {}", encodedEvent);

            log.info(
                    "Publishing event: videoId={}, durationSeconds={}, hlsUrl={}, success={}",
                    encodedEvent.videoId(),
                    encodedEvent.durationSeconds(),
                    encodedEvent.hlsUrl(),
                    encodedEvent.success()
            );

            kafkaTemplate.send(VIDEO_ENCODED_TOPIC, event.videoId(), encodedEvent);
            log.info("VideoEncodedEvent published for video: {}", event.videoId());
        } catch (Exception e) {
            String errorMessage = e.getMessage() != null
                    ? e.getMessage()
                    : e.getClass().getSimpleName();

            log.error(
                    "Encoding failed for video: {} — {}",
                    event.videoId(),
                    errorMessage,
                    e
            );

            VideoEncodedEvent failureEvent = new VideoEncodedEvent(
                    event.videoId(),
                    null,
                    null,
                    null,
                    false,
                    errorMessage
            );

            kafkaTemplate.send(VIDEO_ENCODED_TOPIC, event.videoId(), failureEvent);
        } finally {
            cleanUpTempFiles(videoDir);
        }
    }

    private void downloadFromS3(String videoKey, String localPath) {
        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(videoKey)
                .build();

        s3Client.getObject(getObjectRequest, Paths.get(localPath));
    }

    private long getVideoDuration(String videoPath) throws IOException, InterruptedException {

        ProcessBuilder processBuilder = new ProcessBuilder(
                ffprobePath,
                "-v", "error",
                "-show_entries", "format=duration",
                "-of", "default=noprint_wrappers=1:nokey=1",
                videoPath
        );

        processBuilder.redirectErrorStream(true);

        Process process = processBuilder.start();

        String output;

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()))) {

            output = reader.lines()
                    .collect(Collectors.joining())
                    .trim();
        }

        int exitCode = process.waitFor();

        log.info("ffprobe output: '{}'", output);
        log.info("ffprobe exit code: {}", exitCode);

        if (exitCode != 0) {
            throw new IOException(
                    "ffprobe failed with exit code: " + exitCode
            );
        }

        try {
            double duration = Double.parseDouble(output);

            return Math.round(duration);

        } catch (NumberFormatException e) {
            throw new IOException(
                    "Invalid duration returned by ffprobe: " + output,
                    e
            );
        }
    }

    private void encodeToHLS(
            String inputPath,
            String outputDir,
            int width,
            int height,
            int bitrate
    ) throws IOException, InterruptedException {
        String playlistPath = outputDir + "/playlist.m3u8";
        String segmentPattern = outputDir + "/segment_%03d.ts";

        // FFmpeg command for HLS encoding
        List<String> command = Arrays.asList(
                ffmpegPath,
                "-i", inputPath,                        // Input file
                "-vf", "scale=" + width + ":" + height,  // Scale to resolution
                "-c:v", "libx264",                      // Video codec
                "-b:v", bitrate + "k",                  // Video bitrate
                "-c:a", "aac",                          // Audio codec
                "-b:a", "128k",                         // Audio bitrate
                "-hls_time", "10",                      // 10 second segments
                "-hls_list_size", "0",                  // Keep all segments
                "-hls_segment_filename", segmentPattern,// Segment naming
                "-f", "hls",                            // Output format HLS
                playlistPath                            // Output playlist
        );

        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.redirectErrorStream(true);
        processBuilder.inheritIO();
        Process process = processBuilder.start();
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new RuntimeException("FFmpeg encoding failed with exit code " + exitCode);
        }
    }

    /**
     * Generate master Hls playlist that references all playlist quality
     * This is the file the video player download first
     */
    private void generateMasterPlaylist(String masterPlaylist) throws IOException {
        StringBuilder master = new StringBuilder();
        master.append("#EXTM3U\n");  // Tells the player "This is an HLS playlist"
        master.append("#EXT-X-VERSION:3\n\n");

        int[][] qualities = {
                {1920, 5000, 1080},
                {1280, 2800, 720},
                {854, 1200, 480},
                {640, 800, 360}
        };

        for (int[] q :  qualities) {
            int width = q[0];
            int bitrate = q[1];
            int height = q[2];

            master.append("#EXT-X-STREAM-INF:BANDWIDTH=")
                    .append(bitrate*1000)
                    .append(",RESOLUTION=").append(width).append("x").append(height)
                    .append(",CODECS=\"avc1.42e01e,mp4a.40.2\"\n");

            master.append(height).append("p/playlist.m3u8\n\n");
        }

        Files.writeString(Paths.get(masterPlaylist), master.toString());
    }

    private void uploadEncodedFilesToS3(String localDir, String s3Prefix) throws IOException {
        File directory = new File(localDir);
        uploadDirectoryToS3(directory, localDir, s3Prefix);
    }

    private void uploadDirectoryToS3(File dir, String baseDir, String s3Prefix) throws IOException {
        for (File file : dir.listFiles()) {
            if (file.isDirectory()) {
                uploadDirectoryToS3(file, baseDir, s3Prefix);
            } else {
                String relativePath = file.getAbsolutePath()
                        .substring(baseDir.length() + 1)
                        .replace("\\", "/");

                String s3Key = s3Prefix + relativePath;

                log.info("Base Dir      : {}", baseDir);
                log.info("Absolute Path : {}", file.getAbsolutePath());
                log.info("Relative Path : {}", relativePath);
                log.info("S3 Key        : {}", s3Key);

                String contentType = file.getName().endsWith(".m3u8")
                        ? "application/x-mpegURL"
                        : "video/MP2T";

                PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                        .bucket(bucketName)
                        .key(s3Key)
                        .contentType(contentType)
                        .build();

                s3Client.putObject(putObjectRequest, RequestBody.fromFile(file));
                log.debug("Uploaded to s3 {}", s3Key);
            }
        }
    }

    private void cleanUpTempFiles(String videoDir) {
        try {
            Path dirPath = Paths.get(videoDir);
            if (Files.exists(dirPath)) {
                Files.walk(dirPath)
                        .sorted(java.util.Comparator.reverseOrder())
                        .map(Path::toFile)
                        .forEach(file -> {
                            log.info("Deleting {}", file.getAbsolutePath());
                            file.delete();
                        });
                log.info("Temp files cleaned up for job: {}", videoDir);
            }
        } catch (IOException e) {
            log.warn("Failed to cleanup temp file:, {}", e.getMessage());
        }
    }
}
