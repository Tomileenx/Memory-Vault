# MemoryVault

MemoryVault is a backend-focused video storage and streaming system built with Spring Boot microservices. It allows authenticated users to upload videos, process them into multiple streaming qualities, and stream the resulting videos using HTTP Live Streaming (HLS).

The project demonstrates practical backend concepts including Microservices, JWT authentication, asynchronous communication, Kafka, PostgreSQL, Redis, Amazon S3, FFmpeg, FFprobe, presigned URLs, idempotency, rate limiting, and HLS streaming.

---

# Architecture

```text
                         ┌─────────────────┐
                         │     Client      │
                         └────────┬────────┘
                                  │
                                  ▼
                         ┌─────────────────┐
                         │   User Service  │
                         │                 │
                         │ Registration    │
                         │ Authentication  │
                         │ JWT             │
                         └────────┬────────┘
                                  │
                                  │ JWT
                                  ▼
                         ┌─────────────────┐
                         │  Video Service  │
                         │                 │
                         │ Video Metadata  │
                         │ Upload URLs     │
                         │ Search          │
                         └────────┬────────┘
                                  │
                           video.uploaded
                                  │
                                  ▼
                         ┌─────────────────┐
                         │Encoding Service │
                         │                 │
                         │ FFmpeg          │
                         │ FFprobe         │
                         │ HLS             │
                         │ S3              │
                         └────────┬────────┘
                                  │
                           video.encoded
                                  │
                    ┌─────────────┴─────────────┐
                    ▼                           ▼
             ┌─────────────┐             ┌──────────────┐
             │Video Service│             │   Streaming  │
             │             │             │   Service    │
             │Status       │             │              │
             │Duration     │             │ Redis        │
             │HLS metadata │             │ Signed URLs  │
             └─────────────┘             │ HLS          │
                                         └──────────────┘
```

---

# Services

## User Service

Responsible for user identity and authentication.

Features include:

* User registration
* User authentication
* JWT generation
* Password handling
* User identity management
* Authentication for protected services

## Video Service

Responsible for the video upload lifecycle and video metadata.

Features include:

* Video creation
* Presigned S3 upload URLs
* Upload completion
* Video metadata
* Video search
* Pagination
* Sorting
* Video status management
* Consuming encoding results

## Encoding Service

Responsible for processing uploaded videos.

Features include:

* Downloading original videos from S3
* FFmpeg video encoding
* FFprobe duration extraction
* HLS generation
* Multiple video qualities
* Uploading encoded files to S3
* Publishing encoding events through Kafka

Generated qualities:

```text
1080p
720p
480p
360p
```

## Streaming Service

Responsible for video playback.

Features include:

* Streaming URL generation
* HLS playlist processing
* S3 presigned URLs
* Redis caching
* Playlist URL rewriting
* Streaming endpoint rate limiting

---

# Technologies

* Java 17
* Spring Boot
* Spring Security
* Spring Data JPA
* PostgreSQL
* Apache Kafka
* Redis
* Amazon S3
* AWS SDK
* FFmpeg
* FFprobe
* Docker
* Maven
* JUnit
* Mockito

---

# Installation

## 1. Clone the Repository

```bash
git clone https://github.com/Tomileenx/Memory-Vault.git
cd Memory-Vault
```

## 2. Install Java

MemoryVault uses **Java 17**.

Verify your installation:

```bash
java -version
```

You should see Java 17.

## 3. Install Maven

Verify Maven:

```bash
mvn -version
```

## 4. Install Docker

Docker is used to run the local infrastructure.

Verify Docker:

```bash
docker --version
docker compose version
```

---

# FFmpeg and FFprobe Configuration

The Encoding Service requires both **FFmpeg** and **FFprobe**.

FFmpeg is used to encode the video and generate HLS files.

FFprobe is used to extract metadata such as video duration.

## Windows Installation

Download and install an FFmpeg build that contains:

```text
ffmpeg.exe
ffprobe.exe
```

For example, if the files are located at:

```text
C:\ffmpeg\bin\ffmpeg.exe
C:\ffmpeg\bin\ffprobe.exe
```

configure the Encoding Service:

```yaml
ffmpeg:
  path: "C:/ffmpeg/bin/ffmpeg.exe"

ffprobe:
  path: "C:/ffmpeg/bin/ffprobe.exe"
```

The paths can be changed to match the location on your machine.

### Verify FFmpeg

```powershell
C:\ffmpeg\bin\ffmpeg.exe -version
```

### Verify FFprobe

