package com.example.video_service.enumFolder;

public enum VideoStatus {
    PENDING, // movie added but not uploaded yet
    UPLOADED, // raw video uploaded to S3
    ENCODING, // FFmpeg is encoding the video
    ENCODED, // encoding completed
    READY, // HLS playlist ready
    FAILED // encoding failed
}
