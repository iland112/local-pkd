# Local PKD Container - Native Image
FROM debian:bookworm-slim

# Install required libraries for native image and curl for healthcheck
RUN apt-get update && apt-get install -y --no-install-recommends \
    libfreetype6 \
    fontconfig \
    curl \
    && rm -rf /var/lib/apt/lists/*

# Create app directory
WORKDIR /app

# Copy native image
COPY target/local-pkd /app/local-pkd

# Copy static resources (Thymeleaf templates, static files)
COPY src/main/resources/templates /app/templates
COPY src/main/resources/static /app/static

# Make executable
RUN chmod +x /app/local-pkd

# Expose port
EXPOSE 8081

# Environment variables (can be overridden)
ENV SPRING_PROFILES_ACTIVE=container
ENV SERVER_ADDRESS=0.0.0.0
ENV SERVER_PORT=8081

# Run native image
ENTRYPOINT ["/app/local-pkd"]