```powershell
C:\ffmpeg\bin\ffprobe.exe -version
```

If FFmpeg and FFprobe are added to the system `PATH`, the application can also be configured to use their executable names directly.

---

# AWS S3 Configuration

MemoryVault uses Amazon S3 for video storage.

S3 stores:

```text
Original videos
Encoded video files
HLS playlists
HLS segments
```

Example structure:

```text
encoded/
└── {videoId}/
    ├── master.m3u8
    ├── 1080p/
    ├── 720p/
    ├── 480p/
    └── 360p/
```

Configure the required AWS properties in the service configuration.

Example:

```yaml
aws:
  s3:
    bucket-name: your-bucket-name
    region: af-south-1
```

AWS credentials should **not** be committed to Git.

Use environment variables or another secure credential mechanism.

---

# Application Configuration

Each microservice has its own:

```text
application.yaml
```

The main configuration areas are:

```text
Database
Kafka
Redis
AWS S3
JWT
FFmpeg
FFprobe
```

For example, the Encoding Service requires:

```yaml
ffmpeg:
  path: "C:/ffmpeg/bin/ffmpeg.exe"

ffprobe:
  path: "C:/ffmpeg/bin/ffprobe.exe"
```

The Streaming Service requires access to:

```text
AWS S3
Redis
Kafka
```

The Video Service requires access to:

```text
PostgreSQL
Kafka
AWS S3
```

---

# Local Infrastructure

MemoryVault uses Docker for local infrastructure.

The main infrastructure components are:

```text
PostgreSQL
Redis
Kafka
```

Start them from the project root:

```bash
docker compose up -d
```

Check running containers:

```bash
docker ps
```

Stop the infrastructure:

```bash
docker compose down
```

---

# Service Ports

The services run independently.

| Service           |   Port |
| ----------------- |-------:|
| User Service      | `8084` |
| Video Service     | `8085` |
| Encoding Service  | `8086` |
| Streaming Service | `8087` |

The exact ports can be changed through each service's `application.yaml`.

---

# API Endpoints

All protected endpoints require a JWT:

```http
Authorization: Bearer <JWT_TOKEN>
```

## User Service

Base URL:
```text
http://localhost:8084
```

### Register

```http
POST /api/v1/memVault/register
```

Example:

```json
{
  "email": "user@example.com",
  "fullName": "John Doe",
  "password": "password"
}
```

### Verify Email

```http
GET /api/v1/memVault/verify-email?token=
```

### Login

```http
POST /api/v1/memVault/login
```

Returns authentication information including the JWT used for protected requests.

### Forgot Password

```http
POST /api/v1/memVault/forgot-password
```

### Reset Password

```http
POST /api/v1/memVault/forgot-password?token=
```

### Refresh Token

```http
POST /api/v1/memVault/refreshToken
```

### Logout

```http
POST /api/v1/memVault/logout
```

---

# Video Service API

Base URL:

```text
http://localhost:8085
```

## Request Upload URL

```http
POST /api/v1/memVault/upload-url
Authorization: Bearer <JWT_TOKEN>
```

The Video Service creates the video record and returns a presigned S3 upload URL.

## Upload Directly to S3

Use the returned URL:

```http
PUT <PRESIGNED_S3_URL>
```

The video file is uploaded directly to S3 rather than passing through the Video Service.

## Complete Upload

```http
POST /api/v1/memVault/complete-upload
Authorization: Bearer <JWT_TOKEN>
```

After the upload succeeds, notify the Video Service that the upload has completed.

The Video Service then publishes:

```text
video.uploaded
```

to Kafka.

## Get Video

```http
GET /api/v1/memVault/{videoId}
Authorization: Bearer <JWT_TOKEN>
```

## Get All Videos

```http
GET /api/v1/memVault/videos
Authorization: Bearer <JWT_TOKEN>
```

## Search Videos

```http
GET /api/v1/memVault/search-videos?title=Random%20Video%201
Authorization: Bearer <JWT_TOKEN>
```

The search supports title filtering and pagination/sorting according to the endpoint configuration.

Example:

```text
http://localhost:8085/api/v1/memVault/search-videos?title=Random%20Video%201
```

---

# Encoding Service

The Encoding Service primarily operates through Kafka rather than being called directly by the client.

It consumes:

```text
video.uploaded
```

The processing flow is:

```text
S3 Original Video
       │
       ▼
   FFprobe
       │
       ▼
    FFmpeg
       │
       ├── 1080p
       ├── 720p
       ├── 480p
       └── 360p
       │
       ▼
   HLS Playlist
       │
       ▼
      S3
       │
       ▼
video.encoded
```

After successful processing, the service publishes a `VideoEncodedEvent`.

---

# Streaming Service API

Base URL:

```text
http://localhost:8087
```

## Get Streaming URL

```http
GET /api/v1/memVault/stream/{videoId}
Authorization: Bearer <JWT_TOKEN>
```

Example:

```text
http://localhost:8087/api/v1/memVault/stream/494d89ad-1b1e-47db-a0c9-bbd0f22870f7
```

The Streaming Service:

1. Authenticates the request.
2. Retrieves the master playlist key from Redis.
3. Generates or retrieves a cached signed URL.
4. Returns the streaming information.

## Get Signed Playlist

```http
GET /api/v1/memVault/{videoId}/playlist?path=<playlist-path>
Authorization: Bearer <JWT_TOKEN>
```

The service retrieves the playlist from S3 and rewrites the required S3 paths with signed URLs.

---

# End-to-End API Workflow

The normal workflow is:

```text
┌────────────────────┐
│ 1. Register/Login  │
└─────────┬──────────┘
          │
          │ JWT
          ▼
┌────────────────────┐
│ 2. Request Upload  │
│    URL             │
└─────────┬──────────┘
          │
          │ Presigned URL
          ▼
┌────────────────────┐
│ 3. Upload to S3    │
└─────────┬──────────┘
          │
          ▼
┌────────────────────┐
│ 4. Complete Upload │
└─────────┬──────────┘
          │
          │ video.uploaded
          ▼
┌────────────────────┐
│ 5. Encoding        │
│    Service         │
│                    │
│ FFmpeg + FFprobe   │
└─────────┬──────────┘
          │
          │ video.encoded
          ▼
     ┌────┴─────┐
     │          │
     ▼          ▼
 Video       Streaming
 Service      Service
     │          │
     ▼          ▼
PostgreSQL    Redis
                │
                ▼
          Signed HLS URL
                │
                ▼
             Player
```

---

# Kafka Events

## `video.uploaded`

Published by the Video Service after a successful upload completion.

Consumed by:

```text
Encoding Service
```

## `video.encoded`

Published by the Encoding Service after video processing.

Consumed by:

```text
Video Service
Streaming Service
```

A successful event contains:

```text
videoId
durationSeconds
hlsUrl
masterPlaylistKey
success
errorMessage
```

---

# Running the Services

Start the infrastructure first:

```bash
docker compose up -d
```

Then start each Spring Boot service.

From each service directory:

```bash
mvn spring-boot:run
```

For example:

```bash
cd user-service
mvn spring-boot:run
```

Then:

```bash
cd video-service
mvn spring-boot:run
```

Then:

```bash
cd encoding-service
mvn spring-boot:run
```

And:

```bash
cd streaming-service
mvn spring-boot:run
```

Make sure the required infrastructure and configuration are available before starting the services.

---

# Testing

The project contains unit tests using:

* JUnit
* Mockito

Tests cover areas including:

* Video status updates
* HLS URL updates
* Encoding behavior
* S3 interactions
* Presigned URL generation
* Redis caching
* Playlist processing

Run tests with:

```bash
mvn test
```

---

# Project Structure

```text
MemoryVault/
│
├── user-service/
│   ├── src/main/java/
│   └── pom.xml
│
├── video-service/
│   ├── src/main/java/
│   └── pom.xml
│
├── encoding-service/
│   ├── src/main/java/
│   ├── src/test/
│   └── pom.xml
│
├── streaming-service/
│   ├── src/main/java/
│   ├── src/test/
│   └── pom.xml
│
├── docker-compose.yml
├── memoryVault.html
└── README.md
```

---

# Engineering Concepts Demonstrated

* Spring Boot microservices
* REST APIs
* JWT authentication
* Spring Security
* PostgreSQL and JPA
* Kafka event-driven architecture
* Asynchronous processing
* Redis caching
* Amazon S3
* S3 presigned URLs
* HLS video streaming
* FFmpeg video processing
* FFprobe metadata extraction
* Idempotency
* Rate limiting
* Database transactions
* Concurrency considerations
* Unit testing with Mockito
* Docker-based development infrastructure

---

# Future Improvements

Potential improvements include:

* API Gateway
* Centralized configuration
* Service discovery
* Improved observability and distributed tracing
* Production Kafka configuration
* Automated CI/CD
* Cloud deployment
* Adaptive bitrate improvements
* More comprehensive integration tests

---

# License

This project is for learning and portfolio purposes.
